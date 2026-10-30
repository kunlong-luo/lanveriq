package com.lanveriq.server.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * No global ROLE_ADMIN authority: every action is constrained by an active
 * database group membership and (where needed) data-category grant.
 */
public final class AdminPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
    private final boolean enabled;
    private transient String passwordHash;

    AdminPrincipal(UUID userId, String username, String passwordHash, boolean enabled) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.enabled = enabled;
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "AdminPrincipal[userId=" + userId + ", password=REDACTED]";
    }
}
