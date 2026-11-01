package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL read-committed, single-transaction bootstrap.
 * Lock the stable, Flyway-seeded owner role row BEFORE checking for existing
 * users. This serializes concurrent first-run attempts without a custom lock
 * table, and the second transaction sees the first committed administrator.
 * All inserts and the audit entry roll back on any error.
 */
@Repository
class JpaFirstAdminBootstrapRepository implements FirstAdminBootstrapRepository {

    private final EntityManager entityManager;

    JpaFirstAdminBootstrapRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public BootstrappedAdmin createFirstOwner(String normalizedLogin, String passwordHash,
                                              String groupName) {
        UUID roleId = (UUID) entityManager.createNativeQuery("""
                SELECT id FROM roles
                 WHERE code = 'owner_security_admin'
                 FOR UPDATE
                """).getSingleResult();

        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM users
                """).getSingleResult();
        if (count.longValue() != 0) {
            // Never create a recovery/backdoor account if *any* user exists,
            // including disabled accounts. Recovery requires a separate plan.
            throw new IllegalStateException("Initial administrator already exists");
        }

        UUID groupId = (UUID) entityManager.createNativeQuery("""
                INSERT INTO device_groups (name)
                VALUES (:group_name)
                RETURNING id
                """).setParameter("group_name", groupName).getSingleResult();

        UUID adminId = (UUID) entityManager.createNativeQuery("""
                INSERT INTO users (normalized_login, password_hash)
                VALUES (:login, :hash)
                RETURNING id
                """)
                .setParameter("login", normalizedLogin)
                .setParameter("hash", passwordHash)
                .getSingleResult();

        int assigned = entityManager.createNativeQuery("""
                INSERT INTO user_role_scopes (user_id, role_id, device_group_id)
                VALUES (:user_id, :role_id, :group_id)
                """)
                .setParameter("user_id", adminId)
                .setParameter("role_id", roleId)
                .setParameter("group_id", groupId)
                .executeUpdate();
        if (assigned != 1) {
            // An insert suppressed by a database trigger must never create
            // an initial administrator without the required group role.
            throw new IllegalStateException("Initial administrator grant was not persisted");
        }

        int audited = entityManager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id, resource_id, device_group_id, action, outcome)
                VALUES (:user_id, :user_id, :group_id, 'administrator.bootstrap', 'allowed')
                """)
                .setParameter("user_id", adminId)
                .setParameter("group_id", groupId)
                .executeUpdate();
        if (audited != 1) {
            // A silently dropped audit must roll back user, role and group.
            throw new IllegalStateException("Initial administrator audit was not persisted");
        }

        // Never create an internal_operational grant implicitly.
        return new BootstrappedAdmin(adminId, groupId);
    }
}
