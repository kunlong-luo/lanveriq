//! All tests are offline, with file-backed SQLite; never call an Agent
//! service, TLS socket, enrollment CA, telemetry or remote control.
use lanveriq_agent::first_pin_review::OfflineFirstPinReview;
use lanveriq_agent::heartbeat_credential_pin::{
    HeartbeatCredentialContinuity, HeartbeatCredentialPin, HeartbeatEpochPreflightError,
    HeartbeatEpochPreflightObservation, inspect_explicit_heartbeat_epoch_preflight,
    inspect_local_heartbeat_credential_continuity,
};
use lanveriq_agent::heartbeat_reconciliation::{
    HeartbeatReconciliationError, inspect_explicit_heartbeat_reconciliation,
};
use lanveriq_agent::heartbeat_runtime::{HeartbeatDriverError, run_explicit_heartbeat_once};
use lanveriq_agent::heartbeat_schedule::HeartbeatCheckpoint;
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::CurrentCredentialEpoch;
use lanveriq_agent::transport::{
    CredentialEpochDisposition, DeliveryDisposition, HeartbeatLookupDisposition,
};
use ring::rand::SystemRandom;
use ring::signature::{Ed25519KeyPair, KeyPair};
use std::sync::atomic::{AtomicUsize, Ordering};
use uuid::Uuid;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

fn pin() -> HeartbeatCredentialPin {
    HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"a".repeat(64)).unwrap()
}

async fn install_test_pin(store: &OutboxStore, pin: &HeartbeatCredentialPin) {
    // Synthetic-only signature from a new in-memory reviewer key. This
    // fixture has no administrator approval or production trust root.
    let bytes = Ed25519KeyPair::generate_pkcs8(&SystemRandom::new()).unwrap();
    let signer = Ed25519KeyPair::from_pkcs8(bytes.as_ref()).unwrap();
    store
        .initialize_offline_reviewer_public_key(pin.device_id, signer.public_key().as_ref())
        .await
        .unwrap();
    let mut review = OfflineFirstPinReview {
        schema_version: 1,
        device_id: pin.device_id,
        credential_epoch_id: pin.credential_epoch_id,
        cert_sha256_fingerprint: pin.cert_sha256_fingerprint.clone(),
        review_id: Uuid::now_v7(),
        not_before_unix: 1_795_000_000,
        expires_at_unix: 1_795_000_300,
        signature_hex: String::new(),
    };
    review.signature_hex = hex::encode(signer.sign(&review.signing_payload().unwrap()).as_ref());
    let observed = CurrentCredentialEpoch {
        device_id: pin.device_id,
        credential_epoch_id: pin.credential_epoch_id,
        cert_sha256_fingerprint: pin.cert_sha256_fingerprint.clone(),
    };
    store
        .install_first_pin_with_offline_review(&review, &observed, 1_795_000_010)
        .await
        .unwrap();
}

#[tokio::test]
async fn pinned_epoch_survives_restart_and_legacy_sender_and_lookup_are_denied() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("pinned.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let identity = pin();
    install_test_pin(&store, &identity).await;
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
    install_test_pin(&store, &identity).await;
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
    install_test_pin(&store, &pin()).await;
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
    install_test_pin(&store, &pin()).await;
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

fn observed(pin: &HeartbeatCredentialPin) -> lanveriq_agent::transport::CredentialEpochDisposition {
    lanveriq_agent::transport::CredentialEpochDisposition::ObservedOnly(
        lanveriq_agent::transport::CurrentCredentialEpoch {
            device_id: pin.device_id,
            credential_epoch_id: pin.credential_epoch_id,
            cert_sha256_fingerprint: pin.cert_sha256_fingerprint.clone(),
        },
    )
}

#[tokio::test]
async fn explicit_epoch_preflight_default_off_and_missing_pin_never_install_identity() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("unbound.sqlite"))
        .await
        .unwrap();
    let calls = AtomicUsize::new(0);
    let disabled = inspect_explicit_heartbeat_epoch_preflight(&store, device(), false, |_| {
        calls.fetch_add(1, Ordering::SeqCst);
        async { Ok(observed(&pin())) }
    })
    .await
    .unwrap();
    assert_eq!(disabled, HeartbeatEpochPreflightObservation::Disabled);
    assert_eq!(calls.load(Ordering::SeqCst), 0);
    let result = inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
        Ok(observed(&pin()))
    })
    .await
    .unwrap();
    assert_eq!(
        result,
        HeartbeatEpochPreflightObservation::UnboundNeedsReview
    );
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
}

#[tokio::test]
async fn matching_and_mismatching_existing_pins_are_only_diagnostic() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("matching.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let identity = pin();
    install_test_pin(&store, &identity).await;
    for (candidate, expected) in [
        (
            identity.clone(),
            HeartbeatEpochPreflightObservation::MatchingLocalPinOnly,
        ),
        (
            HeartbeatCredentialPin::new(device(), Uuid::now_v7(), &"a".repeat(64)).unwrap(),
            HeartbeatEpochPreflightObservation::MismatchStayHalted,
        ),
        (
            HeartbeatCredentialPin::new(device(), identity.credential_epoch_id, &"b".repeat(64))
                .unwrap(),
            HeartbeatEpochPreflightObservation::MismatchStayHalted,
        ),
    ] {
        assert_eq!(
            inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
                Ok(observed(&candidate))
            })
            .await
            .unwrap(),
            expected
        );
        assert_eq!(
            store.load_heartbeat_credential_pin(device()).await.unwrap(),
            Some(identity.clone())
        );
    }
    store.close().await;
    let restarted = OutboxStore::open(&path).await.unwrap();
    assert_eq!(
        restarted
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap(),
        Some(identity)
    );
}

#[tokio::test]
async fn epoch_preflight_rejected_or_unresolved_never_releases_legacy_pending() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("held.sqlite"))
        .await
        .unwrap();
    let request = Uuid::now_v7();
    let mut pending = HeartbeatCheckpoint::initial(device()).unwrap();
    pending.begin(true, 100, 30, request).unwrap();
    // SQLite refuses to mint a halted first row: persist an authentic
    // pending UUID first, then emulate the ambiguous ACK-loss transition.
    store
        .save_heartbeat_schedule_checkpoint(&pending, 0)
        .await
        .unwrap();
    pending.note_uncertain_outcome().unwrap();
    store
        .save_heartbeat_schedule_checkpoint(&pending, 1)
        .await
        .unwrap();
    for (disposition, expected) in [
        (
            CredentialEpochDisposition::PauseIdentity,
            HeartbeatEpochPreflightObservation::IdentityRejectedStillHeld,
        ),
        (
            CredentialEpochDisposition::Unresolved,
            HeartbeatEpochPreflightObservation::UnresolvedStillHeld,
        ),
        (
            CredentialEpochDisposition::Quarantine,
            HeartbeatEpochPreflightObservation::QuarantinedStillHeld,
        ),
        (
            observed(&pin()),
            HeartbeatEpochPreflightObservation::UnboundNeedsReview,
        ),
    ] {
        let result =
            inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
                Ok(disposition)
            })
            .await
            .unwrap();
        assert_eq!(result, expected);
        let current = store
            .load_heartbeat_schedule_recovery(device())
            .await
            .unwrap();
        assert_eq!(current.generation, 2);
        assert_eq!(current.checkpoint, Some(pending.clone()));
        assert!(
            store
                .load_heartbeat_credential_pin(device())
                .await
                .unwrap()
                .is_none()
        );
    }
}

#[tokio::test]
async fn concurrent_checkpoint_change_makes_mtls_observation_stale_not_approved() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("checkpoint-race.sqlite"))
        .await
        .unwrap();
    let result = inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
        let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
        checkpoint.begin(true, 100, 30, Uuid::now_v7()).unwrap();
        store
            .save_heartbeat_schedule_checkpoint(&checkpoint, 0)
            .await
            .unwrap();
        Ok(observed(&pin()))
    })
    .await
    .unwrap();
    assert_eq!(
        result,
        HeartbeatEpochPreflightObservation::StaleDuringLookup
    );
    assert!(
        store
            .load_heartbeat_credential_pin(device())
            .await
            .unwrap()
            .is_none()
    );
}

#[tokio::test]
async fn concurrent_first_pin_is_stale_and_cannot_be_used_for_authorization() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("pin-race.sqlite"))
        .await
        .unwrap();
    let identity = pin();
    let result = inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
        install_test_pin(&store, &identity).await;
        Ok(observed(&identity))
    })
    .await
    .unwrap();
    assert_eq!(
        result,
        HeartbeatEpochPreflightObservation::StaleDuringLookup
    );
    assert_eq!(
        store.load_heartbeat_credential_pin(device()).await.unwrap(),
        Some(identity)
    );
}

#[tokio::test]
async fn wrong_device_and_invalid_signed_epoch_are_fail_closed() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("bad-epoch.sqlite"))
        .await
        .unwrap();
    let candidate = pin();
    assert!(
        HeartbeatCredentialPin::new(
            candidate.device_id,
            Uuid::parse_str("550e8400-e29b-41d4-a716-446655440000").unwrap(),
            &candidate.cert_sha256_fingerprint
        )
        .is_err()
    );
    let wrong = Uuid::now_v7();
    assert!(matches!(
        inspect_explicit_heartbeat_epoch_preflight(&store, wrong, true, |_| async {
            Ok(observed(&candidate))
        })
        .await,
        Err(HeartbeatEpochPreflightError::Store(
            StoreError::HeartbeatConflict
        ))
    ));
    let altered =
        HeartbeatCredentialPin::new(wrong, candidate.credential_epoch_id, &"b".repeat(64)).unwrap();
    assert!(matches!(
        inspect_explicit_heartbeat_epoch_preflight(&store, device(), true, |_| async {
            Ok(observed(&altered))
        })
        .await,
        Err(HeartbeatEpochPreflightError::Store(
            StoreError::HeartbeatConflict
        ))
    ));
}
