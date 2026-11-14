-- Batch A9: device-scoped offline reviewer public key continuity (NOT trust).
-- No signature, review, HTTP field or environment value can replace this key.
-- Initial key custody/approval remains an independent operational gate.
CREATE TABLE heartbeat_reviewer_public_key_pin (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    device_id TEXT NOT NULL,
    ed25519_public_key BLOB NOT NULL CHECK(length(ed25519_public_key)=32)
);
CREATE TRIGGER heartbeat_reviewer_public_key_immutable
BEFORE UPDATE ON heartbeat_reviewer_public_key_pin
BEGIN
    SELECT RAISE(ABORT, 'offline reviewer public key is immutable');
END;
CREATE TRIGGER heartbeat_reviewer_public_key_no_delete
BEFORE DELETE ON heartbeat_reviewer_public_key_pin
BEGIN
    SELECT RAISE(ABORT, 'offline reviewer public key cannot be deleted');
END;
ALTER TABLE agent_state
    ADD COLUMN heartbeat_reviewer_public_key_seen INTEGER NOT NULL DEFAULT 0
    CHECK(heartbeat_reviewer_public_key_seen IN (0,1));
