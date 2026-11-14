-- A11-D2-D1: official CredentialRecord-shaped persistence foundation ONLY.
-- Never read these rows as authenticated WebAuthn evidence: no trusted
-- attestation/registration ceremony, verifier, challenge consumer or signer
-- has been enabled. Test fixtures may contain arbitrary synthetic bytea.
-- The V16 inventory and immutable ownership/pinning remain authoritative.
ALTER TABLE admin_passkey_credentials
    ADD CONSTRAINT admin_passkey_credential_owner_rp_unique
    UNIQUE (credential_id,user_id,rp_id);
ALTER TABLE admin_passkey_user_handles
    ADD CONSTRAINT admin_passkey_handle_owner_unique
    UNIQUE (user_id,user_handle);

-- Spring Security CredentialRecord fields not present in V16, staged
-- separately so existing V16 synthetic inventory is NEVER backfilled into
-- "registered" status. Future real registration must also independently
-- verify server challenge, attestation, RP and HTTPS origin before INSERT.
CREATE TABLE admin_passkey_credential_records_staging (
    credential_id BYTEA PRIMARY KEY,
    user_id UUID NOT NULL,
    rp_id VARCHAR(253) NOT NULL,
    user_handle BYTEA NOT NULL CHECK (octet_length(user_handle)=32),
    registration_attestation_object BYTEA NOT NULL
        CHECK (octet_length(registration_attestation_object) BETWEEN 64 AND 32768),
    registration_client_data_json BYTEA NOT NULL
        CHECK (octet_length(registration_client_data_json) BETWEEN 32 AND 16384),
    uv_initialized BOOLEAN NOT NULL CHECK (uv_initialized),
    credential_type VARCHAR(32) NOT NULL DEFAULT 'public-key'
        CHECK (credential_type='public-key'),
    transports TEXT[] NOT NULL DEFAULT '{}'::TEXT[]
        CHECK (transports <@ ARRAY['usb','nfc','ble','internal','hybrid','smart-card']::TEXT[]),
    display_label VARCHAR(128) NOT NULL
        CHECK (length(trim(display_label)) BETWEEN 1 AND 128),
    registration_tracking_id UUID NOT NULL UNIQUE,
    registered_session_version BIGINT NOT NULL
        CHECK (registered_session_version>=0),
    captured_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT admin_passkey_record_credential_binding
        FOREIGN KEY (credential_id,user_id,rp_id)
        REFERENCES admin_passkey_credentials(credential_id,user_id,rp_id),
    CONSTRAINT admin_passkey_record_handle_binding
        FOREIGN KEY (user_id,user_handle)
        REFERENCES admin_passkey_user_handles(user_id,user_handle)
);

-- Synthetic and incomplete records may be inserted in PG18 tests. These
-- immutable staging records alone are NEVER allowed to authenticate.
CREATE FUNCTION reject_admin_passkey_record_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='passkey credential record staging history is immutable';
END;
$$;
CREATE TRIGGER admin_passkey_record_staging_immutable
BEFORE UPDATE OR DELETE ON admin_passkey_credential_records_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

-- V16 sign_count remains the separate *latest* observed server value. It must
-- be compared and advanced with CAS against that latest value after a fully
-- validated assertion, never against registration attestation's signCount.
-- For 0/0 synced Passkeys, signCount cannot prove freshness; only an actual
-- signed, expiring one-use server challenge can do so. No such verifier wired.
