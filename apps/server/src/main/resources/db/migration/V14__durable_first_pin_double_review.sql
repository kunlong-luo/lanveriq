-- A11-B: durable internal-only double-review ledger.
-- No HTTP endpoints, signing keys, issuer, Agent invocation or release enablement.
-- PostgreSQL time is an audit timestamp, NOT an authenticated Agent clock.
CREATE TABLE first_pin_review_requests (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    device_id UUID NOT NULL REFERENCES devices(id),
    device_group_id UUID NOT NULL REFERENCES device_groups(id),
    credential_id UUID NOT NULL UNIQUE REFERENCES device_credentials(id),
    credential_epoch_id UUID NOT NULL UNIQUE,
    cert_sha256_fingerprint CHAR(64) NOT NULL
        CHECK (cert_sha256_fingerprint ~ '^[0-9a-f]{64}$'),
    csr_spki_sha256 CHAR(64) NOT NULL
        CHECK (csr_spki_sha256 ~ '^[0-9a-f]{64}$'),
    requested_by UUID NOT NULL REFERENCES users(id),
    applicant_session_version BIGINT NOT NULL CHECK (applicant_session_version >= 0),
    policy_version INTEGER NOT NULL DEFAULT 1 CHECK (policy_version = 1),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX first_pin_review_requests_device ON first_pin_review_requests(device_id);

CREATE TABLE first_pin_review_approvals (
    request_id UUID NOT NULL REFERENCES first_pin_review_requests(id),
    step SMALLINT NOT NULL CHECK (step IN (1, 2)),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    reviewer_session_version BIGINT NOT NULL CHECK (reviewer_session_version >= 0),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (request_id, step),
    UNIQUE (request_id, reviewer_id)
);

-- Future independent revocation channel; no production mutation method is
-- exposed. An existing approval never becomes a signature authorization.
CREATE TABLE first_pin_review_revocations (
    request_id UUID PRIMARY KEY REFERENCES first_pin_review_requests(id),
    revoked_by UUID NOT NULL REFERENCES users(id),
    reason_code VARCHAR(48) NOT NULL CHECK (
        reason_code IN ('admin_revoked', 'credential_revoked', 'policy_changed')),
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION guard_first_pin_review_approval()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE applicant UUID;
DECLARE previous_reviewer UUID;
BEGIN
    -- Serialized against concurrent approvals on the same request.
    SELECT requested_by INTO applicant
      FROM first_pin_review_requests
     WHERE id = NEW.request_id FOR UPDATE;
    IF applicant IS NULL OR applicant = NEW.reviewer_id
       OR EXISTS (SELECT 1 FROM first_pin_review_revocations
                  WHERE request_id = NEW.request_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='first-pin review applicant/revocation conflict';
    END IF;
    IF NEW.step = 1 THEN
        IF EXISTS (SELECT 1 FROM first_pin_review_approvals
                   WHERE request_id = NEW.request_id) THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='first-pin review already started';
        END IF;
    ELSE
        SELECT reviewer_id INTO previous_reviewer
          FROM first_pin_review_approvals
         WHERE request_id = NEW.request_id AND step = 1;
        IF previous_reviewer IS NULL OR previous_reviewer = NEW.reviewer_id THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='first-pin second reviewer must be independent';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER first_pin_review_approval_guard
BEFORE INSERT ON first_pin_review_approvals FOR EACH ROW
EXECUTE FUNCTION guard_first_pin_review_approval();

-- Prevent normal application SQL from modifying the committed review history.
-- Database owners/superusers and physical storage control remain out of scope.
CREATE FUNCTION reject_first_pin_review_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='first-pin review history is append-only';
END;
$$;
CREATE TRIGGER first_pin_review_request_immutable
BEFORE UPDATE OR DELETE ON first_pin_review_requests FOR EACH ROW
EXECUTE FUNCTION reject_first_pin_review_history_mutation();
CREATE TRIGGER first_pin_review_approval_immutable
BEFORE UPDATE OR DELETE ON first_pin_review_approvals FOR EACH ROW
EXECUTE FUNCTION reject_first_pin_review_history_mutation();
CREATE TRIGGER first_pin_review_revocation_immutable
BEFORE UPDATE OR DELETE ON first_pin_review_revocations FOR EACH ROW
EXECUTE FUNCTION reject_first_pin_review_history_mutation();
