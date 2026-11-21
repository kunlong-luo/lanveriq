-- A11 D4-C7/V28: cross-stream anti-rollback fences for synthetic/untrusted
-- FIDO MDS source receipts. These are NOT official FIDO trust anchors.
-- A new fence is merely an owner-scoped local grouping hint; creating a
-- different fence intentionally does NOT prove globally trusted freshness.
-- No old migration/receipt is modified or promoted.
CREATE TABLE admin_fido_mds_source_fences_staging (
    fence_id UUID PRIMARY KEY DEFAULT uuidv7(),
    created_by UUID NOT NULL REFERENCES users(id),
    creator_session_version BIGINT NOT NULL CHECK (creator_session_version >= 0),
    root_sha256 BYTEA NOT NULL CHECK (octet_length(root_sha256) = 32),
    time_key_sha256 BYTEA NOT NULL CHECK (octet_length(time_key_sha256) = 32),
    fence_state VARCHAR(32) NOT NULL DEFAULT 'fence_untrusted'
        CHECK (fence_state = 'fence_untrusted'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION a11_v28_guard_untrusted_fence_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users actor
        JOIN user_role_scopes scope ON scope.user_id=actor.id
        JOIN roles role ON role.id=scope.role_id
        WHERE actor.id=NEW.created_by AND actor.disabled_at IS NULL
          AND actor.session_version=NEW.creator_session_version
          AND role.code='owner_security_admin'
        FOR SHARE OF actor,scope,role
    ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence requires active owner security scope';
    END IF;
    IF NEW.fence_state <> 'fence_untrusted' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence cannot be promoted';
    END IF;
    NEW.created_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER a11_v28_untrusted_fence_guard
BEFORE INSERT ON admin_fido_mds_source_fences_staging
FOR EACH ROW EXECUTE FUNCTION a11_v28_guard_untrusted_fence_insert();
CREATE TRIGGER a11_v28_untrusted_fence_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_source_fences_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

CREATE TABLE admin_fido_mds_source_fence_checkpoints_staging (
    checkpoint_id UUID PRIMARY KEY DEFAULT uuidv7(),
    fence_id UUID NOT NULL REFERENCES admin_fido_mds_source_fences_staging(fence_id),
    source_receipt_id UUID NOT NULL UNIQUE
        REFERENCES admin_fido_mds_source_receipts_staging(receipt_id),
    checkpoint_sequence BIGINT NOT NULL CHECK (checkpoint_sequence > 0),
    previous_checkpoint_sha256 BYTEA
        CHECK (previous_checkpoint_sha256 IS NULL
               OR octet_length(previous_checkpoint_sha256) = 32),
    checkpoint_sha256 BYTEA NOT NULL DEFAULT decode(repeat('00',32),'hex')
        CHECK (octet_length(checkpoint_sha256) = 32),
    observed_by UUID NOT NULL REFERENCES users(id),
    observer_session_version BIGINT NOT NULL CHECK (observer_session_version >= 0),
    checkpoint_state VARCHAR(32) NOT NULL DEFAULT 'source_untrusted'
        CHECK (checkpoint_state = 'source_untrusted'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (fence_id, checkpoint_sequence)
);
CREATE INDEX admin_fido_mds_fence_checkpoints_recent_idx
    ON admin_fido_mds_source_fence_checkpoints_staging
    (fence_id,checkpoint_sequence DESC);

CREATE FUNCTION a11_v28_guard_source_checkpoint_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    fence admin_fido_mds_source_fences_staging%ROWTYPE;
    source_record admin_fido_mds_source_receipts_staging%ROWTYPE;
    last_checkpoint admin_fido_mds_source_fence_checkpoints_staging%ROWTYPE;
    last_source admin_fido_mds_source_receipts_staging%ROWTYPE;
BEGIN
    -- One fence row is the transaction-wide serialization point for all
    -- its streams, including concurrent first checkpoints of two streams.
    SELECT * INTO fence
      FROM admin_fido_mds_source_fences_staging
      WHERE fence_id=NEW.fence_id AND fence_state='fence_untrusted'
      FOR UPDATE;
    IF fence.fence_id IS NULL OR NEW.observed_by IS DISTINCT FROM fence.created_by
       OR NEW.observer_session_version IS DISTINCT FROM fence.creator_session_version
       OR NOT EXISTS (
           SELECT 1 FROM users actor
           JOIN user_role_scopes scope ON scope.user_id=actor.id
           JOIN roles role ON role.id=scope.role_id
           WHERE actor.id=fence.created_by AND actor.disabled_at IS NULL
             AND actor.session_version=fence.creator_session_version
             AND role.code='owner_security_admin'
           FOR SHARE OF actor,scope,role
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence owner/session is no longer valid';
    END IF;

    SELECT * INTO source_record
      FROM admin_fido_mds_source_receipts_staging
      WHERE receipt_id=NEW.source_receipt_id
        AND observed_by=NEW.observed_by
        AND observer_session_version=NEW.observer_session_version
        AND evidence_state='source_untrusted'
        AND root_sha256=fence.root_sha256
        AND time_key_sha256=fence.time_key_sha256;
    IF source_record.receipt_id IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM admin_fido_mds_untrusted_streams_staging
           WHERE stream_id=source_record.stream_id
             AND created_by=fence.created_by
             AND creator_session_version=fence.creator_session_version
             AND stream_state='synthetic_untrusted'
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='source receipt is not bound to this untrusted fence/owner/pins';
    END IF;

    SELECT * INTO last_checkpoint
      FROM admin_fido_mds_source_fence_checkpoints_staging
      WHERE fence_id=NEW.fence_id
      ORDER BY checkpoint_sequence DESC LIMIT 1;
    IF last_checkpoint.checkpoint_id IS NULL THEN
        IF NEW.checkpoint_sequence<>1
           OR NEW.previous_checkpoint_sha256 IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='source fence genesis checkpoint must start at one';
        END IF;
    ELSE
        SELECT * INTO last_source
          FROM admin_fido_mds_source_receipts_staging
          WHERE receipt_id=last_checkpoint.source_receipt_id;
        IF last_checkpoint.checkpoint_sequence=9223372036854775807
           OR NEW.checkpoint_sequence<>last_checkpoint.checkpoint_sequence+1
           OR NEW.previous_checkpoint_sha256
              IS DISTINCT FROM last_checkpoint.checkpoint_sha256
           OR source_record.blob_no<=last_source.blob_no
           OR source_record.signed_time_sequence<=last_source.signed_time_sequence
           OR source_record.signed_time_at<last_source.signed_time_at THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='cross-stream source receipt rollback/fork/replay denied';
        END IF;
    END IF;
    IF NEW.checkpoint_state <> 'source_untrusted' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source checkpoint cannot be promoted';
    END IF;

    -- The referenced V26 digest is itself PostgreSQL-computed, immutable
    -- and transaction-audited. The V28 digest binds the entire lineage,
    -- never an unverified client-provided 'trusted' field.
    NEW.checkpoint_sha256 := sha256(convert_to(
        'lanveriq/a11/mds/source/fence-checkpoint/v1|' ||
        NEW.fence_id::text || '|' ||
        NEW.checkpoint_sequence::text || '|' ||
        NEW.source_receipt_id::text || '|' ||
        encode(source_record.receipt_sha256,'hex') || '|' ||
        COALESCE(encode(NEW.previous_checkpoint_sha256,'hex'),'GENESIS')
    ,'UTF8'));
    NEW.recorded_at:=clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER a11_v28_fence_checkpoint_guard
BEFORE INSERT ON admin_fido_mds_source_fence_checkpoints_staging
FOR EACH ROW EXECUTE FUNCTION a11_v28_guard_source_checkpoint_insert();
CREATE TRIGGER a11_v28_fence_checkpoint_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_source_fence_checkpoints_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

-- Auditing is database-owned, even for direct SQL writers. BEFORE INSERT
-- audit suppression MUST abort both checkpoint and receipt if they are
-- written in the same SQL transaction.
CREATE FUNCTION a11_v28_auto_audit_untrusted_source_fence()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    audit_id UUID;
    audit_action VARCHAR(120);
    audit_resource UUID;
    audit_actor UUID;
BEGIN
    IF TG_TABLE_NAME='admin_fido_mds_source_fences_staging' THEN
        audit_action:='admin.passkey.mds.source.untrusted.fence';
        audit_resource:=NEW.fence_id;
        audit_actor:=NEW.created_by;
    ELSIF TG_TABLE_NAME='admin_fido_mds_source_fence_checkpoints_staging' THEN
        audit_action:='admin.passkey.mds.source.untrusted.checkpoint';
        audit_resource:=NEW.checkpoint_id;
        audit_actor:=NEW.observed_by;
    ELSE
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='unexpected untrusted source fence audit target';
    END IF;
    INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
    VALUES (audit_actor,audit_resource,audit_action,'allowed')
    RETURNING id INTO audit_id;
    IF audit_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence/checkpoint audit was suppressed';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER a11_v28_fence_automatic_audit
AFTER INSERT ON admin_fido_mds_source_fences_staging
FOR EACH ROW EXECUTE FUNCTION a11_v28_auto_audit_untrusted_source_fence();
CREATE TRIGGER a11_v28_checkpoint_automatic_audit
AFTER INSERT ON admin_fido_mds_source_fence_checkpoints_staging
FOR EACH ROW EXECUTE FUNCTION a11_v28_auto_audit_untrusted_source_fence();

CREATE FUNCTION a11_v28_guard_fence_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.action IN (
          'admin.passkey.mds.source.untrusted.fence',
          'admin.passkey.mds.source.untrusted.checkpoint'
        ) THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='untrusted source fence audit is append-only';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.action IN (
         'admin.passkey.mds.source.untrusted.fence',
         'admin.passkey.mds.source.untrusted.checkpoint'
       ) OR NEW.action IN (
         'admin.passkey.mds.source.untrusted.fence',
         'admin.passkey.mds.source.untrusted.checkpoint'
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence audit cannot be rebound';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER a11_v28_fence_audit_immutable
BEFORE UPDATE OR DELETE ON admin_access_log
FOR EACH ROW EXECUTE FUNCTION a11_v28_guard_fence_audit_mutation();

CREATE FUNCTION a11_v28_guard_fence_audit_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM admin_fido_mds_source_fences_staging)
       OR EXISTS (SELECT 1 FROM admin_fido_mds_source_fence_checkpoints_staging) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence audit cannot be truncated';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER a11_v28_fence_audit_truncate
BEFORE TRUNCATE ON admin_access_log
FOR EACH STATEMENT EXECUTE FUNCTION a11_v28_guard_fence_audit_truncate();

-- TRUNCATE bypasses row-level immutable triggers. The shadow fence and
-- checkpoint history are never legitimately truncated by an app role.
CREATE FUNCTION a11_v28_reject_fence_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='untrusted source fence history cannot be truncated';
END;
$$;
CREATE TRIGGER a11_v28_no_fence_truncate
BEFORE TRUNCATE ON admin_fido_mds_source_fences_staging
FOR EACH STATEMENT EXECUTE FUNCTION a11_v28_reject_fence_truncate();
CREATE TRIGGER a11_v28_no_checkpoint_truncate
BEFORE TRUNCATE ON admin_fido_mds_source_fence_checkpoints_staging
FOR EACH STATEMENT EXECUTE FUNCTION a11_v28_reject_fence_truncate();
