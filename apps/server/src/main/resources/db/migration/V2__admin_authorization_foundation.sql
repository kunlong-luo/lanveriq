-- D0 V0.1: group-scoped administrators and issuance audit foundations.
-- No default user/password is inserted. First-run administrator bootstrap and
-- authenticated sessions remain AUTH-01/SRV-02 follow-on work.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    normalized_login VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    disabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_nonempty_login CHECK (length(trim(normalized_login)) > 0)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(64) NOT NULL UNIQUE
);

INSERT INTO roles (code) VALUES
    ('owner_security_admin'),
    ('it_operator'),
    ('security_auditor'),
    ('recording_reviewer');

CREATE TABLE user_role_scopes (
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    device_group_id UUID NOT NULL REFERENCES device_groups (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id, device_group_id)
);

CREATE INDEX user_role_scopes_group_idx ON user_role_scopes (device_group_id, user_id);

CREATE TABLE admin_access_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_id UUID NOT NULL REFERENCES users (id),
    resource_id UUID,
    device_group_id UUID REFERENCES device_groups (id),
    action VARCHAR(120) NOT NULL,
    outcome VARCHAR(32) NOT NULL CHECK (outcome IN ('allowed', 'denied')),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX admin_access_log_actor_time_idx
    ON admin_access_log (principal_id, recorded_at DESC);

-- Tokens issued without accountable authenticated administrator identity
-- are forbidden. Existing SRV-01 V1 schema never issued any tokens.
ALTER TABLE enrollment_tokens
    ADD COLUMN created_by UUID NOT NULL REFERENCES users (id);
