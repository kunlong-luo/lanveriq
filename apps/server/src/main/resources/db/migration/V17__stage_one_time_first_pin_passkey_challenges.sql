-- A11-D2-C: durable, server-owned, single-use first-pin reviewer challenges.
-- NEVER treat a database row or consumed_at as cryptographic WebAuthn proof.
-- There is NO production assertion verifier, HTTP registration/login/review
-- endpoint, CA signer, Agent activation or device release in this migration.
-- Only SHA-256 of a 32-byte CSPRNG challenge may be stored here, never raw
-- challenge bytes, browser-supplied user IDs, cookies or assertion payloads.
CREATE TABLE admin_passkey_step_up_challenges (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    actor_id UUID NOT NULL REFERENCES users(id),
    actor_session_version BIGINT NOT NULL
        CHECK (actor_session_version >= 0),
    request_id UUID NOT NULL REFERENCES first_pin_review_requests(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    action VARCHAR(40) NOT NULL
        CHECK (action = 'first_pin.review'),
    challenge_sha256 BYTEA NOT NULL UNIQUE
        CHECK (octet_length(challenge_sha256) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT admin_passkey_challenge_lifetime CHECK (
        expires_at > created_at
        AND expires_at <= created_at + INTERVAL '3 minutes'
    ),
    CONSTRAINT admin_passkey_challenge_consumed_window CHECK (
        consumed_at IS NULL OR (
            consumed_at >= created_at AND consumed_at < expires_at
        )
    )
);
CREATE INDEX admin_passkey_challenges_actor_request
    ON admin_passkey_step_up_challenges(actor_id,request_id,expires_at DESC);

-- The ONLY permitted mutation is the irreversible first consumption. It is
-- always conditional on nonce, owner, current session, request and expiry by
-- the JPA transaction service, following independent assertion verification.
-- The trigger is defense in depth, NOT proof any assertion was validated.
CREATE FUNCTION guard_admin_passkey_challenge_history()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='passkey challenge deletion forbidden';
    END IF;
    IF OLD.consumed_at IS NOT NULL
       OR NEW.consumed_at IS NULL
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.actor_session_version IS DISTINCT FROM OLD.actor_session_version
       OR NEW.request_id IS DISTINCT FROM OLD.request_id
       OR NEW.device_id IS DISTINCT FROM OLD.device_id
       OR NEW.action IS DISTINCT FROM OLD.action
       OR NEW.challenge_sha256 IS DISTINCT FROM OLD.challenge_sha256
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.consumed_at < OLD.created_at
       OR NEW.consumed_at >= OLD.expires_at THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='passkey challenge immutable or already consumed';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_challenge_history
BEFORE UPDATE OR DELETE ON admin_passkey_step_up_challenges FOR EACH ROW
EXECUTE FUNCTION guard_admin_passkey_challenge_history();
