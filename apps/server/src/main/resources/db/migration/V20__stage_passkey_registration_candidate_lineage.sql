-- A11-D2-D4-A: append-only UNTRUSTED candidate lineage.
-- No V16/V18 synthetic credential or consumed V19 nonce is login proof.
ALTER TABLE admin_passkey_registration_challenges
  ADD CONSTRAINT admin_passkey_reg_nonce_owner_unique
  UNIQUE (id,actor_id,actor_session_version,rp_id);

CREATE TABLE admin_passkey_registration_candidates_staging (
  challenge_id UUID PRIMARY KEY,
  actor_id UUID NOT NULL,
  actor_session_version BIGINT NOT NULL CHECK (actor_session_version >= 0),
  rp_id VARCHAR(253) NOT NULL CHECK (rp_id='console.lanveriq.internal'),
  credential_id BYTEA NOT NULL UNIQUE,
  registration_response_sha256 BYTEA NOT NULL
    CHECK (octet_length(registration_response_sha256)=32),
  evidence_grade VARCHAR(32) NOT NULL DEFAULT 'untrusted_candidate'
    CHECK (evidence_grade='untrusted_candidate'),
  staged_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT admin_passkey_candidate_owner_nonce
    FOREIGN KEY (challenge_id,actor_id,actor_session_version,rp_id)
    REFERENCES admin_passkey_registration_challenges
      (id,actor_id,actor_session_version,rp_id),
  CONSTRAINT admin_passkey_candidate_credential_owner
    FOREIGN KEY (credential_id,actor_id,rp_id)
    REFERENCES admin_passkey_credentials(credential_id,user_id,rp_id),
  CONSTRAINT admin_passkey_candidate_staging
    FOREIGN KEY (credential_id)
    REFERENCES admin_passkey_credential_records_staging(credential_id)
);
CREATE INDEX admin_passkey_candidate_owner_idx
  ON admin_passkey_registration_candidates_staging(actor_id,staged_at DESC);

-- Enforce consumed, in-window challenge and current owner account at INSERT.
-- The DB superuser is out of scope; this table never grants authentication.
CREATE FUNCTION guard_admin_passkey_candidate_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM admin_passkey_registration_challenges c
      JOIN users u ON u.id=c.actor_id
      JOIN admin_passkey_credentials cred ON cred.credential_id=NEW.credential_id
         AND cred.user_id=c.actor_id AND cred.rp_id=c.rp_id
      JOIN admin_passkey_credential_records_staging s
        ON s.credential_id=cred.credential_id
         AND s.user_id=cred.user_id AND s.rp_id=cred.rp_id
    WHERE c.id=NEW.challenge_id AND c.actor_id=NEW.actor_id
      AND c.actor_session_version=NEW.actor_session_version
      AND c.rp_id=NEW.rp_id AND c.consumed_at IS NOT NULL
      AND c.consumed_at >= c.created_at AND c.consumed_at < c.expires_at
      AND c.consumed_at <= NEW.staged_at AND NEW.staged_at < c.expires_at
      AND u.disabled_at IS NULL AND u.session_version=c.actor_session_version
      AND cred.revoked_at IS NULL
      AND EXISTS (SELECT 1 FROM user_role_scopes sc
        JOIN roles r ON r.id=sc.role_id
        WHERE sc.user_id=u.id AND r.code='owner_security_admin')
    FOR SHARE OF c,u,cred,s
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='registration candidate requires live consumed nonce and owner';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_candidate_insert_guard
BEFORE INSERT ON admin_passkey_registration_candidates_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_candidate_insert();

CREATE TRIGGER admin_passkey_candidate_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_registration_candidates_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
