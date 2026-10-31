//! File-backed SQLite restart and tamper checks, all synthetic.
use lanveriq_agent::inventory::{Hardware, OperatingSystem, RawInventory};
use lanveriq_agent::inventory_schedule::{
    InventoryScheduleCheckpoint, InventoryScheduler, ScheduleDecision, ScheduleHalt,
};
use lanveriq_agent::store::{InventoryStage, OutboxStore, StoreError};
use lanveriq_agent::transport::DeliveryDisposition;
use uuid::Uuid;

fn device() -> Uuid {
    Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
}

fn synthetic_assets() -> RawInventory {
    RawInventory {
        hardware: Hardware {
            manufacturer: None,
            model: None,
            cpu: None,
            memory: None,
        },
        os: OperatingSystem {
            family: "linux".into(),
            name: Some("synthetic".into()),
            version: None,
            build: None,
            kernel_release: None,
            arch: None,
        },
        interfaces: vec![],
        volumes: vec![],
        capabilities: Default::default(),
    }
}

#[tokio::test]
async fn retry_and_strict_pending_identity_survive_real_sqlite_restart() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("schedule.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let pending = match store
        .stage_inventory_snapshot(device(), "2026-10-31T00:00:00Z", synthetic_assets())
        .await
        .unwrap()
    {
        InventoryStage::Staged(pending) => pending,
        other => panic!("unexpected stage: {other:?}"),
    };
    let mut scheduler = InventoryScheduler::restore(device(), true, None).unwrap();
    scheduler
        .note_delivery_result(
            1000,
            &DeliveryDisposition::Retry {
                after: std::time::Duration::from_secs(300),
            },
        )
        .unwrap();
    let deadline = scheduler.checkpoint().retry_not_before.unwrap();
    assert_eq!(
        store
            .save_inventory_schedule_checkpoint(scheduler.checkpoint(), 0)
            .await
            .unwrap(),
        1
    );
    store.close().await;

    let store = OutboxStore::open(&path).await.unwrap();
    let recovery = store.load_inventory_schedule_recovery(device()).await.unwrap();
    assert_eq!(recovery.generation, 1);
    assert_eq!(recovery.pending.as_ref().unwrap().snapshot_id, pending.snapshot_id);
    let restarted =
        InventoryScheduler::restore(device(), true, recovery.checkpoint).unwrap();
    assert_eq!(
        restarted.decide(1001, true),
        ScheduleDecision::WaitUntil(deadline)
    );
    assert_eq!(
        restarted.decide(deadline, true),
        ScheduleDecision::DeliverPending
    );
    let persisted = store
        .verified_pending_inventory(device())
        .await
        .unwrap()
        .unwrap();
    assert_eq!(persisted.snapshot_id, pending.snapshot_id);
    assert_eq!(persisted.revision, pending.revision);
    assert_eq!(persisted.body_json, pending.body_json);
    assert!(
        !store
            .acknowledge_inventory_snapshot(device(), Uuid::now_v7(), pending.revision)
            .await
            .unwrap()
    );
    assert!(
        store
            .verified_pending_inventory(device())
            .await
            .unwrap()
            .is_some()
    );
}

#[tokio::test]
async fn checkpoint_is_device_bound_and_invalid_rows_fail_closed() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("checkpoint.sqlite");
    let store = OutboxStore::open(&path).await.unwrap();
    let own = InventoryScheduleCheckpoint::initial(device()).unwrap();
    assert_eq!(store.save_inventory_schedule_checkpoint(&own, 0).await.unwrap(), 1);
    assert!(matches!(
        store
            .load_inventory_schedule_recovery(Uuid::now_v7())
            .await,
        Err(StoreError::InventoryConflict)
    ));
    assert!(matches!(
        store
            .save_inventory_schedule_checkpoint(
                &InventoryScheduleCheckpoint::initial(Uuid::now_v7()).unwrap(), 1
            )
            .await,
        Err(StoreError::InventoryConflict)
    ));
    assert!(matches!(
        store
            .stage_inventory_snapshot(Uuid::now_v7(), "2026-10-31T00:00:00Z", synthetic_assets())
            .await,
        Err(StoreError::InventoryConflict)
    ));
    store.close().await;

    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", path.display()))
        .await
        .unwrap();
    sqlx::query(
        "UPDATE inventory_schedule_checkpoint SET state_json = json_set(state_json, '$.unknown', 1) WHERE singleton=1",
    )
    .execute(&pool)
    .await
    .unwrap();
    pool.close().await;
    let store = OutboxStore::open(&path).await.unwrap();
    assert!(matches!(
        store.load_inventory_schedule_recovery(device()).await,
        Err(StoreError::InventoryConflict)
    ));
}

#[tokio::test]
async fn identity_and_stale_acks_remain_paused_on_disk() {
    for (idx, disposition) in [
        DeliveryDisposition::PauseIdentity,
        DeliveryDisposition::Quarantine,
    ]
    .into_iter()
    .enumerate()
    {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(format!("halt-{idx}.sqlite"));
        let store = OutboxStore::open(&path).await.unwrap();
        let mut scheduler = InventoryScheduler::restore(device(), true, None).unwrap();
        scheduler.note_delivery_result(100, &disposition).unwrap();
        assert_eq!(
            store
                .save_inventory_schedule_checkpoint(scheduler.checkpoint(), 0)
                .await
                .unwrap(),
            1
        );
        store.close().await;
        let reopened = OutboxStore::open(&path).await.unwrap();
        let recovered = reopened
            .load_inventory_schedule_recovery(device())
            .await
            .unwrap();
        let restarted =
            InventoryScheduler::restore(device(), true, recovered.checkpoint).unwrap();
        assert!(matches!(
            restarted.decide(24 * 60 * 60 * 10, true),
            ScheduleDecision::Halted(ScheduleHalt::Identity | ScheduleHalt::Quarantine)
        ));
    }
}

#[tokio::test]
async fn stale_writer_cannot_overwrite_new_pause_or_clear_halt() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("cas.sqlite")).await.unwrap();
    let initial = InventoryScheduleCheckpoint::initial(device()).unwrap();
    assert_eq!(store.save_inventory_schedule_checkpoint(&initial, 0).await.unwrap(), 1);
    let old_view = store.load_inventory_schedule_recovery(device()).await.unwrap();
    let mut paused = InventoryScheduler::restore(device(), true, old_view.checkpoint.clone()).unwrap();
    paused.note_delivery_result(100, &DeliveryDisposition::PauseIdentity).unwrap();
    assert_eq!(store.save_inventory_schedule_checkpoint(paused.checkpoint(), 1).await.unwrap(), 2);
    // A stale timeout/retry cannot overwrite a newer security halt.
    let mut stale = InventoryScheduler::restore(device(), true, old_view.checkpoint).unwrap();
    stale.note_transport_failure(110).unwrap();
    assert!(matches!(
        store.save_inventory_schedule_checkpoint(stale.checkpoint(), 1).await,
        Err(StoreError::InventoryConflict)
    ));
    // Even a caller with the current generation cannot silently unblock.
    assert!(matches!(
        store.save_inventory_schedule_checkpoint(&initial, 2).await,
        Err(StoreError::InventoryConflict)
    ));
    let recovered = store.load_inventory_schedule_recovery(device()).await.unwrap();
    assert_eq!(recovered.generation, 2);
    assert_eq!(recovered.checkpoint.unwrap().halted, Some(ScheduleHalt::Identity));
}

#[tokio::test]
async fn checkpoint_deleted_after_first_save_does_not_reset_retry_or_halt() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("missing.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    let mut scheduler = InventoryScheduler::restore(device(), true, None).unwrap();
    scheduler.note_delivery_result(5, &DeliveryDisposition::Quarantine).unwrap();
    store.save_inventory_schedule_checkpoint(scheduler.checkpoint(), 0).await.unwrap();
    store.close().await;
    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", db.display())).await.unwrap();
    sqlx::query("DELETE FROM inventory_schedule_checkpoint WHERE singleton=1")
        .execute(&pool).await.unwrap();
    pool.close().await;
    let store = OutboxStore::open(&db).await.unwrap();
    assert!(matches!(
        store.load_inventory_schedule_recovery(device()).await,
        Err(StoreError::InventoryConflict)
    ));
    let first = InventoryScheduleCheckpoint::initial(device()).unwrap();
    assert!(matches!(
        store.save_inventory_schedule_checkpoint(&first, 0).await,
        Err(StoreError::InventoryConflict)
    ));
}

#[tokio::test]
async fn injected_sqlite_write_failure_rolls_back_entire_checkpoint() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("rollback.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    let initial = InventoryScheduleCheckpoint::initial(device()).unwrap();
    store.save_inventory_schedule_checkpoint(&initial, 0).await.unwrap();
    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", db.display())).await.unwrap();
    sqlx::query(
        "CREATE TRIGGER synthetic_fail_checkpoint BEFORE UPDATE ON inventory_schedule_checkpoint \
         BEGIN SELECT RAISE(ABORT, 'synthetic checkpoint write failure'); END"
    )
    .execute(&pool).await.unwrap();
    pool.close().await;
    let mut scheduler = InventoryScheduler::restore(device(), true, Some(initial.clone())).unwrap();
    scheduler.note_transport_failure(10).unwrap();
    assert!(matches!(
        store.save_inventory_schedule_checkpoint(scheduler.checkpoint(), 1).await,
        Err(StoreError::Database(_))
    ));
    let recovered = store.load_inventory_schedule_recovery(device()).await.unwrap();
    assert_eq!(recovered.generation, 1);
    assert_eq!(recovered.checkpoint, Some(initial));
}

#[tokio::test]
async fn corrupted_outbox_blocks_schedule_recovery_and_checkpoint_writes() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("outbox-corruption.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    store.stage_inventory_snapshot(
        device(), "2026-10-31T00:00:00Z", synthetic_assets()
    ).await.unwrap();
    let initial = InventoryScheduleCheckpoint::initial(device()).unwrap();
    store.save_inventory_schedule_checkpoint(&initial, 0).await.unwrap();
    store.close().await;
    let pool = sqlx::SqlitePool::connect(&format!("sqlite:{}", db.display())).await.unwrap();
    sqlx::query(
        "UPDATE inventory_pending SET body_json = json_set(body_json, '$.os.family', 'windows') WHERE singleton=1"
    ).execute(&pool).await.unwrap();
    pool.close().await;
    let store = OutboxStore::open(&db).await.unwrap();
    assert!(matches!(
        store.load_inventory_schedule_recovery(device()).await,
        Err(StoreError::InventoryConflict)
    ));
    assert!(matches!(
        store.save_inventory_schedule_checkpoint(&initial, 1).await,
        Err(StoreError::InventoryConflict)
    ));
}
