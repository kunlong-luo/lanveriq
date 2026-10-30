-- A role and device-group assignment alone must never expose raw IP/MAC.
-- Grants may be revoked without relying on an already-loaded web session.
-- No grants are seeded by migration, even for the owner role.
CREATE TABLE user_data_grants (
    user_id UUID NOT NULL REFERENCES users (id),
    device_group_id UUID NOT NULL REFERENCES device_groups (id),
    classification VARCHAR(64) NOT NULL CHECK (classification = 'internal_operational'),
    granted_by UUID NOT NULL REFERENCES users (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, device_group_id, classification)
);

CREATE INDEX user_data_grants_active_group_idx
    ON user_data_grants (device_group_id, user_id)
    WHERE revoked_at IS NULL;
