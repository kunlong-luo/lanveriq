//! Offline-only certificate-generation continuity check for Batch A.
//!
//! A caller-supplied fingerprint/UUID is NOT proof of mTLS, server issuance,
//! operator approval or key custody. Nothing here unlocks the old one-shot
//! heartbeat driver, clears quarantine or turns on the installed Agent.
use crate::store::{OutboxStore, StoreError};
use crate::transport::{CredentialEpochDisposition, TransportError};
use std::future::Future;
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeartbeatCredentialPin {
    pub device_id: Uuid,
    pub credential_epoch_id: Uuid,
    pub cert_sha256_fingerprint: String,
}

impl HeartbeatCredentialPin {
    pub fn new(
        device_id: Uuid,
        credential_epoch_id: Uuid,
        cert_sha256_fingerprint: &str,
    ) -> Result<Self, StoreError> {
        if device_id.is_nil()
            || credential_epoch_id.get_version_num() != 7
            || cert_sha256_fingerprint.len() != 64
            || !cert_sha256_fingerprint
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
        {
            return Err(StoreError::HeartbeatConflict);
        }
        Ok(Self {
            device_id,
            credential_epoch_id,
            cert_sha256_fingerprint: cert_sha256_fingerprint.to_owned(),
        })
    }
}

/// Only an observation: MATCH does not authorize TLS use or sending a packet.
/// A missing legacy pin never permits automatic enrollment or a new UUID.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatCredentialContinuity {
    UnboundNeedsReview,
    MismatchStayHalted,
    MatchingLocalPinOnly,
}

pub async fn inspect_local_heartbeat_credential_continuity(
    store: &OutboxStore,
    candidate: &HeartbeatCredentialPin,
) -> Result<HeartbeatCredentialContinuity, StoreError> {
    let pin = store
        .load_heartbeat_credential_pin(candidate.device_id)
        .await?;
    Ok(match pin {
        None => HeartbeatCredentialContinuity::UnboundNeedsReview,
        Some(saved) if saved == *candidate => HeartbeatCredentialContinuity::MatchingLocalPinOnly,
        Some(_) => HeartbeatCredentialContinuity::MismatchStayHalted,
    })
}

/** Read-only explicit comparison result. None of these variants is an ACK,
 * approval to install/rotate a local pin, permission to send, or an authority
 * to clear an earlier quarantine. Absent pin always needs independent review.
 */
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatEpochPreflightObservation {
    Disabled,
    UnboundNeedsReview,
    MatchingLocalPinOnly,
    MismatchStayHalted,
    IdentityRejectedStillHeld,
    UnresolvedStillHeld,
    QuarantinedStillHeld,
    StaleDuringLookup,
}

#[derive(Debug, Error)]
pub enum HeartbeatEpochPreflightError {
    #[error("durable heartbeat identity could not be checked")]
    Store(#[from] StoreError),
    #[error("credential epoch reply was not trustworthy")]
    Transport(#[from] TransportError),
}

/// An explicitly authorized one-shot diagnostic that compares a CURRENT
/// certificate-epoch receipt to an existing immutable local SQLite pin.
///
/// The caller must supply a TLS-bound lookup such as
/// AgentTlsTransport::observe_current_credential_epoch, which derives its
/// certificate fingerprint from its actual Reqwest/Rustls identity. The
/// callback form is also used by purely offline, isolated race tests. A
/// synthetic callback alone is NOT proof of TLS/operational authorization.
///
/// Both saved epoch pin and heartbeat checkpoint are checked BEFORE and AFTER
/// the external call. Even a matching result is stale if either changed while
/// the lookup was outstanding. This method has NO SQLite writes whatsoever.
pub async fn inspect_explicit_heartbeat_epoch_preflight<F, Fut>(
    store: &OutboxStore,
    device_id: Uuid,
    enabled: bool,
    lookup: F,
) -> Result<HeartbeatEpochPreflightObservation, HeartbeatEpochPreflightError>
where
    F: FnOnce(Uuid) -> Fut,
    Fut: Future<Output = Result<CredentialEpochDisposition, TransportError>>,
{
    let prior_pin = store.load_heartbeat_credential_pin(device_id).await?;
    let prior_attempt = store.load_heartbeat_schedule_recovery(device_id).await?;
    if !enabled {
        return Ok(HeartbeatEpochPreflightObservation::Disabled);
    }

    let response = lookup(device_id).await?;
    let after_pin = store.load_heartbeat_credential_pin(device_id).await?;
    let after_attempt = store.load_heartbeat_schedule_recovery(device_id).await?;

    // The old SQLite checkpoint is generation-CAS protected; a changed
    // generation is stale even if its visible JSON happens to be identical.
    // An initial pin appearing midflight is also stale, never matchable.
    if prior_pin != after_pin
        || prior_attempt.generation != after_attempt.generation
        || prior_attempt.checkpoint != after_attempt.checkpoint
    {
        return Ok(HeartbeatEpochPreflightObservation::StaleDuringLookup);
    }

    Ok(match response {
        CredentialEpochDisposition::ObservedOnly(current) => {
            // A caller-supplied/injected wrong device or invalid version
            // does not qualify as an authorized local identity. The real
            // network helper already enforces exact certificate DER binding.
            if current.device_id != device_id {
                return Err(HeartbeatEpochPreflightError::Store(
                    StoreError::HeartbeatConflict,
                ));
            }
            let observed = HeartbeatCredentialPin::new(
                current.device_id,
                current.credential_epoch_id,
                &current.cert_sha256_fingerprint,
            )?;
            match prior_pin {
                None => HeartbeatEpochPreflightObservation::UnboundNeedsReview,
                Some(saved) if saved == observed => {
                    HeartbeatEpochPreflightObservation::MatchingLocalPinOnly
                }
                Some(_) => HeartbeatEpochPreflightObservation::MismatchStayHalted,
            }
        }
        CredentialEpochDisposition::PauseIdentity => {
            HeartbeatEpochPreflightObservation::IdentityRejectedStillHeld
        }
        CredentialEpochDisposition::Unresolved => {
            HeartbeatEpochPreflightObservation::UnresolvedStillHeld
        }
        CredentialEpochDisposition::Quarantine => {
            HeartbeatEpochPreflightObservation::QuarantinedStillHeld
        }
    })
}
