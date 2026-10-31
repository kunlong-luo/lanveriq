package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Manual synthetic-only Testcontainers suite for V8. Does not open HTTP,
 * access OS equipment or prove a real client certificate/mTLS connection.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false",
        "lanveriq.agent.ingress.assertions-enabled=false"
})
class PostgreSqlDeviceInventoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_inventory_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private DeviceInventoryService inventory;

    @Autowired
    private EntityManager manager;

    @Autowired
    private PlatformTransactionManager transactions;

    private UUID deviceId;
    private VerifiedDeviceCertificate credential;

    @BeforeEach
    void setupSyntheticActiveDevice() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            UUID group = (UUID) manager.createNativeQuery("""
                    INSERT INTO device_groups(name) VALUES(:name) RETURNING id
                    """).setParameter("name", "synthetic-inventory-" + UUID.randomUUID())
                    .getSingleResult();
            deviceId = (UUID) manager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES(:group,'active') RETURNING id
                    """).setParameter("group", group).getSingleResult();
            String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
            manager.createNativeQuery("""
                    INSERT INTO device_credentials(
                        device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at)
                    VALUES (:device,:fingerprint,'synthetic-not-a-real-ca',
                        :serial,:spki,'active',clock_timestamp()-INTERVAL '1 hour',
                        clock_timestamp()+INTERVAL '1 hour')
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("fingerprint", fingerprint)
                    .setParameter("serial", UUID.randomUUID().toString())
                    .setParameter("spki", "a".repeat(64))
                    .executeUpdate();
            credential = new VerifiedDeviceCertificate(deviceId, fingerprint);
        });
    }

    private DeviceInventorySnapshot snapshot(long revision, UUID snapshotId, boolean twoNics) {
        return new DeviceInventorySnapshot(
                1, snapshotId, revision, deviceId, Instant.now().minusSeconds(90).toString(),
                new DeviceInventorySnapshot.Hardware("SyntheticVendor", "TestModel",
                        new DeviceInventorySnapshot.Cpu("Test CPU", "x86_64", 4, 8),
                        new DeviceInventorySnapshot.Memory(16_000_000_000L)),
                new DeviceInventorySnapshot.OperatingSystem(
                        "linux", "synthetic-linux", "1", null, null, "x86_64"),
                twoNics ? List.of(
                        new DeviceInventorySnapshot.NetworkInterface(
                                "eth0", "ethernet0", "ethernet", "02:00:00:00:00:01",
                                "up", false, List.of(
                                new DeviceInventorySnapshot.Address(
                                        "192.0.2.10", 24, "ipv4", "private"),
                                new DeviceInventorySnapshot.Address(
                                        "2001:db8::1", 64, "ipv6", "global"))),
                        new DeviceInventorySnapshot.NetworkInterface(
                                "wlan0", "wifi0", "wifi", null, "down", false, List.of()))
                        : List.of(new DeviceInventorySnapshot.NetworkInterface(
                                "wlan0", "wifi0", "wifi", null, "up", false, List.of())),
                List.of(new DeviceInventorySnapshot.Volume("root", "/", "ext4",
                        100_000_000L, 50_000_000L, true)),
                Map.of("gpu_inventory", "not_collected"));
    }

    private long count(String table) {
        String sql = switch (table) {
            case "current" -> "SELECT COUNT(*) FROM device_inventory_current WHERE device_id=:id";
            case "interfaces" ->
                    "SELECT COUNT(*) FROM device_inventory_interfaces WHERE device_id=:id";
            case "addresses" ->
                    "SELECT COUNT(*) FROM device_inventory_addresses WHERE device_id=:id";
            case "volumes" -> "SELECT COUNT(*) FROM device_inventory_volumes WHERE device_id=:id";
            default -> throw new IllegalArgumentException("not a synthetic inventory table");
        };
        return ((Number) manager.createNativeQuery(sql)
                .setParameter("id", deviceId).getSingleResult()).longValue();
    }

    @Test
    void v8StoresMultipleNicsAndIpv4Ipv6ThenReplacesCompleteChildren() {
        var first = snapshot(17, UUID.randomUUID(), true);
        assertEquals(DeviceInventoryReceipt.Outcome.applied,
                inventory.ingest(credential, deviceId, first).outcome());
        assertEquals(1L, count("current"));
        assertEquals(2L, count("interfaces"));
        assertEquals(2L, count("addresses"));
        assertEquals(1L, count("volumes"));
        assertEquals(DeviceInventoryReceipt.Outcome.duplicate,
                inventory.ingest(credential, deviceId, first).outcome());
        var replacement = snapshot(19, UUID.randomUUID(), false);
        assertEquals(DeviceInventoryReceipt.Outcome.applied,
                inventory.ingest(credential, deviceId, replacement).outcome());
        assertEquals(1L, count("current"));
        assertEquals(1L, count("interfaces"));
        assertEquals(0L, count("addresses"));
        assertEquals(1L, count("volumes"));
        long revision = ((Number) manager.createNativeQuery("""
                SELECT inventory_revision FROM device_inventory_current WHERE device_id=:id
                """).setParameter("id", deviceId).getSingleResult()).longValue();
        assertEquals(19L, revision);
    }

    @Test
    void lowerRevisionIsIgnoredAndSameRevisionDifferentDigestDenied() {
        var current = snapshot(8, UUID.randomUUID(), true);
        inventory.ingest(credential, deviceId, current);
        var late = snapshot(7, UUID.randomUUID(), false);
        var stale = inventory.ingest(credential, deviceId, late);
        assertEquals(DeviceInventoryReceipt.Outcome.stale_ignored, stale.outcome());
        assertEquals(8L, stale.inventoryRevision());
        assertEquals(2L, count("interfaces"));
        var conflict = snapshot(8, UUID.randomUUID(), false);
        assertEquals(DeviceInventoryRejection.Reason.REVISION_CONFLICT,
                assertThrows(DeviceInventoryRejection.class,
                        () -> inventory.ingest(credential, deviceId, conflict)).reason());
        assertEquals(2L, count("interfaces"));
    }

    @Test
    void wrongAndRevokedCertificateNeverWritesInventory() {
        var forged = new VerifiedDeviceCertificate(deviceId, "f".repeat(64));
        var snapshot = snapshot(1, UUID.randomUUID(), true);
        assertEquals(DeviceInventoryRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceInventoryRejection.class,
                        () -> inventory.ingest(forged, deviceId, snapshot)).reason());
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                manager.createNativeQuery("""
                        UPDATE device_credentials
                        SET status='revoked', revoked_at=clock_timestamp()
                        WHERE device_id=:device
                        """).setParameter("device", deviceId).executeUpdate());
        assertEquals(DeviceInventoryRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceInventoryRejection.class,
                        () -> inventory.ingest(credential, deviceId, snapshot)).reason());
        assertEquals(0L, count("current"));
    }
}
