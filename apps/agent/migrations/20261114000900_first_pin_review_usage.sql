-- A10: offline-only atomic first certificate pin + unique review-id usage.
-- No signing authority, administrator identity, trusted clock or Agent service.
-- Pin INSERT is blocked unless the matching consumed review row exists.
CREATE TABLE heartbeat_first_pin_review_usage (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    review_id TEXT NOT NULL UNIQUE,
    device_id TEXT NOT NULL,
    credential_epoch_id TEXT NOT NULL,
    cert_sha256_fingerprint TEXT NOT NULL CHECK (
        length(cert_sha256_fingerprint)=64
        AND cert_sha256_fingerprint NOT GLOB '*[^0-9a-f]*'
    ),
    reviewer_key_sha256 TEXT NOT NULL CHECK (
        length(reviewer_key_sha256)=64
        AND reviewer_key_sha256 NOT GLOB '*[^0-9a-f]*'
    ),
    signature_sha256 TEXT NOT NULL CHECK (
        length(signature_sha256)=64
        AND signature_sha256 NOT GLOB '*[^0-9a-f]*'
    ),
    applied_at_unix INTEGER NOT NULL CHECK(applied_at_unix > 0)
);
CREATE TRIGGER heartbeat_first_pin_review_usage_immutable
BEFORE UPDATE ON heartbeat_first_pin_review_usage
BEGIN
    SELECT RAISE(ABORT, 'consumed offline review cannot be updated');
END;
CREATE TRIGGER heartbeat_first_pin_review_usage_no_delete
BEFORE DELETE ON heartbeat_first_pin_review_usage
BEGIN
    SELECT RAISE(ABORT, 'consumed offline review cannot be deleted');
END;
CREATE TRIGGER heartbeat_credential_pin_requires_review
BEFORE INSERT ON heartbeat_credential_pin
WHEN NOT EXISTS (
    SELECT 1 FROM heartbeat_first_pin_review_usage
    WHERE singleton=1 AND device_id=NEW.device_id
      AND credential_epoch_id=NEW.credential_epoch_id
      AND cert_sha256_fingerprint=NEW.cert_sha256_fingerprint
)
BEGIN
    SELECT RAISE(ABORT, 'verified offline first-pin review required');
END;
ALTER TABLE agent_state ADD COLUMN heartbeat_first_pin_review_seen
    INTEGER NOT NULL DEFAULT 0 CHECK(heartbeat_first_pin_review_seen IN (0,1));
