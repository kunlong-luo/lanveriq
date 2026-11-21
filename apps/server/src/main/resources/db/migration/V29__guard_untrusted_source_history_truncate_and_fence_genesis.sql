-- A11 D4-C8 / V29: fail-closed append-only history for V25/V26 and
-- one synthetic fence genesis per owner. This never authorizes FIDO MDS.
-- Forward-only: no historical rows or prior migrations are rewritten.

-- Row-level UPDATE/DELETE immutability does NOT protect against TRUNCATE,
-- including TRUNCATE ... CASCADE. Prevent it for each V25/V26 history table,
-- even if empty, with a statement trigger. V28 already guards its tables.
CREATE FUNCTION a11_v29_reject_untrusted_source_history_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='V25/V26 untrusted MDS source history cannot be truncated';
END;
$$;

CREATE TRIGGER a11_v29_no_stream_truncate
BEFORE TRUNCATE ON admin_fido_mds_untrusted_streams_staging
FOR EACH STATEMENT
EXECUTE FUNCTION a11_v29_reject_untrusted_source_history_truncate();

CREATE TRIGGER a11_v29_no_observation_truncate
BEFORE TRUNCATE ON admin_fido_mds_untrusted_observations_staging
FOR EACH STATEMENT
EXECUTE FUNCTION a11_v29_reject_untrusted_source_history_truncate();

CREATE TRIGGER a11_v29_no_receipt_truncate
BEFORE TRUNCATE ON admin_fido_mds_source_receipts_staging
FOR EACH STATEMENT
EXECUTE FUNCTION a11_v29_reject_untrusted_source_history_truncate();

-- A V25-only synthetic stream may already have an application-level audit.
-- The V27/V28 audit TRUNCATE guards previously covered only V26/V28 data.
-- Guard audit TRUNCATE even if the owner has not inserted V26 or V28 data.
CREATE FUNCTION a11_v29_reject_untrusted_observation_audit_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM admin_fido_mds_untrusted_streams_staging)
       OR EXISTS (SELECT 1 FROM admin_fido_mds_untrusted_observations_staging) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='V25 untrusted MDS audit cannot be truncated';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER a11_v29_guard_untrusted_observation_audit_truncate
BEFORE TRUNCATE ON admin_access_log
FOR EACH STATEMENT
EXECUTE FUNCTION a11_v29_reject_untrusted_observation_audit_truncate();

-- Existing V28 allows repeatedly creating a fence for the SAME owner,
-- resetting the optional checkpoint sequence. Future inserts are
-- serialized on the owner row before checking any existing fence. Locking
-- before the other scope rows prevents cross-writer read-then-insert races.
-- This does not rewrite or de-duplicate any existing synthetic fences,
-- and different owner accounts remain independent/untrusted.
CREATE OR REPLACE FUNCTION a11_v28_guard_untrusted_fence_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    live_version BIGINT;
BEGIN
    SELECT session_version INTO live_version
    FROM users
    WHERE id=NEW.created_by AND disabled_at IS NULL
    FOR UPDATE;

    IF live_version IS NULL
       OR live_version<>NEW.creator_session_version
       OR NOT EXISTS (
           SELECT 1 FROM user_role_scopes scope
           JOIN roles role ON role.id=scope.role_id
           WHERE scope.user_id=NEW.created_by
             AND role.code='owner_security_admin'
           FOR SHARE OF scope,role
       ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence requires active owner security scope';
    END IF;

    IF EXISTS (
        SELECT 1 FROM admin_fido_mds_source_fences_staging
        WHERE created_by=NEW.created_by
    ) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source owner already has a fence; restart denied';
    END IF;

    IF NEW.fence_state <> 'fence_untrusted' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='untrusted source fence cannot be promoted';
    END IF;

    NEW.created_at:=clock_timestamp();
    RETURN NEW;
END;
$$;

-- Historical duplicates (if present in older synthetic installations) are
-- retained; this non-unique index only accelerates new serialized checks.
CREATE INDEX admin_fido_mds_source_fences_owner_recent_idx
ON admin_fido_mds_source_fences_staging(created_by,created_at DESC);

-- IMPORTANT: This is a scoped staging control, not a trusted global source
-- namespace. A different owner account can still open its own fence; DB
-- owner/superuser DDL may disable triggers or rewrite local data. Production
-- requires a separate controlled issuer/approval registry, a low-privilege
-- runtime DB principal and an off-DB tamper-evident witness.
