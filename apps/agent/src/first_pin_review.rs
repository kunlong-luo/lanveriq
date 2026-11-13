//! Detached offline first-pin review signature verification, Batch A8.
//!
//! This is NOT a signing service, certificate issuer, local pin installer,
//! automatic enrollment, heartbeat ACK or permission to clear quarantine.
//! The public key MUST come from independently authorized local custody,
//! never from the grant, the HTTP response or a user-selected browser field.
//! There is deliberately no production trust anchor in this repository.
use crate::transport::CurrentCredentialEpoch;
use ring::signature::{ED25519, UnparsedPublicKey};
use serde::{Deserialize, Serialize};
use thiserror::Error;
use uuid::Uuid;

const FIRST_PIN_REVIEW_DOMAIN: &[u8] = b"lanveriq/first-pin-review/ed25519/v1\0";
const MAX_REVIEW_LIFETIME_SECS: u64 = 3600;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OfflineFirstPinReview {
    pub schema_version: u32,
    pub device_id: Uuid,
    pub credential_epoch_id: Uuid,
    pub cert_sha256_fingerprint: String,
    pub review_id: Uuid,
    pub not_before_unix: u64,
    pub expires_at_unix: u64,
    /// Exactly 64 Ed25519 signature bytes, encoded as lowercase hex.
    pub signature_hex: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FirstPinReviewObservation {
    /// Signature verifies against the *supplied* key and observed TLS fields.
    /// This is NOT operational administrator approval or authorization to
    /// persist/rebind an identity. Operational trust-root custody is separate.
    SignatureValidForSuppliedKeyOnly,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum FirstPinReviewError {
    #[error("malformed or unsupported offline review")]
    MalformedReview,
    #[error("offline review is out of its short validity window")]
    OutsideValidityWindow,
    #[error("review is not for the current observed TLS certificate epoch")]
    DifferentCredential,
    #[error("review signature is not valid under the externally supplied key")]
    SignatureRejected,
}

impl OfflineFirstPinReview {
    /// Domain-separated binary encoding for the future independently
    /// controlled reviewer/issuer to sign. No ad hoc JSON canonicalization.
    /// All fields must pass structural validation BEFORE signing/verifying.
    pub fn signing_payload(&self) -> Result<Vec<u8>, FirstPinReviewError> {
        if self.schema_version != 1
            || self.device_id.is_nil()
            || self.credential_epoch_id.get_version_num() != 7
            || self.review_id.get_version_num() != 7
            || self.cert_sha256_fingerprint.len() != 64
            || !self
                .cert_sha256_fingerprint
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
            || self.not_before_unix == 0
            || self
                .expires_at_unix
                .checked_sub(self.not_before_unix)
                .is_none_or(|duration| duration == 0 || duration > MAX_REVIEW_LIFETIME_SECS)
        {
            return Err(FirstPinReviewError::MalformedReview);
        }
        let fingerprint = hex::decode(&self.cert_sha256_fingerprint)
            .map_err(|_| FirstPinReviewError::MalformedReview)?;
        if fingerprint.len() != 32 {
            return Err(FirstPinReviewError::MalformedReview);
        }
        let mut message = Vec::with_capacity(FIRST_PIN_REVIEW_DOMAIN.len() + 4 + 16 * 3 + 32 + 8 * 2);
        message.extend_from_slice(FIRST_PIN_REVIEW_DOMAIN);
        message.extend_from_slice(&self.schema_version.to_be_bytes());
        message.extend_from_slice(self.device_id.as_bytes());
        message.extend_from_slice(self.credential_epoch_id.as_bytes());
        message.extend_from_slice(&fingerprint);
        message.extend_from_slice(self.review_id.as_bytes());
        message.extend_from_slice(&self.not_before_unix.to_be_bytes());
        message.extend_from_slice(&self.expires_at_unix.to_be_bytes());
        Ok(message)
    }
}

/// Pure detached-signature observation. Does not use an embedded/trusted CA,
/// grant-provided key, transport headers or mutable SQLite state. It NEVER
/// writes a pin. A matching signature has no authority until a separate
/// privileged, audited trust-root/administrator provisioning workflow exists.
pub fn inspect_offline_first_pin_review(
    review: &OfflineFirstPinReview,
    observed: &CurrentCredentialEpoch,
    independently_provisioned_ed25519_public_key: &[u8],
    now_unix: u64,
) -> Result<FirstPinReviewObservation, FirstPinReviewError> {
    let payload = review.signing_payload()?;
    if now_unix < review.not_before_unix || now_unix >= review.expires_at_unix {
        return Err(FirstPinReviewError::OutsideValidityWindow);
    }
    if review.device_id != observed.device_id
        || review.credential_epoch_id != observed.credential_epoch_id
        || review.cert_sha256_fingerprint != observed.cert_sha256_fingerprint
        || observed.credential_epoch_id.get_version_num() != 7
    {
        return Err(FirstPinReviewError::DifferentCredential);
    }
    if independently_provisioned_ed25519_public_key.len() != 32
        || review.signature_hex.len() != 128
        || !review
            .signature_hex
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    {
        return Err(FirstPinReviewError::SignatureRejected);
    }
    let signature = hex::decode(&review.signature_hex)
        .map_err(|_| FirstPinReviewError::SignatureRejected)?;
    UnparsedPublicKey::new(&ED25519, independently_provisioned_ed25519_public_key)
        .verify(&payload, &signature)
        .map_err(|_| FirstPinReviewError::SignatureRejected)?;
    Ok(FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly)
}
