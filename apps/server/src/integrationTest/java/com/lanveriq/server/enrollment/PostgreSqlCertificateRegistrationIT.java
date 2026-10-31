package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import java.time.Instant;
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
}
