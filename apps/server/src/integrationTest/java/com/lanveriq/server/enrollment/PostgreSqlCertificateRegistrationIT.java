package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Opt-in synthetic PostgreSQL-only registration transaction tests. The leaf
 * metadata is a fixture; the real PKIX validation lives in the unit suite.
 * No certificate authority, admin HTTP login or Agent ingress is created.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false",
        "lanveriq.client-cert-registration.enabled=false"
})
class PostgreSqlCertificateRegistrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_certificate_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private JpaDeviceCertificateRegistrationRepository registration;

    @Autowired
    private EntityManager manager;

    @Autowired
    private PlatformTransactionManager txManager;

    private UUID deviceId;
    private UUID adminId;
    private UUID groupId;
    private String csrSpkiSha256;

    private TransactionTemplate transaction() {
        return new TransactionTemplate(txManager);
    }

    @BeforeEach
    void createIsolatedSyntheticPendingEnrollmentAndOwner() {
        // Flyway V3 forbids reusing a CSR public key between test devices.
        csrSpkiSha256 = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        transaction().executeWithoutResult(ignored -> {
            groupId = (UUID) manager.createNativeQuery("""
                    INSERT INTO device_groups (name) VALUES(:name) RETURNING id
                    """).setParameter("name", "registration-" + UUID.randomUUID())
                    .getSingleResult();
            adminId = (UUID) manager.createNativeQuery("""
                    INSERT INTO users(normalized_login,password_hash)
                    VALUES(:login,'synthetic-not-an-authentication-secret') RETURNING id
                    """).setParameter("login", "registration." + UUID.randomUUID())
                    .getSingleResult();
            manager.createNativeQuery("""
                    INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                    SELECT :admin, id, :group FROM roles
                    WHERE code='owner_security_admin'
                    """).setParameter("admin", adminId).setParameter("group", groupId)
                    .executeUpdate();
            deviceId = (UUID) manager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES(:group,'pending_certificate') RETURNING id
                    """).setParameter("group", groupId).getSingleResult();
            manager.createNativeQuery("""
                    INSERT INTO device_enrollment_csrs
                        (device_id,csr_pem_public,csr_sha256_digest,
                         csr_spki_digest,pending_expires_at)
                    VALUES (:device,'synthetic-csr-public-only',:sha,:spki,
                            clock_timestamp()+INTERVAL '1 hour')
                    """).setParameter("device", deviceId)
                    .setParameter("sha", "a".repeat(64))
                    .setParameter("spki", csrSpkiSha256).executeUpdate();
        });
    }

    private ClientCertificateProfileValidator.VerifiedLeaf fakeValidatedLeaf() {
        return new ClientCertificateProfileValidator.VerifiedLeaf(
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "a".repeat(64), UUID.randomUUID().toString(), csrSpkiSha256,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(48 * 3600));
    }

    private long count(String table) {
        String sql = switch (table) {
            case "credentials" -> "SELECT COUNT(*) FROM device_credentials WHERE device_id=:id";
            case "audit" -> "SELECT COUNT(*) FROM admin_access_log WHERE resource_id=:id";
            default -> throw new IllegalArgumentException("Unknown synthetic count");
        };
        return ((Number) manager.createNativeQuery(sql)
                .setParameter("id", deviceId).getSingleResult()).longValue();
    }

    @Test
    void scopedOwnerReviewsAndRegistersOnlyPendingCredentialWithAudit() {
        var signed = fakeValidatedLeaf();
        transaction().executeWithoutResult(ignored -> {
            var pending = registration.lockPendingForScopedAdmin(adminId, deviceId);
            assertEquals(groupId, pending.groupId());
            assertEquals(csrSpkiSha256, pending.csrSpkiSha256());
            registration.registerReviewed(adminId, deviceId, signed);
        });
        assertEquals(1L, count("credentials"));
        assertEquals(1L, count("audit"));
        String state = (String) manager.createNativeQuery("""
                SELECT enrollment_state FROM devices WHERE id=:id
                """).setParameter("id", deviceId).getSingleResult();
        assertEquals("pending_certificate", state);
        String credentialState = (String) manager.createNativeQuery("""
                SELECT status FROM device_credentials WHERE device_id=:id
                """).setParameter("id", deviceId).getSingleResult();
        assertEquals("pending", credentialState);
        UUID reviewer = (UUID) manager.createNativeQuery("""
                SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                """).setParameter("id", deviceId).getSingleResult();
        assertEquals(adminId, reviewer);
    }

    @Test
    void unscopedAdminCannotReviewOrCreateCertificate() {
        assertThrows(AccessDeniedException.class, () ->
                transaction().executeWithoutResult(ignored ->
                        registration.lockPendingForScopedAdmin(UUID.randomUUID(), deviceId)));
        assertEquals(0L, count("credentials"));
        assertEquals(0L, count("audit"));
    }

    @Test
    void failedTransactionRevertsReviewCertificateAndAuditTogether() {
        assertThrows(IllegalStateException.class, () ->
                transaction().executeWithoutResult(ignored -> {
                    registration.lockPendingForScopedAdmin(adminId, deviceId);
                    registration.registerReviewed(adminId, deviceId, fakeValidatedLeaf());
                    throw new IllegalStateException("synthetic failure after all inserts");
                }));
        assertEquals(0L, count("credentials"));
        assertEquals(0L, count("audit"));
        Object reviewer = manager.createNativeQuery("""
                SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                """).setParameter("id", deviceId).getSingleResult();
        assertNull(reviewer);
    }

    @Test
    void silentlyDroppedAuditInsertRollsBackCertificateAndReview() {
        transaction().executeWithoutResult(ignored -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION synthetic_registration_audit_suppress()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                      IF NEW.action = 'device_certificate.register' THEN
                        RETURN NULL;
                      END IF;
                      RETURN NEW;
                    END
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER synthetic_registration_audit_suppress
                    BEFORE INSERT ON admin_access_log
                    FOR EACH ROW EXECUTE FUNCTION synthetic_registration_audit_suppress()
                    """).executeUpdate();
        });
        try {
            assertThrows(AccessDeniedException.class, () ->
                    transaction().executeWithoutResult(ignored -> {
                        registration.lockPendingForScopedAdmin(adminId, deviceId);
                        registration.registerReviewed(adminId, deviceId, fakeValidatedLeaf());
                    }));
            assertEquals(0L, count("credentials"));
            assertEquals(0L, count("audit"));
            assertNull(manager.createNativeQuery("""
                    SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                    """).setParameter("id", deviceId).getSingleResult());
        } finally {
            transaction().executeWithoutResult(ignored -> {
                manager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS synthetic_registration_audit_suppress
                        ON admin_access_log
                        """).executeUpdate();
                manager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS synthetic_registration_audit_suppress()
                        """).executeUpdate();
            });
        }
        transaction().executeWithoutResult(ignored -> {
            registration.lockPendingForScopedAdmin(adminId, deviceId);
            registration.registerReviewed(adminId, deviceId, fakeValidatedLeaf());
        });
        assertEquals(1L, count("credentials"));
        assertEquals(1L, count("audit"));
    }

    @Test
    void disablingAdminDuringDeviceLockWaitRejectsStaleReviewAndAudit() throws Exception {
        var holderHasLock = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var workerStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() ->
                    transaction().executeWithoutResult(ignored -> {
                        manager.createNativeQuery("""
                                SELECT id FROM devices WHERE id=:device
                                FOR UPDATE
                                """).setParameter("device", deviceId).getSingleResult();
                        holderHasLock.countDown();
                        try {
                            if (!releaseHolder.await(25, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Synthetic device lock timed out");
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(error);
                        }
                    }));
            try {
                assertTrue(holderHasLock.await(15, TimeUnit.SECONDS));
                var attempt = pool.submit(() -> {
                    workerStarted.countDown();
                    return assertThrows(AccessDeniedException.class, () ->
                            transaction().executeWithoutResult(ignored -> {
                                registration.lockPendingForScopedAdmin(adminId, deviceId);
                                registration.registerReviewed(
                                        adminId, deviceId, fakeValidatedLeaf());
                            }));
                });
                assertTrue(workerStarted.await(15, TimeUnit.SECONDS));

                // Require proof the worker is truly waiting inside PostgreSQL
                // before disabling its administrator (not sequential testing).
                boolean blocked = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < deadline) {
                    long waits = ((Number) manager.createNativeQuery("""
                            SELECT COUNT(*) FROM pg_stat_activity
                            WHERE datname=current_database()
                              AND wait_event_type='Lock'
                              AND query LIKE '%SELECT d.group_id, r.csr_spki_digest%'
                            """).getSingleResult()).longValue();
                    if (waits > 0) {
                        blocked = true;
                        break;
                    }
                    Thread.sleep(50);
                }
                assertTrue(blocked, "Must observe real PostgreSQL registration lock wait");
                transaction().executeWithoutResult(ignored ->
                        manager.createNativeQuery("""
                                UPDATE users SET disabled_at=clock_timestamp()
                                WHERE id=:admin
                                """).setParameter("admin", adminId).executeUpdate());
                releaseHolder.countDown();
                holder.get(25, TimeUnit.SECONDS);
                attempt.get(25, TimeUnit.SECONDS);
            } finally {
                releaseHolder.countDown();
            }
        }
        assertEquals(0L, count("credentials"));
        assertEquals(0L, count("audit"));
        assertNull(manager.createNativeQuery("""
                SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                """).setParameter("id", deviceId).getSingleResult());
    }
}
