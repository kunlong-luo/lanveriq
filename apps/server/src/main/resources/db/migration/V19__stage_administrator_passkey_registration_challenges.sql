-- A11-D2-D3. One-time SERVER-OWNED WebAuthn registration challenges.
-- This is a staging security prerequisite, NOT trusted credential enrollment.
-- No existing synthetic V16/V18 record becomes verified by this migration.
CREATE TABLE admin_passkey_registration_challenges (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    actor_id UUID NOT NULL REFERENCES admin_passkey_user_handles(user_id),
    actor_session_version BIGINT NOT NULL CHECK (actor_session_version >= 0),
    rp_id VARCHAR(253) NOT NULL
        CHECK (rp_id = 'console.lanveriq.internal'),
    origin VARCHAR(300) NOT NULL
        CHECK (origin = 'https://console.lanveriq.internal'),
    challenge_sha256 BYTEA NOT NULL UNIQUE
        CHECK (octet_length(challenge_sha256) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT admin_passkey_registration_challenge_lifetime CHECK (
        expires_at > created_at AND
        expires_at <= created_at + INTERVAL '3 minutes'
    ),
    CONSTRAINT admin_passkey_registration_challenge_consumption CHECK (
        consumed_at IS NULL OR
        (consumed_at >= created_at AND consumed_at < expires_at)
    )
);
CREATE INDEX admin_passkey_registration_challenge_actor_idx
    ON admin_passkey_registration_challenges(actor_id,expires_at DESC);

-- Irreversible one-use state. A consumed challenge never proves that the
-- WebAuthn4J attestation is trustworthy, nor does it authorize an issuer.
CREATE FUNCTION guard_admin_passkey_registration_challenge()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='passkey registration challenge deletion forbidden';
    END IF;
    IF OLD.consumed_at IS NOT NULL
       OR NEW.consumed_at IS NULL
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.actor_session_version IS DISTINCT FROM OLD.actor_session_version
       OR NEW.rp_id IS DISTINCT FROM OLD.rp_id
       OR NEW.origin IS DISTINCT FROM OLD.origin
       OR NEW.challenge_sha256 IS DISTINCT FROM OLD.challenge_sha256
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.consumed_at < OLD.created_at
       OR NEW.consumed_at >= OLD.expires_at THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='passkey registration challenge immutable or already used';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_registration_challenge_guard
BEFORE UPDATE OR DELETE ON admin_passkey_registration_challenges
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_registration_challenge();

-- Forward-only hardening of the existing V16 trigger. V16 permitted a
-- monotonic signature-counter / backup / timestamp update AFTER revocation.
-- After V19, ALL post-revocation changes are denied. Revocation itself is
-- still permitted exactly once, without undoing a previously seen counter.
CREATE OR REPLACE FUNCTION guard_admin_passkey_credential_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.revoked_at IS NOT NULL
       OR NEW.credential_id IS DISTINCT FROM OLD.credential_id
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
