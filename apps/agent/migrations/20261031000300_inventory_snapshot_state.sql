-- INV-01-C: upgrade existing synthetic-only pending storage to the
-- V0.1 maximum 128 KiB, preserving any already-staged legacy fixture.
-- Snapshot content and the latest semantic digest remain separately durable.
CREATE TABLE inventory_pending_v3 (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    snapshot_id TEXT NOT NULL,
    inventory_revision INTEGER NOT NULL CHECK(inventory_revision > 0),
    body_json TEXT NOT NULL CHECK (
        json_valid(body_json)
        AND length(CAST(body_json AS BLOB)) BETWEEN 1 AND 131072
    ),
    content_digest TEXT NOT NULL CHECK (
        length(content_digest) = 64
        AND content_digest NOT GLOB '*[^0-9a-f]*'
    )
);

INSERT INTO inventory_pending_v3 (
    singleton, snapshot_id, inventory_revision, body_json, content_digest
)
SELECT singleton, snapshot_id, inventory_revision, body_json, content_digest
FROM inventory_pending;

DROP TABLE inventory_pending;
ALTER TABLE inventory_pending_v3 RENAME TO inventory_pending;

-- Keep the last selected content digest after an ACK so that unchanged
-- full inventory is not re-queued at each startup/24h reconciliation.
-- This singleton is explicitly bound to the assigned device UUID, not MAC/IP.
CREATE TABLE inventory_latest_state (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    device_id TEXT NOT NULL,
    snapshot_id TEXT NOT NULL,
    inventory_revision INTEGER NOT NULL CHECK(inventory_revision > 0),
    semantic_digest TEXT NOT NULL CHECK (
        length(semantic_digest) = 64
        AND semantic_digest NOT GLOB '*[^0-9a-f]*'
    )
);
