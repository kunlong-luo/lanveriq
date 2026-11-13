-- Batch A: server-side per-credential epoch identity, not an authorization
-- token, ordered sequence, cert-rotation API or permission to retry a request.
-- PostgreSQL 18 backfills existing credential rows with volatile uuidv7().
ALTER TABLE device_credentials
    ADD COLUMN credential_epoch_id UUID NOT NULL DEFAULT uuidv7();

ALTER TABLE device_credentials
    ADD CONSTRAINT device_credential_epoch_identity_unique UNIQUE (credential_epoch_id);

CREATE FUNCTION enforce_device_credential_epoch_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- An explicit caller-supplied UUID cannot become the epoch.
        NEW.credential_epoch_id := uuidv7();
        RETURN NEW;
    END IF;

    -- Ordinary lifecycle status/revocation updates remain possible, but
    -- neither epoch nor the original certificate's identity may be rebound.
    IF NEW.credential_epoch_id IS DISTINCT FROM OLD.credential_epoch_id
        OR NEW.device_id IS DISTINCT FROM OLD.device_id
        OR NEW.client_cert_sha256_fingerprint
            IS DISTINCT FROM OLD.client_cert_sha256_fingerprint
        OR NEW.issuer_id IS DISTINCT FROM OLD.issuer_id
        OR NEW.serial_number IS DISTINCT FROM OLD.serial_number
        OR NEW.csr_spki_digest IS DISTINCT FROM OLD.csr_spki_digest
    THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'credential epoch and certificate identity cannot be reassigned';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER device_credential_epoch_identity_guard
BEFORE INSERT OR UPDATE ON device_credentials
FOR EACH ROW
EXECUTE FUNCTION enforce_device_credential_epoch_identity();
