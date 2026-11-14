//! Batch A9: isolated file-backed SQLite reviewer-key continuity.
//! No real endpoint or signing authority, no live service or credential pin.
use lanveriq_agent::first_pin_review::{
    FirstPinReviewError, FirstPinReviewObservation, LocalReviewerCheckError, OfflineFirstPinReview,
    inspect_offline_review_against_local_key,
};
use lanveriq_agent::heartbeat_schedule::HeartbeatCheckpoint;
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::CurrentCredentialEpoch;
use ring::rand::SystemRandom;
use ring::signature::{Ed25519KeyPair, KeyPair};
use uuid::Uuid;

const NOW: u64 = 1_795_000_000;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

fn reviewer() -> Ed25519KeyPair {
    let pkcs8 = Ed25519KeyPair::generate_pkcs8(&SystemRandom::new()).unwrap();
    Ed25519KeyPair::from_pkcs8(pkcs8.as_ref()).unwrap()
}

fn reviewed(signer: &Ed25519KeyPair) -> (OfflineFirstPinReview, CurrentCredentialEpoch) {
    let current = CurrentCredentialEpoch {
        device_id: device(),
        credential_epoch_id: Uuid::now_v7(),
        cert_sha256_fingerprint: "a".repeat(64),
    };
    let mut review = OfflineFirstPinReview {
        schema_version: 1,
        device_id: device(),
        credential_epoch_id: current.credential_epoch_id,
        cert_sha256_fingerprint: current.cert_sha256_fingerprint.clone(),
        review_id: Uuid::now_v7(),
        not_before_unix: NOW - 30,
        expires_at_unix: NOW + 300,
        signature_hex: String::new(),
    };
    review.signature_hex = hex::encode(signer.sign(&review.signing_payload().unwrap()).as_ref());
    (review, current)
}

#[tokio::test]
async fn no_preprovisioned_reviewer_key_means_no_signature_authority() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("unbound.sqlite"))
        .await
        .unwrap();
    let signer = reviewer();
    let (review, current) = reviewed(&signer);
    assert!(
        store
            .load_offline_reviewer_public_key(device())
            .await
            .unwrap()
            .is_none()
    );
    assert!(matches!(
        inspect_offline_review_against_local_key(&store, &review, &current, NOW).await,
        Err(LocalReviewerCheckError::KeyNotProvisioned)
    ));
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
}

#[tokio::test]
async fn immutable_device_scoped_key_survives_restart_and_rejects_replacement() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("pinned.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = reviewer();
    let wrong = reviewer();
    let key = signer.public_key().as_ref();
    store
        .initialize_offline_reviewer_public_key(device(), key)
        .await
        .unwrap();
    assert!(matches!(
        store
            .initialize_offline_reviewer_public_key(device(), wrong.public_key().as_ref())
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(matches!(
        store
            .initialize_offline_reviewer_public_key(device(), key)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    let (review, current) = reviewed(&signer);
    assert!(matches!(
        inspect_offline_review_against_local_key(&store, &review, &current, NOW).await,
        Ok(FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly)
    ));
    let (wrong_review, _) = reviewed(&wrong);
    assert!(matches!(
        inspect_offline_review_against_local_key(&store, &wrong_review, &current, NOW).await,
        Err(LocalReviewerCheckError::Review(
            FirstPinReviewError::DifferentCredential
        )) | Err(LocalReviewerCheckError::Review(
            FirstPinReviewError::SignatureRejected
        ))
    ));
    let mut forged = review.clone();
    forged.review_id = Uuid::now_v7();
    assert!(matches!(
        inspect_offline_review_against_local_key(&store, &forged, &current, NOW).await,
        Err(LocalReviewerCheckError::Review(
            FirstPinReviewError::SignatureRejected
        ))
    ));
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
    store.close().await;

    let store = OutboxStore::open(&path).await.unwrap();
    assert_eq!(
        store
            .load_offline_reviewer_public_key(device())
            .await
            .unwrap()
            .unwrap()
            .as_slice(),
        key
    );
    assert!(matches!(
        inspect_offline_review_against_local_key(&store, &review, &current, NOW).await,
        Ok(FirstPinReviewObservation::SignatureValidForSuppliedKeyOnly)
    ));
    assert!(matches!(
        store.load_offline_reviewer_public_key(Uuid::now_v7()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    store.close().await;

    // SQLite-level immutability blocks ordinary direct SQL replacement/deletion.
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    assert!(
        sqlx::query("DELETE FROM heartbeat_reviewer_public_key_pin")
            .execute(&pool)
            .await
            .is_err()
    );
    assert!(
        sqlx::query("UPDATE heartbeat_reviewer_public_key_pin SET ed25519_public_key=?")
            .bind(wrong.public_key().as_ref())
            .execute(&pool)
            .await
            .is_err()
    );
    pool.close().await;
}

#[tokio::test]
async fn missing_or_bad_prior_pin_cannot_be_silently_reenrolled() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("tampered.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = reviewer();
    store
        .initialize_offline_reviewer_public_key(device(), signer.public_key().as_ref())
        .await
        .unwrap();
    store.close().await;
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    sqlx::query("DROP TRIGGER heartbeat_reviewer_public_key_no_delete")
        .execute(&pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM heartbeat_reviewer_public_key_pin")
        .execute(&pool)
        .await
        .unwrap();
    pool.close().await;

    let store = OutboxStore::open(&path).await.unwrap();
    assert!(matches!(
        store.load_offline_reviewer_public_key(device()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(matches!(
        store
            .initialize_offline_reviewer_public_key(device(), signer.public_key().as_ref())
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
}

#[tokio::test]
async fn reviewer_bootstrap_is_never_allowed_after_a_heartbeat_or_existing_credential_pin() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("legacy.sqlite"))
        .await
        .unwrap();
    let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
    checkpoint.begin(true, 100, 30, Uuid::now_v7()).unwrap();
    store
        .save_heartbeat_schedule_checkpoint(&checkpoint, 0)
        .await
        .unwrap();
    let signer = reviewer();
    assert!(matches!(
        store
            .initialize_offline_reviewer_public_key(device(), signer.public_key().as_ref())
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(
        store
            .load_offline_reviewer_public_key(device())
            .await
            .unwrap()
            .is_none()
    );
}

#[tokio::test]
async fn malformed_key_and_cross_device_are_denied_before_store_mutation() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("bad-key.sqlite"))
        .await
        .unwrap();
    for key in [vec![], vec![0u8; 32], vec![1u8; 31], vec![1u8; 33]] {
        assert!(matches!(
            store
                .initialize_offline_reviewer_public_key(device(), &key)
                .await,
            Err(StoreError::HeartbeatConflict)
        ));
    }
    assert!(matches!(
        store
            .initialize_offline_reviewer_public_key(Uuid::nil(), &[1u8; 32])
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(
        store
            .load_offline_reviewer_public_key(device())
            .await
            .unwrap()
            .is_none()
    );
    let signer = reviewer();
    store
        .initialize_offline_reviewer_public_key(device(), signer.public_key().as_ref())
        .await
        .unwrap();
    assert!(matches!(
        store.load_offline_reviewer_public_key(Uuid::now_v7()).await,
        Err(StoreError::HeartbeatConflict)
    ));
}
