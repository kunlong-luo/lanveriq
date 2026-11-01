package com.lanveriq.server.enrollment;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

/**
 * No SQL operation trusts a claimed group or principal from request JSON.
 * The device row lock serializes concurrent registration/activation attempts.
 * All statements join the calling service's Spring JPA transaction.
 */
@Repository
final class JpaDeviceCertificateRegistrationRepository
        implements DeviceCertificateRegistrationRepository {
    private final EntityManager manager;

    JpaDeviceCertificateRegistrationRepository(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public PendingReview lockPendingForScopedAdmin(UUID adminId, UUID deviceId) {
        var rows = manager.createNativeQuery("""
                SELECT d.group_id, r.csr_spki_digest
                FROM devices AS d
                JOIN device_enrollment_csrs AS r ON r.device_id=d.id
                WHERE d.id=:device_id
                  AND d.enrollment_state='pending_certificate'
                  AND d.revoked_at IS NULL
                  AND r.pending_expires_at>clock_timestamp()
                  AND NOT EXISTS (
                      SELECT 1 FROM device_credentials AS c
                      WHERE c.device_id=d.id
                  )
                FOR UPDATE OF d
                """)
                .setParameter("device_id", deviceId)
                .getResultList();
        if (rows.size() != 1) {
            throw denied();
        }
        // READ COMMITTED may reuse a pre-wait snapshot for the original
        // SELECT ... FOR UPDATE. Take a FRESH statement AFTER the device
        // lock and lock both the current user and exact group-role grant.
        // Disabling this user or deleting the grant cannot race a committed
        // certificate review once this transaction holds these SHARE locks.
        var grants = manager.createNativeQuery("""
                SELECT s.user_id
                FROM users AS u
                JOIN user_role_scopes AS s ON s.user_id=u.id
                JOIN roles AS role ON role.id=s.role_id
                JOIN devices AS d ON d.group_id=s.device_group_id
                WHERE d.id=:device_id AND u.id=:admin_id
                  AND u.disabled_at IS NULL
                  AND role.code='owner_security_admin'
                FOR SHARE OF u, s
                """)
                .setParameter("device_id", deviceId)
                .setParameter("admin_id", adminId)
                .getResultList();
        if (grants.size() != 1) {
            throw denied();
        }
        Object[] row = (Object[]) rows.getFirst();
        return new PendingReview(deviceId, (UUID) row[0], (String) row[1]);
    }

    @Override
    public void registerReviewed(UUID adminId, UUID deviceId,
                                 ClientCertificateProfileValidator.VerifiedLeaf leaf) {
        Objects.requireNonNull(leaf);
        int reviewed = manager.createNativeQuery("""
                UPDATE device_enrollment_csrs AS r
                   SET reviewed_by=:admin_id, reviewed_at=clock_timestamp()
                FROM devices AS d
                WHERE r.device_id=:device_id AND d.id=r.device_id
                  AND d.enrollment_state='pending_certificate'
                  AND d.revoked_at IS NULL
                  AND r.pending_expires_at>clock_timestamp()
                  AND r.csr_spki_digest=:spki
                  AND EXISTS (
                      SELECT 1 FROM users AS u
                      JOIN user_role_scopes AS s ON s.user_id=u.id
                      JOIN roles AS role ON role.id=s.role_id
                      WHERE u.id=:admin_id AND u.disabled_at IS NULL
                        AND s.device_group_id=d.group_id
                        AND role.code='owner_security_admin'
                  )
                """)
                .setParameter("device_id", deviceId)
                .setParameter("admin_id", adminId)
                .setParameter("spki", leaf.csrSpkiSha256())
                .executeUpdate();
        if (reviewed != 1) {
            throw denied();
        }

        var inserted = manager.createNativeQuery("""
                INSERT INTO device_credentials(
                    device_id,client_cert_sha256_fingerprint,issuer_id,
                    serial_number,csr_spki_digest,status,issued_at,expires_at)
                SELECT d.id,:fingerprint,:issuer,:serial,:spki,'pending',
                       CAST(:issued AS timestamptz),CAST(:expires AS timestamptz)
                FROM devices AS d
                JOIN device_enrollment_csrs AS r ON r.device_id=d.id
                WHERE d.id=:device_id
                  AND d.enrollment_state='pending_certificate'
                  AND d.revoked_at IS NULL
                  AND r.reviewed_by=:admin_id AND r.reviewed_at IS NOT NULL
                  AND r.pending_expires_at>clock_timestamp()
                  AND r.csr_spki_digest=:spki
                  AND CAST(:issued AS timestamptz)<=clock_timestamp()
                  AND CAST(:expires AS timestamptz)>clock_timestamp()
                  AND NOT EXISTS (
                      SELECT 1 FROM device_credentials AS c WHERE c.device_id=d.id
                  )
                RETURNING id
                """)
                .setParameter("device_id", deviceId)
                .setParameter("admin_id", adminId)
                .setParameter("fingerprint", leaf.fingerprint())
                .setParameter("issuer", leaf.issuerId())
                .setParameter("serial", leaf.serial())
                .setParameter("spki", leaf.csrSpkiSha256())
                .setParameter("issued", leaf.issuedAt().toString())
                .setParameter("expires", leaf.expiresAt().toString())
                .getResultList();
        if (inserted.size() != 1) {
            throw denied();
        }

        int audited = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,device_group_id,action,outcome)
                SELECT :admin_id,d.id,d.group_id,'device_certificate.register','allowed'
                FROM devices AS d WHERE d.id=:device_id
                """)
                .setParameter("admin_id", adminId)
                .setParameter("device_id", deviceId)
                .executeUpdate();
        if (audited != 1) {
            // A BEFORE INSERT trigger can RETURN NULL without SQL error.
            // Refuse to commit review and credential without one audit row.
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Certificate registration is not authorized or expired");
    }
}
