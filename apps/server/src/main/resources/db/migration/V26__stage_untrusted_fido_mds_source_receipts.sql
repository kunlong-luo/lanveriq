-- A11 D4-C5 V26: isolated source-evidence receipts, NEVER trusted FIDO MDS.
-- This table cannot authorize WebAuthn, reviewers, Agent, CA or certificates.
-- V25 synthetic stream is the owner/serialization point; its observations
-- are NOT promoted. PostgreSQL binds all verified-shaped hash receipts
-- atomically, but DOES NOT cryptographically verify JWS/CRLs/time signatures.
CREATE TABLE admin_fido_mds_source_receipts_staging (
    receipt_id UUID PRIMARY KEY DEFAULT uuidv7(),
    stream_id UUID NOT NULL REFERENCES admin_fido_mds_untrusted_streams_staging(stream_id),
    receipt_sequence BIGINT NOT NULL CHECK (receipt_sequence > 0),
    blob_no BIGINT NOT NULL CHECK (blob_no > 0),
    signed_time_sequence BIGINT NOT NULL CHECK (signed_time_sequence > 0),
    signed_time_at TIMESTAMPTZ NOT NULL,
    leaf_sha256 BYTEA NOT NULL CHECK (octet_length(leaf_sha256) = 32),
    jws_sha256 BYTEA NOT NULL CHECK (octet_length(jws_sha256) = 32),
    root_sha256 BYTEA NOT NULL CHECK (octet_length(root_sha256) = 32),
    crl_evidence_sha256 BYTEA NOT NULL CHECK (octet_length(crl_evidence_sha256) = 32),
    time_key_sha256 BYTEA NOT NULL CHECK (octet_length(time_key_sha256) = 32),
    signed_time_proof_sha256 BYTEA NOT NULL
        CHECK (octet_length(signed_time_proof_sha256) = 32),
    previous_receipt_sha256 BYTEA
        CHECK (previous_receipt_sha256 IS NULL
               OR octet_length(previous_receipt_sha256) = 32),
    receipt_sha256 BYTEA NOT NULL DEFAULT decode(repeat('00',32),'hex')
        CHECK (octet_length(receipt_sha256) = 32),
    observed_by UUID NOT NULL REFERENCES users(id),
    observer_session_version BIGINT NOT NULL CHECK (observer_session_version >= 0),
    evidence_state VARCHAR(32) NOT NULL DEFAULT 'source_untrusted'
        CHECK (evidence_state = 'source_untrusted'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (stream_id,receipt_sequence),
    UNIQUE (stream_id,blob_no),
    UNIQUE (stream_id,signed_time_sequence),
    UNIQUE (stream_id,jws_sha256)
);
CREATE INDEX admin_fido_mds_source_receipts_recent_idx
    ON admin_fido_mds_source_receipts_staging(stream_id,receipt_sequence DESC);

CREATE FUNCTION guard_admin_fido_mds_source_receipt_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    creator UUID;
    creator_version BIGINT;
    last_receipt admin_fido_mds_source_receipts_staging%ROWTYPE;
BEGIN
    -- Lock V25 synthetic stream first. All concurrent source receipts in
    -- the same stream serialize here, including different JWS/versions.
    SELECT created_by,creator_session_version
      INTO creator,creator_version
      FROM admin_fido_mds_untrusted_streams_staging
      WHERE stream_id=NEW.stream_id AND stream_state='synthetic_untrusted'
      FOR UPDATE;
    IF creator IS NULL OR NEW.observed_by IS DISTINCT FROM creator
       OR NOT EXISTS (
          SELECT 1 FROM users actor
          JOIN user_role_scopes scope ON scope.user_id=actor.id
          JOIN roles role ON role.id=scope.role_id
          WHERE actor.id=creator AND actor.disabled_at IS NULL
            AND actor.session_version=creator_version
            AND actor.session_version=NEW.observer_session_version
            AND role.code='owner_security_admin'
          FOR SHARE OF actor,scope,role
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted MDS source receipt requires live stream owner/session';
    END IF;
    SELECT * INTO last_receipt
      FROM admin_fido_mds_source_receipts_staging
      WHERE stream_id=NEW.stream_id
      ORDER BY receipt_sequence DESC LIMIT 1;
    IF last_receipt.receipt_id IS NULL THEN
        IF NEW.receipt_sequence<>1 OR NEW.previous_receipt_sha256 IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='MDS source genesis receipt sequence/predecessor denied';
        END IF;
    ELSE
        IF last_receipt.receipt_sequence=9223372036854775807
           OR NEW.receipt_sequence<>last_receipt.receipt_sequence+1
           OR NEW.previous_receipt_sha256 IS DISTINCT FROM last_receipt.receipt_sha256
           OR NEW.blob_no<=last_receipt.blob_no
           OR NEW.signed_time_sequence<=last_receipt.signed_time_sequence
           OR NEW.signed_time_at<last_receipt.signed_time_at
           OR NEW.root_sha256 IS DISTINCT FROM last_receipt.root_sha256
           OR NEW.time_key_sha256 IS DISTINCT FROM last_receipt.time_key_sha256 THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='MDS source receipt rollback/fork/anchor swap denied';
        END IF;
    END IF;
    IF NEW.evidence_state <> 'source_untrusted' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='trusted MDS source state cannot be created';
    END IF;
    -- Database wall-clock is only a local bounded synthetic gate,
    -- NOT organization-approved trusted time or signature provenance.
    IF NEW.signed_time_at>clock_timestamp()+INTERVAL '5 minutes'
       OR NEW.signed_time_at<clock_timestamp()-INTERVAL '48 hours' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='MDS source receipt time outside synthetic window';
    END IF;
    -- Compute canonical record digest inside PostgreSQL; never accept a
    -- client-provided digest for any chain link or evidence field.
    NEW.receipt_sha256:=sha256(convert_to(
        'lanveriq/a11/mds/source/receipt/v1|' ||
        NEW.stream_id::text || '|' ||
        NEW.receipt_sequence::text || '|' ||
        NEW.blob_no::text || '|' ||
        NEW.signed_time_sequence::text || '|' ||
        to_char(NEW.signed_time_at AT TIME ZONE 'UTC',
                'YYYY-MM-DD"T"HH24:MI:SS.US') || 'Z|' ||
        encode(NEW.leaf_sha256,'hex') || '|' ||
        encode(NEW.jws_sha256,'hex') || '|' ||
        encode(NEW.root_sha256,'hex') || '|' ||
        encode(NEW.crl_evidence_sha256,'hex') || '|' ||
        encode(NEW.time_key_sha256,'hex') || '|' ||
        encode(NEW.signed_time_proof_sha256,'hex') || '|' ||
        COALESCE(encode(NEW.previous_receipt_sha256,'hex'),'GENESIS')
    ,'UTF8'));
    NEW.evidence_state:='source_untrusted';
    NEW.recorded_at:=clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER admin_fido_mds_source_receipts_insert_guard
BEFORE INSERT ON admin_fido_mds_source_receipts_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_fido_mds_source_receipt_insert();
CREATE TRIGGER admin_fido_mds_source_receipts_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_source_receipts_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
