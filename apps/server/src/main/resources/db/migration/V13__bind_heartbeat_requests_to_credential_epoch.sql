-- V13: bind every NEW accepted request UUID to the server-owned V12
-- credential identity. Previously committed V9-V12 rows are grandfathered:
-- certificate/epoch provenance cannot be safely reconstructed retroactively.
-- V9 (device_id,request_id) unique replay key and V11 immutable UPDATE
-- protection remain unchanged; there is NO automatic TTL or retention purge.
ALTER TABLE device_heartbeat_request_ids
    ADD COLUMN credential_epoch_id UUID;

ALTER TABLE device_heartbeat_request_ids
    ADD CONSTRAINT heartbeat_request_credential_epoch_required
    CHECK (credential_epoch_id IS NOT NULL) NOT VALID;

CREATE FUNCTION bind_heartbeat_request_credential_epoch()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    canonical_epoch UUID;
BEGIN
    -- Lock order mirrors the JPA write path: device first, credential second.
    -- This database trigger is a consistency check, NOT proof of mTLS.
    PERFORM d.id FROM devices d
    WHERE d.id=NEW.device_id
      AND d.enrollment_state='active'
      AND d.revoked_at IS NULL
    FOR SHARE OF d;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'heartbeat request device is not active';
    END IF;

    SELECT c.credential_epoch_id INTO canonical_epoch
    FROM device_credentials c
    WHERE c.device_id=NEW.device_id
      AND c.client_cert_sha256_fingerprint=NEW.client_cert_sha256_fingerprint
      AND c.status='active'
      AND c.revoked_at IS NULL
      AND c.issued_at<=clock_timestamp()
      AND c.expires_at>clock_timestamp()
    FOR SHARE OF c;
    IF canonical_epoch IS NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'heartbeat request certificate is not active';
    END IF;
    IF NEW.credential_epoch_id IS NOT NULL
       AND NEW.credential_epoch_id IS DISTINCT FROM canonical_epoch THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'heartbeat request epoch does not belong to submitting certificate';
    END IF;
    -- Server source of truth, not a value from JSON/header/Agent clock.
    NEW.credential_epoch_id := canonical_epoch;
    RETURN NEW;
END;
$$;

CREATE TRIGGER heartbeat_request_bind_credential_epoch
BEFORE INSERT ON device_heartbeat_request_ids
FOR EACH ROW
EXECUTE FUNCTION bind_heartbeat_request_credential_epoch();
