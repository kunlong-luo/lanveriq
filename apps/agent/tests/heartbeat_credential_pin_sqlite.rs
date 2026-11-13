//! All tests are offline, with file-backed SQLite; never call an Agent
//! service, TLS socket, enrollment CA, telemetry or remote control.
use lanveriq_agent::heartbeat_credential_pin::{
    HeartbeatCredentialContinuity, HeartbeatCredentialPin,
    inspect_local_heartbeat_credential_continuity,
};
use lanveriq_agent::heartbeat_reconciliation::{
    HeartbeatReconciliationError, inspect_explicit_heartbeat_reconciliation,
};
use lanveriq_agent::heartbeat_runtime::{HeartbeatDriverError, run_explicit_heartbeat_once};
use lanveriq_agent::heartbeat_schedule::HeartbeatCheckpoint;
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::{DeliveryDisposition, HeartbeatLookupDisposition};
use std::sync::atomic::{AtomicUsize, Ordering};
use uuid::Uuid;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

fn pin() -> HeartbeatCredentialPin {
    HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"a".repeat(64)).unwrap()
}

#[tokio::test]
async fn pinned_epoch_survives_restart_and_legacy_sender_and_lookup_are_denied() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("pinned.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let identity = pin();
    store
        .initialize_heartbeat_credential_pin(&identity)
        .await
        .unwrap();
    store.close().await;

    let restarted = OutboxStore::open(&path).await.unwrap();
    assert_eq!(
        restarted
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap(),
        Some(identity.clone())
    );
    assert_eq!(
        inspect_local_heartbeat_credential_continuity(&restarted, &identity)
            .await
            .unwrap(),
        HeartbeatCredentialContinuity::MatchingLocalPinOnly
    );
    let sends = AtomicUsize::new(0);
    let result = run_explicit_heartbeat_once(&restarted, device(), true, 100, 30, |_| {
        sends.fetch_add(1, Ordering::SeqCst);
        async { Ok(DeliveryDisposition::Accepted) }
    })
    .await;
    assert!(matches!(
        result,
        Err(HeartbeatDriverError::Store(StoreError::HeartbeatConflict))
    ));
    let lookups = AtomicUsize::new(0);
    let result = inspect_explicit_heartbeat_reconciliation(&restarted, device(), true, |_, _| {
        lookups.fetch_add(1, Ordering::SeqCst);
        async { Ok(HeartbeatLookupDisposition::Committed) }
    })
    .await;
    assert!(matches!(
        result,
        Err(HeartbeatReconciliationError::Store(
            StoreError::HeartbeatConflict
        ))
    ));
    assert_eq!(sends.load(Ordering::SeqCst), 0);
    assert_eq!(lookups.load(Ordering::SeqCst), 0);
}

#[tokio::test]
async fn mismatch_and_wrong_device_never_rewrite_pinned_identity() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("mismatch.sqlite"))
        .await
        .unwrap();
    let identity = pin();
    store
        .initialize_heartbeat_credential_pin(&identity)
        .await
        .unwrap();
    for alternate in [
        HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"a".repeat(64)).unwrap(),
        HeartbeatCredentialPin::new(device(), identity.credential_epoch_id, &"b".repeat(64))
            .unwrap(),
    ] {
        assert_eq!(
            inspect_local_heartbeat_credential_continuity(&store, &alternate)
                .await
                .unwrap(),
            HeartbeatCredentialContinuity::MismatchStayHalted
        );
        assert!(matches!(
            store.initialize_heartbeat_credential_pin(&alternate).await,
            Err(StoreError::HeartbeatConflict)
        ));
    }
    assert!(matches!(
        store.load_heartbeat_credential_pin(Uuid::now_v7()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert_eq!(
        store.load_heartbeat_credential_pin(device()).await.unwrap(),
        Some(identity)
    );
}

#[tokio::test]
async fn previous_heartbeat_attempt_cannot_be_retroactively_claimed() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("legacy.sqlite"))
        .await
        .unwrap();
    let id = Uuid::now_v7();
    let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
    checkpoint.begin(true, 100, 30, id).unwrap();
    store
        .save_heartbeat_schedule_checkpoint(&checkpoint, 0)
        .await
        .unwrap();
    assert!(matches!(
        store.initialize_heartbeat_credential_pin(&pin()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert_eq!(
        inspect_local_heartbeat_credential_continuity(&store, &pin())
            .await
            .unwrap(),
        HeartbeatCredentialContinuity::UnboundNeedsReview
    );
    let existing = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(existing.checkpoint.unwrap().pending_request_id, Some(id));
    assert_eq!(existing.generation, 1);
}

#[tokio::test]
async fn deleted_or_corrupted_pin_marker_never_looks_like_fresh_enrollment() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("tamper.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    store
        .initialize_heartbeat_credential_pin(&pin())
        .await
        .unwrap();
    store.close().await;

    // SQLite's immutable DELETE trigger blocks ordinary deletion. Simulate
    // tampering by removing it directly; seen marker remains durable.
    let database = sqlx::SqlitePool::connect(&format!("sqlite://{}", path.display()))
        .await
        .unwrap();
    assert!(
        sqlx::query("DELETE FROM heartbeat_credential_pin")
            .execute(&database)
            .await
            .is_err()
    );
    sqlx::query("DROP TRIGGER heartbeat_credential_pin_no_delete")
        .execute(&database)
        .await
        .unwrap();
    sqlx::query("DELETE FROM heartbeat_credential_pin")
        .execute(&database)
        .await
        .unwrap();
    database.close().await;
    let recovered = OutboxStore::open(&path).await.unwrap();
    assert!(matches!(
        recovered.load_heartbeat_credential_pin(device()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(matches!(
        recovered.initialize_heartbeat_credential_pin(&pin()).await,
        Err(StoreError::HeartbeatConflict)
    ));
}

#[tokio::test]
async fn pinned_epoch_blocks_direct_checkpoint_writer_inside_sqlite_transaction() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("write-race.sqlite"))
        .await
        .unwrap();
    let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
    let pending = Uuid::now_v7();
    checkpoint.begin(true, 100, 30, pending).unwrap();

    // Emulates a legacy driver holding a stale pre-pin checkpoint snapshot.
    store
        .initialize_heartbeat_credential_pin(&pin())
        .await
        .unwrap();
    assert!(matches!(
        store
            .save_heartbeat_schedule_checkpoint(&checkpoint, 0)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
    let existing = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(existing.generation, 0);
    assert!(existing.checkpoint.is_none());
}

#[test]
fn invalid_local_credential_metadata_rejected() {
    assert!(HeartbeatCredentialPin::new(Uuid::nil(), Uuid::now_v7(), &"a".repeat(64)).is_err());
    assert!(HeartbeatCredentialPin::new(device(), Uuid::nil(), &"a".repeat(64)).is_err());
    assert!(HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"A".repeat(64)).is_err());
    assert!(HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"z".repeat(64)).is_err());
}
