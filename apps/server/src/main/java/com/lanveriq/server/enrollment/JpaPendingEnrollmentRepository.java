package com.lanveriq.server.enrollment;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

/**
 * All statements run in one Spring-managed transaction from PendingEnrollmentService.
 * The conditional UPDATE takes a PostgreSQL row lock and is the sole consumption CAS.
 * Any later failed device/CSR INSERT must roll back the token UPDATE.
 */
@Repository
final class JpaPendingEnrollmentRepository implements PendingEnrollmentRepository {

    private final EntityManager entityManager;

    JpaPendingEnrollmentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PendingDevice consumeAndRegister(
            byte[] verifier, long policyNoticeVersion,
            CertificateSigningRequestValidator.ValidatedCsr csr) {

        int consumed = entityManager.createNativeQuery("""
                UPDATE enrollment_tokens
                   SET consumed_at = CURRENT_TIMESTAMP
                 WHERE token_sha256 = :digest
                   AND consumed_at IS NULL
                   AND expires_at > CURRENT_TIMESTAMP
                   AND policy_notice_version = :notice
                """)
                .setParameter("digest", verifier)
                .setParameter("notice", policyNoticeVersion)
                .executeUpdate();

        if (consumed != 1) {
            // Same external failure for unknown, expired, used or wrong-notice tokens.
            throw new AccessDeniedException("Enrollment authorization is invalid or expired");
        }

        UUID groupId = (UUID) entityManager.createNativeQuery("""
                SELECT group_id FROM enrollment_tokens WHERE token_sha256 = :digest
                """)
                .setParameter("digest", verifier)
                .getSingleResult();

        UUID deviceId = (UUID) entityManager.createNativeQuery("""
                INSERT INTO devices (group_id, enrollment_state)
                VALUES (:group_id, 'pending_certificate')
                RETURNING id
                """)
                .setParameter("group_id", groupId)
                .getSingleResult();

        entityManager.createNativeQuery("""
                INSERT INTO device_enrollment_csrs
                    (device_id, csr_pem_public, csr_sha256_digest,
                     csr_spki_digest, pending_expires_at)
                VALUES
                    (:device_id, :csr_pem, :csr_sha, :spki_sha,
                     CURRENT_TIMESTAMP + INTERVAL '24 hours')
                """)
                .setParameter("device_id", deviceId)
                .setParameter("csr_pem", csr.publicPem())
                .setParameter("csr_sha", csr.csrSha256Digest())
                .setParameter("spki_sha", csr.spkiSha256Digest())
                .executeUpdate();

        return new PendingDevice(deviceId, groupId);
    }
}
