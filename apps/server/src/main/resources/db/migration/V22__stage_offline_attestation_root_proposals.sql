-- A11-D2-D4-B4-A: OFFLINE, UNTRUSTED root proposals only.
-- NEVER use this table as WebAuthn4J TrustAnchorRepository or admin MFA.
-- A proposal is neither approved nor revocable as an operational root,
-- because it can never become a trust anchor in this schema.
CREATE TABLE admin_passkey_attestation_root_proposals_staging (
  proposal_id UUID PRIMARY KEY DEFAULT uuidv7(),
  submitted_by UUID NOT NULL REFERENCES users(id),
  submitted_session_version BIGINT NOT NULL CHECK (submitted_session_version >= 0),
  certificate_der BYTEA NOT NULL
    CHECK (octet_length(certificate_der) BETWEEN 256 AND 8192),
  -- Core PostgreSQL 18 SHA-256, computed by the database, never by caller.
  certificate_sha256 BYTEA GENERATED ALWAYS AS (sha256(certificate_der)) STORED UNIQUE,
  source VARCHAR(32) NOT NULL DEFAULT 'offline_candidate'
    CHECK (source='offline_candidate'),
  trust_state VARCHAR(32) NOT NULL DEFAULT 'untrusted_proposal'
    CHECK (trust_state='untrusted_proposal'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX admin_passkey_root_proposals_actor_idx
  ON admin_passkey_attestation_root_proposals_staging(submitted_by,created_at DESC);

CREATE FUNCTION guard_admin_passkey_root_proposal_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM users u
    JOIN user_role_scopes s ON s.user_id=u.id
    JOIN roles r ON r.id=s.role_id
    WHERE u.id=NEW.submitted_by AND u.disabled_at IS NULL
      AND u.session_version=NEW.submitted_session_version
      AND r.code='owner_security_admin'
    FOR SHARE OF u,s,r
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='attestation root proposal requires active scoped owner admin';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_root_proposal_guard
BEFORE INSERT ON admin_passkey_attestation_root_proposals_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_root_proposal_insert();
CREATE TRIGGER admin_passkey_root_proposal_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_attestation_root_proposals_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
