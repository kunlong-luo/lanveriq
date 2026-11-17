-- A11-D2-D4-B3: append-only, explicitly NOT TRUSTED attestation assessment.
-- This is not a trusted verifier result, credential source, reviewer proof or
-- certificate-authority authorization. It cannot be promoted by SQL updates.
-- The existing V20 untrusted candidate lineage remains unchanged.
ALTER TABLE admin_passkey_registration_candidates_staging
  ADD CONSTRAINT admin_passkey_candidate_assessment_lineage_unique
  UNIQUE (challenge_id,actor_id,actor_session_version,rp_id,
          credential_id,registration_response_sha256);

CREATE TABLE admin_passkey_attestation_rejections_staging (
  challenge_id UUID PRIMARY KEY,
  actor_id UUID NOT NULL,
  actor_session_version BIGINT NOT NULL CHECK (actor_session_version >= 0),
  rp_id VARCHAR(253) NOT NULL
    CHECK (rp_id='console.lanveriq.internal'),
  credential_id BYTEA NOT NULL CHECK (octet_length(credential_id) BETWEEN 16 AND 1024),
  registration_response_sha256 BYTEA NOT NULL
    CHECK (octet_length(registration_response_sha256)=32),
  evidence_grade VARCHAR(32) NOT NULL DEFAULT 'not_trusted'
    CHECK (evidence_grade='not_trusted'),
  rejection_code VARCHAR(64) NOT NULL DEFAULT 'trust_roots_unconfigured'
    CHECK (rejection_code='trust_roots_unconfigured'),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT admin_passkey_attestation_rejection_candidate_fk
    FOREIGN KEY (challenge_id,actor_id,actor_session_version,rp_id,
                 credential_id,registration_response_sha256)
    REFERENCES admin_passkey_registration_candidates_staging
      (challenge_id,actor_id,actor_session_version,rp_id,
       credential_id,registration_response_sha256)
);

-- Reject direct SQL insertion for absent, stale, revoked or cross-user rows.
-- Privileged PostgreSQL owners are outside this staging table's trust model.
CREATE FUNCTION guard_admin_passkey_attestation_rejection_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM admin_passkey_registration_candidates_staging candidate
    JOIN admin_passkey_registration_challenges nonce
      ON nonce.id=candidate.challenge_id
    JOIN users actor ON actor.id=candidate.actor_id
    JOIN admin_passkey_credentials credential
      ON credential.credential_id=candidate.credential_id
         AND credential.user_id=candidate.actor_id
         AND credential.rp_id=candidate.rp_id
    WHERE candidate.challenge_id=NEW.challenge_id
      AND candidate.actor_id=NEW.actor_id
      AND candidate.actor_session_version=NEW.actor_session_version
      AND candidate.rp_id=NEW.rp_id
      AND candidate.credential_id=NEW.credential_id
      AND candidate.registration_response_sha256=NEW.registration_response_sha256
      AND candidate.evidence_grade='untrusted_candidate'
      AND candidate.staged_at <= NEW.recorded_at
      AND nonce.consumed_at IS NOT NULL
      AND nonce.consumed_at >= nonce.created_at
      AND nonce.consumed_at < nonce.expires_at
      AND nonce.actor_id=actor.id
      AND nonce.actor_session_version=candidate.actor_session_version
      AND actor.disabled_at IS NULL
      AND actor.session_version=candidate.actor_session_version
      AND credential.revoked_at IS NULL
      AND EXISTS (SELECT 1 FROM user_role_scopes scope
        JOIN roles r ON r.id=scope.role_id
        WHERE scope.user_id=actor.id AND r.code='owner_security_admin')
    FOR SHARE OF candidate,nonce,actor,credential
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='attestation rejection requires live bound untrusted candidate';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_attestation_rejection_guard
BEFORE INSERT ON admin_passkey_attestation_rejections_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_attestation_rejection_insert();

CREATE TRIGGER admin_passkey_attestation_rejection_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_attestation_rejections_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
