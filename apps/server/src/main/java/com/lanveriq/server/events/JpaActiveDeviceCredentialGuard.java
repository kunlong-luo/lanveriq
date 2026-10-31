package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;

/**
 * One transaction-bound authorization gate for V0.1 device writes.
 *
 * Always lock the canonical device FIRST, then the exact registered client
 * certificate. This establishes one lock order across inventory, heartbeat
 * and event paths, and serializes certificate status/expiry changes with the
 * downstream writes. Never accept a fingerprint from HTTP request data.
 *
 * The calling Spring service MUST run inside @Transactional. Only PostgreSQL
 * and Hibernate/JPA are used; there is no JDBC or public HTTP entry point.
 */
final class JpaActiveDeviceCredentialGuard {

    private JpaActiveDeviceCredentialGuard() {
    }

    static boolean lock(EntityManager manager, VerifiedDeviceCertificate identity, UUID deviceId) {
        Objects.requireNonNull(manager);
        if (identity == null || deviceId == null || !deviceId.equals(identity.deviceId())) {
            return false;
        }

        // Use separate statements so the credential query gets a fresh
        // READ COMMITTED snapshot after any device-lock wait.
        var device = manager.createNativeQuery("""
                SELECT d.id FROM devices AS d
                WHERE d.id=:device_id AND d.enrollment_state='active'
                  AND d.revoked_at IS NULL
                FOR UPDATE OF d
                """)
                .setParameter("device_id", deviceId)
                .getResultList();
        if (device.size() != 1) {
            return false;
        }

        // This row lock is retained until the enclosing service transaction
        // commits or rolls back, so a concurrent credential revocation must
        // serialize with the current heartbeat/event/inventory operation.
        var certificate = manager.createNativeQuery("""
                SELECT c.id FROM device_credentials AS c
                WHERE c.device_id=:device_id
                  AND c.client_cert_sha256_fingerprint=:fingerprint
                  AND c.status='active' AND c.revoked_at IS NULL
                  AND c.issued_at<=clock_timestamp()
                  AND c.expires_at>clock_timestamp()
                FOR UPDATE OF c
                """)
                .setParameter("device_id", deviceId)
                .setParameter("fingerprint", identity.sha256Fingerprint())
                .getResultList();
        return certificate.size() == 1;
    }
}
