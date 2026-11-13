-- Batch A: once a heartbeat UUID is committed, its certificate attribution
-- and replay key must not be rewritten, including legacy V9 NULL attribution.
-- V10 already binds new accepted rows to the submitting mTLS fingerprint.
-- This migration is additive and leaves all existing ledger rows unchanged.
-- It deliberately does NOT purge or expire request IDs: no authenticated
-- epoch/lifetime protocol exists yet, so age-based deletion would reopen
-- replay windows. Device erasure/cascade behavior remains unchanged.
CREATE FUNCTION reject_heartbeat_request_ledger_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        MESSAGE = 'committed heartbeat request ledger rows are immutable';
END;
$$;

CREATE TRIGGER heartbeat_request_ledger_immutable
BEFORE UPDATE ON device_heartbeat_request_ids
FOR EACH ROW
EXECUTE FUNCTION reject_heartbeat_request_ledger_update();
