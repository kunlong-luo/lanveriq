use clap::Parser;
#[cfg(target_os = "linux")]
use clap::Subcommand;
use lanveriq_agent::config::AgentConfig;
use lanveriq_agent::runtime::run_until_shutdown;
#[cfg(target_os = "linux")]
use lanveriq_agent::{
    inventory_platform::collect_minimal_linux_inventory,
    store::{InventoryStage, OutboxStore},
    transport::{AgentTlsTransport, DeliveryDisposition},
};
#[cfg(target_os = "linux")]
use std::path::Path;
use std::path::PathBuf;
#[cfg(target_os = "linux")]
use uuid::Uuid;

/// The installed service stays idle. The Linux-only subcommands are explicit,
/// single-shot developer operations, never invoked by systemd or a timer.
#[derive(Debug, Parser)]
#[command(version, about)]
struct Args {
    /// Required absolute path to a root/operator-managed idle-service TOML file.
    #[arg(long)]
    config: Option<PathBuf>,

    #[cfg(target_os = "linux")]
    #[command(subcommand)]
    command: Option<AgentCommand>,

    /// Only for a service registered explicitly through Windows SCM.
    #[cfg(windows)]
    #[arg(long, hide = true)]
    service: bool,
}

#[cfg(target_os = "linux")]
#[derive(Debug, Subcommand)]
enum AgentCommand {
    /// Collect only Linux OS/CPU/RAM once and stage it in the SQLite outbox.
    Once {
        /// Absolute path to the existing-parent SQLite outbox file.
        #[arg(long)]
        state_file: PathBuf,
        /// Previously enrolled device UUID (never derived from local hardware).
        #[arg(long)]
        device_id: Uuid,
        /// Explicit UTC observation time, e.g. 2026-11-26T08:00:00Z.
        #[arg(long)]
        observed_at: String,
    },
    /// Send one verified pending inventory over the pinned TLS 1.3/mTLS origin.
    Send {
        /// Absolute path to the same SQLite outbox file used by once.
        #[arg(long)]
        state_file: PathBuf,
        /// The same already enrolled device UUID used by once.
        #[arg(long)]
        device_id: Uuid,
        /// Absolute path to the approved server CA PEM.
        #[arg(long)]
        ca_pem: PathBuf,
        /// Absolute path to a restricted PEM containing client cert and key.
        #[arg(long)]
        identity_pem: PathBuf,
    },
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .with_target(false)
        .init();
    let args = Args::parse();

    #[cfg(target_os = "linux")]
    if let Some(command) = args.command {
        if args.config.is_some() {
            return Err("--config is for the idle service, not once/send".into());
        }
        return match command {
            AgentCommand::Once {
                state_file,
                device_id,
                observed_at,
            } => {
                let stage = stage_once(&state_file, device_id, &observed_at).await?;
                match stage {
                    InventoryStage::Staged(pending) => println!(
                        "staged: device={device_id}, snapshot={}, revision={}; not sent",
                        pending.snapshot_id, pending.revision
                    ),
                    InventoryStage::AlreadyPending(pending) => println!(
                        "already pending: device={device_id}, snapshot={}, revision={}; not sent",
                        pending.snapshot_id, pending.revision
                    ),
                    InventoryStage::Unchanged => println!("unchanged: no new snapshot; not sent"),
                }
                Ok(())
            }
            AgentCommand::Send {
                state_file,
                device_id,
                ca_pem,
                identity_pem,
            } => send_once(&state_file, device_id, &ca_pem, &identity_pem).await,
        };
    }

    let config_path = args
        .config
        .ok_or("idle service requires --config <absolute TOML path>")?;
    #[cfg(windows)]
    if args.service {
        // Only SCM can successfully start the service dispatcher.
        lanveriq_agent::windows_service::dispatch(config_path)?;
        return Ok(());
    }

    let config = AgentConfig::load(&config_path)?;
    tracing::info!("AGT-01 lifecycle started; inventory and network disabled");
    run_until_shutdown(&config, wait_for_shutdown()).await;
    Ok(())
}

/// Explicit one-shot collection. No connection, timer, or background task.
#[cfg(target_os = "linux")]
async fn stage_once(
    state_file: &Path,
    device_id: Uuid,
    observed_at: &str,
) -> Result<InventoryStage, Box<dyn std::error::Error>> {
    if device_id.is_nil() {
        return Err("an enrolled, non-nil --device-id is required".into());
    }
    // Open and validate the durable store before reading any host attributes.
    let store = OutboxStore::open(state_file).await?;
    let raw = collect_minimal_linux_inventory();
    let stage = store
        .stage_inventory_snapshot(device_id, observed_at, raw)
        .await?;
    store.close().await;
    Ok(stage)
}

/// Only the existing adapter can acknowledge the exact persisted snapshot.
/// Neither this command nor the idle daemon enables periodic networking.
#[cfg(target_os = "linux")]
async fn send_once(
    state_file: &Path,
    device_id: Uuid,
    ca_pem: &Path,
    identity_pem: &Path,
) -> Result<(), Box<dyn std::error::Error>> {
    if device_id.is_nil() {
        return Err("an enrolled, non-nil --device-id is required".into());
    }
    let ca = read_pem_file(ca_pem, false)?;
    let identity = read_pem_file(identity_pem, true)?;
    // The adapter rejects any other origin, plaintext, redirects and proxies.
    let transport = AgentTlsTransport::new("https://agent.lanveriq.internal", &ca, &identity)?;
    let store = OutboxStore::open(state_file).await?;
    let result = transport.send_next_inventory(&store, device_id).await?;
    let still_pending = store.verified_pending_inventory(device_id).await?.is_some();
    store.close().await;

    match result {
        None => println!("no verified pending snapshot; nothing sent"),
        Some(DeliveryDisposition::Accepted) => {
            println!("exact server ACK accepted; remaining_pending={still_pending}")
        }
        Some(other) => {
            return Err(format!(
                "upload was not acknowledged ({other:?}); local pending snapshot retained"
            )
            .into());
        }
    }
    Ok(())
}

/// Refuse relative, symlinked, empty, oversized and group/world-readable
/// client-key files. Never print PEM bytes or include them in command args.
#[cfg(target_os = "linux")]
fn read_pem_file(
    path: &Path,
    private_identity: bool,
) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    use std::os::unix::fs::PermissionsExt;

    if !path.is_absolute() {
        return Err("PEM paths must be absolute".into());
    }
    let meta = std::fs::symlink_metadata(path)?;
    if !meta.is_file() || meta.len() == 0 || meta.len() > 256 * 1024 {
        return Err("PEM must be a nonempty regular file of at most 256 KiB".into());
    }
    if private_identity && (meta.permissions().mode() & 0o077 != 0) {
        return Err("client identity PEM must not be readable by group or others".into());
    }
    let bytes = std::fs::read(path)?;
    if bytes.is_empty() || bytes.len() > 256 * 1024 {
        return Err("PEM content must be between 1 byte and 256 KiB".into());
    }
    Ok(bytes)
}

async fn wait_for_shutdown() {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{SignalKind, signal};
        let mut terminate = match signal(SignalKind::terminate()) {
            Ok(stream) => stream,
            Err(_) => {
                tracing::error!("SIGTERM listener unavailable; exiting safely");
                return;
            }
        };
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {},
            _ = terminate.recv() => {},
        }
    }

    #[cfg(not(unix))]
    {
        if tokio::signal::ctrl_c().await.is_err() {
            tracing::error!("console signal listener unavailable; exiting safely");
        }
    }
}

#[cfg(all(test, target_os = "linux"))]
mod tests {
    use super::*;
    use clap::CommandFactory;
    use std::os::unix::fs::PermissionsExt;

    #[test]
    fn cli_requires_explicit_one_shot_arguments_and_keeps_idle_entrypoint() {
        Args::command().debug_assert();
        assert!(
            Args::try_parse_from(["agent", "--config", "/etc/lanveriq-agent/agent.toml"]).is_ok()
        );
        assert!(
            Args::try_parse_from([
                "agent",
                "once",
                "--state-file",
                "/tmp/inventory.sqlite",
                "--device-id",
                "018f9fad-5c71-7db1-b89e-b71f8d620f44",
                "--observed-at",
                "2026-11-26T08:00:00Z",
            ])
            .is_ok()
        );
        assert!(Args::try_parse_from(["agent", "once"]).is_err());
        assert!(Args::try_parse_from(["agent", "send"]).is_err());
    }

    #[tokio::test]
    async fn explicit_once_stages_only_minimal_linux_fields_and_survives_restart() {
        let tmp = tempfile::tempdir().unwrap();
        let state = tmp.path().join("outbox.sqlite");
        let device = Uuid::now_v7();
        let stage = stage_once(&state, device, "2026-11-26T08:00:00Z")
            .await
            .unwrap();
        assert!(matches!(stage, InventoryStage::Staged(_)));
        let store = OutboxStore::open(&state).await.unwrap();
        let pending = store
            .verified_pending_inventory(device)
            .await
            .unwrap()
            .unwrap();
        let body: serde_json::Value = serde_json::from_str(&pending.body_json).unwrap();
        assert_eq!(body["device_id"], device.to_string());
        assert_eq!(body["os"]["family"], "linux");
        assert!(body["interfaces"].as_array().unwrap().is_empty());
        assert!(body["volumes"].as_array().unwrap().is_empty());
        assert_eq!(body["capabilities"]["network_interfaces"], "not_collected");
        assert_eq!(body["capabilities"]["mounted_volumes"], "not_collected");
        store.close().await;

        let stage_again = stage_once(&state, device, "2026-11-26T08:01:00Z")
            .await
            .unwrap();
        assert!(matches!(
            stage_again,
            InventoryStage::AlreadyPending(_) | InventoryStage::Staged(_)
        ));
        let store = OutboxStore::open(&state).await.unwrap();
        assert!(
            store
                .verified_pending_inventory(device)
                .await
                .unwrap()
                .is_some()
        );
        assert!(
            store
                .verified_pending_inventory(Uuid::now_v7())
                .await
                .is_err()
        );
    }

    #[tokio::test]
    async fn invalid_tls_input_refuses_send_without_clearing_pending() {
        let tmp = tempfile::tempdir().unwrap();
        let state = tmp.path().join("outbox.sqlite");
        let device = Uuid::now_v7();
        stage_once(&state, device, "2026-11-26T08:00:00Z")
            .await
            .unwrap();
        assert!(
            send_once(
                &state,
                device,
                &tmp.path().join("missing-ca.pem"),
                &tmp.path().join("missing-identity.pem"),
            )
            .await
            .is_err()
        );
        let store = OutboxStore::open(&state).await.unwrap();
        assert!(
            store
                .verified_pending_inventory(device)
                .await
                .unwrap()
                .is_some()
        );
    }

    #[test]
    fn identity_must_be_private_regular_absolute_pem_file() {
        let tmp = tempfile::tempdir().unwrap();
        let secret = tmp.path().join("identity.pem");
        std::fs::write(&secret, b"synthetic-not-a-real-key").unwrap();
        std::fs::set_permissions(&secret, std::fs::Permissions::from_mode(0o644)).unwrap();
        assert!(read_pem_file(&secret, true).is_err());
        assert!(read_pem_file(&secret, false).is_ok());
        std::fs::set_permissions(&secret, std::fs::Permissions::from_mode(0o600)).unwrap();
        assert!(read_pem_file(&secret, true).is_ok());
        assert!(read_pem_file(Path::new("identity.pem"), true).is_err());
        let link = tmp.path().join("identity-link.pem");
        std::os::unix::fs::symlink(&secret, &link).unwrap();
        assert!(read_pem_file(&link, true).is_err());
    }
}
