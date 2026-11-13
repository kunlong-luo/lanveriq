-- Local-only certificate-generation pin. No networking, TLS or re-enrollment.
-- An existing heartbeat checkpoint must never be retroactively rebound.
-- A reviewed rotation/recovery protocol does NOT exist yet, so both UPDATE
-- and DELETE of a pinned identity are denied, even to the old Agent driver.
CREATE TABLE heartbeat_credential_pin (
    singleton INTEGER PRIMARY KEY CHECK (singleton=1),
    device_id TEXT NOT NULL,
    credential_epoch_id TEXT NOT NULL,
    cert_sha256_fingerprint TEXT NOT NULL CHECK (
        length(cert_sha256_fingerprint)=64
        AND cert_sha256_fingerprint NOT GLOB '*[^0-9a-f]*'
    )
);

CREATE TRIGGER heartbeat_credential_pin_immutable
BEFORE UPDATE ON heartbeat_credential_pin
BEGIN
    SELECT RAISE(ABORT, 'heartbeat credential pin is immutable');
END;

CREATE TRIGGER heartbeat_credential_pin_no_delete
BEFORE DELETE ON heartbeat_credential_pin
BEGIN
    SELECT RAISE(ABORT, 'heartbeat credential pin cannot be deleted');
END;

ALTER TABLE agent_state
    ADD COLUMN heartbeat_credential_pin_seen INTEGER NOT NULL DEFAULT 0
    CHECK (heartbeat_credential_pin_seen IN (0, 1));
