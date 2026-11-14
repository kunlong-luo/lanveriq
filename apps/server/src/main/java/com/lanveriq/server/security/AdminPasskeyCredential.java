package com.lanveriq.server.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Read-only V16 credential inventory for the future official WebAuthn adapter.
 * No credential can be registered, verified, revoked or used for an approval
 * through this entity. COSE validation and challenge consumption are NOT
 * implemented; a database row alone must never authenticate a principal.
 */
@Entity
@Immutable
@Table(name = "admin_passkey_credentials")
class AdminPasskeyCredential {

    @Id
    @Column(name = "credential_id", nullable = false, columnDefinition = "bytea")
    private byte[] credentialId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rp_id", nullable = false, length = 253)
    private String rpId;

    @Column(name = "credential_public_key_cose", nullable = false,
            columnDefinition = "bytea")
    private byte[] credentialPublicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    protected AdminPasskeyCredential() {
    }

    byte[] credentialId() {
        return credentialId == null ? null : credentialId.clone();
    }

    UUID userId() {
        return userId;
    }

    boolean revoked() {
        return revokedAt != null;
    }
}
