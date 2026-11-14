//! Batch A10 offline atomic first-pin consumption. All keys are ephemeral
//! TEST fixtures; no real administrator/CA/trusted clock or Agent runtime.
use lanveriq_agent::first_pin_review::OfflineFirstPinReview;
use lanveriq_agent::heartbeat_credential_pin::HeartbeatCredentialPin;
use lanveriq_agent::heartbeat_schedule::HeartbeatCheckpoint;
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::CurrentCredentialEpoch;
use ring::rand::SystemRandom;
use ring::signature::{Ed25519KeyPair, KeyPair};
use uuid::Uuid;

const NOW: u64 = 1_795_000_010;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

fn make_signer() -> Ed25519KeyPair {
    let bytes = Ed25519KeyPair::generate_pkcs8(&SystemRandom::new()).unwrap();
    Ed25519KeyPair::from_pkcs8(bytes.as_ref()).unwrap()
}

fn signed_review(signer: &Ed25519KeyPair) -> (OfflineFirstPinReview, CurrentCredentialEpoch) {
    let observed = CurrentCredentialEpoch {
        device_id: device(),
        credential_epoch_id: Uuid::now_v7(),
        cert_sha256_fingerprint: "a".repeat(64),
    };
    let mut review = OfflineFirstPinReview {
        schema_version: 1,
        device_id: observed.device_id,
        credential_epoch_id: observed.credential_epoch_id,
        cert_sha256_fingerprint: observed.cert_sha256_fingerprint.clone(),
        review_id: Uuid::now_v7(),
        not_before_unix: NOW - 10,
        expires_at_unix: NOW + 300,
        signature_hex: String::new(),
    };
    review.signature_hex = hex::encode(signer.sign(&review.signing_payload().unwrap()).as_ref());
    (review, observed)
}

async fn provision(store: &OutboxStore, signer: &Ed25519KeyPair) {
    store
        .initialize_offline_reviewer_public_key(device(), signer.public_key().as_ref())
        .await
        .unwrap();
}

#[tokio::test]
async fn first_review_and_pin_commit_together_survive_restart_and_replay_fails() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("atomic.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = make_signer();
    provision(&store, &signer).await;
    let (review, observed) = signed_review(&signer);
    let expected = HeartbeatCredentialPin::new(
        device(),
        observed.credential_epoch_id,
        &observed.cert_sha256_fingerprint,
    )
    .unwrap();
    assert!(matches!(
        store.initialize_heartbeat_credential_pin(&expected).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
    store
        .install_first_pin_with_offline_review(&review, &observed, NOW)
        .await
        .unwrap();
    assert_eq!(
        store.load_heartbeat_credential_pin(device()).await.unwrap(),
        Some(expected.clone())
    );
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    let mut second = review.clone();
    second.review_id = Uuid::now_v7();
    second.signature_hex = hex::encode(signer.sign(&second.signing_payload().unwrap()).as_ref());
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&second, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    store.close().await;

    let store = OutboxStore::open(&path).await.unwrap();
    assert_eq!(
        store.load_heartbeat_credential_pin(device()).await.unwrap(),
        Some(expected)
    );
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    let usage: (String, String, String, String, i64) = sqlx::query_as(
        "SELECT review_id,device_id,credential_epoch_id,cert_sha256_fingerprint,applied_at_unix \
         FROM heartbeat_first_pin_review_usage WHERE singleton=1",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(usage.0, review.review_id.to_string());
    assert_eq!(usage.1, device().to_string());
    assert_eq!(usage.2, observed.credential_epoch_id.to_string());
    assert_eq!(usage.3, observed.cert_sha256_fingerprint);
    assert_eq!(usage.4, NOW as i64);
    assert!(
        sqlx::query("DELETE FROM heartbeat_first_pin_review_usage")
            .execute(&pool)
            .await
            .is_err()
    );
    assert!(
        sqlx::query("UPDATE heartbeat_first_pin_review_usage SET review_id=?")
            .bind(Uuid::now_v7().to_string())
            .execute(&pool)
            .await
            .is_err()
    );
    pool.close().await;
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
}

#[tokio::test]
async fn no_trusted_local_key_or_bad_signature_writes_no_audit_and_no_pin() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("reject.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = make_signer();
    let (review, observed) = signed_review(&signer);
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    provision(&store, &signer).await;
    let bad_signer = make_signer();
    let (bad_review, _) = signed_review(&bad_signer);
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&bad_review, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    let mut changed = review.clone();
    changed.review_id = Uuid::now_v7();
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&changed, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW + 301)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    let different = CurrentCredentialEpoch {
        cert_sha256_fingerprint: "b".repeat(64),
        ..observed.clone()
    };
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &different, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
    store.close().await;
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM heartbeat_first_pin_review_usage")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
    pool.close().await;
}

#[tokio::test]
async fn old_heartbeat_history_blocks_review_consumption_and_immutable_pin() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("old-heartbeat.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = make_signer();
    provision(&store, &signer).await;
    let mut pending = HeartbeatCheckpoint::initial(device()).unwrap();
    let pending_id = Uuid::now_v7();
    pending.begin(true, 100, 30, pending_id).unwrap();
    store
        .save_heartbeat_schedule_checkpoint(&pending, 0)
        .await
        .unwrap();
    pending.note_uncertain_outcome().unwrap();
    store
        .save_heartbeat_schedule_checkpoint(&pending, 1)
        .await
        .unwrap();
    let (review, observed) = signed_review(&signer);
    assert!(matches!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
    let recovery = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, 2);
    assert_eq!(
        recovery.checkpoint.unwrap().pending_request_id,
        Some(pending_id)
    );
    store.close().await;
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM heartbeat_first_pin_review_usage")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
    pool.close().await;
}

#[tokio::test]
async fn injected_pin_insert_failure_rolls_back_review_id_and_can_retry_exact_one_time() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("rolled-back.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = make_signer();
    provision(&store, &signer).await;
    let (review, observed) = signed_review(&signer);
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    sqlx::query(
        "CREATE TRIGGER synthetic_fail_pin_insert BEFORE INSERT ON heartbeat_credential_pin \
         BEGIN SELECT RAISE(ABORT, 'synthetic failure after review audit insertion'); END",
    )
    .execute(&pool)
    .await
    .unwrap();
    assert!(
        store
            .install_first_pin_with_offline_review(&review, &observed, NOW)
            .await
            .is_err()
    );
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM heartbeat_first_pin_review_usage")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
    sqlx::query("DROP TRIGGER synthetic_fail_pin_insert")
        .execute(&pool)
        .await
        .unwrap();
    store
        .install_first_pin_with_offline_review(&review, &observed, NOW)
        .await
        .unwrap();
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM heartbeat_first_pin_review_usage")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 1);
    pool.close().await;
}

#[tokio::test]
async fn direct_sql_pin_without_audit_is_rejected_and_review_audit_deletion_is_detected() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("sql-guard.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let signer = make_signer();
    provision(&store, &signer).await;
    let (review, observed) = signed_review(&signer);
    let pool = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    assert!(sqlx::query(
        "INSERT INTO heartbeat_credential_pin(singleton,device_id,credential_epoch_id,cert_sha256_fingerprint) \
         VALUES(1,?,?,?)"
    ).bind(device().to_string()).bind(observed.credential_epoch_id.to_string())
        .bind(&observed.cert_sha256_fingerprint).execute(&pool).await.is_err());
    store
        .install_first_pin_with_offline_review(&review, &observed, NOW)
        .await
        .unwrap();
    sqlx::query("DROP TRIGGER heartbeat_first_pin_review_usage_no_delete")
        .execute(&pool)
        .await
        .unwrap();
    sqlx::query("DELETE FROM heartbeat_first_pin_review_usage")
        .execute(&pool)
        .await
        .unwrap();
    assert!(matches!(
        store.load_heartbeat_credential_pin(device()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    pool.close().await;
}
