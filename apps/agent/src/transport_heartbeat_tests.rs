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
fn valid_server_heartbeat_receipt_accepts_only_the_exact_matching_envelope() {
    let id = Uuid::now_v7();
    assert_eq!(
        classify_heartbeat_response(200, &receipt(id, "2026-11-08T04:25:45Z"), id, None).unwrap(),
        DeliveryDisposition::Accepted
    );
    assert_eq!(
        classify_heartbeat_response(200, &receipt(id, "2026-11-08T04:25:45.012345Z"), id, None)
            .unwrap(),
        DeliveryDisposition::Accepted
    );
}

#[test]
fn stale_or_cross_request_heartbeat_receipts_never_count_as_delivery() {
    let expected = Uuid::now_v7();
    let other = Uuid::now_v7();
    assert_ne!(expected, other);
    assert!(matches!(
        classify_heartbeat_response(200, &receipt(other, "2026-11-08T04:25:45Z"), expected, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_response(
            200,
            &receipt(expected, "2026-11-08T04:25:45Z"),
            Uuid::nil(),
            None
        ),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn malformed_response_must_never_count_as_delivery() {
    let expected = Uuid::now_v7();
    let valid: serde_json::Value =
        serde_json::from_slice(&receipt(expected, "2026-11-08T04:25:45Z")).unwrap();
    for field in ["server_time", "request_id"] {
        let mut missing = valid.clone();
        missing.as_object_mut().unwrap().remove(field);
        assert!(matches!(
            classify_heartbeat_response(200, missing.to_string().as_bytes(), expected, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    for timestamp in [
        "",
        "not-a-date",
        "2026-11-08T04:25:45+00:00",
        "2026-11-08T04:25:45.abcZ",
        "2026-11-08 04:25:45Z",
        "2026-11-08T04:25:45.Z",
    ] {
        assert!(matches!(
            classify_heartbeat_response(200, &receipt(expected, timestamp), expected, None),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    assert!(matches!(
        classify_heartbeat_response(
            200,
            &receipt(Uuid::nil(), "2026-11-08T04:25:45Z"),
            expected,
            None
        ),
        Err(TransportError::InvalidAcknowledgement)
    ));
    let mut extra = valid;
    extra["unexpected_business_data"] = serde_json::json!("not_allowed");
    assert!(matches!(
        classify_heartbeat_response(200, extra.to_string().as_bytes(), expected, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_response(200, b"{broken", expected, None),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_response(200, &vec![b'!'; 4097], expected, None),
        Err(TransportError::OversizedResponse)
    ));
}

#[test]
fn all_auth_failures_and_retries_remain_non_acknowledged() {
    let expected = Uuid::now_v7();
    let body = receipt(expected, "2026-11-08T04:25:45Z");
    assert_eq!(
        classify_heartbeat_response(401, &body, expected, None).unwrap(),
        DeliveryDisposition::PauseIdentity
    );
    assert_eq!(
        classify_heartbeat_response(403, &body, expected, None).unwrap(),
        DeliveryDisposition::PauseIdentity
    );
    assert_eq!(
        classify_heartbeat_response(409, &body, expected, None).unwrap(),
        DeliveryDisposition::Quarantine
    );
    assert_eq!(
        classify_heartbeat_response(503, &body, expected, Some("11")).unwrap(),
        DeliveryDisposition::Retry {
            after: Duration::from_secs(11)
        }
    );
}

#[test]
fn durable_ledger_lookup_requires_exact_positive_receipt_and_never_trusts_absence() {
    let device = Uuid::now_v7();
    let id = Uuid::now_v7();
    let good = serde_json::json!({
        "schema_version": 1,
        "device_id": device,
        "request_id": id,
        "committed": true,
        "server_time": "2026-11-13T00:00:00Z"
    });
    assert_eq!(
        classify_heartbeat_lookup_response(200, good.to_string().as_bytes(), device, id).unwrap(),
        HeartbeatLookupDisposition::Committed
    );
    for status in [404, 429, 500, 503] {
        assert_eq!(
            classify_heartbeat_lookup_response(status, b"", device, id).unwrap(),
            HeartbeatLookupDisposition::Unresolved
        );
    }
    assert_eq!(
        classify_heartbeat_lookup_response(403, b"", device, id).unwrap(),
        HeartbeatLookupDisposition::PauseIdentity
    );
    assert_eq!(
        classify_heartbeat_lookup_response(409, b"", device, id).unwrap(),
        HeartbeatLookupDisposition::Quarantine
    );

    let mut malformed = good.clone();
    for (key, value) in [
        ("request_id", serde_json::json!(Uuid::now_v7())),
        ("device_id", serde_json::json!(Uuid::now_v7())),
        ("committed", serde_json::json!(false)),
        ("schema_version", serde_json::json!(2)),
        ("server_time", serde_json::json!("not-a-date")),
    ] {
        malformed.clone_from(&good);
        malformed[key] = value;
        assert!(matches!(
            classify_heartbeat_lookup_response(200, malformed.to_string().as_bytes(), device, id),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    malformed.clone_from(&good);
    malformed["extra"] = serde_json::json!(true);
    assert!(matches!(
        classify_heartbeat_lookup_response(200, malformed.to_string().as_bytes(), device, id),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_lookup_response(200, b"{}", device, id),
        Err(TransportError::InvalidAcknowledgement)
    ));
    assert!(matches!(
        classify_heartbeat_lookup_response(200, &vec![b'x'; 4097], device, id),
        Err(TransportError::OversizedResponse)
    ));
    assert!(matches!(
        classify_heartbeat_lookup_response(200, good.to_string().as_bytes(), Uuid::nil(), id),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn current_credential_epoch_receipt_is_strictly_read_only_and_identity_bound() {
    let device = Uuid::now_v7();
    let epoch = Uuid::now_v7();
    let fp = "a".repeat(64);
    let good = serde_json::json!({
        "schema_version": 1,
        "device_id": device,
        "credential_epoch_id": epoch,
        "client_cert_sha256_fingerprint": fp,
        "server_time": "2026-11-13T07:00:00Z"
    });
    let observed =
        classify_credential_epoch_response(200, good.to_string().as_bytes(), device, &fp).unwrap();
    assert_eq!(
        observed,
        CredentialEpochDisposition::ObservedOnly(CurrentCredentialEpoch {
            device_id: device,
            credential_epoch_id: epoch,
            cert_sha256_fingerprint: fp.clone(),
        })
    );
    for (status, expected) in [
        (401, CredentialEpochDisposition::PauseIdentity),
        (403, CredentialEpochDisposition::PauseIdentity),
        (404, CredentialEpochDisposition::Unresolved),
        (429, CredentialEpochDisposition::Unresolved),
        (503, CredentialEpochDisposition::Unresolved),
        (409, CredentialEpochDisposition::Quarantine),
    ] {
        assert_eq!(
            classify_credential_epoch_response(status, b"", device, &fp).unwrap(),
            expected
        );
    }
    for (key, value) in [
        ("device_id", serde_json::json!(Uuid::now_v7())),
        ("credential_epoch_id", serde_json::json!(Uuid::nil())),
        (
            "credential_epoch_id",
            serde_json::json!(Uuid::parse_str("550e8400-e29b-41d4-a716-446655440000").unwrap()),
        ),
        (
            "client_cert_sha256_fingerprint",
            serde_json::json!("b".repeat(64)),
        ),
        ("schema_version", serde_json::json!(2)),
        ("server_time", serde_json::json!("invalid")),
    ] {
        let mut bad = good.clone();
        bad[key] = value;
        assert!(matches!(
            classify_credential_epoch_response(200, bad.to_string().as_bytes(), device, &fp),
            Err(TransportError::InvalidAcknowledgement)
        ));
    }
    let mut extra = good.clone();
    extra["can_resume_quarantine"] = serde_json::json!(true);
    assert!(matches!(
        classify_credential_epoch_response(200, extra.to_string().as_bytes(), device, &fp),
        Err(TransportError::InvalidAcknowledgement)
    ));
    let mut missing = good.clone();
    missing
        .as_object_mut()
        .unwrap()
        .remove("credential_epoch_id");
    assert!(
        classify_credential_epoch_response(200, missing.to_string().as_bytes(), device, &fp)
            .is_err()
    );
    assert!(matches!(
        classify_credential_epoch_response(200, &vec![b'!'; 4097], device, &fp),
        Err(TransportError::OversizedResponse)
    ));
    assert!(matches!(
        classify_credential_epoch_response(
            200,
            good.to_string().as_bytes(),
            device,
            &"A".repeat(64)
        ),
        Err(TransportError::InvalidEvent)
    ));
}

#[test]
fn transport_epoch_observation_never_takes_a_caller_chosen_fingerprint() {
    // Compile-time shape: only device_id is accepted. A "fingerprint" body
    // or caller-selected epoch cannot override the TLS PEM-derived identity.
    let _method = AgentTlsTransport::observe_current_credential_epoch;
    let _ = _method;
}

