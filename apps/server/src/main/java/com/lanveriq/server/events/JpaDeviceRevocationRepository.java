package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

/**
 * The device row is the serialization gate shared with event, heartbeat,
 * inventory and activation. Every call rechecks a current enabled user's
 * security-admin role and exact device group AFTER the device lock. The
 * current user and role/group grant stay row-locked until commit.
 *
 * Revocation is terminal for this device ID: an already-revoked device is
 * idempotent for an authorized admin, never reactivated by this path.
 */
@Repository
final class JpaDeviceRevocationRepository implements DeviceRevocationRepository {
    private final EntityManager manager;

    JpaDeviceRevocationRepository(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public boolean revoke(UUID authenticatedAdminId, UUID deviceId) {
        Objects.requireNonNull(authenticatedAdminId);
        Objects.requireNonNull(deviceId);

        // No enumeration for unknown devices, wrong-group/disabled users or
        // non-security administrators. Never use a claimed group from JSON.
        var rows = manager.createNativeQuery("""
                SELECT d.enrollment_state
                FROM devices AS d
                WHERE d.id=:device_id
                  AND EXISTS (
                      SELECT 1 FROM users AS u
                      JOIN user_role_scopes AS s ON s.user_id=u.id
                      JOIN roles AS r ON r.id=s.role_id
                      WHERE u.id=:admin_id AND u.disabled_at IS NULL
                        AND s.device_group_id=d.group_id
                        AND r.code='owner_security_admin'
                  )
                FOR UPDATE OF d
                """)
                .setParameter("admin_id", authenticatedAdminId)
                .setParameter("device_id", deviceId)
                .getResultList();
        if (rows.size() != 1) {
            throw denied();
        }

        // A separate READ COMMITTED statement obtains a fresh snapshot
        // AFTER waiting for the device lock. Locking BOTH the user and exact
        // role/group grant prevents disable or grant deletion from racing
        // with this audit-bound mutation. A stale principal or prior SELECT
        // is never sufficient to authorize a device revocation.
        var currentGrant = manager.createNativeQuery("""
                SELECT s.user_id
                FROM users AS u
                JOIN user_role_scopes AS s ON s.user_id=u.id
                JOIN roles AS r ON r.id=s.role_id
                JOIN devices AS d ON d.group_id=s.device_group_id
                WHERE d.id=:device_id AND u.id=:admin_id
                  AND u.disabled_at IS NULL
                  AND r.code='owner_security_admin'
                FOR SHARE OF u, s
                """)
                .setParameter("admin_id", authenticatedAdminId)
                .setParameter("device_id", deviceId)
                .getResultList();
        if (currentGrant.size() != 1) {
            throw denied();
        }

        String state = (String) rows.getFirst();
        if ("revoked".equals(state)) {
            return false;
        }
        if (!"active".equals(state) && !"pending_certificate".equals(state)) {
            throw denied();
        }

        // Device-lock first is the same ordering as all ingest paths. A
        // concurrent ingest must complete or fail before this operation
        // proceeds; once this transaction commits, future writes are denied.
        manager.createNativeQuery("""
                UPDATE device_credentials
                SET status='revoked', revoked_at=COALESCE(revoked_at,clock_timestamp())
                WHERE device_id=:device_id
                  AND (status<>'revoked' OR revoked_at IS NULL)
                """)
                .setParameter("device_id", deviceId)
                .executeUpdate();

        int changed = manager.createNativeQuery("""
                UPDATE devices
                SET enrollment_state='revoked',
                    revoked_at=COALESCE(revoked_at,clock_timestamp())
                WHERE id=:device_id AND enrollment_state IN
                    ('pending_certificate','active') AND revoked_at IS NULL
                """)
                .setParameter("device_id", deviceId)
                .executeUpdate();
        if (changed != 1) {
            throw denied();
        }

        // The audit row must commit atomically with all revocations. Audit
        // failures cause the calling Spring @Transactional service to roll
        // back the device and credential changes.
        int audited = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,device_group_id,action,outcome)
                SELECT :admin_id,d.id,d.group_id,'device.revoke','allowed'
                FROM devices AS d WHERE d.id=:device_id
                """)
                .setParameter("admin_id", authenticatedAdminId)
                .setParameter("device_id", deviceId)
                .executeUpdate();
        if (audited != 1) {
            // PostgreSQL BEFORE INSERT triggers may silently suppress a row
            // (RETURN NULL). The device and all credential changes must be
            // rolled back unless exactly one accountable audit was persisted.
            throw denied();
        }
        return true;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Device revocation is not authorized");
    }
}
