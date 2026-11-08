//! Batch A: pure heartbeat ACK handling, no network, scheduling or OS reads.
use super::*;

fn receipt(request_id: Uuid, time: &str) -> Vec<u8> {
    serde_json::json!({
        "server_time": time,
        "request_id": request_id
    })
    .to_string()
    .into_bytes()
}

#[test]
fn valid_server_heartbeat_receipt_accepts_only_the_exact_two_field_envelope() {
    let id = Uuid::now_v7();
    assert_eq!(
        classify_heartbeat_response(200, &receipt(id, "2026-11-08T04:25:45Z"), None).unwrap(),
        DeliveryDisposition::Accepted
    );
    assert_eq!(
        classify_heartbeat_response(
            200, &receipt(id, "2026-11-08T04:25:45.012345Z"), None
        ).unwrap(),
        DeliveryDisposition::Accepted
    );
}

#[test]
fn malformed_response_must_never_count_as_delivery() {
    let valid: serde_json::Value =
        serde_json::from_slice(&receipt(Uuid::now_v7(), "2026-11-08T04:25:45Z")).unwrap();
    for field in ["server_time", "request_id"] {
        let mut missing = valid.clone();
        missing.as_object_mut().unwrap().remove(field);
        assert!(matches!(
            classify_heartbeat_response(200, missing.to_string().as_bytes(), None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    for timestamp in [
        "", "not-a-date", "2026-11-08T04:25:45+00:00",
        "2026-11-08T04:25:45.abcZ", "2026-11-08 04:25:45Z",
        "2026-11-08T04:25:45.Z",
    ] {
        assert!(matches!(
            classify_heartbeat_response(
                200, &receipt(Uuid::now_v7(), timestamp), None
            ),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_heartbeat_response(
            200, &receipt(Uuid::nil(), "2026-11-08T04:25:45Z"), None
        ),
        Err(TransportError::InvalidAcknowledgement)
    ));
    let mut extra = valid;
    extra["unexpected_business_data"] = serde_json::json!("not_allowed");
    assert!(matches!(
        classify_heartbeat_response(200, extra.to_string().as_bytes(), None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_response(200, b"{broken", None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_response(200, &vec![b'!'; 4097], None),
        Err(TransportError::OversizedResponse)
    ));
}

#[test]
fn all_auth_failures_and_retries_remain_non_acknowledged() {
    let body = receipt(Uuid::now_v7(), "2026-11-08T04:25:45Z");
    assert_eq!(
        classify_heartbeat_response(401, &body, None).unwrap(),
        DeliveryDisposition::PauseIdentity
    );
    assert_eq!(
        classify_heartbeat_response(403, &body, None).unwrap(),
        DeliveryDisposition::PauseIdentity
    );
    assert_eq!(
        classify_heartbeat_response(409, &body, None).unwrap(),
        DeliveryDisposition::Quarantine
    );
    assert_eq!(
        classify_heartbeat_response(503, &body, Some("11")).unwrap(),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(11)
        }
    );
}
