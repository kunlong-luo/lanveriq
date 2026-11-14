package com.lanveriq.server.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Read-only Flyway V16 JPA mapping of a stable, opaque WebAuthn user handle.
 * This is NOT a WebAuthn registration endpoint, nor a credential validator.
 * An authenticated + verified registration flow must separately create rows
 * after policy review; no production mutation API is present.
 */
@Entity
@Immutable
@Table(name = "admin_passkey_user_handles")
class AdminPasskeyUserHandle {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_handle", nullable = false, columnDefinition = "bytea")
    private byte[] userHandle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminPasskeyUserHandle() {
    }

    UUID userId() {
        return userId;
    }

    byte[] userHandle() {
        return userHandle == null ? null : userHandle.clone();
    }
}
