package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Synthetic-only database checks. The fixture simulates an already-reviewed
 * registration row and does NOT validate a real CSR, leaf certificate or CA.
 * No network activation or real device authorization is exercised here.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false"
})
class PostgreSqlDeviceActivationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_activation_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private DeviceActivationService activation;

    @Autowired
    private DeviceHeartbeatService heartbeatService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager txManager;

    private UUID deviceId;
    private VerifiedDeviceCertificate credential;

    @BeforeEach
    void createSyntheticReviewedPendingCredential() {
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(ignored -> {
            UUID groupId = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO device_groups(name) VALUES(:name) RETURNING id
                    """).setParameter("name", "activation-test-" + UUID.randomUUID()).getSingleResult();
            deviceId = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES(:group,'pending_certificate') RETURNING id
                    """).setParameter("group", groupId).getSingleResult();
            entityManager.createNativeQuery("""
                    INSERT INTO device_enrollment_csrs
                        (device_id,csr_pem_public,csr_sha256_digest,csr_spki_digest,
                         pending_expires_at,reviewed_by,reviewed_at)
                    VALUES (:device,'synthetic-public-only',
                            :csr_digest,:spki_digest,
                            CURRENT_TIMESTAMP + INTERVAL '1 hour',
                            :reviewer,CURRENT_TIMESTAMP)
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("csr_digest", "a".repeat(64))
                    .setParameter("spki_digest", "b".repeat(64))
                    .setParameter("reviewer", UUID.randomUUID())
                    .executeUpdate();
            String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
            entityManager.createNativeQuery("""
                    INSERT INTO device_credentials(
                        device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at)
                    VALUES (:device,:fingerprint,'synthetic-not-a-ca',
                            :serial,:spki_digest,'pending',
                            CURRENT_TIMESTAMP - INTERVAL '1 hour',
                            CURRENT_TIMESTAMP + INTERVAL '1 hour')
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("fingerprint", fingerprint)
                    .setParameter("serial", UUID.randomUUID().toString())
                    .setParameter("spki_digest", "b".repeat(64))
                    .executeUpdate();
            credential = new VerifiedDeviceCertificate(deviceId, fingerprint);
        });
    }

    private DeviceActivationRequest request() {
        return new DeviceActivationRequest(1, deviceId, "0.1.0");
    }

    private String state() {
        return (String) entityManager.createNativeQuery("""
                SELECT enrollment_state FROM devices WHERE id=:device
                """).setParameter("device", deviceId).getSingleResult();
    }

    private String credentialState() {
        return (String) entityManager.createNativeQuery("""
                SELECT status FROM device_credentials
                WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult();
    }

    @Test
    void validActivationCommitsBothRowsAndExactSameCertificateRetryIsIdempotent() {
        assertEquals("pending_certificate", state());
        var first = activation.activate(credential, deviceId, request());
        var retry = activation.activate(credential, deviceId, request());
        assertEquals("active", first.enrollmentState());
        assertEquals("active", retry.enrollmentState());
        assertEquals("active", state());
        assertEquals("active", credentialState());
        long enrolled = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM devices
                WHERE id=:device AND enrolled_at IS NOT NULL
                """).setParameter("device", deviceId).getSingleResult()).longValue();
        assertEquals(1L, enrolled);
        long count = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM device_credentials WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult()).longValue();
        assertEquals(1L, count);
    }

    @Test
    void pendingCredentialCannotSendHeartbeatBeforeActivation() {
        var heartbeat = new DeviceHeartbeat(1, deviceId, "2026-10-30T09:00:00Z",
                "0.1.0", 1, "running",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, false, List.of());
        assertEquals(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceHeartbeatRejection.class,
                        () -> heartbeatService.ingest(credential, deviceId, heartbeat)).reason());
        activation.activate(credential, deviceId, request());
        heartbeatService.ingest(credential, deviceId, heartbeat);
        long count = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM device_heartbeat_latest WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult()).longValue();
        assertEquals(1L, count);
    }

    @Test
    void forgedFingerprintOrMissingAdminReviewCannotActivate() {
        var forged = new VerifiedDeviceCertificate(deviceId, "f".repeat(64));
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> activation.activate(forged, deviceId, request())).reason());
        new TransactionTemplate(txManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        UPDATE device_enrollment_csrs
                        SET reviewed_at=NULL, reviewed_by=NULL WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate());
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> activation.activate(credential, deviceId, request())).reason());
        assertEquals("pending_certificate", state());
        assertEquals("pending", credentialState());
    }

    @Test
    void expiredPendingEnrollmentAndRevokedCertificateRemainDenied() {
        new TransactionTemplate(txManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        UPDATE device_enrollment_csrs
                        SET pending_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 minute'
                        WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate());
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> activation.activate(credential, deviceId, request())).reason());
        new TransactionTemplate(txManager).executeWithoutResult(ignored -> {
            entityManager.createNativeQuery("""
                    UPDATE device_enrollment_csrs
                    SET pending_expires_at=CURRENT_TIMESTAMP + INTERVAL '1 hour'
                    WHERE device_id=:device
                    """).setParameter("device", deviceId).executeUpdate();
            entityManager.createNativeQuery("""
                    UPDATE device_credentials
                    SET status='revoked', revoked_at=CURRENT_TIMESTAMP
                    WHERE device_id=:device
                    """).setParameter("device", deviceId).executeUpdate();
        });
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> activation.activate(credential, deviceId, request())).reason());
        assertEquals("pending_certificate", state());
        assertEquals("revoked", credentialState());
    }

    @Test
    void twoConcurrentSameCertificateActivationsSerializeAndConverge() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return activation.activate(credential, deviceId, request()).enrollmentState();
            });
            var second = pool.submit(() -> {
                start.await();
                return activation.activate(credential, deviceId, request()).enrollmentState();
            });
            start.countDown();
            assertEquals("active", first.get(30, TimeUnit.SECONDS));
            assertEquals("active", second.get(30, TimeUnit.SECONDS));
        }
        assertEquals("active", state());
        assertEquals("active", credentialState());
        assertTrue(((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM device_credentials
                WHERE device_id=:device AND status='active'
                """).setParameter("device", deviceId).getSingleResult()).longValue() == 1L);
    }
}
