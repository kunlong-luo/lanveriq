-- INV-01-E: optional pure-decision scheduler checkpoint. Storing this row
-- does NOT enable a collector, a Tokio task, or a network sender.
-- The caller must independently verify the same device-bound outbox.
CREATE TABLE inventory_schedule_checkpoint (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    device_id TEXT NOT NULL,
    state_json TEXT NOT NULL CHECK (
        json_valid(state_json)
        AND length(CAST(state_json AS BLOB)) BETWEEN 2 AND 2048
    )
);
