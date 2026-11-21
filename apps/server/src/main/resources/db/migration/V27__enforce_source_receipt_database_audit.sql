-- A11 D4-C5 V27: automatic, transaction-atomic DB audit for V26
-- source_untrusted receipts. This migration is forward-only; V26 history
-- and all existing metadata are left intact and permanently UNTRUSTED.
--
-- AFTER INSERT runs for every normal SQL writer, not just the Java ledger.
-- A before-insert suppression trigger on admin_access_log must fail this
-- receipt transaction; successful DML without an audit is forbidden.
-- This protects ordinary non-DDL app roles only. A privileged database
-- owner/superuser can disable triggers or rewrite data; this is NOT a
-- cryptographically independent, tamper-evident external audit service.
CREATE FUNCTION a11_v27_audit_source_receipt_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    audit_id UUID;
BEGIN
    INSERT INTO admin_access_log
      (principal_id,resource_id,action,outcome)
    VALUES
      (NEW.observed_by,NEW.receipt_id,
       'admin.passkey.mds.source.untrusted.receipt','allowed')
    RETURNING id INTO audit_id;

    -- PostgreSQL permits a BEFORE INSERT trigger to suppress an audit row.
    -- An INSERT with zero rows would silently succeed without this check.
    IF audit_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='source-untrusted MDS audit must persist with its receipt';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER a11_v27_source_receipt_db_audit
AFTER INSERT ON admin_fido_mds_source_receipts_staging
FOR EACH ROW EXECUTE FUNCTION a11_v27_audit_source_receipt_insert();

-- For this action only, preserve a committed one-to-one audit linkage.
-- Other historical audit actions retain their previous semantics.
-- Do not let DML rewrite the action/resource/actor or erase evidence.
CREATE FUNCTION a11_v27_protect_source_receipt_audit()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.action='admin.passkey.mds.source.untrusted.receipt' THEN
            RAISE EXCEPTION USING ERRCODE='23514',
                MESSAGE='source-untrusted MDS audit is append-only';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.action='admin.passkey.mds.source.untrusted.receipt'
        OR NEW.action='admin.passkey.mds.source.untrusted.receipt' THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='source-untrusted MDS audit cannot be rebound';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER a11_v27_source_receipt_audit_immutable
BEFORE UPDATE OR DELETE ON admin_access_log
FOR EACH ROW EXECUTE FUNCTION a11_v27_protect_source_receipt_audit();

-- TRUNCATE does not fire DELETE row triggers. Treat truncation as a
-- privileged maintenance operation: deny it whenever source receipts
-- exist. No production DDL/TRUNCATE privilege is granted here.
CREATE FUNCTION a11_v27_guard_source_audit_truncate()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM admin_fido_mds_source_receipts_staging) THEN
        RAISE EXCEPTION USING ERRCODE='23514',
            MESSAGE='source-untrusted MDS audit cannot be truncated';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER a11_v27_source_audit_truncate_guard
BEFORE TRUNCATE ON admin_access_log
FOR EACH STATEMENT EXECUTE FUNCTION a11_v27_guard_source_audit_truncate();
