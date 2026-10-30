package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminAction;
import com.lanveriq.server.security.AdminPrincipal;
import com.lanveriq.server.security.AdminScopeService;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Future console-only entrypoint: never accepts a caller-supplied user ID.
 * No HTTP mapping is exposed while TLS/session/CSRF/rate controls are pending.
 */
@Service
public final class AuthenticatedEnrollmentTokenIssuer {

    private final AdminScopeService scopes;
    private final EnrollmentTokenIssuer issuer;

    AuthenticatedEnrollmentTokenIssuer(AdminScopeService scopes, EnrollmentTokenIssuer issuer) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
    }

    public EnrollmentTokenIssuer.IssuedToken issue(UUID groupId, long noticeVersion, Duration ttl) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof AdminPrincipal principal) ||
                !principal.isEnabled()) {
            throw new AccessDeniedException("Authenticated administrator required");
        }
        scopes.require(principal.userId(), groupId, AdminAction.ISSUE_ENROLLMENT);
        // Repository independently checks the same group's current owner role.
        return issuer.issue(principal.userId(), groupId, noticeVersion, ttl);
    }
}
