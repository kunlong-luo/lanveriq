package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
    private String csrSpkiSha256;

    @BeforeEach
    void createSyntheticReviewedPendingCredential() {
        // V3 uniquely indexes stored CSR public-key digests across ALL devices.
        // Each @BeforeEach fixture must therefore represent a fresh key.
        csrSpkiSha256 = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
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
                    .setParameter("spki_digest", csrSpkiSha256)
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
                    .setParameter("spki_digest", csrSpkiSha256)
                    .executeUpdate();
            credential = new VerifiedDeviceCertificate(deviceId, fingerprint);
        });
    }

    private DeviceActivationRequest request() {
        return new DeviceActivationRequest(1, deviceId, "0.1.0", UUID.randomUUID());
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
                0, 0, null, null, 0, false, List.of(), UUID.randomUUID());
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
    void earlierAcceptedRequestIdReplayCannotReplaceNewerHeartbeat() {
        activation.activate(credential, deviceId, request());
        UUID oldRequestId = UUID.randomUUID();
        UUID newRequestId = UUID.randomUUID();
        var oldHeartbeat = new DeviceHeartbeat(1, deviceId, "2026-11-12T09:00:00Z",
                "0.1.0", 1, "running",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, false, List.of(), oldRequestId);
        var newHeartbeat = new DeviceHeartbeat(1, deviceId, "2026-11-12T09:01:00Z",
                "0.1.0", 2, "degraded",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, true, List.of(), newRequestId);

        assertEquals(oldRequestId,
                heartbeatService.ingest(credential, deviceId, oldHeartbeat).requestId());
        assertEquals(newRequestId,
                heartbeatService.ingest(credential, deviceId, newHeartbeat).requestId());
        // The real PG18 lookup must be positive only for committed IDs under
        // the CURRENT certificate lock, and unknown UUID is not a resend grant.
        var lookup = heartbeatService.lookupAccepted(credential, deviceId, oldRequestId);
        assertTrue(lookup.isPresent());
        assertEquals(oldRequestId, lookup.orElseThrow().requestId());
        assertEquals(deviceId, lookup.orElseThrow().deviceId());
        assertTrue(lookup.orElseThrow().committed());
        assertTrue(heartbeatService.lookupAccepted(credential, deviceId, newRequestId).isPresent());
        assertTrue(heartbeatService.lookupAccepted(
                credential, deviceId, UUID.randomUUID()).isEmpty());
        Object[] newest = (Object[]) entityManager.createNativeQuery("""
                SELECT agent_status, policy_version, integrity_degraded, received_at
                FROM device_heartbeat_latest WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult();
        Object lastSeen = entityManager.createNativeQuery("""
                SELECT last_seen_at FROM devices WHERE id=:device
                """).setParameter("device", deviceId).getSingleResult();
        assertEquals("degraded", newest[0]);
        assertEquals(2L, ((Number) newest[1]).longValue());
        assertEquals(true, newest[2]);

        var denied = assertThrows(DeviceHeartbeatRejection.class, () ->
                heartbeatService.ingest(credential, deviceId, oldHeartbeat));
        assertEquals(DeviceHeartbeatRejection.Reason.REPLAYED_REQUEST, denied.reason());
        Object[] after = (Object[]) entityManager.createNativeQuery("""
                SELECT agent_status, policy_version, integrity_degraded, received_at
                FROM device_heartbeat_latest WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult();
        assertEquals(java.util.Arrays.asList(newest), java.util.Arrays.asList(after));
        assertEquals(lastSeen, entityManager.createNativeQuery("""
                SELECT last_seen_at FROM devices WHERE id=:device
                """).setParameter("device", deviceId).getSingleResult());
        long accepted = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM device_heartbeat_request_ids WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult()).longValue();
        assertEquals(2L, accepted);
    }

    @Test
    void readOnlyLedgerLookupCannotBypassRevokedCredentialOrMutateHeartbeatState() {
        activation.activate(credential, deviceId, request());
        UUID acceptedId = UUID.randomUUID();
        var heartbeat = new DeviceHeartbeat(1, deviceId, "2026-11-12T09:00:00Z",
                "0.1.0", 1, "running",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, false, List.of(), acceptedId);
        heartbeatService.ingest(credential, deviceId, heartbeat);
        assertTrue(heartbeatService.lookupAccepted(
                credential, deviceId, acceptedId).isPresent());
        var foreign = new VerifiedDeviceCertificate(deviceId, "f".repeat(64));
        assertEquals(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        heartbeatService.lookupAccepted(foreign, deviceId, acceptedId)).reason());

        new TransactionTemplate(txManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        UPDATE device_credentials SET status='revoked',
                            revoked_at=clock_timestamp()
                        WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate());
        assertEquals(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        heartbeatService.lookupAccepted(
                                credential, deviceId, acceptedId)).reason());
        long accepted = ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM device_heartbeat_request_ids WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult()).longValue();
        assertEquals(1L, accepted);
    }

    @Test
    void acceptedRequestLookupIsBoundToTheCertificateThatSubmittedIt() {
        activation.activate(credential, deviceId, request());
        UUID firstId = UUID.randomUUID();
        var firstHeartbeat = new DeviceHeartbeat(1, deviceId, "2026-11-12T09:00:00Z",
                "0.1.0", 1, "running",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, false, List.of(), firstId);
        heartbeatService.ingest(credential, deviceId, firstHeartbeat);

        String secondFingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        String secondSpki = UUID.randomUUID().toString().replace("-", "").repeat(2);
        var secondCredential = new VerifiedDeviceCertificate(deviceId, secondFingerprint);
        new TransactionTemplate(txManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        INSERT INTO device_credentials(
                            device_id,client_cert_sha256_fingerprint,issuer_id,
                            serial_number,csr_spki_digest,status,issued_at,expires_at)
                        VALUES (:device,:fingerprint,'synthetic-second-issuer',
                                :serial,:spki_digest,'active',
                                CURRENT_TIMESTAMP - INTERVAL '1 hour',
                                CURRENT_TIMESTAMP + INTERVAL '1 hour')
                        """)
                        .setParameter("device", deviceId)
                        .setParameter("fingerprint", secondFingerprint)
                        .setParameter("serial", UUID.randomUUID().toString())
                        .setParameter("spki_digest", secondSpki)
                        .executeUpdate());
        assertTrue(heartbeatService.lookupAccepted(credential, deviceId, firstId).isPresent());
        assertTrue(heartbeatService.lookupAccepted(secondCredential, deviceId, firstId).isEmpty());

        UUID secondId = UUID.randomUUID();
        var secondHeartbeat = new DeviceHeartbeat(1, deviceId, "2026-11-12T09:01:00Z",
                "0.1.0", 2, "degraded",
                Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, true, List.of(), secondId);
        heartbeatService.ingest(secondCredential, deviceId, secondHeartbeat);
        assertTrue(heartbeatService.lookupAccepted(secondCredential, deviceId, secondId).isPresent());
        assertTrue(heartbeatService.lookupAccepted(credential, deviceId, secondId).isEmpty());
        assertEquals(DeviceHeartbeatRejection.Reason.REPLAYED_REQUEST,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        heartbeatService.ingest(secondCredential, deviceId, firstHeartbeat)).reason());

    }

    @Test
    void v9ToV12UpgradeKeepsLegacyLedgerAndBackfillsCredentialEpoch() throws SQLException {
        String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        var old = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();
        old.migrate();

        UUID oldDevice = UUID.randomUUID();
        UUID oldRequest = UUID.randomUUID();
        UUID oldCredential = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            UUID group = UUID.randomUUID();
            try (var insert = connection.prepareStatement(
                    "INSERT INTO device_groups(id,name) VALUES (?,?)")) {
                insert.setObject(1, group);
                insert.setString(2, "legacy-group");
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO devices(id,group_id,enrollment_state) VALUES (?,?,'active')")) {
                insert.setObject(1, oldDevice);
                insert.setObject(2, group);
                insert.executeUpdate();
            }
            // Actual V9 row has no epoch column before migration.
            try (var insert = connection.prepareStatement("""
                    INSERT INTO device_credentials(
                        id,device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at)
                    VALUES (?,?,repeat('e',64),'v9-test-issuer',
                            'v9-test-serial',repeat('c',64),'active',
                            CURRENT_TIMESTAMP - INTERVAL '1 hour',
                            CURRENT_TIMESTAMP + INTERVAL '1 hour')
                    """)) {
                insert.setObject(1, oldCredential);
                insert.setObject(2, oldDevice);
                assertEquals(1, insert.executeUpdate());
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO device_heartbeat_request_ids(device_id,request_id) VALUES (?,?)")) {
                insert.setObject(1, oldDevice);
                insert.setObject(2, oldRequest);
                insert.executeUpdate();
            }
        }

        var upgraded = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        assertEquals(3, upgraded.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            // V12 backfills the existing V9 credential with PostgreSQL UUIDv7.
            try (var select = connection.prepareStatement("""
                    SELECT credential_epoch_id, uuid_extract_version(credential_epoch_id)
                    FROM device_credentials WHERE id=?
                    """)) {
                select.setObject(1, oldCredential);
                try (var rows = select.executeQuery()) {
                    assertTrue(rows.next());
                    assertTrue(!((UUID) rows.getObject(1)).equals(new UUID(0, 0)));
                    assertEquals(7, rows.getInt(2));
                }
            }
            try (var select = connection.prepareStatement("""
                    SELECT client_cert_sha256_fingerprint
                    FROM device_heartbeat_request_ids
                    WHERE device_id=? AND request_id=?
                    """)) {
                select.setObject(1, oldDevice);
                select.setObject(2, oldRequest);
                try (var rows = select.executeQuery()) {
                    assertTrue(rows.next());
                    assertNull(rows.getString(1));
                }
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO device_heartbeat_request_ids(device_id,request_id) VALUES (?,?)")) {
                insert.setObject(1, oldDevice);
                insert.setObject(2, UUID.randomUUID());
                var denied = assertThrows(SQLException.class, insert::executeUpdate);
                assertEquals("23514", denied.getSQLState());
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO device_heartbeat_request_ids
                        (device_id,request_id,client_cert_sha256_fingerprint)
                    VALUES (?,?,?)
                    """)) {
                insert.setObject(1, oldDevice);
                insert.setObject(2, oldRequest);
                insert.setString(3, "a".repeat(64));
                var denied = assertThrows(SQLException.class, insert::executeUpdate);
                assertEquals("23505", denied.getSQLState());
            }
            // V11: even a legacy V9 row with NULL attribution cannot be
            // retroactively assigned a certificate after the migration.
            try (var update = connection.prepareStatement("""
                    UPDATE device_heartbeat_request_ids
                    SET client_cert_sha256_fingerprint=?
                    WHERE device_id=? AND request_id=?
                    """)) {
                update.setString(1, "a".repeat(64));
                update.setObject(2, oldDevice);
                update.setObject(3, oldRequest);
                var denied = assertThrows(SQLException.class, update::executeUpdate);
                assertEquals("23514", denied.getSQLState());
            }

            // New V11 requests remain insertable. Every accepted row is
            // immutable: neither ownership nor UUID may be rewritten.
            UUID newRequest = UUID.randomUUID();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO device_heartbeat_request_ids
                        (device_id,request_id,client_cert_sha256_fingerprint)
                    VALUES (?,?,?)
                    """)) {
                insert.setObject(1, oldDevice);
                insert.setObject(2, newRequest);
                insert.setString(3, "a".repeat(64));
                assertEquals(1, insert.executeUpdate());
            }
            // Rebinding to a different certificate, or even a no-op
            // rewrite of the original attribution, must be rejected.
            for (String candidate : List.of("b".repeat(64), "a".repeat(64))) {
                try (var update = connection.prepareStatement("""
                        UPDATE device_heartbeat_request_ids
                        SET client_cert_sha256_fingerprint=?
                        WHERE device_id=? AND request_id=?
                        """)) {
                    update.setString(1, candidate);
                    update.setObject(2, oldDevice);
                    update.setObject(3, newRequest);
                    var denied = assertThrows(SQLException.class, update::executeUpdate);
                    assertEquals("23514", denied.getSQLState());
                }
            }
            try (var update = connection.prepareStatement("""
                    UPDATE device_heartbeat_request_ids
                    SET request_id=? WHERE device_id=? AND request_id=?
                    """)) {
                update.setObject(1, UUID.randomUUID());
                update.setObject(2, oldDevice);
                update.setObject(3, newRequest);
                var denied = assertThrows(SQLException.class, update::executeUpdate);
                assertEquals("23514", denied.getSQLState());
            }
            // The failed mutations cannot invalidate the original replay key
            // or resurrect an unbound legacy request as a positive lookup.
            try (var lookup = connection.prepareStatement("""
                    SELECT client_cert_sha256_fingerprint
                    FROM device_heartbeat_request_ids
                    WHERE device_id=? AND request_id=?
                    """)) {
                lookup.setObject(1, oldDevice);
                lookup.setObject(2, newRequest);
                try (var rows = lookup.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("a".repeat(64), rows.getString(1));
                }
                lookup.setObject(2, oldRequest);
                try (var rows = lookup.executeQuery()) {
                    assertTrue(rows.next());
                    assertNull(rows.getString(1));
                }
            }
        }
    }

    @Test
    void serverIssuesImmutableCredentialEpochAndAllowsRevocation() throws SQLException {
        activation.activate(credential, deviceId, request());
        String secondFingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        UUID forgedEpoch = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            UUID firstEpoch;
            try (var query = connection.prepareStatement("""
                    SELECT credential_epoch_id, uuid_extract_version(credential_epoch_id)
                    FROM device_credentials WHERE client_cert_sha256_fingerprint=?
                    """)) {
                query.setString(1, credential.sha256Fingerprint());
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    firstEpoch = (UUID) rows.getObject(1);
                    assertEquals(7, rows.getInt(2));
                }
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO device_credentials(
                        device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at,
                        credential_epoch_id)
                    VALUES (?,?,'synthetic-second-issuer',?,repeat('c',64),'active',
                            clock_timestamp()-INTERVAL '1 hour',
                            clock_timestamp()+INTERVAL '1 hour',?)
                    """)) {
                insert.setObject(1, deviceId);
                insert.setString(2, secondFingerprint);
                insert.setString(3, UUID.randomUUID().toString());
                insert.setObject(4, forgedEpoch);
                assertEquals(1, insert.executeUpdate());
            }
            UUID secondEpoch;
            try (var query = connection.prepareStatement("""
                    SELECT credential_epoch_id, uuid_extract_version(credential_epoch_id)
                    FROM device_credentials WHERE client_cert_sha256_fingerprint=?
                    """)) {
                query.setString(1, secondFingerprint);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    secondEpoch = (UUID) rows.getObject(1);
                    assertEquals(7, rows.getInt(2));
                }
            }
            assertTrue(!firstEpoch.equals(secondEpoch));
            assertTrue(!forgedEpoch.equals(secondEpoch));
            // The immutable identity check must not prevent revocation.
            try (var update = connection.prepareStatement("""
                    UPDATE device_credentials
                    SET status='revoked',revoked_at=clock_timestamp()
                    WHERE client_cert_sha256_fingerprint=?
                    """)) {
                update.setString(1, secondFingerprint);
                assertEquals(1, update.executeUpdate());
            }
            for (String column : List.of(
                    "credential_epoch_id", "device_id",
                    "client_cert_sha256_fingerprint", "issuer_id",
                    "serial_number", "csr_spki_digest")) {
                String candidate = switch (column) {
                    case "credential_epoch_id", "device_id" -> UUID.randomUUID().toString();
                    case "client_cert_sha256_fingerprint", "csr_spki_digest" -> "f".repeat(64);
                    default -> "forged-synthetic-identity";
                };
                String expr = column.equals("credential_epoch_id")
                        || column.equals("device_id") ? "CAST(? AS uuid)" : "?";
                try (var update = connection.prepareStatement(
                        "UPDATE device_credentials SET " + column + "=" + expr
                                + " WHERE client_cert_sha256_fingerprint=?")) {
                    update.setString(1, candidate);
                    update.setString(2, secondFingerprint);
                    var denied = assertThrows(SQLException.class, update::executeUpdate);
                    assertEquals("23514", denied.getSQLState());
                }
            }
            try (var query = connection.prepareStatement("""
                    SELECT credential_epoch_id,status
                    FROM device_credentials WHERE client_cert_sha256_fingerprint=?
                    """)) {
                query.setString(1, secondFingerprint);
                try (var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(secondEpoch, rows.getObject(1));
                    assertEquals("revoked", rows.getString(2));
                }
            }
        }
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
                        -- An expired CSR must still obey V3's constraint
                        -- pending_expires_at > requested_at. Backdate both
                        -- timestamps to represent a genuinely expired request,
                        -- rather than inserting an impossible database state.
                        SET requested_at=CURRENT_TIMESTAMP - INTERVAL '2 hours',
                            pending_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 hour'
                        WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate());
        // Regression for the real PostgreSQL V3 check: a test fixture must
        // represent an expired but internally valid CSR. Do not weaken the
        // database CHECK constraint to make an impossible fixture pass.
        assertTrue((Boolean) entityManager.createNativeQuery("""
                SELECT requested_at < pending_expires_at
                   AND pending_expires_at < clock_timestamp()
                FROM device_enrollment_csrs WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult());
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
