-- Only a request committed with the current exact mTLS certificate may be
-- confirmed by the read-only lookup. V9 rows have no trustworthy certificate
-- attribution, so they remain NULL and cannot yield a positive lookup.
-- Preserve the original (device_id, request_id) primary key across rotations:
-- a different certificate cannot make an old UUID safe to replay.
ALTER TABLE device_heartbeat_request_ids
    ADD COLUMN client_cert_sha256_fingerprint CHAR(64);

ALTER TABLE device_heartbeat_request_ids
    ADD CONSTRAINT heartbeat_request_certificate_fingerprint_hex
    CHECK (client_cert_sha256_fingerprint IS NULL
           OR client_cert_sha256_fingerprint ~ '^[0-9a-f]{64}$');

-- PostgreSQL enforces a NOT VALID CHECK for new rows and updates, while
-- grandfathering V9 rows whose submitting certificate cannot be recovered.
ALTER TABLE device_heartbeat_request_ids
    ADD CONSTRAINT heartbeat_request_certificate_fingerprint_required
    CHECK (client_cert_sha256_fingerprint IS NOT NULL) NOT VALID;
