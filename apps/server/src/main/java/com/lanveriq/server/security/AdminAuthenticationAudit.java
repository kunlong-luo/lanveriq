package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only login/logout trace. Never persist passwords, submitted login
 * names, cookies, CSRF secrets, source IPs or forwarded client headers.
 * Fail closed if the database is unable to record a successful authentication.
 */
@Service
public class AdminAuthenticationAudit {

    private final EntityManager entityManager;

    AdminAuthenticationAudit(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginAllowed(UUID authenticatedUserId) {
        recordKnown(authenticatedUserId, "login");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logoutAllowed(UUID authenticatedUserId) {
        recordKnown(authenticatedUserId, "logout");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginDenied() {
        entityManager.createNativeQuery("""
                INSERT INTO admin_auth_log (action, outcome)
                VALUES ('login', 'denied')
                """).executeUpdate();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginThrottled() {
        entityManager.createNativeQuery("""
                INSERT INTO admin_auth_log (action, outcome)
                VALUES ('login', 'rate_limited')
                """).executeUpdate();
    }

    private void recordKnown(UUID userId, String action) {
        Objects.requireNonNull(userId, "userId");
        if (!"login".equals(action) && !"logout".equals(action)) {
            throw new IllegalArgumentException("Invalid authentication audit action");
        }
        entityManager.createNativeQuery("""
                INSERT INTO admin_auth_log (principal_id, action, outcome)
                VALUES (:principal, :action, 'allowed')
                """)
                .setParameter("principal", userId)
                .setParameter("action", action)
                .executeUpdate();
    }
}
