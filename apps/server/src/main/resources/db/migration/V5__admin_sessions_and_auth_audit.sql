-- V0.1: immediate account-wide logout on disable, credential reset or
-- security revocation. Any such mutation MUST increment session_version.
ALTER TABLE users
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0
        CHECK (session_version >= 0);

-- Authentication audit records do not contain supplied usernames, passwords,
-- cookie values, source IPs, CSRF tokens or enrollment secrets.
-- Unknown/failed login attempts are intentionally unlinked to principal IDs.
CREATE TABLE admin_auth_log (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    principal_id UUID REFERENCES users (id),
    action VARCHAR(32) NOT NULL CHECK (action IN ('login', 'logout')),
    outcome VARCHAR(24) NOT NULL
        CHECK (outcome IN ('allowed', 'denied', 'rate_limited')),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auth_log_allow_known CHECK (outcome <> 'allowed' OR principal_id IS NOT NULL)
);

CREATE INDEX admin_auth_log_principal_time_idx
    ON admin_auth_log (principal_id, recorded_at DESC);
