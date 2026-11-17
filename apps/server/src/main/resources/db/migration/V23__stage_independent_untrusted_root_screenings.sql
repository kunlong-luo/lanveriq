-- A11-D2-D4-B4-B: independently screened OFFLINE CANDIDATES only.
-- No approval/activation state, no authorized root, no WebAuthn4J consumer.
CREATE TABLE admin_passkey_attestation_root_screenings_staging (
  proposal_id UUID NOT NULL REFERENCES admin_passkey_attestation_root_proposals_staging(proposal_id),
  step SMALLINT NOT NULL CHECK (step IN (1,2)),
  reviewer_id UUID NOT NULL REFERENCES users(id),
  reviewer_session_version BIGINT NOT NULL CHECK (reviewer_session_version >= 0),
  certificate_sha256 BYTEA NOT NULL CHECK (octet_length(certificate_sha256)=32),
  screening_state VARCHAR(32) NOT NULL DEFAULT 'screened_untrusted'
    CHECK (screening_state='screened_untrusted'),
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (proposal_id,step),
  UNIQUE (proposal_id,reviewer_id)
);

CREATE FUNCTION guard_admin_passkey_root_screening_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  submitter UUID;
  submitter_session BIGINT;
  recorded_digest BYTEA;
  first_reviewer UUID;
  first_session BIGINT;
BEGIN
  -- Lock V22 proposal as the single transaction serialization point.
  -- Its untrusted state can never be promoted by updates or reviewer votes.
  SELECT submitted_by,submitted_session_version,certificate_sha256
    INTO submitter,submitter_session,recorded_digest
    FROM admin_passkey_attestation_root_proposals_staging
    WHERE proposal_id=NEW.proposal_id AND trust_state='untrusted_proposal'
    FOR UPDATE;
  IF submitter IS NULL OR submitter=NEW.reviewer_id
     OR recorded_digest IS DISTINCT FROM NEW.certificate_sha256 THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='root candidate screening requires matching independent reviewer';
  END IF;

  -- Require currently active, same-session owner and reviewer sharing a
  -- specific current owner_security_admin device group scope.
  IF NOT EXISTS (
    SELECT 1 FROM users p
    JOIN user_role_scopes ps ON ps.user_id=p.id
    JOIN roles role ON role.id=ps.role_id AND role.code='owner_security_admin'
    JOIN user_role_scopes rs ON rs.role_id=ps.role_id
         AND rs.device_group_id=ps.device_group_id
         AND rs.user_id=NEW.reviewer_id
    JOIN users reviewer ON reviewer.id=rs.user_id
    WHERE p.id=submitter AND p.disabled_at IS NULL
      AND p.session_version=submitter_session
      AND reviewer.disabled_at IS NULL
      AND reviewer.session_version=NEW.reviewer_session_version
    FOR SHARE OF p,ps,role,rs,reviewer
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='root screening owner or reviewer no longer authorized';
  END IF;

  IF NEW.step=1 THEN
    IF EXISTS (SELECT 1 FROM admin_passkey_attestation_root_screenings_staging
               WHERE proposal_id=NEW.proposal_id) THEN
      RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='root screening already started';
    END IF;
  ELSE
    SELECT reviewer_id,reviewer_session_version
      INTO first_reviewer,first_session
      FROM admin_passkey_attestation_root_screenings_staging
      WHERE proposal_id=NEW.proposal_id AND step=1;
    IF first_reviewer IS NULL OR first_reviewer=NEW.reviewer_id THEN
      RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='root second screening must have distinct first reviewer';
    END IF;
    -- A revoked first reviewer/session cannot be inherited by a second step.
    IF NOT EXISTS (
      SELECT 1 FROM users first_actor
      JOIN user_role_scopes first_scope ON first_scope.user_id=first_actor.id
      JOIN roles r ON r.id=first_scope.role_id AND r.code='owner_security_admin'
      JOIN user_role_scopes proposer_scope ON proposer_scope.role_id=r.id
           AND proposer_scope.device_group_id=first_scope.device_group_id
           AND proposer_scope.user_id=submitter
      JOIN user_role_scopes second_scope ON second_scope.role_id=r.id
           AND second_scope.device_group_id=first_scope.device_group_id
           AND second_scope.user_id=NEW.reviewer_id
      WHERE first_actor.id=first_reviewer
        AND first_actor.disabled_at IS NULL
        AND first_actor.session_version=first_session
      FOR SHARE OF first_actor,first_scope,r,proposer_scope,second_scope
    ) THEN
      RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='root first reviewer has been revoked or rotated';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_passkey_root_screening_guard
BEFORE INSERT ON admin_passkey_attestation_root_screenings_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_root_screening_insert();
CREATE TRIGGER admin_passkey_root_screening_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_attestation_root_screenings_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
