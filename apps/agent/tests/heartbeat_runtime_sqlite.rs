//! Batch A3: actual file-backed SQLite recovery and one-shot decision tests.
//! All identities/data are synthetic; no TLS request, OS collection or agent
//! service installation is performed.
use lanveriq_agent::heartbeat_runtime::{
    HeartbeatAttemptOutcome, HeartbeatDriverError, run_explicit_heartbeat_once,
};
use lanveriq_agent::heartbeat_schedule::{
    HeartbeatCheckpoint, HeartbeatHalt,
};
use lanveriq_agent::store::{OutboxStore, StoreError};
use lanveriq_agent::transport::{DeliveryDisposition, TransportError};
use std::sync::atomic::{AtomicUsize, Ordering};
use uuid::Uuid;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

#[tokio::test]
async fn disabled_is_off_and_exact_ack_is_durable_across_restart() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("heartbeat.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let sends = AtomicUsize::new(0);
    let disabled = run_explicit_heartbeat_once(&store, device(), false, 1_000, 30, |_| {
        sends.fetch_add(1, Ordering::SeqCst);
        async { Ok(DeliveryDisposition::Accepted) }
    })
    .await
    .unwrap();
    assert_eq!(disabled, HeartbeatAttemptOutcome::Disabled);
    assert_eq!(sends.load(Ordering::SeqCst), 0);
    assert_eq!(
        store.load_heartbeat_schedule_recovery(device()).await.unwrap().generation,
        0
    );

    let accepted = run_explicit_heartbeat_once(&store, device(), true, 1_000, 30, |id| {
        assert!(!id.is_nil());
        sends.fetch_add(1, Ordering::SeqCst);
        async { Ok(DeliveryDisposition::Accepted) }
    })
    .await
    .unwrap();
    assert_eq!(accepted, HeartbeatAttemptOutcome::Accepted);
    assert_eq!(sends.load(Ordering::SeqCst), 1);
    store.close().await;

    let restarted = OutboxStore::open(&path).await.unwrap();
    let recovery = restarted
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovery.generation, 2);
    let saved = recovery.checkpoint.unwrap();
    assert_eq!(saved.pending_request_id, None);
    assert_eq!(saved.last_acknowledged_at, Some(1_000));
    let too_soon =
        run_explicit_heartbeat_once(&restarted, device(), true, 1_029, 30, |_| {
            sends.fetch_add(1, Ordering::SeqCst);
            async { Ok(DeliveryDisposition::Accepted) }
        })
        .await
        .unwrap();
    assert_eq!(too_soon, HeartbeatAttemptOutcome::WaitUntil(1_030));
    assert_eq!(sends.load(Ordering::SeqCst), 1);
}

#[tokio::test]
async fn crash_after_prepare_before_network_requires_reconciliation() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("crash.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let id = Uuid::now_v7();
    let mut staged = HeartbeatCheckpoint::initial(device()).unwrap();
    staged.begin(true, 10, 30, id).unwrap();
    assert_eq!(
        store.save_heartbeat_schedule_checkpoint(&staged, 0).await.unwrap(),
        1
    );
    store.close().await;

    let restarted = OutboxStore::open(&path).await.unwrap();
    let called = AtomicUsize::new(0);
    let result = run_explicit_heartbeat_once(&restarted, device(), true, 100_000, 30, |_| {
        called.fetch_add(1, Ordering::SeqCst);
        async { Ok(DeliveryDisposition::Accepted) }
    })
    .await
    .unwrap();
    assert_eq!(result, HeartbeatAttemptOutcome::NeedsReconciliation(id));
    assert_eq!(called.load(Ordering::SeqCst), 0);
    assert_eq!(
        restarted
            .load_heartbeat_schedule_recovery(device())
            .await
            .unwrap()
            .checkpoint
            .unwrap()
            .pending_request_id,
        Some(id)
    );
}

#[tokio::test]
async fn identity_denial_and_uncertain_network_result_survive_restart() {
    for (index, disposition, expected) in [
        (
            0,
            Some(DeliveryDisposition::PauseIdentity),
            HeartbeatHalt::Identity,
        ),
        (
            1,
            Some(DeliveryDisposition::Quarantine),
            HeartbeatHalt::Quarantine,
        ),
        (2, None, HeartbeatHalt::Quarantine),
    ] {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(format!("halt-{index}.sqlite"));
        let store = OutboxStore::open(&path).await.unwrap();
        let reply = disposition.clone();
        let result = run_explicit_heartbeat_once(&store, device(), true, 100, 30, |_| async move {
            reply.ok_or(TransportError::InvalidAcknowledgement)
        })
        .await;
        if disposition.is_none() {
            assert!(matches!(result, Err(HeartbeatDriverError::Transport(_))));
        } else {
            assert_eq!(result.unwrap(), HeartbeatAttemptOutcome::Halted(expected));
        }
        store.close().await;

        let restarted = OutboxStore::open(&path).await.unwrap();
        let saved = restarted
            .load_heartbeat_schedule_recovery(device())
            .await
            .unwrap()
            .checkpoint
            .unwrap();
        assert_eq!(saved.halted, Some(expected));
        assert!(saved.pending_request_id.is_some());
        let sends = AtomicUsize::new(0);
        let result =
            run_explicit_heartbeat_once(&restarted, device(), true, 100_000, 30, |_| {
                sends.fetch_add(1, Ordering::SeqCst);
                async { Ok(DeliveryDisposition::Accepted) }
            })
            .await
            .unwrap();
        assert_eq!(result, HeartbeatAttemptOutcome::Halted(expected));
        assert_eq!(sends.load(Ordering::SeqCst), 0);
    }
}

#[tokio::test]
async fn cross_device_stale_writer_and_silent_unpause_are_denied() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("cas.sqlite"))
        .await
        .unwrap();
    let id = Uuid::now_v7();
    let mut first = HeartbeatCheckpoint::initial(device()).unwrap();
    first.begin(true, 100, 30, id).unwrap();
    store.save_heartbeat_schedule_checkpoint(&first, 0).await.unwrap();
    assert!(matches!(
        store.load_heartbeat_schedule_recovery(Uuid::now_v7()).await,
        Err(StoreError::HeartbeatConflict)
    ));

    let mut paused = first.clone();
    paused
        .finish(id, 101, DeliveryDisposition::PauseIdentity)
        .unwrap();
    assert_eq!(
        store.save_heartbeat_schedule_checkpoint(&paused, 1).await.unwrap(),
        2
    );
    let mut old_writer = first.clone();
    old_writer.finish(id, 102, DeliveryDisposition::Accepted).unwrap();
    assert!(matches!(
        store.save_heartbeat_schedule_checkpoint(&old_writer, 1).await,
        Err(StoreError::HeartbeatConflict)
    ));
    let mut unpause = paused.clone();
    unpause.halted = None;
    assert!(matches!(
        store.save_heartbeat_schedule_checkpoint(&unpause, 2).await,
        Err(StoreError::HeartbeatConflict)
    ));
    let recovered = store
        .load_heartbeat_schedule_recovery(device())
        .await
        .unwrap();
    assert_eq!(recovered.generation, 2);
    assert_eq!(recovered.checkpoint.unwrap().halted, Some(HeartbeatHalt::Identity));
}

#[tokio::test]
async fn missing_or_malformed_checkpoint_cannot_reset_identity_pause() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("tamper.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
    let id = Uuid::now_v7();
    checkpoint.begin(true, 100, 30, id).unwrap();
    checkpoint
        .finish(id, 101, DeliveryDisposition::PauseIdentity)
        .unwrap();
    // First persist the pending state before the identity pause.
    let mut pending = HeartbeatCheckpoint::initial(device()).unwrap();
    pending.begin(true, 100, 30, id).unwrap();
    store.save_heartbeat_schedule_checkpoint(&pending, 0).await.unwrap();
    store.save_heartbeat_schedule_checkpoint(&checkpoint, 1).await.unwrap();
    store.close().await;

    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", path.display()))
        .await
        .unwrap();
    sqlx::query(
        "UPDATE heartbeat_schedule_checkpoint SET state_json = \
         json_set(state_json, '$.forged', 1) WHERE singleton=1",
    )
    .execute(&pool)
    .await
    .unwrap();
    pool.close().await;
    let reopened = OutboxStore::open(&path).await.unwrap();
    assert!(matches!(
        reopened.load_heartbeat_schedule_recovery(device()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    reopened.close().await;

    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", path.display()))
        .await
        .unwrap();
    sqlx::query("DELETE FROM heartbeat_schedule_checkpoint WHERE singleton=1")
        .execute(&pool)
        .await
        .unwrap();
    pool.close().await;
    let reopened = OutboxStore::open(&path).await.unwrap();
    assert!(matches!(
        reopened.load_heartbeat_schedule_recovery(device()).await,
        Err(StoreError::HeartbeatConflict)
    ));
    assert!(matches!(
        reopened
            .save_heartbeat_schedule_checkpoint(&HeartbeatCheckpoint::initial(device()).unwrap(), 0)
            .await,
        Err(StoreError::HeartbeatConflict)
    ));
}

#[tokio::test]
async fn write_ahead_checkpoint_is_visible_inside_sender_before_any_http() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("write-ahead.sqlite"))
        .await
        .unwrap();
    let db = &store;
    let outcome = run_explicit_heartbeat_once(&store, device(), true, 100, 30, |id| async move {
        let recovered = db.load_heartbeat_schedule_recovery(device()).await.unwrap();
        assert_eq!(recovered.generation, 1);
        assert_eq!(recovered.checkpoint.unwrap().pending_request_id, Some(id));
        Ok(DeliveryDisposition::Accepted)
    })
    .await
    .unwrap();
    assert_eq!(outcome, HeartbeatAttemptOutcome::Accepted);
}

#[tokio::test]
async fn failed_ack_checkpoint_update_leaves_durable_pending_and_never_claims_success() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("failed-checkpoint.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", path.display()))
        .await
        .unwrap();
    sqlx::query(
        "CREATE TRIGGER synthetic_fail_heartbeat_checkpoint \\
         BEFORE UPDATE ON heartbeat_schedule_checkpoint \\
         BEGIN SELECT RAISE(ABORT, 'synthetic heartbeat checkpoint failure'); END",
    )
    .execute(&pool)
    .await
    .unwrap();
    pool.close().await;
    let outcome = run_explicit_heartbeat_once(&store, device(), true, 1_000, 30, |_| async {
        Ok(DeliveryDisposition::Accepted)
    })
    .await;
    assert!(matches!(outcome, Err(HeartbeatDriverError::Store(StoreError::Database(_)))));
    let pending = store.load_heartbeat_schedule_recovery(device()).await.unwrap();
    assert_eq!(pending.generation, 1);
    assert!(pending.checkpoint.unwrap().pending_request_id.is_some());
}
