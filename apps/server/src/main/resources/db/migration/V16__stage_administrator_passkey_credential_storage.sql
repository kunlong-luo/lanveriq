-- A11-D2-A: PostgreSQL persistence foundation for administrator passkeys.
-- This stores NO valid production credentials yet: no WebAuthn registration,
-- assertion verification, HttpSecurity.webAuthn() chain, issuer or Agent route.
-- Data must only be written by a future independently verified WebAuthn flow.
-- user_handle is a server-generated opaque 32-byte WebAuthn user.id, NOT the
-- PostgreSQL UUID bytes, login name, client-supplied ID or session cookie.
CREATE TABLE admin_passkey_user_handles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    user_handle BYTEA NOT NULL UNIQUE
        CHECK (octet_length(user_handle) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Never use an in-memory repository or password login to populate this.
-- The future JPA implementation must bind verified credential ownership to
-- users.id, RP ID, COSE public key, current session and a consumed challenge.
-- Sign counter and backup flags need actual WebAuthn4J assertion verification.
CREATE TABLE admin_passkey_credentials (
    credential_id BYTEA PRIMARY KEY
        CHECK (octet_length(credential_id) BETWEEN 16 AND 1024),
    user_id UUID NOT NULL REFERENCES admin_passkey_user_handles(user_id)
        ON DELETE RESTRICT,
    rp_id VARCHAR(253) NOT NULL
        CHECK (rp_id ~ '^[a-z0-9-]+(\.[a-z0-9-]+)+$'),
    credential_public_key_cose BYTEA NOT NULL
        CHECK (octet_length(credential_public_key_cose) BETWEEN 32 AND 4096),
    signature_count BIGINT NOT NULL DEFAULT 0
        CHECK (signature_count >= 0),
    backup_eligible BOOLEAN NOT NULL DEFAULT false,
    backup_state BOOLEAN NOT NULL DEFAULT false,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_verified_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES users(id),
    CONSTRAINT admin_passkey_backup_consistency
        CHECK (NOT backup_state OR backup_eligible),
    CONSTRAINT admin_passkey_revocation_pair
        CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);
CREATE INDEX admin_passkey_credentials_owner
    ON admin_passkey_credentials(user_id);

-- A record cannot be moved to another administrator or RP, nor may its
-- verified public key or credential ID be silently replaced. The only
-- allowed mutable state is monotonic counter, backup state, verified time
-- and one-way revocation. This does not make the database tamper-proof to
-- its superuser or imply that application SQL itself verifies WebAuthn.
CREATE FUNCTION guard_admin_passkey_credential_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.credential_id IS DISTINCT FROM OLD.credential_id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.rp_id IS DISTINCT FROM OLD.rp_id
       OR NEW.credential_public_key_cose IS DISTINCT FROM OLD.credential_public_key_cose
       OR NEW.registered_at IS DISTINCT FROM OLD.registered_at
       OR NEW.backup_eligible IS DISTINCT FROM OLD.backup_eligible
       OR NEW.signature_count < OLD.signature_count
       OR (OLD.last_verified_at IS NOT NULL AND
           (NEW.last_verified_at IS NULL
            OR NEW.last_verified_at < OLD.last_verified_at))
       OR (OLD.revoked_at IS NOT NULL AND
           (NEW.revoked_at IS DISTINCT FROM OLD.revoked_at
            OR NEW.revoked_by IS DISTINCT FROM OLD.revoked_by)) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='administrator passkey identity, counter or revocation violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_credential_update_guard
BEFORE UPDATE ON admin_passkey_credentials FOR EACH ROW
EXECUTE FUNCTION guard_admin_passkey_credential_update();

-- Handle ownership is immutable: users must never silently change their
-- WebAuthn user.id and thereby retarget an existing credential.
CREATE FUNCTION reject_admin_passkey_handle_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='administrator passkey user handle immutable';
END;
$$;
CREATE TRIGGER admin_passkey_handle_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_user_handles FOR EACH ROW
EXECUTE FUNCTION reject_admin_passkey_handle_change();

-- Physical deletion would erase revocation history. Future retention and
-- account-erasure policy require a separately reviewed audited migration.
CREATE TRIGGER admin_passkey_credential_no_delete
BEFORE DELETE ON admin_passkey_credentials FOR EACH ROW
EXECUTE FUNCTION reject_admin_passkey_handle_change();
