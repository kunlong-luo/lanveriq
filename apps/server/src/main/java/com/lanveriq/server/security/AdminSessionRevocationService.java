package com.lanveriq.server.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * No HTTP route: an approved console handler will obtain the authenticated
 * owner from the server SecurityContext and enforce CSRF/Origin/rate limits.
 */
@Service
public class AdminSessionRevocationService {

    private final AdminSessionRevocationRepository repository;

    AdminSessionRevocationService(AdminSessionRevocationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional
    public long revokeAll(UUID targetUserId, UUID deviceGroupId) {
        Objects.requireNonNull(targetUserId);
        Objects.requireNonNull(deviceGroupId);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal principal)
                || !principal.isEnabled()) {
            throw new AccessDeniedException("Authenticated security administrator required");
        }
        return repository.rotateAllSessions(principal.userId(), targetUserId, deviceGroupId);
    }
}
