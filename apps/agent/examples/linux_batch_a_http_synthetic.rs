//! CI-only real Reqwest/Rustls POST probe against a disposable Caddy/Tomcat/PG18 stack.
//! This is never linked into or invoked from the installed Agent runtime.
//! The test runner must provide a loopback-only DNS entry and throwaway PKI.
#[cfg(target_os = "linux")]
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    use lanveriq_agent::heartbeat_runtime::{HeartbeatAttemptOutcome, run_explicit_heartbeat_once};
    use lanveriq_agent::store::OutboxStore;
    use lanveriq_agent::transport::{
        ActivationRequest, AgentTlsTransport, DeliveryDisposition, Heartbeat,
        HeartbeatLookupDisposition,
    };
    use std::{cell::Cell, env, fs, path::PathBuf};
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

        // Reopen the REAL same SQLite file, not an in-memory fake or a new
        // heartbeat ID store. Only an exactly acknowledged attempt clears
        // pending and permits another send after the configured interval.
        let store = OutboxStore::open(&state_file).await?;
        let second_outcome = run_explicit_heartbeat_once(&store, device, true, 1_030, 30, |id| {
            let mut heartbeat = Heartbeat::synthetic(device, "2026-11-12T09:01:00Z".into());
            heartbeat.request_id = id;
            heartbeat.status = "degraded";
            heartbeat.policy_version = 2;
            heartbeat.integrity_degraded = true;
            let sender = &client;
            async move { sender.send_heartbeat(&heartbeat).await }
        })
        .await?;
        if second_outcome != HeartbeatAttemptOutcome::Accepted {
            return Err("synthetic second heartbeat did not earn a strict ACK".into());
        }
        let state = store.load_heartbeat_schedule_recovery(device).await?;
        if state.generation != 4
            || state.checkpoint.is_none_or(|checkpoint| {
                checkpoint.pending_request_id.is_some()
                    || checkpoint.last_acknowledged_at != Some(1_030)
                    || checkpoint.halted.is_some()
            })
        {
            return Err("synthetic persistent heartbeat recovery did not match strict ACKs".into());
        }
        store.close().await;

        // The server must prove *durable ledger membership*, with the same
        // current registered leaf via both Caddy/Tomcat TLS1.3 hops. An
        // unknown UUID is unresolved, NOT negative proof for retransmission.
        assert_eq!(
            client
                .lookup_heartbeat_request(device, first_request_id.get())
                .await?,
            HeartbeatLookupDisposition::Committed
        );
        let missing = Uuid::now_v7();
        assert_eq!(
            client.lookup_heartbeat_request(device, missing).await?,
            HeartbeatLookupDisposition::Unresolved
        );

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
            "PASS: real Rust Reqwest TLS1.3 activation, SQLite restart/strict ACK, committed/unknown read-only ledger lookup, replay and wrong-device denial."
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
