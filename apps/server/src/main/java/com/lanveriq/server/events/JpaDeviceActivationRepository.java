package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Pending certificate proof -> active identity. Registration/leaf/CA checks
 * MUST happen in a distinct reviewed admin workflow before this internal call.
 * An untrusted HTTP header must never become VerifiedDeviceCertificate.
 */
@Repository
final class JpaDeviceActivationRepository implements DeviceActivationRepository {

    private final EntityManager entityManager;

    JpaDeviceActivationRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public void activate(VerifiedDeviceCertificate verifiedPeer, UUID deviceId) {
        Objects.requireNonNull(verifiedPeer);
        if (!verifiedPeer.deviceId().equals(deviceId)) {
            throw denied();
        }

        // Every attempt locks the canonical device, including retries. A
        // pending identity is usable only when an operator-reviewed CSR and
        // an unexpired registered pending credential agree on the SPKI digest.
        // The branch for an already-active identical credential permits safe
        // retries, not activating arbitrary replacement pending credentials.
        List<?> bound = entityManager.createNativeQuery("""
                SELECT d.enrollment_state, c.status
                FROM devices AS d
                JOIN device_credentials AS c ON c.device_id=d.id
                JOIN device_enrollment_csrs AS r ON r.device_id=d.id
                WHERE d.id=:device_id
                  AND c.client_cert_sha256_fingerprint=:fingerprint
                  AND d.revoked_at IS NULL AND c.revoked_at IS NULL
                  AND c.issued_at<=clock_timestamp()
                  AND c.expires_at>clock_timestamp()
                  AND c.csr_spki_digest=r.csr_spki_digest
                  AND r.reviewed_by IS NOT NULL AND r.reviewed_at IS NOT NULL
                  AND (
                    (d.enrollment_state='pending_certificate'
                        AND c.status='pending'
                        AND r.pending_expires_at>clock_timestamp())
                    OR (d.enrollment_state='active' AND c.status='active')
                  )
                FOR UPDATE OF d
                """)
                .setParameter("device_id", deviceId)
                .setParameter("fingerprint", verifiedPeer.sha256Fingerprint())
                .getResultList();
        if (bound.size() != 1) {
            throw denied();
        }
        Object[] state = (Object[]) bound.getFirst();
        if ("active".equals(state[0]) && "active".equals(state[1])) {
            // Already committed, same registered certificate, no writes.
            return;
        }
        if (!"pending_certificate".equals(state[0]) || !"pending".equals(state[1])) {
            throw denied();
        }

        // Conditional updates are both inside the same service transaction.
        // If a concurrent credential revocation wins before this UPDATE,
        // zero affected rows cause complete rollback of this activation.
        int activated = entityManager.createNativeQuery("""
                UPDATE device_credentials
                SET status='active'
                WHERE device_id=:device_id
                  AND client_cert_sha256_fingerprint=:fingerprint
                  AND status='pending' AND revoked_at IS NULL
                  AND issued_at<=clock_timestamp()
                  AND expires_at>clock_timestamp()
                """)
                .setParameter("device_id", deviceId)
                .setParameter("fingerprint", verifiedPeer.sha256Fingerprint())
                .executeUpdate();
        if (activated != 1) {
            throw denied();
        }
        int enrolled = entityManager.createNativeQuery("""
                UPDATE devices
                SET enrollment_state='active',
                    enrolled_at=COALESCE(enrolled_at, clock_timestamp())
                WHERE id=:device_id AND enrollment_state='pending_certificate'
                  AND revoked_at IS NULL
                """)
                .setParameter("device_id", deviceId)
                .executeUpdate();
        if (enrolled != 1) {
            throw denied();
        }
    }

    private static DeviceActivationRejection denied() {
        return new DeviceActivationRejection(DeviceActivationRejection.Reason.DENIED);
    }
}
