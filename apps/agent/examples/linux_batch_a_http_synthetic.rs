//! CI-only real Reqwest/Rustls POST probe against a disposable Caddy/Tomcat/PG18 stack.
//! This is never linked into or invoked from the installed Agent runtime.
//! The test runner must provide a loopback-only DNS entry and throwaway PKI.
#[cfg(target_os = "linux")]
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    use lanveriq_agent::first_pin_review::{
        FirstPinReviewObservation, OfflineFirstPinReview, inspect_offline_first_pin_review,
    };
    use lanveriq_agent::heartbeat_credential_pin::{
        HeartbeatEpochPreflightObservation, inspect_explicit_heartbeat_epoch_preflight,
    };
    use lanveriq_agent::heartbeat_runtime::{HeartbeatAttemptOutcome, run_explicit_heartbeat_once};
    use lanveriq_agent::heartbeat_schedule::HeartbeatHalt;
    use lanveriq_agent::store::OutboxStore;
    use lanveriq_agent::transport::{
        ActivationRequest, AgentTlsTransport, CredentialEpochDisposition, DeliveryDisposition,
        Heartbeat, HeartbeatLookupDisposition, TransportError,
    };
    use ring::rand::SystemRandom;
    use ring::signature::{Ed25519KeyPair, KeyPair};
    use std::{
        cell::Cell,
        env, fs,
        path::PathBuf,
        time::{SystemTime, UNIX_EPOCH},
    };
    use uuid::Uuid;

    if env::var("GITHUB_ACTIONS").as_deref() != Ok("true")
        || env::var("RUNNER_OS").as_deref() != Ok("Linux")
        || env::var("LANVERIQ_SYNTHETIC_AGENT_HTTP_ONLY").as_deref() != Ok("yes")
    {
        return Err(
            "only explicit disposable Linux GitHub Actions can issue synthetic POSTs".into(),
        );
    }
    let args: Vec<String> = env::args().collect();
    if args.len() != 3 || !matches!(args[1].as_str(), "accepted" | "revoked" | "expired") {
        return Err("expected one test phase and one disposable device UUID".into());
    }
    let device = Uuid::parse_str(&args[2])?;
    if device.to_string() != "00000000-0000-7000-8000-000000000001" {
        return Err("only the fixed disposable fixture ID is permitted".into());
    }
    let pki = PathBuf::from(env::var("LANVERIQ_SYNTHETIC_AGENT_HTTP_PKI")?);
    if !pki.is_absolute()
        || pki.file_name().and_then(|s| s.to_str()) != Some("pki")
        || !pki.starts_with("/tmp")
    {
        return Err("synthetic PKI must be inside a private temporary directory".into());
    }
    let ca = fs::read(env::var("LANVERIQ_SYNTHETIC_AGENT_HTTP_CA")?)?;
    let mut identity = fs::read(pki.join("agent.crt"))?;
    identity.extend_from_slice(b"\n");
    identity.extend_from_slice(&fs::read(pki.join("agent.key"))?);
    let client = AgentTlsTransport::new("https://agent.lanveriq.internal", &ca, &identity)?;
    if args[1] == "accepted" {
        let activation = ActivationRequest::synthetic(device);
        assert_disposition(
            "activation",
            client.send_activation(&activation).await?,
            DeliveryDisposition::Accepted,
        )?;
        // A3: only this throwaway, explicitly approved CI example is wired
        // to the SQLite attempt coordinator. The installed Agent still has
        // no sender task, timer, credentials or networking enabled.
        let state_file = pki
            .parent()
            .ok_or("synthetic PKI parent missing")?
            .join("synthetic-heartbeat.sqlite");
        let first_request_id = Cell::new(Uuid::nil());
        let store = OutboxStore::open(&state_file).await?;
        let first_outcome = run_explicit_heartbeat_once(&store, device, true, 1_000, 30, |id| {
            first_request_id.set(id);
            let mut heartbeat = Heartbeat::synthetic(device, "2026-11-12T09:00:00Z".into());
            heartbeat.request_id = id;
            let sender = &client;
            async move { sender.send_heartbeat(&heartbeat).await }
        })
        .await?;
        if first_outcome != HeartbeatAttemptOutcome::Accepted {
            return Err("synthetic first heartbeat did not earn a strict ACK".into());
        }
        store.close().await;

        // Reopen the same SQLite file. The second POST will commit in PG18,
        // but an injected local ACK-loss window must retain its pending UUID.
        let store = OutboxStore::open(&state_file).await?;
        let second_request_id = Cell::new(Uuid::nil());
        let second_outcome = run_explicit_heartbeat_once(&store, device, true, 1_030, 30, |id| {
            second_request_id.set(id);
            let mut heartbeat = Heartbeat::synthetic(device, "2026-11-12T09:01:00Z".into());
            heartbeat.request_id = id;
            heartbeat.status = "degraded";
            heartbeat.policy_version = 2;
            heartbeat.integrity_degraded = true;
            let sender = &client;
            async move {
                let disposition = sender.send_heartbeat(&heartbeat).await?;
                if disposition != DeliveryDisposition::Accepted {
                    return Ok(disposition);
                }
                // The server has returned its strict ACK, but the caller
                // deliberately loses it before updating the local checkpoint.
                Err(TransportError::InvalidAcknowledgement)
            }
        })
        .await;
        if !matches!(
            second_outcome,
            Err(
                lanveriq_agent::heartbeat_runtime::HeartbeatDriverError::Transport(
                    TransportError::InvalidAcknowledgement
                )
            )
        ) {
            return Err("synthetic ACK-loss injection was not quarantined".into());
        }
        store.close().await;

        let store = OutboxStore::open(&state_file).await?;
        let state = store.load_heartbeat_schedule_recovery(device).await?;
        if state.generation != 4
            || state.checkpoint.as_ref().is_none_or(|checkpoint| {
                checkpoint.pending_request_id != Some(second_request_id.get())
                    || checkpoint.last_acknowledged_at != Some(1_000)
                    || checkpoint.halted != Some(HeartbeatHalt::Quarantine)
            })
        {
            return Err("synthetic ACK-loss checkpoint did not survive restart".into());
        }

        // The server must prove *durable ledger membership*, with the same
        // current registered leaf via both Caddy/Tomcat TLS1.3 hops. An
        // unknown UUID is unresolved, NOT negative proof for retransmission.
        assert_eq!(
            client
                .lookup_heartbeat_request(device, first_request_id.get())
                .await?,
            HeartbeatLookupDisposition::Committed
        );
        assert_eq!(
            client
                .lookup_heartbeat_request(device, second_request_id.get())
                .await?,
            HeartbeatLookupDisposition::Committed
        );
        // A7: real TLS1.3 current-certificate epoch preflight. The
        // certificate fingerprint comes only from the loaded local DER,
        // never an arbitrary caller-provided expected fingerprint. Because
        // ACK-loss produced a durable checkpoint, this MUST remain unbound
        // and cannot install a retroactively attributed SQLite pin.
        let preflight = inspect_explicit_heartbeat_epoch_preflight(&store, device, true, |id| {
            client.observe_current_credential_epoch(id)
        })
        .await?;
        if preflight != HeartbeatEpochPreflightObservation::UnboundNeedsReview {
            return Err("synthetic epoch preflight unexpectedly granted local identity".into());
        }
        if store.load_heartbeat_credential_pin(device).await?.is_some() {
            return Err("synthetic epoch preflight silently installed a local pin".into());
        }
        let after_preflight = store.load_heartbeat_schedule_recovery(device).await?;
        if after_preflight.generation != state.generation
            || after_preflight.checkpoint != state.checkpoint
        {
            return Err("synthetic preflight modified durable pending/quarantine".into());
        }
        let wrong_epoch_device = Uuid::parse_str("00000000-0000-7000-8000-000000000002")?;
        if client
            .observe_current_credential_epoch(wrong_epoch_device)
            .await?
            != CredentialEpochDisposition::PauseIdentity
        {
            return Err("synthetic cross-device epoch read was not denied".into());
        }
        println!(
            "PASS: real Rust TLS1.3 SQLite epoch preflight is unbound-needs-review, no pin/ACK release and durable quarantine unchanged."
        );
        // A8: a fresh, entirely synthetic reviewer key exists only in
        // this disposable CI process. Its signature is NOT a real admin
        // authorization or a deployable trust root. The binding includes
        // a CURRENT TLS DER-bound observation from the actual mTLS client.
        let verified_epoch = match client.observe_current_credential_epoch(device).await? {
            CredentialEpochDisposition::ObservedOnly(epoch) => epoch,
            _ => return Err("synthetic current TLS epoch unavailable for signature test".into()),
        };
        let random = SystemRandom::new();
        let test_pkcs8 = Ed25519KeyPair::generate_pkcs8(&random)
            .map_err(|_| "cannot create disposable CI reviewer key")?;
        let ephemeral_reviewer = Ed25519KeyPair::from_pkcs8(test_pkcs8.as_ref())
            .map_err(|_| "invalid disposable CI reviewer key")?;
        let now = SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs();
        let mut signed_review = OfflineFirstPinReview {
            schema_version: 1,
            device_id: verified_epoch.device_id,
            credential_epoch_id: verified_epoch.credential_epoch_id,
            cert_sha256_fingerprint: verified_epoch.cert_sha256_fingerprint.clone(),
            review_id: Uuid::now_v7(),
            not_before_unix: now - 30,
            expires_at_unix: now + 300,
            signature_hex: String::new(),
        };
        signed_review.signature_hex = hex::encode(
            ephemeral_reviewer
                .sign(&signed_review.signing_payload()?)
                .as_ref(),
        );
        if inspect_offline_first_pin_review(
            &signed_review,
            &verified_epoch,
            ephemeral_reviewer.public_key().as_ref(),
            now,
        )? != FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly
        {
            return Err("synthetic detached review signature did not verify".into());
        }
        let mut forged_review = signed_review.clone();
        forged_review.review_id = Uuid::now_v7();
        if inspect_offline_first_pin_review(
            &forged_review,
            &verified_epoch,
            ephemeral_reviewer.public_key().as_ref(),
            now,
        )
        .is_ok()
        {
            return Err("synthetic tampered review signature was accepted".into());
        }
        if store.load_heartbeat_credential_pin(device).await?.is_some()
            || store
                .load_heartbeat_schedule_recovery(device)
                .await?
                .generation
                != state.generation
        {
            return Err("synthetic review signature implicitly modified SQLite".into());
        }
        println!(
            "PASS: real TLS-bound epoch and synthetic Ed25519 detached review match; tampered review denied and durable ACK quarantine unchanged."
        );
        let missing = Uuid::now_v7();
        assert_eq!(
            client.lookup_heartbeat_request(device, missing).await?,
            HeartbeatLookupDisposition::Unresolved
        );
        let resend_count = Cell::new(0);
        let no_resend = run_explicit_heartbeat_once(&store, device, true, 100_000, 30, |_| {
            resend_count.set(resend_count.get() + 1);
            async { Ok(DeliveryDisposition::Accepted) }
        })
        .await?;
        if no_resend != HeartbeatAttemptOutcome::Halted(HeartbeatHalt::Quarantine)
            || resend_count.get() != 0
        {
            return Err("synthetic ledger read implicitly cleared quarantine".into());
        }
        let unchanged = store.load_heartbeat_schedule_recovery(device).await?;
        if unchanged.generation != state.generation || unchanged.checkpoint != state.checkpoint {
            return Err("synthetic ledger read mutated the local checkpoint".into());
        }
        store.close().await;

        // A real duplicate wire request is an intentional negative probe,
        // never an automatic retry by the now-settled coordinator.
        let mut first = Heartbeat::synthetic(device, "2026-11-12T09:00:00Z".into());
        first.request_id = first_request_id.get();
        // Persist the first outbound JSON for a separate real HTTP 409 probe.
        // This is an operational-only synthetic payload; no device collection.
        fs::write(
            env::var("LANVERIQ_SYNTHETIC_FIRST_HEARTBEAT_FILE")?,
            serde_json::to_vec(&first)?,
        )?;
        assert_disposition(
            "heartbeat-replay",
            client.send_heartbeat(&first).await?,
            DeliveryDisposition::Quarantine,
        )?;
        let wrong_device = Uuid::parse_str("00000000-0000-7000-8000-000000000002")?;
        assert_eq!(
            client
                .lookup_heartbeat_request(wrong_device, first_request_id.get())
                .await?,
            HeartbeatLookupDisposition::PauseIdentity
        );
        let mismatch = Heartbeat::synthetic(wrong_device, "2026-11-12T09:02:00Z".into());
        assert_disposition(
            "heartbeat-wrong-device",
            client.send_heartbeat(&mismatch).await?,
            DeliveryDisposition::PauseIdentity,
        )?;
        println!(
            "PASS: real Rust Reqwest TLS1.3 activation, SQLite restart/strict ACK, injected post-commit ACK loss with durable quarantine, committed/unknown read-only ledger lookup, replay and wrong-device denial."
        );
    } else {
        let denied = Heartbeat::synthetic(device, "2026-11-12T09:03:00Z".into());
        assert_disposition(
            "heartbeat-denied-credential",
            client.send_heartbeat(&denied).await?,
            DeliveryDisposition::PauseIdentity,
        )?;
        let first: serde_json::Value = serde_json::from_slice(&fs::read(env::var(
            "LANVERIQ_SYNTHETIC_FIRST_HEARTBEAT_FILE",
        )?)?)?;
        let request_id = Uuid::parse_str(
            first["request_id"]
                .as_str()
                .ok_or("missing disposable request UUID")?,
        )?;
        assert_eq!(
            client.lookup_heartbeat_request(device, request_id).await?,
            HeartbeatLookupDisposition::PauseIdentity
        );
        // Independently reject read-only epoch access with the same revoked
        // or expired credential after the server-side status transition.
        assert_eq!(
            client.observe_current_credential_epoch(device).await?,
            CredentialEpochDisposition::PauseIdentity
        );
        println!(
            "PASS: real Rust Reqwest heartbeat and read-only ledger lookup denied after synthetic credential {}.",
            args[1]
        );
    }
    Ok(())
}

#[cfg(target_os = "linux")]
fn assert_disposition(
    stage: &str,
    actual: lanveriq_agent::transport::DeliveryDisposition,
    wanted: lanveriq_agent::transport::DeliveryDisposition,
) -> Result<(), Box<dyn std::error::Error>> {
    if actual == wanted {
        Ok(())
    } else {
        Err(format!("synthetic {stage}: response {actual:?}, expected {wanted:?}").into())
    }
}

#[cfg(not(target_os = "linux"))]
fn main() {
    panic!("Linux CI-only synthetic HTTP probe");
}
