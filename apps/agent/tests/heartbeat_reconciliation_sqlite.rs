//! Batch A3 opt-in offline inspection: file-backed SQLite, no real Agent,
//! network, CA, telemetry or production service. No automatic unpause exists.
use lanveriq_agent::heartbeat_reconciliation::{
    HeartbeatReconciliationObservation, inspect_explicit_heartbeat_reconciliation,
};
use lanveriq_agent::heartbeat_runtime::{HeartbeatAttemptOutcome, run_explicit_heartbeat_once};
use lanveriq_agent::heartbeat_schedule::{HeartbeatCheckpoint, HeartbeatHalt};
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::{DeliveryDisposition, HeartbeatLookupDisposition, TransportError};
use std::sync::atomic::{AtomicUsize, Ordering};
use uuid::Uuid;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

async fn prepare(store: &OutboxStore, request_id: Uuid, halt: Option<HeartbeatHalt>) -> i64 {
    let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
    checkpoint.begin(true, 100, 30, request_id).unwrap();
    let generation = store
        .save_heartbeat_schedule_checkpoint(&checkpoint, 0)
        .await
        .unwrap();
    if let Some(reason) = halt {
        match reason {
            HeartbeatHalt::Identity => checkpoint
                .finish(request_id, 101, DeliveryDisposition::PauseIdentity)
                .unwrap(),
            HeartbeatHalt::Quarantine => checkpoint.note_uncertain_outcome().unwrap(),
        }
        return store
            .save_heartbeat_schedule_checkpoint(&checkpoint, generation)
            .await
            .unwrap();
    }
    generation
}

#[tokio::test]
async fn disabled_and_no_pending_never_invoke_lookup_or_write_checkpoint() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("idle.sqlite"))
        .await
        .unwrap();
    let calls = AtomicUsize::new(0);
    for (enabled, expected) in [
        (false, HeartbeatReconciliationObservation::Disabled),
        (true, HeartbeatReconciliationObservation::NoPending),
    ] {
        let seen = inspect_explicit_heartbeat_reconciliation(&store, device(), enabled, |_, _| {
            calls.fetch_add(1, Ordering::SeqCst);
            async { Ok(HeartbeatLookupDisposition::Committed) }
        })
        .await
        .unwrap();
        assert_eq!(seen, expected);
    }
    assert_eq!(calls.load(Ordering::SeqCst), 0);
    assert_eq!(
        store
            .load_heartbeat_schedule_recovery(device())
            .await
            .unwrap()
            .generation,
        0
    );
}

#[tokio::test]
async fn committed_ledger_member_remains_pending_after_restart_and_no_sender_runs() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("committed.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let id = Uuid::now_v7();
    let before = prepare(&store, id, Some(HeartbeatHalt::Quarantine)).await;
    store.close().await;

    let restarted = OutboxStore::open(&path).await.unwrap();
    let observed = inspect_explicit_heartbeat_reconciliation(
        &restarted,
        device(),
        true,
        |device_id, pending| async move {
            assert_eq!(device_id, device());
            assert_eq!(pending, id);
            Ok(HeartbeatLookupDisposition::Committed)
        },
    )
    .await
    .unwrap();
    assert_eq!(
        observed,
        HeartbeatReconciliationObservation::CommittedStillHeld(id)
    );
    let recovery = restarted
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, before);
    let checkpoint = recovery.checkpoint.unwrap();
    assert_eq!(checkpoint.pending_request_id, Some(id));
    assert_eq!(checkpoint.halted, Some(HeartbeatHalt::Quarantine));

    let sends = AtomicUsize::new(0);
    let result = run_explicit_heartbeat_once(&restarted, device(), true, 999_999, 30, |_| {
        sends.fetch_add(1, Ordering::SeqCst);
        async { Ok(DeliveryDisposition::Accepted) }
    })
    .await
    .unwrap();
    assert_eq!(
        result,
        HeartbeatAttemptOutcome::Halted(HeartbeatHalt::Quarantine)
    );
    assert_eq!(sends.load(Ordering::SeqCst), 0);
}

#[tokio::test]
async fn unknown_denied_quarantined_and_malformed_lookup_never_mutate_state() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("unknown.sqlite"))
        .await
        .unwrap();
    let id = Uuid::now_v7();
    let generation = prepare(&store, id, None).await;

    for (disposition, expected) in [
        (
            HeartbeatLookupDisposition::Unresolved,
            HeartbeatReconciliationObservation::UnresolvedStillHeld(id),
        ),
        (
            HeartbeatLookupDisposition::PauseIdentity,
            HeartbeatReconciliationObservation::IdentityRejectedStillHeld(id),
        ),
        (
            HeartbeatLookupDisposition::Quarantine,
            HeartbeatReconciliationObservation::QuarantinedStillHeld(id),
        ),
        (
            HeartbeatLookupDisposition::Committed,
            HeartbeatReconciliationObservation::CommittedStillHeld(id),
        ),
    ] {
        let actual = inspect_explicit_heartbeat_reconciliation(
            &store,
            device(),
            true,
            |owner, request| async move {
                assert_eq!(owner, device());
                assert_eq!(request, id);
                Ok(disposition)
            },
        )
        .await
        .unwrap();
        assert_eq!(actual, expected);
    }
    assert!(
        inspect_explicit_heartbeat_reconciliation(&store, device(), true, |_, _| async {
            Err(TransportError::InvalidAcknowledgement)
        },)
        .await
        .is_err()
    );
    let recovery = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, generation);
    let checkpoint = recovery.checkpoint.unwrap();
    assert_eq!(checkpoint.pending_request_id, Some(id));
    assert_eq!(checkpoint.halted, None);
}

#[tokio::test]
async fn identity_halt_does_not_lookup_and_cross_device_is_rejected() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("identity.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let id = Uuid::now_v7();
    let generation = prepare(&store, id, Some(HeartbeatHalt::Identity)).await;
    let calls = AtomicUsize::new(0);
    assert_eq!(
        inspect_explicit_heartbeat_reconciliation(&store, device(), true, |_, _| {
            calls.fetch_add(1, Ordering::SeqCst);
            async { Ok(HeartbeatLookupDisposition::Committed) }
        },)
        .await
        .unwrap(),
        HeartbeatReconciliationObservation::IdentityHalted
    );
    assert_eq!(calls.load(Ordering::SeqCst), 0);
    assert_eq!(
        store
            .load_heartbeat_schedule_recovery(device())
            .await
            .unwrap()
            .generation,
        generation
    );
    assert!(matches!(
        inspect_explicit_heartbeat_reconciliation(&store, Uuid::now_v7(), true, |_, _| async {
            Ok(HeartbeatLookupDisposition::Committed)
        },)
        .await,
        Err(
            lanveriq_agent::heartbeat_reconciliation::HeartbeatReconciliationError::Store(
                StoreError::HeartbeatConflict
            )
        )
    ));
}

#[tokio::test]
async fn concurrent_quarantine_during_lookup_never_reports_stale_committed_as_current() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("race.sqlite"))
        .await
        .unwrap();
    let id = Uuid::now_v7();
    let generation = prepare(&store, id, None).await;

    let observed =
        inspect_explicit_heartbeat_reconciliation(&store, device(), true, |owner, pending| {
            let store = &store;
            async move {
                assert_eq!(owner, device());
                assert_eq!(pending, id);
                let recovery = store
                    .load_heartbeat_schedule_recovery(device())
                    .await
                    .unwrap();
                assert_eq!(recovery.generation, generation);
                let mut checkpoint = recovery.checkpoint.unwrap();
                checkpoint.note_uncertain_outcome().unwrap();
                store
                    .save_heartbeat_schedule_checkpoint(&checkpoint, generation)
                    .await
                    .unwrap();
                Ok(HeartbeatLookupDisposition::Committed)
            }
        })
        .await
        .unwrap();
    assert_eq!(
        observed,
        HeartbeatReconciliationObservation::StaleDuringLookup(id)
    );
    let recovery = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, generation + 1);
    let checkpoint = recovery.checkpoint.unwrap();
    assert_eq!(checkpoint.pending_request_id, Some(id));
    assert_eq!(checkpoint.halted, Some(HeartbeatHalt::Quarantine));
}

#[tokio::test]
async fn same_state_cas_bump_during_lookup_also_invalidates_evidence() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("generation.sqlite"))
        .await
        .unwrap();
    let id = Uuid::now_v7();
    let generation = prepare(&store, id, None).await;

    let observed =
        inspect_explicit_heartbeat_reconciliation(&store, device(), true, |_, pending| {
            let store = &store;
            async move {
                assert_eq!(pending, id);
                let checkpoint = store
                    .load_heartbeat_schedule_recovery(device())
                    .await
                    .unwrap()
                    .checkpoint
                    .unwrap();
                // A redundant same-state CAS still invalidates the original
                // observation, not a license to assume nothing else changed.
                store
                    .save_heartbeat_schedule_checkpoint(&checkpoint, generation)
                    .await
                    .unwrap();
                Ok(HeartbeatLookupDisposition::Unresolved)
            }
        })
        .await
        .unwrap();
    assert_eq!(
        observed,
        HeartbeatReconciliationObservation::StaleDuringLookup(id)
    );
    let recovery = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, generation + 1);
    assert_eq!(recovery.checkpoint.unwrap().pending_request_id, Some(id));
}
