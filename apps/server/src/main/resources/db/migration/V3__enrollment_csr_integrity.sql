-- Device key continuity: reuse of any previously enrolled CSR key is invalid.
-- Never promote a CSR's requested subject/SAN to the canonical device identity.
CREATE UNIQUE INDEX device_enrollment_csrs_unique_spki
    ON device_enrollment_csrs (csr_spki_digest);

ALTER TABLE device_enrollment_csrs
    ADD CONSTRAINT csr_digest_hex
        CHECK (csr_sha256_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT csr_spki_hex
        CHECK (csr_spki_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT csr_public_pem_bounded
        CHECK (octet_length(csr_pem_public) BETWEEN 1 AND 16384),
    ADD CONSTRAINT csr_pending_expiration_after_request
        CHECK (pending_expires_at > requested_at);
