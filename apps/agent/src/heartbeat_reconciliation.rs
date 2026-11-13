//! Batch A3: explicit read-only reconciliation inspection.
//!
//! The real Agent service does not call this module. Even a correctly signed,
//! currently authorized ledger lookup that proves an ID committed is NOT an
//! original HTTP ACK, permission to resend, or permission to lift quarantine.
//! Only a separately approved recovery protocol may ever change the durable
//! checkpoint; this module deliberately exposes no persistence method.
use crate::heartbeat_schedule::{HeartbeatHalt, HeartbeatScheduleError};
use crate::store::{OutboxStore, StoreError};
use crate::transport::{HeartbeatLookupDisposition, TransportError};
use std::future::Future;
use thiserror::Error;
use uuid::Uuid;

/// A point-in-time diagnostic, not an authorization or recovery transition.
/// All positive, negative, and unknown observations leave the exact same
/// SQLite request UUID, halt state, and generation on disk.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatReconciliationObservation {
    Disabled,
    NoPending,
    /// 401/403 already persisted: do not initiate a second credential lookup.
    IdentityHalted,
    /// A matching committed-ledger read is NOT an original accepted POST ACK.
    CommittedStillHeld(Uuid),
    /// 404, 429, and 5xx are never evidence that a POST did not commit.
    UnresolvedStillHeld(Uuid),
    IdentityRejectedStillHeld(Uuid),
    QuarantinedStillHeld(Uuid),
    /// A concurrent checkpoint update invalidated the observation, even if
    /// the lookup appeared to prove durable membership for the old UUID.
    StaleDuringLookup(Uuid),
}

#[derive(Debug, Error)]
pub enum HeartbeatReconciliationError {
    #[error("durable heartbeat state cannot be trusted")]
    Store(#[from] StoreError),
    #[error("invalid heartbeat checkpoint identity")]
    Schedule(#[from] HeartbeatScheduleError),
    #[error("verified heartbeat ledger lookup failed")]
    Transport(#[from] TransportError),
}

/// Explicit, opt-in, single read of a CURRENT credential's request ledger.
/// The callback MUST call the strict mTLS AgentTlsTransport lookup with the
/// exact (device_id, pending_request_id); its typed disposition is not proof
/// of operator consent or a license to clear the durable pending state.
/// A second durable read detects any concurrent CAS generation/state change
/// while the lookup was in flight. This is a read-only, point-in-time
/// diagnostic, NOT an authorization token valid after this function returns.
/// No network is attempted when disabled, idle, or identity-halted.
pub async fn inspect_explicit_heartbeat_reconciliation<F, Fut>(
    store: &OutboxStore,
    device_id: Uuid,
    enabled: bool,
    lookup: F,
) -> Result<HeartbeatReconciliationObservation, HeartbeatReconciliationError>
where
    F: FnOnce(Uuid, Uuid) -> Fut,
    Fut: Future<Output = Result<HeartbeatLookupDisposition, TransportError>>,
{
    // Validate the durable owner/seen marker first, including when disabled.
    // Silently falling back to "fresh" after local corruption is forbidden.
    let recovery = store.load_heartbeat_schedule_recovery(device_id).await?;
    if !enabled {
        return Ok(HeartbeatReconciliationObservation::Disabled);
    }
    let Some(checkpoint) = recovery.checkpoint else {
        return Ok(HeartbeatReconciliationObservation::NoPending);
    };
    checkpoint.validate(device_id)?;
    if checkpoint.halted == Some(HeartbeatHalt::Identity) {
        return Ok(HeartbeatReconciliationObservation::IdentityHalted);
    }
    let Some(pending) = checkpoint.pending_request_id else {
        return Ok(HeartbeatReconciliationObservation::NoPending);
    };
    let result = lookup(device_id, pending).await?;
    // A network lookup may take arbitrarily long. Another local writer may
    // pause the identity, quarantine or advance the attempt meanwhile. Never
    // label a stale ledger result as the CURRENT pending request diagnosis.
    // Compare both the CAS generation and the full state: even a same-state
    // generation bump invalidates the snapshot. This does not mutate SQLite.
    let current = store.load_heartbeat_schedule_recovery(device_id).await?;
    if current.generation != recovery.generation || current.checkpoint.as_ref() != Some(&checkpoint)
    {
        return Ok(HeartbeatReconciliationObservation::StaleDuringLookup(
            pending,
        ));
    }
    Ok(match result {
        HeartbeatLookupDisposition::Committed => {
            HeartbeatReconciliationObservation::CommittedStillHeld(pending)
        }
        HeartbeatLookupDisposition::Unresolved => {
            HeartbeatReconciliationObservation::UnresolvedStillHeld(pending)
        }
        HeartbeatLookupDisposition::PauseIdentity => {
            HeartbeatReconciliationObservation::IdentityRejectedStillHeld(pending)
        }
        HeartbeatLookupDisposition::Quarantine => {
            HeartbeatReconciliationObservation::QuarantinedStillHeld(pending)
        }
    })
}
