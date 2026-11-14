package com.lanveriq.server.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Read-only Hibernate mapping of Flyway V18 storage fields. STAGING ONLY:
 * arbitrary DB rows are not WebAuthn assertions or valid registrations.
 * No adapter returns these as CredentialRecord or trusts them for login.
 */
@Entity
@Immutable
@Table(name = "admin_passkey_credential_records_staging")
class AdminPasskeyCredentialRecordStaging {
    @Id
    @Column(name = "credential_id", nullable = false, columnDefinition = "bytea")
    private byte[] credentialId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rp_id", nullable = false, length = 253)
    private String rpId;

    @Column(name = "user_handle", nullable = false, columnDefinition = "bytea")
    private byte[] userHandle;

    @Column(name = "registration_attestation_object", nullable = false,
            columnDefinition = "bytea")
    private byte[] attestationObject;

    @Column(name = "registration_client_data_json", nullable = false,
            columnDefinition = "bytea")
    private byte[] clientDataJson;

    protected AdminPasskeyCredentialRecordStaging() {
    }

    UUID userId() {
        return userId;
    }

    byte[] credentialId() {
        return credentialId == null ? null : credentialId.clone();
    }
}
