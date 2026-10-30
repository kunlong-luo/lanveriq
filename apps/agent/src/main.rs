use clap::Parser;
use lanveriq_agent::config::AgentConfig;
use lanveriq_agent::runtime::run_until_shutdown;
use std::path::PathBuf;

/// AGT-01: explicit local service lifecycle; no network or device collection.
#[derive(Debug, Parser)]
#[command(version, about)]
struct Args {
    /// Required absolute path to a root/operator-managed TOML file.
    #[arg(long)]
    config: PathBuf,

    /// Only for a service registered explicitly through Windows SCM.
    #[cfg(windows)]
    #[arg(long, hide = true)]
    service: bool,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .with_target(false)
        .init();
    let args = Args::parse();
    #[cfg(windows)]
    if args.service {
        // Only SCM can successfully start the service dispatcher.
        lanveriq_agent::windows_service::dispatch(args.config)?;
        return Ok(());
    }

    let config = AgentConfig::load(&args.config)?;
    tracing::info!("AGT-01 lifecycle started; inventory and network disabled");
    run_until_shutdown(&config, wait_for_shutdown()).await;
    Ok(())
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
