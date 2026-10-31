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
    store
        .save_inventory_schedule_checkpoint(scheduler.checkpoint())
        .await
        .unwrap();
    store.close().await;

    let store = OutboxStore::open(&path).await.unwrap();
    let saved = store
        .load_inventory_schedule_checkpoint(device())
        .await
        .unwrap()
        .unwrap();
    let restarted = InventoryScheduler::restore(device(), true, Some(saved)).unwrap();
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
    store
        .save_inventory_schedule_checkpoint(&own)
        .await
        .unwrap();
    assert!(matches!(
        store
            .load_inventory_schedule_checkpoint(Uuid::now_v7())
            .await,
        Err(StoreError::InventoryConflict)
    ));
    assert!(matches!(
        store
            .save_inventory_schedule_checkpoint(
                &InventoryScheduleCheckpoint::initial(Uuid::now_v7()).unwrap()
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
        store.load_inventory_schedule_checkpoint(device()).await,
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
        store
            .save_inventory_schedule_checkpoint(scheduler.checkpoint())
            .await
            .unwrap();
        store.close().await;
        let reopened = OutboxStore::open(&path).await.unwrap();
        let saved = reopened
            .load_inventory_schedule_checkpoint(device())
            .await
            .unwrap();
        let restarted = InventoryScheduler::restore(device(), true, saved).unwrap();
        assert!(matches!(
            restarted.decide(24 * 60 * 60 * 10, true),
            ScheduleDecision::Halted(ScheduleHalt::Identity | ScheduleHalt::Quarantine)
        ));
    }
}
