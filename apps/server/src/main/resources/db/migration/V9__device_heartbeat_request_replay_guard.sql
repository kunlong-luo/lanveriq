-- Synthetic-first durable per-device heartbeat replay prevention.
-- Retain only accepted IDs, not heartbeat bodies or activity details.
-- Deleting ledger rows by age would reopen replay windows until an
-- authenticated epoch/lifetime protocol is designed and verified.
CREATE TABLE device_heartbeat_request_ids (
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    request_id UUID NOT NULL
        CHECK (request_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    PRIMARY KEY (device_id, request_id)
);
