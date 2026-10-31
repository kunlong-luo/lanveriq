-- SRV-03 canonical, immutable event store. No default device rows or events.
-- All writes happen in one Spring-managed transaction AFTER an active
-- certificate-to-device binding is verified against the current database.
CREATE TABLE endpoint_events (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    device_id UUID NOT NULL REFERENCES devices (id),
    device_sequence BIGINT NOT NULL CHECK (device_sequence > 0),
    event_id UUID NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    source VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    policy_version BIGINT NOT NULL CHECK (policy_version > 0),
    payload_json JSONB NOT NULL CHECK (jsonb_typeof(payload_json) = 'object'),
    content_digest CHAR(64) NOT NULL
        CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT endpoint_events_device_sequence_unique
        UNIQUE (device_id, device_sequence),
    CONSTRAINT endpoint_events_device_event_id_unique
        UNIQUE (device_id, event_id),
    CONSTRAINT endpoint_events_source_allowlist
        CHECK (
            (source = 'agent_transport' AND event_type = 'collection_gap')
            OR (source = 'synthetic_test' AND event_type = 'protocol_fixture')
        )
);

CREATE INDEX endpoint_events_received_cursor_idx
    ON endpoint_events (device_id, received_at, event_id);

-- Defense in depth: even a privileged accidental UPDATE or DELETE through
-- application paths must not silently mutate accepted canonical evidence.
-- Retention requires a separately reviewed bounded maintenance path.
CREATE FUNCTION reject_endpoint_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'canonical endpoint events are immutable';
END
$$;

CREATE TRIGGER endpoint_events_immutable
BEFORE UPDATE OR DELETE ON endpoint_events
FOR EACH ROW EXECUTE FUNCTION reject_endpoint_event_mutation();
