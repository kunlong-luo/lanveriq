//! Explicit single-attempt coordinator for Batch A3 synthetic/authorized callers.
//! This module does not run a timer, construct telemetry or change the installed
//! Agent service. The caller must separately authorize and validate a request
//! whose UUID equals the supplied durable attempt ID.
use crate::heartbeat_schedule::{
    HeartbeatCheckpoint, HeartbeatDecision, HeartbeatHalt, HeartbeatScheduleError,
};
use crate::store::{OutboxStore, StoreError};
use crate::transport::{DeliveryDisposition, TransportError};
use std::future::Future;
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatAttemptOutcome {
    Disabled,
    WaitUntil(u64),
    NeedsReconciliation(Uuid),
    Halted(HeartbeatHalt),
    Accepted,
}

#[derive(Debug, Error)]
pub enum HeartbeatDriverError {
    #[error("local heartbeat policy refused request")]
    Schedule(#[from] HeartbeatScheduleError),
    #[error("durable heartbeat checkpoint could not be saved")]
    Store(#[from] StoreError),
    #[error("heartbeat sender did not return a verified response")]
    Transport(#[from] TransportError),
}

/// The only allowed sequence is: recover -> decide -> persist pending UUID ->
/// attempt exactly ONE send -> persist the already-classified response.
/// A crash at any step leaves a pending UUID, which forces reconciliation
/// rather than automatic retransmission with a changed body/request identity.
/// This helper is NOT called by the Agent service or systemd unit.
pub async fn run_explicit_heartbeat_once<F, Fut>(
    store: &OutboxStore,
    device_id: Uuid,
    enabled: bool,
    now: u64,
    interval_seconds: u64,
    sender: F,
) -> Result<HeartbeatAttemptOutcome, HeartbeatDriverError>
where
    F: FnOnce(Uuid) -> Fut,
    Fut: Future<Output = Result<DeliveryDisposition, TransportError>>,
{
    // This legacy driver has no authenticated certificate/epoch evidence.
    // Once a local epoch is pinned, even a valid-looking original ACK must
    // never let this old entry point send or release a pending request.
    if store
        .load_heartbeat_credential_pin(device_id)
        .await?
        .is_some()
    {
        return Err(HeartbeatDriverError::Store(StoreError::HeartbeatConflict));
    }
    let recovery = store.load_heartbeat_schedule_recovery(device_id).await?;
    let mut checkpoint = match recovery.checkpoint {
        Some(value) => value,
        None => HeartbeatCheckpoint::initial(device_id)?,
    };
    match checkpoint.decide(enabled, now, interval_seconds)? {
        HeartbeatDecision::Disabled => return Ok(HeartbeatAttemptOutcome::Disabled),
        HeartbeatDecision::WaitUntil(until) => {
            return Ok(HeartbeatAttemptOutcome::WaitUntil(until));
        }
        HeartbeatDecision::NeedsReconciliation(id) => {
            return Ok(HeartbeatAttemptOutcome::NeedsReconciliation(id));
        }
        HeartbeatDecision::Halted(reason) => {
            return Ok(HeartbeatAttemptOutcome::Halted(reason));
        }
        HeartbeatDecision::Ready => {}
    }

    let request_id = Uuid::now_v7();
    checkpoint.begin(enabled, now, interval_seconds, request_id)?;
    let pending_generation = store
        .save_heartbeat_schedule_checkpoint(&checkpoint, recovery.generation)
        .await?;

    let disposition = match sender(request_id).await {
        Ok(disposition) => disposition,
        Err(error) => {
            checkpoint.note_uncertain_outcome()?;
            store
                .save_heartbeat_schedule_checkpoint(&checkpoint, pending_generation)
                .await?;
            return Err(HeartbeatDriverError::Transport(error));
        }
    };
    checkpoint.finish(request_id, now, disposition.clone())?;
    store
        .save_heartbeat_schedule_checkpoint(&checkpoint, pending_generation)
        .await?;
    Ok(match disposition {
        DeliveryDisposition::Accepted => HeartbeatAttemptOutcome::Accepted,
        DeliveryDisposition::PauseIdentity => {
            HeartbeatAttemptOutcome::Halted(HeartbeatHalt::Identity)
        }
        DeliveryDisposition::Quarantine | DeliveryDisposition::Retry { .. } => {
            HeartbeatAttemptOutcome::Halted(HeartbeatHalt::Quarantine)
        }
    })
}
