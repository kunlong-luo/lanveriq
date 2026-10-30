package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Rechecks DB permissions on every call. A stale session role snapshot never
 * grants device-group access. SQL fragments are fixed by enum, not input.
 */
@Repository
final class JpaAdminScopeRepository implements AdminScopeRepository {

    private final EntityManager entityManager;

    JpaAdminScopeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean hasCurrentGrant(UUID authenticatedAdminId, UUID deviceGroupId,
                                   AdminAction action) {
        Objects.requireNonNull(authenticatedAdminId, "authenticatedAdminId");
        Objects.requireNonNull(deviceGroupId, "deviceGroupId");
        Objects.requireNonNull(action, "action");

        String categoryCheck = action.requiresInternalOperational() ? """
                  AND EXISTS (
                      SELECT 1 FROM user_data_grants AS g
                      WHERE g.user_id = :user_id
                        AND g.device_group_id = :group_id
                        AND g.classification = 'internal_operational'
                        AND g.revoked_at IS NULL
                  )
                """ : "";

        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM users AS u
                    JOIN user_role_scopes AS s ON s.user_id = u.id
                    JOIN roles AS r ON r.id = s.role_id
                    WHERE u.id = :user_id
                      AND u.disabled_at IS NULL
                      AND s.device_group_id = :group_id
                      AND r.code IN (:allowed_roles)
                """ + categoryCheck + """
                )
                """;

        Object result = entityManager.createNativeQuery(sql)
                .setParameter("user_id", authenticatedAdminId)
                .setParameter("group_id", deviceGroupId)
                .setParameter("allowed_roles", action.allowedRoleCodes())
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
