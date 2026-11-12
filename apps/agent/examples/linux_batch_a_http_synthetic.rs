//! CI-only real Reqwest/Rustls POST probe against a disposable Caddy/Tomcat/PG18 stack.
//! This is never linked into or invoked from the installed Agent runtime.
//! The test runner must provide a loopback-only DNS entry and throwaway PKI.
#[cfg(target_os = "linux")]
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    use lanveriq_agent::transport::{
        ActivationRequest, AgentTlsTransport, DeliveryDisposition, Heartbeat,
    };
    use std::{env, fs, path::PathBuf};
    use uuid::Uuid;

    if env::var("GITHUB_ACTIONS").as_deref() != Ok("true")
        || env::var("RUNNER_OS").as_deref() != Ok("Linux")
        || env::var("LANVERIQ_SYNTHETIC_AGENT_HTTP_ONLY").as_deref() != Ok("yes")
    {
        return Err("only explicit disposable Linux GitHub Actions can issue synthetic POSTs".into());
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
    let assert_disposition = |actual: DeliveryDisposition, wanted: DeliveryDisposition| {
        if actual == wanted {
            Ok(())
        } else {
            Err("synthetic server acknowledgement or identity classification mismatch")
        }
    };
    if args[1] == "accepted" {
        let activation = ActivationRequest::synthetic(device);
        assert_disposition(
            client.send_activation(&activation).await?,
            DeliveryDisposition::Accepted,
        )?;
        let first = Heartbeat::synthetic(device, "2026-11-12T09:00:00Z".into());
        let mut second = Heartbeat::synthetic(device, "2026-11-12T09:01:00Z".into());
        second.status = "degraded";
        second.policy_version = 2;
        second.integrity_degraded = true;
        assert_disposition(client.send_heartbeat(&first).await?, DeliveryDisposition::Accepted)?;
        assert_disposition(client.send_heartbeat(&second).await?, DeliveryDisposition::Accepted)?;
        // Persist the first outbound JSON for a separate real HTTP 409 probe.
        // This is an operational-only synthetic payload; no device collection.
        fs::write(env::var("LANVERIQ_SYNTHETIC_FIRST_HEARTBEAT_FILE")?,
                  serde_json::to_vec(&first)?)?;
        assert_disposition(client.send_heartbeat(&first).await?, DeliveryDisposition::Quarantine)?;
        let wrong_device = Uuid::parse_str("00000000-0000-7000-8000-000000000002")?;
        let mismatch = Heartbeat::synthetic(wrong_device, "2026-11-12T09:02:00Z".into());
        assert_disposition(
            client.send_heartbeat(&mismatch).await?,
            DeliveryDisposition::PauseIdentity,
        )?;
        println!("PASS: real Rust Reqwest TLS1.3 activation, two committed heartbeats, strict ACK, replay quarantine and cross-device denial.");
    } else {
        let denied = Heartbeat::synthetic(device, "2026-11-12T09:03:00Z".into());
        assert_disposition(
            client.send_heartbeat(&denied).await?,
            DeliveryDisposition::PauseIdentity,
        )?;
        println!("PASS: real Rust Reqwest heartbeat denied after synthetic credential {}.", args[1]);
    }
    Ok(())
}

#[cfg(not(target_os = "linux"))]
fn main() {
    panic!("Linux CI-only synthetic HTTP probe");
}
