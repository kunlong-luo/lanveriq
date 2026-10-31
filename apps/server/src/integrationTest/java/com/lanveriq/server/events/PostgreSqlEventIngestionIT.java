package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in ONLY: executes Flyway V1-V6 and the real JPA/Hibernate PostgreSQL
 * statements against an isolated synthetic Testcontainers PostgreSQL 18.6.
 * Not included in ./gradlew test or automatic CI integration execution.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false"
})
class PostgreSqlEventIngestionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private DeviceEventIngestionService ingestion;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper mapper;

    private UUID deviceId;
    private VerifiedDeviceCertificate credential;

    @BeforeEach
    void provisionIsolatedSyntheticActiveDevice() {
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> {
            var groupId = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO device_groups(name) VALUES(:name) RETURNING id
                    """)
                    .setParameter("name", "synthetic-" + UUID.randomUUID())
                    .getSingleResult();
            deviceId = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES(:group_id,'active') RETURNING id
                    """)
                    .setParameter("group_id", groupId)
                    .getSingleResult();
            String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
            entityManager.createNativeQuery("""
                    INSERT INTO device_credentials(
                        device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at)
                    VALUES (:device_id,:fingerprint,'synthetic-offline-ca',
                        :serial,:spki,'active',
                        CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        CURRENT_TIMESTAMP + INTERVAL '2 hours')
                    """)
                    .setParameter("device_id", deviceId)
                    .setParameter("fingerprint", fingerprint)
                    .setParameter("serial", UUID.randomUUID().toString())
                    .setParameter("spki", "b".repeat(64))
                    .executeUpdate();
            credential = new VerifiedDeviceCertificate(deviceId, fingerprint);
        });
    }

    private DeviceEventBatch.Event event(UUID id, long sequence, String start) {
        JsonNode payload = mapper.readTree("""
                {"affected_source":"agent_transport",
                 "reason":"quota_exceeded",
                 "range_start":"%s",
                 "range_end":"2026-10-30T09:00:00Z"}
                """.formatted(start));
        return new DeviceEventBatch.Event(id, sequence, 1, "agent_transport",
                "collection_gap", 1, "2026-10-30T09:00:00Z", payload);
    }

    private DeviceEventBatch request(DeviceEventBatch.Event... events) {
        return new DeviceEventBatch(1, UUID.randomUUID(), deviceId, List.of(events));
    }

    private long deviceEventCount() {
        return ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM endpoint_events WHERE device_id=:device_id
                """)
                .setParameter("device_id", deviceId)
                .getSingleResult()).longValue();
    }

    @Test
    void postgres18ExecutesAllSixFlywayMigrationsAndNativeUuidv7() {
        int serverVersion = Integer.parseInt((String) entityManager
                .createNativeQuery("SHOW server_version_num").getSingleResult());
        assertTrue(serverVersion >= 180000 && serverVersion < 190000,
                "An actual PostgreSQL 18 server is required");
        long count = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success AND version IN ('1','2','3','4','5','6')
                """).getSingleResult()).longValue();
        assertEquals(6, count);
        assertEquals(7, ((Number) entityManager.createNativeQuery("""
                SELECT uuid_extract_version(id) FROM devices WHERE id=:id
                """).setParameter("id", deviceId).getSingleResult()).intValue());
        assertEquals(0, deviceEventCount());
    }

    @Test
    void identicalReplayAcknowledgesAllSequencesWithoutCreatingNewRows() {
        var a = event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z");
        var b = event(UUID.randomUUID(), 3, "2026-10-30T08:58:00Z");
        assertEquals(List.of(1L, 3L),
                ingestion.ingest(credential, deviceId, request(a, b)).acceptedSequences());
        assertEquals(List.of(1L, 3L),
                ingestion.ingest(credential, deviceId, request(a, b)).acceptedSequences());
        assertEquals(2L, deviceEventCount());
    }

    @Test
    void reusedSequenceWithChangedContentRollsBackEarlierNewEventInSameBatch() {
        var stored = event(UUID.randomUUID(), 3, "2026-10-30T08:57:00Z");
        ingestion.ingest(credential, deviceId, request(stored));
        var newEarlier = event(UUID.randomUUID(), 2, "2026-10-30T08:58:00Z");
        var mutated = event(stored.eventId(), 3, "2026-10-30T08:59:00Z");
        var rejected = assertThrows(DeviceBatchRejection.class, () ->
                ingestion.ingest(credential, deviceId, request(newEarlier, mutated)));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, rejected.reason());
        assertEquals(1L, deviceEventCount());
        assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM endpoint_events
                WHERE device_id=:device AND device_sequence=2
                """).setParameter("device", deviceId).getSingleResult()).longValue());
    }

    @Test
    void reusedEventIdWithDifferentSequenceIsEntirelyRejected() {
        var id = UUID.randomUUID();
        ingestion.ingest(credential, deviceId,
                request(event(id, 1, "2026-10-30T08:57:00Z")));
        var rejected = assertThrows(DeviceBatchRejection.class, () ->
                ingestion.ingest(credential, deviceId,
                        request(event(id, 2, "2026-10-30T08:57:00Z"))));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, rejected.reason());
        assertEquals(1L, deviceEventCount());
    }

    @Test
    void revokedCertificateAndRevokedDeviceBothDenyAnyInsert() {
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> entityManager.createNativeQuery("""
                UPDATE device_credentials SET status='revoked', revoked_at=CURRENT_TIMESTAMP
                WHERE device_id=:device
                """).setParameter("device", deviceId).executeUpdate());
        var event = event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z");
        assertEquals(DeviceBatchRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceBatchRejection.class, () ->
                        ingestion.ingest(credential, deviceId, request(event))).reason());
        assertEquals(0, deviceEventCount());

        tx.executeWithoutResult(ignored -> {
            entityManager.createNativeQuery("""
                    UPDATE device_credentials SET status='active', revoked_at=NULL
                    WHERE device_id=:device
                    """).setParameter("device", deviceId).executeUpdate();
            entityManager.createNativeQuery("""
                    UPDATE devices SET enrollment_state='revoked',revoked_at=CURRENT_TIMESTAMP
                    WHERE id=:device
                    """).setParameter("device", deviceId).executeUpdate();
        });
        assertEquals(DeviceBatchRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceBatchRejection.class, () ->
                        ingestion.ingest(credential, deviceId, request(event))).reason());
        assertEquals(0, deviceEventCount());
    }

    @Test
    void forgedFingerprintAndExpiredCertificateAreDeniedByTheDatabase() {
        var e = event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z");
        var forged = new VerifiedDeviceCertificate(deviceId, "f".repeat(64));
        assertEquals(DeviceBatchRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceBatchRejection.class, () ->
                        ingestion.ingest(forged, deviceId, request(e))).reason());

        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> entityManager.createNativeQuery("""
                UPDATE device_credentials
                SET expires_at=CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                WHERE device_id=:device
                """).setParameter("device", deviceId).executeUpdate());
        assertEquals(DeviceBatchRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceBatchRejection.class, () ->
                        ingestion.ingest(credential, deviceId, request(e))).reason());
        assertEquals(0, deviceEventCount());
    }

    @Test
    void competingConcurrentPayloadsHaveOneWinnerAndOneConflict() throws Exception {
        var one = request(event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z"));
        var other = request(event(UUID.randomUUID(), 1, "2026-10-30T08:58:00Z"));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                try {
                    ingestion.ingest(credential, deviceId, one);
                    return "committed";
                } catch (DeviceBatchRejection rejected) {
                    return rejected.reason().name();
                }
            });
            var second = workers.submit(() -> {
                start.await();
                try {
                    ingestion.ingest(credential, deviceId, other);
                    return "committed";
                } catch (DeviceBatchRejection rejected) {
                    return rejected.reason().name();
                }
            });
            start.countDown();
            assertEquals(java.util.Set.of("committed", "SEQUENCE_CONFLICT"),
                    java.util.Set.of(first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS)));
        }
        assertEquals(1L, deviceEventCount());
    }

    @Test
    void forbiddenMutationFailsAgainstRealPostgresqlTrigger() {
        var e = event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z");
        ingestion.ingest(credential, deviceId, request(e));
        var tx = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () ->
                tx.executeWithoutResult(ignored -> entityManager.createNativeQuery("""
                        UPDATE endpoint_events SET policy_version=2
                        WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate()));
        assertEquals(1, deviceEventCount());
    }

    @Test
    void concurrentIdenticalBatchesSerializeAndDoNotDuplicateEvidence() throws Exception {
        var e = event(UUID.randomUUID(), 1, "2026-10-30T08:57:00Z");
        var batch = request(e);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return ingestion.ingest(credential, deviceId, batch).acceptedSequences();
            });
            var second = workers.submit(() -> {
                start.await();
                return ingestion.ingest(credential, deviceId, batch).acceptedSequences();
            });
            start.countDown();
            assertEquals(List.of(1L), first.get(30, TimeUnit.SECONDS));
            assertEquals(List.of(1L), second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1L, deviceEventCount());
    }
}
