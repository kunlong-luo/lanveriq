package com.lanveriq.server.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read-only JPA mapping: Flyway owns schema and the initial bootstrap flow is not open. */
@Entity
@Immutable
@Table(name = "users")
public class AdminAccount {

    @Id
    private UUID id;

    @Column(name = "normalized_login", nullable = false, length = 190)
    private String normalizedLogin;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected AdminAccount() {
    }

    AdminAccount(UUID id, String normalizedLogin, String passwordHash, Instant disabledAt) {
        this.id = id;
        this.normalizedLogin = normalizedLogin;
        this.passwordHash = passwordHash;
        this.disabledAt = disabledAt;
    }

    public UUID getId() {
        return id;
    }

    public String getNormalizedLogin() {
        return normalizedLogin;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return disabledAt == null;
    }
}
