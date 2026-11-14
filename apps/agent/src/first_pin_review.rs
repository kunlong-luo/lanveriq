//! Detached offline first-pin review signature verification, Batch A8.
//!
//! This is NOT a signing service, certificate issuer, local pin installer,
//! automatic enrollment, heartbeat ACK or permission to clear quarantine.
//! The public key MUST come from independently authorized local custody,
//! never from the grant, the HTTP response or a user-selected browser field.
//! There is deliberately no production trust anchor in this repository.
use crate::store::{OutboxStore, StoreError};
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
        let mut message =
            Vec::with_capacity(FIRST_PIN_REVIEW_DOMAIN.len() + 4 + 16 * 3 + 32 + 8 * 2);
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
    let signature =
        hex::decode(&review.signature_hex).map_err(|_| FirstPinReviewError::SignatureRejected)?;
    UnparsedPublicKey::new(&ED25519, independently_provisioned_ed25519_public_key)
        .verify(&payload, &signature)
        .map_err(|_| FirstPinReviewError::SignatureRejected)?;
    Ok(FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly)
}

#[derive(Debug, Error)]
pub enum LocalReviewerCheckError {
    #[error("independently provisioned local reviewer key required")]
    KeyNotProvisioned,
    #[error("reviewer key pin corrupted or belongs to a different device")]
    Store(#[from] StoreError),
    #[error("review does not verify under the previously pinned local key")]
    Review(#[from] FirstPinReviewError),
}

/// Offline read-only verifier uses the locally immutable public key, NEVER
/// a public key supplied by a review or a live HTTP response. An arbitrary
/// initial on-disk pin still does NOT prove actual administrator approval.
/// No pin installation, consumption, reset or send is performed here.
pub async fn inspect_offline_review_against_local_key(
    store: &OutboxStore,
    review: &OfflineFirstPinReview,
    observed: &CurrentCredentialEpoch,
    now_unix: u64,
) -> Result<FirstPinReviewObservation, LocalReviewerCheckError> {
    let key = store
        .load_offline_reviewer_public_key(observed.device_id)
        .await?
        .ok_or(LocalReviewerCheckError::KeyNotProvisioned)?;
    let observation = inspect_offline_first_pin_review(review, observed, &key, now_unix)?;
    if store
        .load_offline_reviewer_public_key(observed.device_id)
        .await?
        != Some(key)
    {
        return Err(LocalReviewerCheckError::Store(
            StoreError::HeartbeatConflict,
        ));
    }
    Ok(observation)
}

#[cfg(test)]
mod tests {
    use super::*;
    use ring::rand::SystemRandom;
    use ring::signature::{Ed25519KeyPair, KeyPair};

    const NOW: u64 = 1_795_000_000;

    fn new_reviewer() -> Ed25519KeyPair {
        let private = Ed25519KeyPair::generate_pkcs8(&SystemRandom::new()).unwrap();
        Ed25519KeyPair::from_pkcs8(private.as_ref()).unwrap()
    }

    fn signed_fixture() -> (
        OfflineFirstPinReview,
        CurrentCredentialEpoch,
        Ed25519KeyPair,
    ) {
        let current = CurrentCredentialEpoch {
            device_id: Uuid::now_v7(),
            credential_epoch_id: Uuid::now_v7(),
            cert_sha256_fingerprint: "a".repeat(64),
        };
        let signer = new_reviewer();
        let mut grant = OfflineFirstPinReview {
            schema_version: 1,
            device_id: current.device_id,
            credential_epoch_id: current.credential_epoch_id,
            cert_sha256_fingerprint: current.cert_sha256_fingerprint.clone(),
            review_id: Uuid::now_v7(),
            not_before_unix: NOW - 30,
            expires_at_unix: NOW + 300,
            signature_hex: String::new(),
        };
        grant.signature_hex = hex::encode(signer.sign(&grant.signing_payload().unwrap()).as_ref());
        (grant, current, signer)
    }

    #[test]
    fn valid_signature_binds_exact_current_epoch_but_never_returns_install_permission() {
        let (grant, current, signer) = signed_fixture();
        assert_eq!(
            inspect_offline_first_pin_review(&grant, &current, signer.public_key().as_ref(), NOW),
            Ok(FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly)
        );
        assert_eq!(
            grant.signing_payload().unwrap(),
            grant.signing_payload().unwrap()
        );
        assert!(
            grant
                .signing_payload()
                .unwrap()
                .starts_with(FIRST_PIN_REVIEW_DOMAIN)
        );
    }

    #[test]
    fn changed_signed_fields_and_wrong_public_key_fail_closed() {
        let (grant, current, signer) = signed_fixture();
        let mut altered = grant.clone();
        altered.review_id = Uuid::now_v7();
        assert_eq!(
            inspect_offline_first_pin_review(&altered, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        let mut altered = grant.clone();
        altered.not_before_unix -= 1;
        assert_eq!(
            inspect_offline_first_pin_review(&altered, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        let mut altered = grant.clone();
        altered.expires_at_unix += 1;
        assert_eq!(
            inspect_offline_first_pin_review(&altered, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        let another = new_reviewer();
        assert_eq!(
            inspect_offline_first_pin_review(&grant, &current, another.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        assert_eq!(
            inspect_offline_first_pin_review(&grant, &current, &[0u8; 31], NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        let mut invalid = grant.clone();
        invalid.signature_hex = "b".repeat(128);
        assert_eq!(
            inspect_offline_first_pin_review(&invalid, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
        invalid.signature_hex = grant.signature_hex.to_uppercase();
        assert_eq!(
            inspect_offline_first_pin_review(&invalid, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::SignatureRejected)
        );
    }

    #[test]
    fn different_tls_device_epoch_or_fingerprint_never_matches_signed_review() {
        let (grant, current, signer) = signed_fixture();
        for mutated in [
            CurrentCredentialEpoch {
                device_id: Uuid::now_v7(),
                ..current.clone()
            },
            CurrentCredentialEpoch {
                credential_epoch_id: Uuid::now_v7(),
                ..current.clone()
            },
            CurrentCredentialEpoch {
                cert_sha256_fingerprint: "b".repeat(64),
                ..current.clone()
            },
        ] {
            assert_eq!(
                inspect_offline_first_pin_review(
                    &grant,
                    &mutated,
                    signer.public_key().as_ref(),
                    NOW
                ),
                Err(FirstPinReviewError::DifferentCredential)
            );
        }
    }

    #[test]
    fn expired_future_or_long_lived_review_cannot_be_accepted() {
        let (grant, current, signer) = signed_fixture();
        for now in [NOW - 31, NOW + 300, NOW + 301] {
            assert_eq!(
                inspect_offline_first_pin_review(
                    &grant,
                    &current,
                    signer.public_key().as_ref(),
                    now
                ),
                Err(FirstPinReviewError::OutsideValidityWindow)
            );
        }
        let mut too_long = grant.clone();
        too_long.expires_at_unix = too_long.not_before_unix + 3601;
        assert_eq!(
            inspect_offline_first_pin_review(
                &too_long,
                &current,
                signer.public_key().as_ref(),
                NOW
            ),
            Err(FirstPinReviewError::MalformedReview)
        );
        let mut inverted = grant.clone();
        inverted.expires_at_unix = inverted.not_before_unix - 1;
        assert_eq!(
            inspect_offline_first_pin_review(
                &inverted,
                &current,
                signer.public_key().as_ref(),
                NOW
            ),
            Err(FirstPinReviewError::MalformedReview)
        );
    }

    #[test]
    fn invalid_versions_fingerprints_fields_and_json_extensions_are_rejected() {
        let (grant, current, signer) = signed_fixture();
        let mut altered = grant.clone();
        altered.schema_version = 2;
        assert!(matches!(
            inspect_offline_first_pin_review(&altered, &current, signer.public_key().as_ref(), NOW),
            Err(FirstPinReviewError::MalformedReview)
        ));
        altered = grant.clone();
        altered.review_id = Uuid::nil();
        assert!(altered.signing_payload().is_err());
        altered = grant.clone();
        altered.credential_epoch_id =
            Uuid::parse_str("550e8400-e29b-41d4-a716-446655440000").unwrap();
        assert!(altered.signing_payload().is_err());
        altered = grant.clone();
        altered.cert_sha256_fingerprint = "A".repeat(64);
        assert!(altered.signing_payload().is_err());
        altered = grant.clone();
        altered.not_before_unix = 0;
        assert!(altered.signing_payload().is_err());

        let mut json = serde_json::to_value(&grant).unwrap();
        json["auto_install_pin"] = serde_json::json!(true);
        assert!(serde_json::from_value::<OfflineFirstPinReview>(json).is_err());
        let replay =
            serde_json::from_slice::<OfflineFirstPinReview>(&serde_json::to_vec(&grant).unwrap())
                .unwrap();
        assert_eq!(replay, grant);
    }
}
