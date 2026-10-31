use lanveriq_agent::store::PendingEvent;
use lanveriq_agent::transport::{
    AgentTlsTransport, DeliveryDisposition, EventBatch, Heartbeat, TransportError,
    classify_batch_response, classify_error_status,
};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::time::Duration;
use uuid::Uuid;

fn pending(sequence: i64) -> PendingEvent {
    let payload_json = format!(r#"{{"synthetic":true,"counter":{sequence}}}"#);
    let content_digest = format!("{:x}", Sha256::digest(payload_json.as_bytes()));
    PendingEvent {
        event_id: Uuid::now_v7(),
        sequence,
        policy_version: 1,
        occurred_at_utc: "2026-10-31T00:00:00Z".into(),
        payload_json,
        content_digest,
    }
}

fn ack_json(sequences: &[i64]) -> Vec<u8> {
    serde_json::to_vec(&json!({
        "schema_version":1,
        "accepted_sequences":sequences,
        "server_time":"2026-10-31T00:00:00Z",
        "request_id":Uuid::now_v7(),
    }))
    .unwrap()
}

#[test]
fn exact_full_ack_accepts_unordered_but_not_partial_duplicate_or_extra_sequences() {
    let expected = [1, 2, 3];
    assert_eq!(
        classify_batch_response(200, &ack_json(&[3, 1, 2]), &expected, None).unwrap(),
        DeliveryDisposition::Accepted
    );
    for wrong in [&[1, 2][..], &[1, 2, 2][..], &[1, 2, 5][..]] {
        assert!(matches!(
            classify_batch_response(200, &ack_json(wrong), &expected, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_batch_response(200, &ack_json(&[1, 2, 3]), &[1, 1, 3], None),
        Err(TransportError::InvalidEvent)
    ));
    assert!(matches!(
        classify_batch_response(200, b"{}", &expected, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
}

#[test]
fn incomplete_or_wrong_schema_200_is_not_a_commit_signal() {
    for response in [
        json!({"schema_version":2,"accepted_sequences":[1],"server_time":"2026-10-31T00:00:00Z","request_id":Uuid::now_v7()}),
        json!({"schema_version":1,"accepted_sequences":[1],"server_time":"","request_id":Uuid::now_v7()}),
        json!({"schema_version":1,"accepted_sequences":[1],"server_time":"2026-10-31T00:00:00Z","request_id":Uuid::nil()}),
    ] {
        assert!(matches!(
            classify_batch_response(200, &serde_json::to_vec(&response).unwrap(), &[1], None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_batch_response(200, &vec![b'x'; 4097], &[1], None),
        Err(TransportError::OversizedResponse)
    ));
}

#[test]
fn auth_revocation_conflicts_and_retryable_responses_never_ack() {
    for status in [401, 403] {
        assert_eq!(
            classify_batch_response(status, &ack_json(&[1]), &[1], None).unwrap(),
            DeliveryDisposition::PauseIdentity
        );
    }
    for status in [400, 409, 413, 422, 301] {
        assert_eq!(
            classify_batch_response(status, &ack_json(&[1]), &[1], None).unwrap(),
            DeliveryDisposition::Quarantine
        );
    }
    for status in [429, 500, 503] {
        assert_eq!(
            classify_batch_response(status, &ack_json(&[1]), &[1], Some("999999")).unwrap(),
            DeliveryDisposition::Retry {
                after: Duration::from_secs(300)
            }
        );
    }
    assert_eq!(
        classify_error_status(429, Some("injected-date")),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(30)
        }
    );
    assert_eq!(
        classify_error_status(503, Some("0")),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(1)
        }
    );
}

#[test]
fn outbound_event_wire_uses_canonical_fields_with_persisted_identity() {
    let device_id = Uuid::now_v7();
    let batch_id = Uuid::now_v7();
    let a = pending(1);
    let b = pending(5);
    let batch = EventBatch::from_pending(device_id, batch_id, &[a.clone(), b.clone()]).unwrap();
    assert_eq!(batch.sequences(), vec![1, 5]);
    let json = serde_json::to_value(&batch).unwrap();
    assert_eq!(json["schema_version"], 1);
    assert_eq!(json["device_id"], device_id.to_string());
    assert_eq!(json["batch_id"], batch_id.to_string());
    assert_eq!(json["events"][0]["event_id"], a.event_id.to_string());
    assert_eq!(json["events"][0]["device_sequence"], 1);
    assert_eq!(json["events"][0]["source"], "synthetic_test");
    assert_eq!(json["events"][0]["event_type"], "protocol_fixture");
    assert_eq!(json["events"][0]["occurred_at"], "2026-10-31T00:00:00Z");
    assert_eq!(json["events"][0]["payload"]["synthetic"], true);
    assert!(json["events"][0].get("content_digest").is_none());
}

#[test]
fn tampered_local_payload_digest_cannot_be_uploaded() {
    let mut event = pending(1);
    event.payload_json = r#"{"synthetic":false}"#.to_string();
    assert!(matches!(
        EventBatch::from_pending(Uuid::now_v7(), Uuid::now_v7(), &[event]),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn invalid_envelope_or_wrong_order_or_oversize_is_refused() {
    let event = pending(1);
    let device_id = Uuid::now_v7();
    assert!(matches!(
        EventBatch::from_pending(device_id, Uuid::now_v7(), &[]),
        Err(TransportError::InvalidEvent)
    ));
    assert!(matches!(
        EventBatch::from_pending(device_id, Uuid::now_v7(), &[event.clone(), event.clone()]),
        Err(TransportError::InvalidEvent)
    ));
    let mut massive = event;
    massive.payload_json = format!(r#"{{"x":"{}"}}"#, "x".repeat(530_000));
    massive.content_digest = format!("{:x}", Sha256::digest(massive.payload_json.as_bytes()));
    assert!(matches!(
        EventBatch::from_pending(device_id, Uuid::now_v7(), &[massive]),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn heartbeat_explicitly_discloses_missing_capabilities() {
    let id = Uuid::now_v7();
    let payload = serde_json::to_value(Heartbeat::synthetic(
        id, "2026-10-31T00:00:00Z".into(),
    ))
    .unwrap();
    assert_eq!(payload["device_id"], id.to_string());
    assert_eq!(payload["capabilities"]["hardware_inventory"], "not_collected");
    assert_eq!(payload["capabilities"]["app_activity"], "not_collected");
    assert_eq!(payload["queue_count"], 0);
    assert!(payload["last_successful_upload_at"].is_null());
}

#[test]
fn nonapproved_hostname_and_invalid_tls_identity_fail_before_network() {
    for origin in [
        "http://agent.lanveriq.internal",
        "https://console.lanveriq.internal",
        "https://agent.lanveriq.internal:444",
        "https://agent.lanveriq.internal.evil.test",
        "https://agent.lanveriq.internal/path",
        "https://127.0.0.1",
    ] {
        assert!(matches!(
            AgentTlsTransport::new(origin, b"fake CA", b"fake key"),
            Err(TransportError::InvalidOrigin)
        ));
    }
    assert!(matches!(
        AgentTlsTransport::new("https://agent.lanveriq.internal", b"", b""),
        Err(TransportError::InvalidTlsMaterial)
    ));
    assert!(matches!(
        AgentTlsTransport::new(
            "https://agent.lanveriq.internal",
            b"garbage CA material",
            b"garbage private key",
        ),
        Err(TransportError::InvalidTlsMaterial)
    ));
}
