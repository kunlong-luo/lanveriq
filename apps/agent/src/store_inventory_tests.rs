//! File-backed, synthetic-only SQLx tests for INV-01-C. No OS probing.
use super::{InventoryStage, OutboxStore, PendingInventory, StoreError};
use crate::inventory::{
    Address, Cpu, Hardware, Memory, NetworkInterface, OperatingSystem, RawInventory, Volume,
};
use std::collections::BTreeMap;
use uuid::Uuid;

const OBSERVED: &str = "2026-10-31T09:00:00Z";

fn raw() -> RawInventory {
    RawInventory {
        hardware: Hardware {
            manufacturer: Some("Synthetic Vendor".into()),
            model: Some("Test Desktop".into()),
            cpu: Some(Cpu {
                model: Some("Synthetic CPU".into()),
                arch: Some("x86_64".into()),
                physical_cores: Some(4),
                logical_cores: Some(8),
            }),
            memory: Some(Memory {
                total_bytes: Some(16_000_000_000),
            }),
        },
        os: OperatingSystem {
            family: "linux".into(),
            name: Some("Test Linux".into()),
            version: Some("24.04".into()),
            build: None,
            kernel_release: Some("synthetic-kernel".into()),
            arch: Some("x86_64".into()),
        },
        interfaces: vec![NetworkInterface {
            key: "nic-a".into(),
            name: "eth0".into(),
            kind: Some("ethernet".into()),
            mac_address: Some("02:00:00:00:00:01".into()),
            link_state: Some("up".into()),
            is_virtual: Some(false),
            addresses: vec![Address {
                ip: "192.0.2.10".into(),
                prefix_length: 24,
                family: "ipv4".into(),
                scope: Some("private".into()),
            }],
        }],
        volumes: vec![Volume {
            key: "root".into(),
            mount_point: "/".into(),
            filesystem: Some("ext4".into()),
            total_bytes: 100_000_000_000,
            free_bytes: Some(50_000_000_000),
            is_system: Some(true),
        }],
        capabilities: BTreeMap::from([("desktop_session".into(), "not_collected".into())]),
    }
}

fn staged(stage: InventoryStage) -> PendingInventory {
    match stage {
        InventoryStage::Staged(item) => item,
        other => panic!("expected staged, got {other:?}"),
    }
}

#[tokio::test]
async fn unchanged_content_does_not_advance_revision_or_change_stable_pending_uuid() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("no-churn.sqlite");
    let device = Uuid::now_v7();
    let store = OutboxStore::open(&path).await.unwrap();
    let first = staged(
        store
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await
            .unwrap(),
    );
    assert_eq!(first.revision, 1);
    assert_eq!(store.latest_inventory_revision().await.unwrap(), Some(1));
    let wire: serde_json::Value = serde_json::from_str(&first.body_json).unwrap();
    assert_eq!(wire["schema_version"], 1);
    assert_eq!(wire["snapshot_id"], first.snapshot_id.to_string());
    assert_eq!(wire["device_id"], device.to_string());
    assert_eq!(wire["inventory_revision"], 1);
    assert_eq!(wire["interfaces"][0]["addresses"][0]["ip"], "192.0.2.10");

    let repeated = store
        .stage_inventory_snapshot(device, "2026-10-31T09:01:00Z", raw())
        .await
        .unwrap();
    assert_eq!(repeated, InventoryStage::AlreadyPending(first.clone()));
    assert_eq!(store.pending_inventory().await.unwrap().unwrap(), first);
    assert_eq!(store.next_sequence().await.unwrap(), 1);
    store.close().await;

    let reopened = OutboxStore::open(&path).await.unwrap();
    assert_eq!(reopened.pending_inventory().await.unwrap().unwrap(), first);
    assert_eq!(
        reopened
            .stage_inventory_snapshot(device, "2026-10-31T09:05:00Z", raw())
            .await
            .unwrap(),
        InventoryStage::AlreadyPending(first.clone())
    );
    assert_eq!(reopened.latest_inventory_revision().await.unwrap(), Some(1));
    assert!(
        reopened
            .acknowledge_inventory_snapshot(device, first.snapshot_id, first.revision)
            .await
            .unwrap()
    );
    assert!(reopened.pending_inventory().await.unwrap().is_none());
    assert_eq!(
        reopened
            .stage_inventory_snapshot(device, "2026-10-31T09:07:00Z", raw())
            .await
            .unwrap(),
        InventoryStage::Unchanged
    );
    assert_eq!(reopened.latest_inventory_revision().await.unwrap(), Some(1));
    reopened.close().await;

    let after_ack_restart = OutboxStore::open(&path).await.unwrap();
    assert_eq!(
        after_ack_restart
            .stage_inventory_snapshot(device, "2026-10-31T09:10:00Z", raw())
            .await
            .unwrap(),
        InventoryStage::Unchanged
    );
}

#[tokio::test]
async fn changed_asset_increments_only_inventory_revision_and_stale_ack_cannot_erase_it() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("supersession.sqlite"))
        .await
        .unwrap();
    let device = Uuid::now_v7();
    let first = staged(
        store
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await
            .unwrap(),
    );
    let mut new_asset = raw();
    new_asset.interfaces[0].addresses[0].ip = "192.0.2.11".into();
    let second = staged(
        store
            .stage_inventory_snapshot(device, "2026-10-31T10:00:00Z", new_asset.clone())
            .await
            .unwrap(),
    );
    assert_eq!(second.revision, 2);
    assert_ne!(second.snapshot_id, first.snapshot_id);
    assert_eq!(store.next_sequence().await.unwrap(), 1);
    assert_eq!(store.latest_inventory_revision().await.unwrap(), Some(2));
    assert!(
        !store
            .acknowledge_inventory_snapshot(device, first.snapshot_id, first.revision)
            .await
            .unwrap()
    );
    assert!(
        !store
            .acknowledge_inventory_snapshot(Uuid::now_v7(), second.snapshot_id, second.revision)
            .await
            .unwrap()
    );
    assert!(
        !store
            .acknowledge_inventory_snapshot(device, Uuid::now_v7(), second.revision)
            .await
            .unwrap()
    );
    assert_eq!(store.pending_inventory().await.unwrap().unwrap(), second);
    assert_eq!(
        store
            .stage_inventory_snapshot(device, "2026-10-31T10:01:00Z", new_asset)
            .await
            .unwrap(),
        InventoryStage::AlreadyPending(second.clone())
    );
    assert!(
        store
            .acknowledge_inventory_snapshot(device, second.snapshot_id, second.revision)
            .await
            .unwrap()
    );
    assert!(
        !store
            .acknowledge_inventory_snapshot(device, second.snapshot_id, second.revision)
            .await
            .unwrap()
    );
    assert!(store.pending_inventory().await.unwrap().is_none());
}

#[tokio::test]
async fn same_semantics_in_different_nic_order_are_not_new_inventory() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("canonical.sqlite"))
        .await
        .unwrap();
    let device = Uuid::now_v7();
    let mut original = raw();
    let mut other = original.interfaces[0].clone();
    other.key = "nic-b".into();
    other.name = "wlan0".into();
    other.mac_address = None;
    other.addresses = vec![Address {
        ip: "2001:db8::2".into(),
        family: "ipv6".into(),
        prefix_length: 64,
        scope: Some("global".into()),
    }];
    original.interfaces.push(other);
    let first = staged(
        store
            .stage_inventory_snapshot(device, OBSERVED, original.clone())
            .await
            .unwrap(),
    );
    original.interfaces.reverse();
    assert_eq!(
        store
            .stage_inventory_snapshot(device, "2026-10-31T09:01:00Z", original)
            .await
            .unwrap(),
        InventoryStage::AlreadyPending(first)
    );
}

#[tokio::test]
async fn different_device_id_and_unbound_legacy_inventory_fail_closed() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("bound.sqlite"))
        .await
        .unwrap();
    let device = Uuid::now_v7();
    let first = staged(
        store
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await
            .unwrap(),
    );
    assert!(matches!(
        store
            .stage_inventory_snapshot(Uuid::now_v7(), OBSERVED, raw())
            .await,
        Err(StoreError::InventoryConflict)
    ));
    assert_eq!(store.pending_inventory().await.unwrap().unwrap(), first);
    assert!(matches!(
        store
            .stage_synthetic_inventory(Uuid::now_v7(), &serde_json::json!({"synthetic":true}))
            .await,
        Err(StoreError::InventoryConflict)
    ));

    let legacy = OutboxStore::open(&directory.path().join("legacy.sqlite"))
        .await
        .unwrap();
    assert_eq!(
        legacy
            .stage_synthetic_inventory(Uuid::now_v7(), &serde_json::json!({"synthetic":true}))
            .await
            .unwrap(),
        1
    );
    assert!(matches!(
        legacy
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await,
        Err(StoreError::InventoryConflict)
    ));
    assert_eq!(legacy.latest_inventory_revision().await.unwrap(), None);
}

#[tokio::test]
async fn database_failure_rolls_back_pending_row_digest_and_revision_atomically() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("rollback.sqlite"))
        .await
        .unwrap();
    let device = Uuid::now_v7();
    sqlx::query(
        "CREATE TRIGGER reject_state BEFORE INSERT ON inventory_latest_state \
         BEGIN SELECT RAISE(ABORT, 'synthetic inventory state fault'); END",
    )
    .execute(&store.pool)
    .await
    .unwrap();
    assert!(matches!(
        store
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await,
        Err(StoreError::Database(_))
    ));
    assert!(store.pending_inventory().await.unwrap().is_none());
    assert_eq!(store.latest_inventory_revision().await.unwrap(), None);
    let revision: i64 =
        sqlx::query_scalar("SELECT next_inventory_revision FROM agent_state WHERE singleton=1")
            .fetch_one(&store.pool)
            .await
            .unwrap();
    assert_eq!(revision, 1);
    sqlx::query("DROP TRIGGER reject_state")
        .execute(&store.pool)
        .await
        .unwrap();

    let first = staged(
        store
            .stage_inventory_snapshot(device, OBSERVED, raw())
            .await
            .unwrap(),
    );
    sqlx::query(
        "CREATE TRIGGER reject_update BEFORE UPDATE ON inventory_latest_state \
         BEGIN SELECT RAISE(ABORT, 'synthetic inventory replacement fault'); END",
    )
    .execute(&store.pool)
    .await
    .unwrap();
    let mut changed = raw();
    changed.os.version = Some("synthetic-upgraded".into());
    assert!(matches!(
        store
            .stage_inventory_snapshot(device, "2026-10-31T10:00:00Z", changed)
            .await,
        Err(StoreError::Database(_))
    ));
    assert_eq!(store.pending_inventory().await.unwrap().unwrap(), first);
    assert_eq!(store.latest_inventory_revision().await.unwrap(), Some(1));
    let revision: i64 =
        sqlx::query_scalar("SELECT next_inventory_revision FROM agent_state WHERE singleton=1")
            .fetch_one(&store.pool)
            .await
            .unwrap();
    assert_eq!(revision, 2);
}

#[tokio::test]
async fn invalid_inventory_is_rejected_before_any_state_or_pending_mutation() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("invalid.sqlite"))
        .await
        .unwrap();
    let device = Uuid::now_v7();
    let mut malicious = raw();
    malicious.interfaces[0].addresses[0].ip = "private.example".into();
    assert!(matches!(
        store
            .stage_inventory_snapshot(device, OBSERVED, malicious)
            .await,
        Err(StoreError::Inventory(_))
    ));
    assert!(store.pending_inventory().await.unwrap().is_none());
    assert_eq!(store.latest_inventory_revision().await.unwrap(), None);
    assert_eq!(store.next_sequence().await.unwrap(), 1);
}

#[tokio::test]
async fn modified_pending_json_is_never_forwarded_acknowledged_or_retried() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("integrity.sqlite"))
        .await.unwrap();
    let device = Uuid::now_v7();
    let first = staged(store.stage_inventory_snapshot(device, OBSERVED, raw()).await.unwrap());
    assert_eq!(store.verified_pending_inventory(device).await.unwrap(), Some(first.clone()));
    sqlx::query(
        "UPDATE inventory_pending SET body_json=json_set(body_json, '$.os.version', 'tampered') \
         WHERE singleton=1",
    ).execute(&store.pool).await.unwrap();
    assert!(matches!(store.verified_pending_inventory(device).await,
        Err(StoreError::InventoryConflict)));
    assert!(matches!(store.stage_inventory_snapshot(device, OBSERVED, raw()).await,
        Err(StoreError::InventoryConflict)));
    assert!(matches!(store.acknowledge_inventory_snapshot(
        device, first.snapshot_id, first.revision).await,
        Err(StoreError::InventoryConflict)));
    assert!(store.pending_inventory().await.unwrap().is_some());
    assert_eq!(store.latest_inventory_revision().await.unwrap(), Some(1));
}

#[tokio::test]
async fn recomputed_wire_digest_cannot_bypass_semantic_digest_or_identity_checks() {
    let directory = tempfile::tempdir().unwrap();
    let store = OutboxStore::open(&directory.path().join("forged.sqlite"))
        .await.unwrap();
    let device = Uuid::now_v7();
    let first = staged(store.stage_inventory_snapshot(device, OBSERVED, raw()).await.unwrap());
    let mut value: serde_json::Value = serde_json::from_str(&first.body_json).unwrap();
    value["os"]["version"] = serde_json::json!("edited");
    let rewritten = serde_json::to_string(&value).unwrap();
    let digest = format!("{:x}", sha2::Sha256::digest(rewritten.as_bytes()));
    sqlx::query(
        "UPDATE inventory_pending SET body_json=?,content_digest=? WHERE singleton=1",
    ).bind(&rewritten).bind(&digest).execute(&store.pool).await.unwrap();
    assert!(matches!(store.verified_pending_inventory(device).await,
        Err(StoreError::InventoryConflict)));

    // Restore the original wire content and digest, then tamper the embedded
    // device identity with an internally consistent wire checksum.
    let initial_digest = format!("{:x}", sha2::Sha256::digest(first.body_json.as_bytes()));
    sqlx::query(
        "UPDATE inventory_pending SET body_json=?,content_digest=? WHERE singleton=1",
    ).bind(&first.body_json).bind(initial_digest).execute(&store.pool).await.unwrap();
    assert_eq!(store.verified_pending_inventory(device).await.unwrap(), Some(first));
    assert!(matches!(store.verified_pending_inventory(Uuid::now_v7()).await,
        Err(StoreError::InventoryConflict)));
}
