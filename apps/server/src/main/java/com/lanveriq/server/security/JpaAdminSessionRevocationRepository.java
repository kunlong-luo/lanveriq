package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL atomically requires an active owner assigned to the SAME group
 * as the target user, increments the target session_version and writes an
 * accountable audit entry inside the caller's Spring transaction.
 */
@Repository
final class JpaAdminSessionRevocationRepository implements AdminSessionRevocationRepository {

    private final EntityManager entityManager;

    JpaAdminSessionRevocationRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public long rotateAllSessions(UUID authenticatedOwnerId, UUID targetUserId, UUID deviceGroupId) {
        Objects.requireNonNull(authenticatedOwnerId);
        Objects.requireNonNull(targetUserId);
        Objects.requireNonNull(deviceGroupId);

        List<?> updated = entityManager.createNativeQuery("""
                UPDATE users AS target
                   SET session_version = target.session_version + 1
                 WHERE target.id = :target_id
                   AND target.session_version < 9223372036854775807
                   AND EXISTS (
                       SELECT 1 FROM user_role_scopes AS assigned
                        WHERE assigned.user_id = target.id
                          AND assigned.device_group_id = :group_id
                   )
                   AND EXISTS (
                       SELECT 1 FROM users AS issuer
                       JOIN user_role_scopes AS s ON s.user_id = issuer.id
                       JOIN roles AS r ON r.id = s.role_id
                       WHERE issuer.id = :issuer_id
                         AND issuer.disabled_at IS NULL
                         AND s.device_group_id = :group_id
                         AND r.code = 'owner_security_admin'
                   )
                RETURNING target.session_version
                """)
                .setParameter("target_id", targetUserId)
                .setParameter("issuer_id", authenticatedOwnerId)
                .setParameter("group_id", deviceGroupId)
                .getResultList();

        if (updated.size() != 1) {
            // Do not reveal if the target UUID or group membership exists.
            throw new AccessDeniedException("Session revocation not authorized");
        }
        entityManager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id, resource_id, device_group_id, action, outcome)
                VALUES (:issuer_id, :target_id, :group_id, 'admin.sessions.revoke', 'allowed')
                """)
                .setParameter("issuer_id", authenticatedOwnerId)
                .setParameter("target_id", targetUserId)
                .setParameter("group_id", deviceGroupId)
                .executeUpdate();

        return ((Number) updated.getFirst()).longValue();
    }
}
