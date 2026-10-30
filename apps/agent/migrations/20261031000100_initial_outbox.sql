-- SQLx embedded migration. Source whitelist intentionally synthetic-only.
CREATE TABLE agent_state (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    next_sequence INTEGER NOT NULL CHECK(next_sequence > 0),
    next_inventory_revision INTEGER NOT NULL CHECK(next_inventory_revision > 0)
);
INSERT INTO agent_state VALUES (1,1,1);

CREATE TABLE outbox_events (
    event_id TEXT PRIMARY KEY,
    device_sequence INTEGER NOT NULL UNIQUE CHECK(device_sequence > 0),
    schema_version INTEGER NOT NULL CHECK(schema_version=1),
    source TEXT NOT NULL CHECK(source='synthetic_test'),
    event_type TEXT NOT NULL CHECK(event_type='protocol_fixture'),
    policy_version INTEGER NOT NULL CHECK(policy_version > 0),
    occurred_at_utc TEXT NOT NULL,
    payload_json TEXT NOT NULL CHECK(json_valid(payload_json) AND length(payload_json) <= 16384),
    content_digest TEXT NOT NULL CHECK(length(content_digest)=64),
    bytes INTEGER NOT NULL CHECK(bytes > 0 AND bytes <= 16384),
    created_at_utc TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX outbox_sequence_idx ON outbox_events(device_sequence);

CREATE TABLE collector_cursors (
    source TEXT PRIMARY KEY CHECK(source='synthetic_test'),
    cursor_blob BLOB NOT NULL CHECK(length(cursor_blob) BETWEEN 1 AND 4096),
    last_event_sequence INTEGER NOT NULL CHECK(last_event_sequence > 0)
);

CREATE TABLE collection_gaps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL CHECK(source='synthetic_test'),
    reason TEXT NOT NULL CHECK(reason='queue_full'),
    observed_at_utc TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_pending (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    snapshot_id TEXT NOT NULL,
    inventory_revision INTEGER NOT NULL CHECK(inventory_revision > 0),
    body_json TEXT NOT NULL CHECK(json_valid(body_json) AND length(body_json) <= 65536),
    content_digest TEXT NOT NULL CHECK(length(content_digest)=64)
);
