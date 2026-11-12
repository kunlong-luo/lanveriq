-- Batch A3: local-only, device-bound heartbeat attempt/identity halt.
-- Does not schedule a service, read the OS, or authorize network requests.
-- A missing checkpoint after prior persistence is corruption, not a reset.
CREATE TABLE heartbeat_schedule_checkpoint (
    singleton INTEGER PRIMARY KEY CHECK (singleton=1),
    device_id TEXT NOT NULL,
    state_json TEXT NOT NULL CHECK (
        json_valid(state_json)
        AND length(CAST(state_json AS BLOB)) BETWEEN 2 AND 2048
    ),
    generation INTEGER NOT NULL CHECK (generation > 0 AND generation < 9223372036854775807)
);
ALTER TABLE agent_state
    ADD COLUMN heartbeat_schedule_seen INTEGER NOT NULL DEFAULT 0
    CHECK (heartbeat_schedule_seen IN (0, 1));
