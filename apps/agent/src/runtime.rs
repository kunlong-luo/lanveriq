use crate::config::AgentConfig;
use std::future::Future;
use std::time::Duration;
use tokio::time::{MissedTickBehavior, interval};

/// A cancellable, resource-bounded lifecycle. No actual collection or network
/// activity is performed in AGT-01. The interval is only a service liveness
/// scheduling primitive until authorized heartbeat work is implemented.
pub async fn run_until_shutdown<F>(config: &AgentConfig, shutdown: F)
where
    F: Future<Output = ()>,
{
    let mut ticks = interval(Duration::from_secs(config.runtime.cycle_seconds));
    ticks.set_missed_tick_behavior(MissedTickBehavior::Skip);
    tokio::pin!(shutdown);

    loop {
        tokio::select! {
            biased;
            () = &mut shutdown => {
                tracing::info!("agent stopping");
                break;
            }
            _ = ticks.tick() => {
                tracing::trace!("idle AGT-01 lifecycle tick (no collection)");
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::AgentConfig;

    #[tokio::test]
    async fn cancellation_wins_over_immediate_tick() {
        let config = AgentConfig::parse(
            "schema_version = 1\n[runtime]\ncycle_seconds = 10\n",
        )
        .unwrap();
        let finished = tokio::time::timeout(
            Duration::from_secs(1),
            run_until_shutdown(&config, async {}),
        )
        .await;
        assert!(finished.is_ok());
    }

    #[tokio::test]
    async fn cancels_without_waiting_for_next_long_tick() {
        let config = AgentConfig::parse(
            "schema_version = 1\n[runtime]\ncycle_seconds = 300\n",
        )
        .unwrap();
        let (sender, receiver) = tokio::sync::oneshot::channel::<()>();
        let future = run_until_shutdown(&config, async {
            let _ = receiver.await;
        });
        tokio::pin!(future);
        tokio::select! {
            _ = &mut future => panic!("agent exited before shutdown"),
            _ = tokio::time::sleep(Duration::from_millis(10)) => {}
        }
        sender.send(()).unwrap();
        assert!(
            tokio::time::timeout(Duration::from_secs(1), future)
                .await
                .is_ok()
        );
    }
}
