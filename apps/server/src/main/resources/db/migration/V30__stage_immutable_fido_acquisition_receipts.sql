-- A11 D4-C15 / V30: persistent, strictly UNTRUSTED off-line acquisition
-- evidence, linked to V26 source receipts. No actual network retrieval,
-- production root, real approval, signature validation in SQL, or admission.
-- Always additive: V25-V29 and historical data remain unchanged.
CREATE TABLE admin_fido_mds_acquisition_receipts_staging (
    acquisition_id UUID PRIMARY KEY DEFAULT uuidv7(),
    stream_id UUID NOT NULL
        REFERENCES admin_fido_mds_untrusted_streams_staging(stream_id),
    source_receipt_id UUID NOT NULL UNIQUE
        REFERENCES admin_fido_mds_source_receipts_staging(receipt_id),
    acquisition_sequence BIGINT NOT NULL CHECK (acquisition_sequence > 0),
    signed_manifest BYTEA NOT NULL
        CHECK (octet_length(signed_manifest) BETWEEN 128 AND 32768),
    signature_bytes BYTEA NOT NULL CHECK (octet_length(signature_bytes)=64),
    acquisition_key_sha256 BYTEA NOT NULL
        CHECK (octet_length(acquisition_key_sha256)=32),
    jws_sha256 BYTEA NOT NULL CHECK (octet_length(jws_sha256)=32),
    crl_evidence_sha256 BYTEA NOT NULL
        CHECK (octet_length(crl_evidence_sha256)=32),
    signed_manifest_sha256 BYTEA NOT NULL DEFAULT decode(repeat('00',32),'hex')
        CHECK (octet_length(signed_manifest_sha256)=32),
    signature_sha256 BYTEA NOT NULL DEFAULT decode(repeat('00',32),'hex')
        CHECK (octet_length(signature_sha256)=32),
    previous_acquisition_sha256 BYTEA
        CHECK (previous_acquisition_sha256 IS NULL
               OR octet_length(previous_acquisition_sha256)=32),
    acquisition_sha256 BYTEA NOT NULL DEFAULT decode(repeat('00',32),'hex')
        CHECK (octet_length(acquisition_sha256)=32),
    recorded_by UUID NOT NULL REFERENCES users(id),
    recorder_session_version BIGINT NOT NULL CHECK (recorder_session_version>=0),
    evidence_state VARCHAR(32) NOT NULL DEFAULT 'acquisition_untrusted'
        CHECK (evidence_state='acquisition_untrusted'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (stream_id, acquisition_sequence),
    UNIQUE (stream_id, signed_manifest_sha256),
    UNIQUE (stream_id, signature_sha256)
);
CREATE INDEX admin_fido_mds_acquisition_recent_idx
ON admin_fido_mds_acquisition_receipts_staging
(stream_id, acquisition_sequence DESC);

CREATE FUNCTION a11_v30_guard_acquisition_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    owner_id UUID;
    owner_version BIGINT;
    source_record admin_fido_mds_source_receipts_staging%ROWTYPE;
    prior admin_fido_mds_acquisition_receipts_staging%ROWTYPE;
BEGIN
    -- One V25 stream FOR UPDATE serializes competing first and later
    -- acquisition receipts; no read-then-insert fork is admitted.
    SELECT created_by, creator_session_version
      INTO owner_id, owner_version
      FROM admin_fido_mds_untrusted_streams_staging
      WHERE stream_id=NEW.stream_id AND stream_state='synthetic_untrusted'
      FOR UPDATE;
    IF owner_id IS NULL OR NEW.recorded_by IS DISTINCT FROM owner_id
       OR NEW.recorder_session_version IS DISTINCT FROM owner_version
       OR NOT EXISTS (
           SELECT 1 FROM users actor
           JOIN user_role_scopes scope ON scope.user_id=actor.id
           JOIN roles r ON r.id=scope.role_id
           WHERE actor.id=owner_id AND actor.disabled_at IS NULL
             AND actor.session_version=owner_version
             AND r.code='owner_security_admin'
           FOR SHARE OF actor,scope,r
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted acquisition requires current source owner/session';
    END IF;

    SELECT * INTO source_record
    FROM admin_fido_mds_source_receipts_staging
    WHERE receipt_id=NEW.source_receipt_id
      AND stream_id=NEW.stream_id
      AND receipt_sequence=NEW.acquisition_sequence
      AND observed_by=NEW.recorded_by
      AND observer_session_version=NEW.recorder_session_version
      AND evidence_state='source_untrusted';
    IF source_record.receipt_id IS NULL
       OR NEW.jws_sha256 IS DISTINCT FROM source_record.jws_sha256
       OR NEW.crl_evidence_sha256 IS DISTINCT FROM source_record.crl_evidence_sha256
       OR NEW.evidence_state<>'acquisition_untrusted' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='acquisition evidence is not bound to untrusted source receipt';
    END IF;

    SELECT * INTO prior
    FROM admin_fido_mds_acquisition_receipts_staging
    WHERE stream_id=NEW.stream_id
    ORDER BY acquisition_sequence DESC LIMIT 1;
    IF prior.acquisition_id IS NULL THEN
        IF NEW.acquisition_sequence<>1
           OR NEW.previous_acquisition_sha256 IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='acquisition genesis must start at source sequence one';
        END IF;
    ELSE
        IF prior.acquisition_sequence=9223372036854775807
           OR NEW.acquisition_sequence<>prior.acquisition_sequence+1
           OR NEW.previous_acquisition_sha256
              IS DISTINCT FROM prior.acquisition_sha256
           OR NEW.acquisition_key_sha256 IS DISTINCT FROM
              prior.acquisition_key_sha256 THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='acquisition receipt replay/fork/key swap denied';
        END IF;
    END IF;

    -- DATABASE-OWNED digests: never trust supplied hashes over signed bytes.
    -- PostgreSQL does NOT verify Ed25519 or alleged TLS/CRL provenance.
    NEW.signed_manifest_sha256 := sha256(NEW.signed_manifest);
    NEW.signature_sha256 := sha256(NEW.signature_bytes);
    NEW.acquisition_sha256 := sha256(convert_to(
        'lanveriq/a11/mds/acquisition/receipt/v1|' ||
        NEW.stream_id::text || '|' ||
        NEW.source_receipt_id::text || '|' ||
        NEW.acquisition_sequence::text || '|' ||
        encode(source_record.receipt_sha256,'hex') || '|' ||
        encode(NEW.acquisition_key_sha256,'hex') || '|' ||
        encode(NEW.jws_sha256,'hex') || '|' ||
        encode(NEW.crl_evidence_sha256,'hex') || '|' ||
        encode(NEW.signed_manifest_sha256,'hex') || '|' ||
        encode(NEW.signature_sha256,'hex') || '|' ||
        NEW.recorded_by::text || '|' ||
        NEW.recorder_session_version::text || '|' ||
        COALESCE(encode(NEW.previous_acquisition_sha256,'hex'),'GENESIS'),
        'UTF8'));
    NEW.recorded_at:=clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER a11_v30_acquisition_guard
BEFORE INSERT ON admin_fido_mds_acquisition_receipts_staging
FOR EACH ROW EXECUTE FUNCTION a11_v30_guard_acquisition_insert();
CREATE TRIGGER a11_v30_acquisition_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_acquisition_receipts_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

-- DELETE immutability does NOT catch TRUNCATE or TRUNCATE ... CASCADE.
CREATE FUNCTION a11_v30_reject_acquisition_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='untrusted acquisition history cannot be truncated';
END;
$$;
CREATE TRIGGER a11_v30_no_acquisition_truncate
BEFORE TRUNCATE ON admin_fido_mds_acquisition_receipts_staging
FOR EACH STATEMENT EXECUTE FUNCTION a11_v30_reject_acquisition_truncate();

-- AFTER trigger executes even for direct SQL writers; a before-insert audit
-- suppressor returning NULL MUST abort the whole enclosing transaction.
CREATE FUNCTION a11_v30_auto_acquisition_audit()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE audit_id UUID;
BEGIN
    INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
    VALUES (NEW.recorded_by,NEW.acquisition_id,
            'admin.passkey.mds.source.untrusted.acquisition','allowed')
    RETURNING id INTO audit_id;
    IF audit_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted acquisition audit cannot be suppressed';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER a11_v30_acquisition_auto_audit
AFTER INSERT ON admin_fido_mds_acquisition_receipts_staging
FOR EACH ROW EXECUTE FUNCTION a11_v30_auto_acquisition_audit();

CREATE FUNCTION a11_v30_protect_acquisition_audit()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.action='admin.passkey.mds.source.untrusted.acquisition' THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='acquisition audit is immutable';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.action='admin.passkey.mds.source.untrusted.acquisition'
       OR NEW.action='admin.passkey.mds.source.untrusted.acquisition' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='acquisition audit cannot be rebound';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER a11_v30_acquisition_audit_immutable
BEFORE UPDATE OR DELETE ON admin_access_log
FOR EACH ROW EXECUTE FUNCTION a11_v30_protect_acquisition_audit();

-- Existing V29 audit TRUNCATE guard blocks removal when V25 streams exist;
-- V30 adds an explicit guard for independent audit-history visibility.
CREATE FUNCTION a11_v30_guard_acquisition_audit_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM admin_fido_mds_acquisition_receipts_staging) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted acquisition audit cannot be truncated';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER a11_v30_acquisition_audit_truncate
BEFORE TRUNCATE ON admin_access_log
FOR EACH STATEMENT EXECUTE FUNCTION a11_v30_guard_acquisition_audit_truncate();

-- No authorization view, grant, data promotion or real FIDO root is added.
-- A PostgreSQL migrator/owner can disable triggers, so these rules are
-- NOT an independent external timestamp or tamper-evident witness.
