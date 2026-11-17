-- A11-D2-D4-C1: irreversible disqualification of an OFFLINE UNTRUSTED
-- candidate. This is NOT CRL/OCSP or revocation of an approved trust anchor.
-- No "active" or "trusted" state exists anywhere in V22-V24.
CREATE TABLE admin_passkey_attestation_root_disqualifications_staging (
  proposal_id UUID PRIMARY KEY
    REFERENCES admin_passkey_attestation_root_proposals_staging(proposal_id),
  disqualified_by UUID NOT NULL REFERENCES users(id),
  disqualifier_session_version BIGINT NOT NULL
    CHECK (disqualifier_session_version >= 0),
  certificate_sha256 BYTEA NOT NULL
    CHECK (octet_length(certificate_sha256) = 32),
  reason_code VARCHAR(40) NOT NULL CHECK
    (reason_code IN ('operator_withdrawal','suspected_compromise','metadata_status_changed')),
  decision_state VARCHAR(32) NOT NULL DEFAULT 'disqualified_untrusted'
    CHECK (decision_state='disqualified_untrusted'),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION guard_admin_passkey_root_disqualification_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  locked_digest BYTEA;
BEGIN
  -- Exactly the same row lock as V23 screening; one of the conflicting
  -- actions must serialize after the other. Terminal disqualification wins
  -- for every subsequent screening without rewriting earlier history.
  SELECT certificate_sha256 INTO locked_digest
    FROM admin_passkey_attestation_root_proposals_staging
   WHERE proposal_id=NEW.proposal_id AND trust_state='untrusted_proposal'
   FOR UPDATE;
  IF locked_digest IS NULL
     OR locked_digest IS DISTINCT FROM NEW.certificate_sha256 THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='candidate disqualification requires matching immutable digest';
  END IF;
  -- Organization-wide deny operation: allow any CURRENT owner security
  -- admin even when the original proposer was removed or disabled.
  IF NOT EXISTS (
    SELECT 1 FROM users actor
    JOIN user_role_scopes scope ON scope.user_id=actor.id
    JOIN roles role ON role.id=scope.role_id
    WHERE actor.id=NEW.disqualified_by
      AND actor.disabled_at IS NULL
      AND actor.session_version=NEW.disqualifier_session_version
      AND role.code='owner_security_admin'
    FOR SHARE OF actor,scope,role
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='candidate disqualification requires live security owner';
  END IF;
  -- A caller cannot supply a forged later/earlier receipt timestamp.
  NEW.recorded_at := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE TRIGGER admin_passkey_root_disqualification_guard
BEFORE INSERT ON admin_passkey_attestation_root_disqualifications_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_root_disqualification_insert();

CREATE TRIGGER admin_passkey_root_disqualification_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_attestation_root_disqualifications_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

-- PostgreSQL runs triggers of the same type in name order. The original V23
-- screening guard first locks the V22 proposal; this LAST guard, under that
-- same lock, rejects screening if V24 disqualification is already committed.
-- An already committed screening remains an immutable historical receipt.
CREATE FUNCTION guard_admin_passkey_root_screening_disqualified()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM 1 FROM admin_passkey_attestation_root_proposals_staging
   WHERE proposal_id=NEW.proposal_id FOR UPDATE;
  IF EXISTS (
    SELECT 1 FROM admin_passkey_attestation_root_disqualifications_staging d
    WHERE d.proposal_id=NEW.proposal_id
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='disqualified root candidate cannot be screened';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER zz_admin_passkey_root_screening_disqualification_guard
BEFORE INSERT ON admin_passkey_attestation_root_screenings_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_passkey_root_screening_disqualified();
