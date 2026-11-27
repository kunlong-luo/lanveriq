package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.io.StringWriter;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Opt-in synthetic PostgreSQL-only registration transaction tests. The scoped
 * token/CSR lifecycle test uses real ephemeral PKIX leaf validation; other
 * transaction fault tests use fixture metadata. No deployed CA, real admin
 * login or Agent ingress is created.
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7")
            .withDatabaseName("lanveriq_certificate_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private JpaDeviceCertificateRegistrationRepository registration;

    @Autowired
    private EnrollmentTokenIssuer tokenIssuer;

    @Autowired
    private PendingEnrollmentService pendingEnrollment;

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

    private static KeyPair syntheticP256Keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String syntheticP256Csr() throws Exception {
        return syntheticP256Csr(syntheticP256Keys());
    }

    private static String syntheticP256Csr(KeyPair keys) throws Exception {
        var request = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=untrusted-synthetic-subject"), keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate()));
        var output = new StringWriter();
        try (var writer = new JcaPEMWriter(output)) {
            writer.writeObject(request);
        }
        return output.toString();
    }

    // Ephemeral certificates are generated only inside this Testcontainers
    // integration test. No host PKI, external database, or deployed issuer.
    private record SyntheticIssuer(X500Name name, KeyPair keys, X509Certificate root) {}

    private static X509Certificate signSynthetic(JcaX509v3CertificateBuilder builder,
                                                 KeyPair keys) throws Exception {
        return new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate())));
    }

    private static SyntheticIssuer syntheticIssuer() throws Exception {
        var keys = syntheticP256Keys();
        var name = new X500Name("CN=Disposable registration test CA");
        var now = Instant.now();
        var root = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(now.minusSeconds(3600)), Date.from(now.plusSeconds(7 * 86400)),
                name, keys.getPublic());
        root.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        root.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        return new SyntheticIssuer(name, keys, signSynthetic(root, keys));
    }

    private static String syntheticSignedLeaf(SyntheticIssuer issuer, UUID device,
                                              KeyPair keys) throws Exception {
        var now = Instant.now();
        var leaf = new JcaX509v3CertificateBuilder(issuer.name(),
                new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16)
                        .add(BigInteger.ONE),
                Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(3600)),
                new X500Name("CN=Disposable registration test leaf"), keys.getPublic());
        leaf.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        leaf.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        leaf.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        leaf.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier,
                        "urn:uuid:" + device)));
        var output = new StringWriter();
        try (var writer = new JcaPEMWriter(output)) {
            writer.writeObject(signSynthetic(leaf, issuer.keys()));
        }
        return output.toString();
    }

    // Only the test principal is simulated; registration's database scope,
    // persisted CSR, PKIX checks, review write and audit are all real.
    private void authenticateSyntheticAdmin(UUID actor) {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        Mockito.when(principal.isEnabled()).thenReturn(true);
        Mockito.when(principal.userId()).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void scopedTokenCsrAndReviewFormOnePostgresPendingIdentityLifecycle() throws Exception {
        assertThrows(AccessDeniedException.class, () ->
                tokenIssuer.issue(UUID.randomUUID(), groupId, 9, Duration.ofMinutes(5)));
        var token = tokenIssuer.issue(adminId, groupId, 9, Duration.ofMinutes(5));
        assertTrue(token.toString().contains("plaintext=REDACTED"));
        byte[] storedVerifier = (byte[]) manager.createNativeQuery("""
                SELECT token_sha256 FROM enrollment_tokens WHERE id=:id
                """).setParameter("id", token.id()).getSingleResult();
        assertArrayEquals(new EnrollmentTokenCodec().verifierFor(token.plaintext()), storedVerifier);
        KeyPair agentKeys = syntheticP256Keys();
        String csr = syntheticP256Csr(agentKeys);
        String spki = new CertificateSigningRequestValidator()
                .validate(csr).spkiSha256Digest();

        assertThrows(AccessDeniedException.class, () ->
                pendingEnrollment.register(token.plaintext(), 10, csr));
        assertThrows(IllegalArgumentException.class, () ->
                pendingEnrollment.register(token.plaintext(), 9, "invalid-csr"));
        assertNull(manager.createNativeQuery("""
                SELECT consumed_at FROM enrollment_tokens WHERE id=:id
                """).setParameter("id", token.id()).getSingleResult());

        var pending = pendingEnrollment.register(token.plaintext(), 9, csr);
        assertEquals(groupId, pending.deviceGroupId());
        assertEquals("pending_certificate", manager.createNativeQuery("""
                SELECT enrollment_state FROM devices WHERE id=:id
                """).setParameter("id", pending.deviceId()).getSingleResult());
        assertEquals(spki, manager.createNativeQuery("""
                SELECT csr_spki_digest FROM device_enrollment_csrs WHERE device_id=:id
                """).setParameter("id", pending.deviceId()).getSingleResult());
        assertNotNull(manager.createNativeQuery("""
                SELECT consumed_at FROM enrollment_tokens WHERE id=:id
                """).setParameter("id", token.id()).getSingleResult());
        assertThrows(AccessDeniedException.class, () ->
                pendingEnrollment.register(token.plaintext(), 9, csr));
        assertThrows(AccessDeniedException.class, () ->
                transaction().executeWithoutResult(ignored ->
                        registration.lockPendingForScopedAdmin(UUID.randomUUID(), pending.deviceId())));

        var issuer = syntheticIssuer();
        var validator = new ClientCertificateProfileValidator(issuer.root(), Clock.systemUTC());
        var registrar = new ReviewedDeviceCertificateRegistrationService(registration, validator);
        String validLeaf = syntheticSignedLeaf(issuer, pending.deviceId(), agentKeys);
        String wrongDevice = syntheticSignedLeaf(issuer, UUID.randomUUID(), agentKeys);
        String wrongKey = syntheticSignedLeaf(issuer, pending.deviceId(), syntheticP256Keys());
        String wrongCa = syntheticSignedLeaf(syntheticIssuer(), pending.deviceId(), agentKeys);
        try {
            SecurityContextHolder.clearContext();
            assertThrows(AccessDeniedException.class, () ->
                    transaction().executeWithoutResult(ignored ->
                            registrar.register(pending.deviceId(), validLeaf)));
            authenticateSyntheticAdmin(UUID.randomUUID());
            assertThrows(AccessDeniedException.class, () ->
                    transaction().executeWithoutResult(ignored ->
                            registrar.register(pending.deviceId(), validLeaf)));
            authenticateSyntheticAdmin(adminId);
            for (String invalid : List.of(wrongDevice, wrongKey, wrongCa)) {
                assertThrows(IllegalArgumentException.class, () ->
                        transaction().executeWithoutResult(ignored ->
                                registrar.register(pending.deviceId(), invalid)));
                assertEquals(0L, ((Number) manager.createNativeQuery("""
                        SELECT COUNT(*) FROM device_credentials WHERE device_id=:id
                        """).setParameter("id", pending.deviceId()).getSingleResult()).longValue());
                assertNull(manager.createNativeQuery("""
                        SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                        """).setParameter("id", pending.deviceId()).getSingleResult());
                assertEquals(0L, ((Number) manager.createNativeQuery("""
                        SELECT COUNT(*) FROM admin_access_log WHERE resource_id=:id
                          AND action='device_certificate.register'
                        """).setParameter("id", pending.deviceId()).getSingleResult()).longValue());
            }
            var registered = transaction().execute(ignored ->
                    registrar.register(pending.deviceId(), validLeaf));
            assertNotNull(registered);
            assertEquals(pending.deviceId(), registered.deviceId());
            assertEquals(validator.validate(validLeaf, pending.deviceId(), spki).fingerprint(),
                    registered.fingerprint());
            assertEquals(registered.fingerprint(), manager.createNativeQuery("""
                    SELECT trim(client_cert_sha256_fingerprint) FROM device_credentials
                    WHERE device_id=:id
                    """).setParameter("id", pending.deviceId()).getSingleResult());
            assertThrows(AccessDeniedException.class, () ->
                    transaction().executeWithoutResult(ignored ->
                            registrar.register(pending.deviceId(), validLeaf)));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals("pending_certificate", manager.createNativeQuery("""
                SELECT enrollment_state FROM devices WHERE id=:id
                """).setParameter("id", pending.deviceId()).getSingleResult());
        assertEquals(adminId, manager.createNativeQuery("""
                SELECT reviewed_by FROM device_enrollment_csrs WHERE device_id=:id
                """).setParameter("id", pending.deviceId()).getSingleResult());
        assertEquals("pending", manager.createNativeQuery("""
                SELECT status FROM device_credentials WHERE device_id=:id
                """).setParameter("id", pending.deviceId()).getSingleResult());
        long tokenAudit = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE resource_id=:id AND action='enrollment_token.issue'
                """).setParameter("id", token.id()).getSingleResult()).longValue();
        long reviewAudit = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE resource_id=:id AND action='device_certificate.register'
                """).setParameter("id", pending.deviceId()).getSingleResult()).longValue();
        assertEquals(1L, tokenAudit);
        assertEquals(1L, reviewAudit);
    }

    @Test
    void concurrentSingleUseTokenCreatesOnlyOnePendingCsr() throws Exception {
        var token = tokenIssuer.issue(adminId, groupId, 9, Duration.ofMinutes(5));
        String firstCsr = syntheticP256Csr();
        String secondCsr = syntheticP256Csr();
        long before = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM devices WHERE group_id=:group_id
                """).setParameter("group_id", groupId).getSingleResult()).longValue();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                try {
                    pendingEnrollment.register(token.plaintext(), 9, firstCsr);
                    return true;
                } catch (AccessDeniedException denied) {
                    return false;
                }
            });
            var second = pool.submit(() -> {
                start.await();
                try {
                    pendingEnrollment.register(token.plaintext(), 9, secondCsr);
                    return true;
                } catch (AccessDeniedException denied) {
                    return false;
                }
            });
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }
        long after = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM devices WHERE group_id=:group_id
                """).setParameter("group_id", groupId).getSingleResult()).longValue();
        assertEquals(before + 1, after);
        assertNotNull(manager.createNativeQuery("""
                SELECT consumed_at FROM enrollment_tokens WHERE id=:id
                """).setParameter("id", token.id()).getSingleResult());
    }

    @Test
    void expiredTokenCannotCreateOrConsumePendingDevice() throws Exception {
        var token = tokenIssuer.issue(adminId, groupId, 9, Duration.ofMinutes(5));
        transaction().executeWithoutResult(ignored ->
                manager.createNativeQuery("""
                        UPDATE enrollment_tokens
                        SET expires_at=clock_timestamp()-INTERVAL '1 second'
                        WHERE id=:id
                        """).setParameter("id", token.id()).executeUpdate());
        long before = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM devices WHERE group_id=:group_id
                """).setParameter("group_id", groupId).getSingleResult()).longValue();
        assertThrows(AccessDeniedException.class, () ->
                pendingEnrollment.register(token.plaintext(), 9, syntheticP256Csr()));
        long after = ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM devices WHERE group_id=:group_id
                """).setParameter("group_id", groupId).getSingleResult()).longValue();
        assertEquals(before, after);
        assertNull(manager.createNativeQuery("""
                SELECT consumed_at FROM enrollment_tokens WHERE id=:id
                """).setParameter("id", token.id()).getSingleResult());
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
