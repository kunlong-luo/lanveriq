package com.lanveriq.server.enrollment;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

/**
 * Narrow PostgreSQL-native JPA operation. DB performs the group/role check and
 * expiry clock calculation; app never uses JdbcTemplate or raw JDBC.
 *
 * All writes (including the access audit) join the calling Spring transaction.
 */
@Repository
final class JpaEnrollmentTokenRepository implements EnrollmentTokenRepository {

    private final EntityManager entityManager;

    JpaEnrollmentTokenRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public UUID insertForScopedAdmin(
            UUID authenticatedAdminId,
            UUID deviceGroupId,
            long policyNoticeVersion,
            int lifetimeSeconds,
            byte[] tokenVerifier) {
        Objects.requireNonNull(authenticatedAdminId, "authenticatedAdminId");
        Objects.requireNonNull(deviceGroupId, "deviceGroupId");

        // Owner/security-admin is the only initially permitted issuer. Delegated
        // IT issuance requires a later explicitly approved permission grant.
        String sql = """
                INSERT INTO enrollment_tokens
                    (token_sha256, group_id, policy_notice_version, expires_at, created_by)
                SELECT :digest, :group_id, :policy_version,
                       CURRENT_TIMESTAMP + (:lifetime * INTERVAL '1 second'), :admin_id
                WHERE EXISTS (
                    SELECT 1 FROM users AS u
                    JOIN user_role_scopes AS s ON s.user_id = u.id
                    JOIN roles AS r ON r.id = s.role_id
                    WHERE u.id = :admin_id
                      AND u.disabled_at IS NULL
                      AND s.device_group_id = :group_id
                      AND r.code = 'owner_security_admin'
                )
                RETURNING id
                """;

        var query = entityManager.createNativeQuery(sql)
                .setParameter("digest", tokenVerifier)
                .setParameter("group_id", deviceGroupId)
                .setParameter("policy_version", policyNoticeVersion)
                .setParameter("lifetime", lifetimeSeconds)
                .setParameter("admin_id", authenticatedAdminId);
        java.util.List<?> issuedRows = query.getResultList();
        if (issuedRows.isEmpty()) {
            throw new AccessDeniedException("Enrollment token creation not authorized");
        }

        UUID tokenId = (UUID) issuedRows.getFirst();
        entityManager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id, resource_id, device_group_id, action, outcome)
                VALUES (:admin_id, :token_id, :group_id, 'enrollment_token.issue', 'allowed')
                """)
                .setParameter("admin_id", authenticatedAdminId)
                .setParameter("token_id", tokenId)
                .setParameter("group_id", deviceGroupId)
                .executeUpdate();
        return tokenId;
    }
}
