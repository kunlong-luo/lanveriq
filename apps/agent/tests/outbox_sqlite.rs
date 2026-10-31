use lanveriq_agent::store::{OutboxStore, StoreError, SyntheticEvent};
use lanveriq_agent::transport::EventBatch;
use serde_json::json;
use std::path::Path;
use uuid::Uuid;

fn fixture(id: Uuid, cursor: u8) -> SyntheticEvent {
    SyntheticEvent {
        event_id: id,
        occurred_at_utc: "2026-10-31T00:00:00Z".to_string(),
        policy_version: 1,
        payload: json!({"synthetic":true,"value":cursor}),
        cursor: vec![cursor],
    }
}

#[tokio::test]
async fn sqlite_wal_full_foreign_keys_and_migration_are_real() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("agent.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    assert_eq!(store.settings().await.unwrap(), ("wal".to_string(), 2, 1));
    assert!(store.sqlite_version().starts_with("3."));
    assert_eq!(store.next_sequence().await.unwrap(), 1);
    store.close().await;
    assert!(
        OutboxStore::open(Path::new("relative.sqlite"))
            .await
            .is_err()
    );
}

#[tokio::test]
async fn event_cursor_and_sequence_survive_restart_and_ack_is_sparse() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("outbox.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    let first = Uuid::now_v7();
    let second = Uuid::now_v7();
    assert_eq!(store.append_synthetic(&fixture(first, 1)).await.unwrap(), 1);
    assert_eq!(
        store.append_synthetic(&fixture(second, 2)).await.unwrap(),
        2
    );
    assert_eq!(store.cursor().await.unwrap(), Some((vec![2], 2)));
    assert_eq!(store.next_sequence().await.unwrap(), 3);
    assert!(matches!(
        store.append_synthetic(&fixture(first, 3)).await,
        Err(StoreError::Duplicate)
    ));
    assert_eq!(store.next_sequence().await.unwrap(), 3);
    let batch = store.pending_batch().await.unwrap();
    assert_eq!(batch.len(), 2);
    assert_eq!(batch[0].event_id, first);
    assert_eq!(batch[1].event_id, second);
    assert_eq!(batch[0].sequence, 1);
    assert_eq!(batch[1].sequence, 2);
    assert_eq!(store.acknowledge(&[2]).await.unwrap(), 1);
    assert_eq!(store.pending_batch().await.unwrap()[0].sequence, 1);
    store.close().await;

    // Simulate crash after local commit but before a verified server ACK:
    // the same stable event ID and digest must be replayed.
    let restarted = OutboxStore::open(&db).await.unwrap();
    let pending = restarted.pending_batch().await.unwrap();
    assert_eq!(pending.len(), 1);
    assert_eq!(pending[0].event_id, first);
    assert_eq!(pending[0].content_digest.len(), 64);
    assert_eq!(restarted.next_sequence().await.unwrap(), 3);
    assert_eq!(restarted.acknowledge(&[9999]).await.unwrap(), 0);
    assert_eq!(restarted.pending_batch().await.unwrap().len(), 1);
    assert_eq!(restarted.acknowledge(&[1]).await.unwrap(), 1);
    assert!(restarted.pending_batch().await.unwrap().is_empty());
    assert_eq!(restarted.next_sequence().await.unwrap(), 3);
}

#[tokio::test]
async fn inventory_revision_is_independent_and_old_ack_cannot_drop_newer_state() {
    let dir = tempfile::tempdir().unwrap();
    let db = dir.path().join("inventory.sqlite");
    let store = OutboxStore::open(&db).await.unwrap();
    let old = Uuid::now_v7();
    let new = Uuid::now_v7();
    assert_eq!(
        store
            .stage_synthetic_inventory(old, &json!({"x":1}))
            .await
            .unwrap(),
        1
    );
    assert_eq!(
        store
            .stage_synthetic_inventory(new, &json!({"x":2}))
            .await
            .unwrap(),
        2
    );
    assert!(!store.acknowledge_inventory(1).await.unwrap());
    let pending = store.pending_inventory().await.unwrap().unwrap();
    assert_eq!(pending.revision, 2);
    assert_eq!(pending.snapshot_id, new);
    assert_eq!(store.next_sequence().await.unwrap(), 1);
    store.close().await;
    let reopened = OutboxStore::open(&db).await.unwrap();
    assert_eq!(
        reopened
            .pending_inventory()
            .await
            .unwrap()
            .unwrap()
            .revision,
        2
    );
    assert!(reopened.acknowledge_inventory(2).await.unwrap());
    assert!(reopened.pending_inventory().await.unwrap().is_none());
}

#[tokio::test]
async fn invalid_fixtures_leave_cursor_and_sequence_unchanged() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("invalid.sqlite"))
        .await
        .unwrap();
    let mut sample = fixture(Uuid::now_v7(), 1);
    sample.cursor.clear();
    assert!(matches!(
        store.append_synthetic(&sample).await,
        Err(StoreError::InvalidFixture)
    ));
    sample.cursor.push(1);
    sample.policy_version = 0;
    assert!(matches!(
        store.append_synthetic(&sample).await,
        Err(StoreError::InvalidFixture)
    ));
    assert_eq!(store.next_sequence().await.unwrap(), 1);
    assert!(store.cursor().await.unwrap().is_none());
}

#[tokio::test]
async fn prepared_batch_identity_and_membership_survive_retry_and_restart() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join("prepared.sqlite");
    let first = Uuid::now_v7();
    let second = Uuid::now_v7();
    let store = OutboxStore::open(&file).await.unwrap();
    assert!(store.prepare_synthetic_batch().await.unwrap().is_none());
    store.append_synthetic(&fixture(first, 1)).await.unwrap();
    let prepared = store.prepare_synthetic_batch().await.unwrap().unwrap();
    assert_eq!(prepared.events.len(), 1);
    assert_eq!(prepared.events[0].event_id, first);
    let batch_id = prepared.batch_id;
    // An incoming event must NOT expand an outstanding ambiguous-timeout batch.
    store.append_synthetic(&fixture(second, 2)).await.unwrap();
    let retry = store.prepare_synthetic_batch().await.unwrap().unwrap();
    assert_eq!(retry.batch_id, batch_id);
    assert_eq!(retry.events.len(), 1);
    store.close().await;

    let recovered = OutboxStore::open(&file).await.unwrap();
    let retry = recovered.prepare_synthetic_batch().await.unwrap().unwrap();
    assert_eq!(retry.batch_id, batch_id);
    assert_eq!(retry.events[0].event_id, first);
    // Only an externally validated ACK would invoke this low-level method.
    assert_eq!(recovered.acknowledge(&[1]).await.unwrap(), 1);
    let next = recovered.prepare_synthetic_batch().await.unwrap().unwrap();
    assert_ne!(next.batch_id, batch_id);
    assert_eq!(next.events.len(), 1);
    assert_eq!(next.events[0].event_id, second);
    assert_eq!(next.events[0].sequence, 2);
    assert_eq!(recovered.acknowledge(&[2]).await.unwrap(), 1);
    assert!(recovered.prepare_synthetic_batch().await.unwrap().is_none());
}

#[tokio::test]
async fn prepared_selection_leaves_room_for_serialized_event_envelopes() {
    let dir = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&dir.path().join("wire-cap.sqlite"))
        .await
        .unwrap();
    for i in 0..100 {
        let mut event = fixture(Uuid::now_v7(), i as u8);
        event.payload = serde_json::json!({"synthetic": true, "text": "x".repeat(5100)});
        store.append_synthetic(&event).await.unwrap();
    }
    let planned = store.prepare_synthetic_batch().await.unwrap().unwrap();
    let batch =
        EventBatch::from_pending(Uuid::now_v7(), planned.batch_id, &planned.events).unwrap();
    assert!(planned.events.len() < 100);
    assert!(serde_json::to_vec(&batch).unwrap().len() <= 512 * 1024);
}
