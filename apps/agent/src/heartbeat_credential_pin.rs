//! Offline-only certificate-generation continuity check for Batch A.
//!
//! A caller-supplied fingerprint/UUID is NOT proof of mTLS, server issuance,
//! operator approval or key custody. Nothing here unlocks the old one-shot
//! heartbeat driver, clears quarantine or turns on the installed Agent.
use crate::store::{OutboxStore, StoreError};
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
            || credential_epoch_id.is_nil()
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
