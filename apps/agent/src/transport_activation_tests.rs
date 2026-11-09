//! Batch A2 source-only Agent activation envelope and strict receipt checks.
//! No runtime wiring, real CA, filesystem keys, actual network or PG18.
use super::*;

fn ack(device: Uuid, request: Uuid, state: &str, time: &str) -> Vec<u8> {
    serde_json::json!({
        "schema_version": 1,
        "device_id": device,
        "enrollment_state": state,
        "server_time": time,
        "request_id": request,
    })
    .to_string()
    .into_bytes()
}

#[test]
fn activation_wire_is_explicit_and_contains_no_secrets() {
    let device = Uuid::now_v7();
    let attempt = ActivationRequest::synthetic(device);
    assert!(attempt.is_valid());
    let wire = serde_json::to_value(&attempt).unwrap();
    assert_eq!(wire["schema_version"], 1);
    assert_eq!(wire["device_id"], device.to_string());
    assert_eq!(wire["agent_version"], "0.1.0");
    assert_eq!(wire["request_id"], attempt.request_id.to_string());
    assert_eq!(wire.as_object().unwrap().len(), 4);
    assert_ne!(
        attempt.request_id,
        ActivationRequest::synthetic(device).request_id
    );
}

#[test]
fn exact_device_attempt_and_active_state_accept_only_real_receipt() {
    let device = Uuid::now_v7();
    let attempt = Uuid::now_v7();
    let body = ack(device, attempt, "active", "2026-11-09T10:20:30.123Z");
    assert_eq!(
        classify_activation_response(200, &body, device, attempt, None).unwrap(),
        DeliveryDisposition::Accepted
    );
}

#[test]
fn nil_cross_device_stale_and_nonactive_receipts_are_denied() {
    let device = Uuid::now_v7();
    let attempt = Uuid::now_v7();
    let other = Uuid::now_v7();
    let good = ack(device, attempt, "active", "2026-11-09T10:20:30Z");
    for payload in [
        ack(other, attempt, "active", "2026-11-09T10:20:30Z"),
        ack(device, other, "active", "2026-11-09T10:20:30Z"),
        ack(device, attempt, "pending", "2026-11-09T10:20:30Z"),
        ack(device, attempt, "revoked", "2026-11-09T10:20:30Z"),
        ack(device, attempt, "ACTIVE", "2026-11-09T10:20:30Z"),
        ack(Uuid::nil(), attempt, "active", "2026-11-09T10:20:30Z"),
        ack(device, Uuid::nil(), "active", "2026-11-09T10:20:30Z"),
    ] {
        assert!(matches!(
            classify_activation_response(200, &payload, device, attempt, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_activation_response(200, &good, Uuid::nil(), attempt, None),
        Err(TransportError::InvalidEvent)
    ));
    assert!(matches!(
        classify_activation_response(200, &good, device, Uuid::nil(), None),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn malformed_or_oversized_200_never_activates() {
    let device = Uuid::now_v7();
    let attempt = Uuid::now_v7();
    let base: serde_json::Value =
        serde_json::from_slice(&ack(device, attempt, "active", "2026-11-09T10:20:30Z")).unwrap();
    for field in [
        "schema_version",
        "device_id",
        "enrollment_state",
        "server_time",
        "request_id",
    ] {
        let mut missing = base.clone();
        missing.as_object_mut().unwrap().remove(field);
        assert!(matches!(
            classify_activation_response(
                200,
                missing.to_string().as_bytes(),
                device,
                attempt,
                None
            ),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    for time in [
        "",
        "not-a-date",
        "2026-11-09T10:20:30+00:00",
        "2026-11-09T10:20:30.abcZ",
        "2026-11-09 10:20:30Z",
    ] {
        assert!(matches!(
            classify_activation_response(
                200,
                &ack(device, attempt, "active", time),
                device,
                attempt,
                None
            ),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    let mut extra = base.clone();
    extra["secret"] = serde_json::json!("should-not-exist");
    let mut version = base;
    version["schema_version"] = serde_json::json!(2);
    for bad in [
        extra.to_string().into_bytes(),
        version.to_string().into_bytes(),
        b"{".to_vec(),
    ] {
        assert!(matches!(
            classify_activation_response(200, &bad, device, attempt, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_activation_response(200, &vec![b'!'; 4097], device, attempt, None),
        Err(TransportError::OversizedResponse)
    ));
}

#[test]
fn authentication_denials_and_transient_errors_never_count_as_activation() {
    let device = Uuid::now_v7();
    let attempt = Uuid::now_v7();
    let good = ack(device, attempt, "active", "2026-11-09T10:20:30Z");
    for status in [401, 403] {
        assert_eq!(
            classify_activation_response(status, &good, device, attempt, None).unwrap(),
            DeliveryDisposition::PauseIdentity
        );
    }
    assert_eq!(
        classify_activation_response(409, &good, device, attempt, None).unwrap(),
        DeliveryDisposition::Quarantine
    );
    assert_eq!(
        classify_activation_response(503, &good, device, attempt, Some("17")).unwrap(),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(17)
        }
    );
}

#[test]
fn malformed_agent_activation_request_cannot_be_sent() {
    let device = Uuid::now_v7();
    let mut request = ActivationRequest::synthetic(device);
    let oversized = "a".repeat(65);
    for name in [
        "",
        "-leading",
        "two words",
        "unsafe/path",
        oversized.as_str(),
        "中文",
    ] {
        request.agent_version = name.to_owned();
        assert!(!request.is_valid());
    }
    request.agent_version = "0.1.0".into();
    assert!(request.is_valid());
    request.schema_version = 2;
    assert!(!request.is_valid());
    request.schema_version = 1;
    request.request_id = Uuid::nil();
    assert!(!request.is_valid());
    request.request_id = Uuid::now_v7();
    request.device_id = Uuid::nil();
    assert!(!request.is_valid());
}
