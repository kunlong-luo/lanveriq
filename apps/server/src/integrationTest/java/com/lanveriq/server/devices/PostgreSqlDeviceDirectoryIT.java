package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Synthetic-only, manually dispatched database scope/projection tests. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false",
        "lanveriq.device-directory.enabled=true"
})
class PostgreSqlDeviceDirectoryIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_directory_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired private DeviceDirectoryService directory;
    @Autowired private MonitoringOverviewService monitoring;
    @Autowired private EntityManager manager;
    @Autowired private PlatformTransactionManager managerTx;

    private UUID first;
    private UUID second;
    private UUID outsider;
    private UUID scopedAdmin;
    private UUID otherAdmin;

    @BeforeEach
    void setup() {
        new TransactionTemplate(managerTx).executeWithoutResult(ignored -> {
            UUID group = insertGroup();
            UUID otherGroup = insertGroup();
            scopedAdmin = insertAdmin(group, true);
            otherAdmin = insertAdmin(otherGroup, false);
            first = insertDevice(group, "Synthetic A", "active");
            second = insertDevice(group, "Synthetic B", "pending_certificate");
            outsider = insertDevice(otherGroup, "Confidential other group", "active");
            manager.createNativeQuery("""
                    INSERT INTO device_heartbeat_latest(
                      device_id,agent_sent_at,agent_version,agent_status,
                      policy_version,capabilities_json,queue_count,queue_bytes,
                      dropped_count,integrity_degraded,collection_errors_json)
                    VALUES(:device,clock_timestamp(),'0.1.0','running',1,
                      CAST('{}' AS jsonb),3,100,0,false,CAST('[]' AS jsonb))
                    """).setParameter("device", first).executeUpdate();
            manager.createNativeQuery("""
                    INSERT INTO device_inventory_current(
                      device_id,schema_version,snapshot_id,inventory_revision,
                      content_digest,observed_at,os_family,os_name,cpu_model,
                      memory_total_bytes,capabilities_json)
                    VALUES (:device,1,uuidv7(),1,:digest,clock_timestamp(),
                      'linux','Synthetic Linux','Synthetic CPU',16000000000,
                      CAST('{}' AS jsonb))
                    """).setParameter("device", first)
                    .setParameter("digest", "a".repeat(64)).executeUpdate();
            manager.createNativeQuery("""
                    INSERT INTO device_inventory_interfaces(
                      device_id,interface_key,interface_name,mac_address)
                    VALUES (:device,'eth0','private-network','02:00:00:00:00:01')
                    """).setParameter("device", first).executeUpdate();
            manager.createNativeQuery("""
                    INSERT INTO device_inventory_addresses(
                      device_id,interface_key,ip,prefix_length,family)
                    VALUES (:device,'eth0',CAST('192.0.2.41' AS inet),24,'ipv4')
                    """).setParameter("device", first).executeUpdate();
        });
        login(scopedAdmin);
    }

    private UUID insertGroup() {
        return (UUID) manager.createNativeQuery("""
                INSERT INTO device_groups(name) VALUES(:name) RETURNING id
                """).setParameter("name", "directory-synthetic-" + UUID.randomUUID())
                .getSingleResult();
    }

    private UUID insertAdmin(UUID group, boolean multipleRoles) {
        UUID id = (UUID) manager.createNativeQuery("""
                INSERT INTO users(normalized_login,password_hash)
                VALUES (:login,'synthetic-DO-NOT-LOGIN') RETURNING id
                """).setParameter("login", "directory-" + UUID.randomUUID())
                .getSingleResult();
        manager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :actor,r.id,:group FROM roles AS r
                WHERE r.code IN (:roles)
                """)
                .setParameter("actor", id)
                .setParameter("group", group)
                .setParameter("roles", multipleRoles
                        ? List.of("it_operator", "security_auditor")
                        : List.of("it_operator"))
                .executeUpdate();
        return id;
    }

    private UUID insertDevice(UUID group, String label, String state) {
        return (UUID) manager.createNativeQuery("""
                INSERT INTO devices(group_id,device_label,enrollment_state)
                VALUES(:group,:label,:state) RETURNING id
                """).setParameter("group", group)
                .setParameter("label", label)
                .setParameter("state", state).getSingleResult();
    }

    private void login(UUID id) {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.userId()).thenReturn(id);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void groupScopedKeysetAndDetailDoNotLeakOtherGroupOrPrivateNetwork() {
        var received = new ArrayList<DeviceDirectoryEntry>();
        UUID cursor = null;
        do {
            var page = directory.list(cursor, 1);
            assertTrue(page.items().size() <= 1);
            received.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertEquals(2, received.size()); // Two roles must not duplicate rows.
        assertEquals(Set.of(first, second),
                Set.of(received.get(0).deviceId(), received.get(1).deviceId()));
        assertTrue(directory.find(outsider).isEmpty()); // 404-like no enumeration.
        assertEquals(1, directory.list(null, 100).items().stream()
                .filter(e -> e.deviceId().equals(first)).count());

        var full = directory.find(first).orElseThrow();
        assertEquals("Synthetic A", full.label());
        assertEquals("0.1.0", full.agentVersion());
        assertEquals(3L, full.queueCount());
        assertEquals(16_000_000_000L, full.memoryTotalBytes());
        assertTrue(full.heartbeatReceivedAt() != null);
        assertTrue(full.inventoryReceivedAt() != null);
        var pending = directory.find(second).orElseThrow();
        assertEquals("pending_certificate", pending.enrollmentState());
        assertNull(pending.heartbeatReceivedAt());
        assertNull(pending.memoryTotalBytes());

        // Neither IP/MAC nor raw credentials exist in the projection type.
        for (var component : DeviceDirectoryEntry.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("ipaddress") || name.contains("macaddress")
                    || name.contains("credential") || name.contains("event"));
        }

        login(otherAdmin);
        var otherRows = directory.list(null, 100).items();
        assertEquals(1, otherRows.size());
        assertEquals(outsider, otherRows.getFirst().deviceId());
        assertTrue(directory.find(first).isEmpty());
    }

    @Test
    void disabledAdministratorLosesAllHealthScopeDespiteStalePrincipal() {
        assertEquals(2, directory.list(null, 100).items().size());
        new TransactionTemplate(managerTx).executeWithoutResult(ignored ->
                manager.createNativeQuery("""
                        UPDATE users SET disabled_at=clock_timestamp() WHERE id=:actor
                        """).setParameter("actor", scopedAdmin).executeUpdate());
        assertTrue(directory.list(null, 100).items().isEmpty());
        assertTrue(directory.find(first).isEmpty());
        assertTrue(directory.find(outsider).isEmpty());
    }
    @Test
    void monitoringOverviewCountsOnlyCurrentGroupsAndExcludesPendingFromOffline() {
        new TransactionTemplate(managerTx).executeWithoutResult(ignored -> {
            UUID group = (UUID) manager.createNativeQuery(
                    "SELECT group_id FROM devices WHERE id=:device")
                    .setParameter("device", first).getSingleResult();
            UUID stale = insertDevice(group, "Synthetic stale", "active");
            UUID old = insertDevice(group, "Synthetic offline", "active");
            insertDevice(group, "Synthetic never-seen", "active");
            UUID revoked = insertDevice(group, "Synthetic revoked", "revoked");
            insertDevice(group, "Synthetic retired", "retired");
            manager.createNativeQuery("""
                    UPDATE devices SET revoked_at=clock_timestamp() WHERE id=:device
                    """).setParameter("device", revoked).executeUpdate();
            for (UUID device : List.of(stale, old)) {
                manager.createNativeQuery("""
                        INSERT INTO device_heartbeat_latest(
                            device_id,received_at,agent_sent_at,agent_version,
                            agent_status,policy_version,capabilities_json,
                            queue_count,queue_bytes,dropped_count,integrity_degraded,
                            collection_errors_json)
                        VALUES(:device,
                            clock_timestamp() - CASE WHEN :is_stale
                                THEN INTERVAL '120 seconds'
                                ELSE INTERVAL '400 seconds' END,
                            clock_timestamp(),'0.1.0','running',1,
                            CAST('{}' AS jsonb),0,0,0,false,CAST('[]' AS jsonb))
                        """).setParameter("device", device)
                        .setParameter("is_stale", device.equals(stale)).executeUpdate();
            }
        });
        var result = monitoring.overview();
        assertEquals(7L, result.total());
        assertEquals(1L, result.online());
        assertEquals(1L, result.stale());
        assertEquals(2L, result.offline());
        assertEquals(1L, result.pending());
        assertEquals(1L, result.revoked());
        assertEquals(1L, result.retired());
        assertTrue(result.asOf().isBefore(java.time.Instant.now().plusSeconds(10)));

        login(otherAdmin);
        var other = monitoring.overview();
        assertEquals(1L, other.total());
        assertEquals(0L, other.online());
        assertEquals(1L, other.offline());
    }

    @Test
    void monitoringOverviewDisablesAllCountsAfterCurrentGrantIsRemoved() {
        var initially = monitoring.overview();
        assertEquals(2L, initially.total());
        assertEquals(1L, initially.online());
        assertEquals(1L, initially.pending());
        new TransactionTemplate(managerTx).executeWithoutResult(ignored ->
                manager.createNativeQuery("""
                        DELETE FROM user_role_scopes WHERE user_id=:actor
                        """).setParameter("actor", scopedAdmin).executeUpdate());
        var removed = monitoring.overview();
        assertEquals(0L, removed.total());
        assertEquals(0L, removed.online());
        assertEquals(0L, removed.offline());
    }

}
