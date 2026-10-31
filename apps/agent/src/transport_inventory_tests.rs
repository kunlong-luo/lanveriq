//! Synthetic inventory ACK classification. Never sends an HTTP request.
use super::*;

fn pending() -> PendingInventory {
    PendingInventory {
        snapshot_id: Uuid::now_v7(),
        revision: 17,
        body_json: "{\"schema_version\":1}".into(),
    }
}

fn response(device: Uuid, item: &PendingInventory, outcome: &str) -> Vec<u8> {
    serde_json::json!({
        "schema_version": 1,
        "device_id": device,
        "snapshot_id": item.snapshot_id,
        "inventory_revision": item.revision,
        "outcome": outcome,
        "server_time": "2026-10-31T09:00:00Z"
    })
    .to_string()
    .into_bytes()
}

#[test]
fn exact_applied_and_duplicate_receipts_are_accepted() {
    let device = Uuid::now_v7();
    let item = pending();
    for outcome in ["applied", "duplicate"] {
        assert_eq!(
            classify_inventory_response(
                200,
                &response(device, &item, outcome),
                device,
                &item,
                None
            )
            .unwrap(),
            DeliveryDisposition::Accepted
        );
    }
}

#[test]
fn missing_wrong_or_ambiguous_ack_cannot_remove_inventory() {
    let device = Uuid::now_v7();
    let item = pending();
    let mut ack: serde_json::Value =
        serde_json::from_slice(&response(device, &item, "applied")).unwrap();
    for field in [
        "device_id",
        "snapshot_id",
        "inventory_revision",
        "schema_version",
        "server_time",
    ] {
        let mut changed = ack.clone();
        changed.as_object_mut().unwrap().remove(field);
        assert!(matches!(
            classify_inventory_response(200, changed.to_string().as_bytes(), device, &item, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    for (field, value) in [
        ("device_id", serde_json::json!(Uuid::now_v7())),
        ("snapshot_id", serde_json::json!(Uuid::now_v7())),
        ("inventory_revision", serde_json::json!(18)),
        ("schema_version", serde_json::json!(2)),
        ("server_time", serde_json::json!("invalid")),
    ] {
        ack[field] = value;
        assert!(matches!(
            classify_inventory_response(200, ack.to_string().as_bytes(), device, &item, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
        ack = serde_json::from_slice(&response(device, &item, "applied")).unwrap();
    }
    ack["extra_field"] = serde_json::json!("unreviewed");
    assert!(matches!(
        classify_inventory_response(200, ack.to_string().as_bytes(), device, &item, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_inventory_response(200, b"{}", device, &item, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
}

#[test]
fn stale_ignored_is_never_an_exact_positive_ack() {
    let device = Uuid::now_v7();
    let item = pending();
    assert_eq!(
        classify_inventory_response(
            200,
            &response(device, &item, "stale_ignored"),
            device,
            &item,
            None
        )
        .unwrap(),
        DeliveryDisposition::Quarantine
    );
    assert!(matches!(
        classify_inventory_response(
            200,
            &response(device, &item, "unknown_outcome"),
            device,
            &item,
            None
        ),
        Err(TransportError::InvalidAcknowledgement)
    ));
}

#[test]
fn status_classification_never_acknowledges_non_200() {
    let device = Uuid::now_v7();
    let item = pending();
    let body = response(device, &item, "applied");
    assert_eq!(
        classify_inventory_response(403, &body, device, &item, None).unwrap(),
        DeliveryDisposition::PauseIdentity
    );
    assert_eq!(
        classify_inventory_response(409, &body, device, &item, None).unwrap(),
        DeliveryDisposition::Quarantine
    );
    assert_eq!(
        classify_inventory_response(503, &body, device, &item, Some("10")).unwrap(),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(10)
        }
    );
    assert!(matches!(
        classify_inventory_response(200, &vec![0; 4097], device, &item, None),
        Err(TransportError::OversizedResponse)
    ));
}
