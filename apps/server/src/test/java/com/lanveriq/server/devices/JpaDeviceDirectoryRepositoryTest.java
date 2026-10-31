package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lanveriq.server.security.AdminAction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class JpaDeviceDirectoryRepositoryTest {
    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query query = Mockito.mock(Query.class);
    private final JpaDeviceDirectoryRepository repo = new JpaDeviceDirectoryRepository(manager);
    private final UUID adminId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        when(query.setMaxResults(Mockito.anyInt())).thenReturn(query);
    }

    private Object[] sample() {
        return new Object[]{
                deviceId, groupId, "Engineering", "Synthetic laptop", "active",
                1_800_000_000L, 1_800_000_001L, "0.1.0", 12L, 1500L,
                "linux", "Synthetic Linux", "Synthetic CPU", 16_000_000_000L,
                1_800_000_002L};
    }

    @Test
    void queryChecksLiveGroupRoleAndDisabledAdminAndNeverReadsNetworkTables() {
        wire();
        when(query.getResultList()).thenReturn(List.<Object[]>of(sample()));
        var results = repo.list(adminId, null, 10);
        assertEquals(1, results.size());
        var summary = results.getFirst();
        assertEquals(deviceId, summary.deviceId());
        assertEquals(Instant.ofEpochSecond(1_800_000_000L), summary.lastSeenAt());
        assertEquals(Instant.ofEpochSecond(1_800_000_001L), summary.heartbeatReceivedAt());
        assertEquals(Instant.ofEpochSecond(1_800_000_002L), summary.inventoryReceivedAt());
        assertEquals(12L, summary.queueCount());
        assertEquals(16_000_000_000L, summary.memoryTotalBytes());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("u.disabled_at IS NULL"));
        assertTrue(sql.getValue().contains("s.device_group_id=d.group_id"));
        assertTrue(sql.getValue().contains("EXISTS"));
        assertTrue(sql.getValue().contains("ORDER BY d.id ASC"));
        assertFalse(sql.getValue().contains("device_inventory_addresses"));
        assertFalse(sql.getValue().contains("mac_address"));
        assertFalse(sql.getValue().contains("client_cert_sha256_fingerprint"));
        verify(query).setParameter("admin_id", adminId);
        verify(query).setParameter("health_roles", AdminAction.VIEW_HEALTH.allowedRoleCodes());
        verify(query).setMaxResults(10);
    }

    @Test
    void cursorQueryAndSingleDeviceQueryCannotBypassDatabaseScope() {
        wire();
        when(query.getResultList()).thenReturn(List.<Object[]>of(sample()));
        repo.list(adminId, deviceId, 2);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("d.id>:cursor"));
        verify(query).setParameter("cursor", deviceId);

        Mockito.clearInvocations(manager, query);
        assertEquals(deviceId, repo.find(adminId, deviceId).orElseThrow().deviceId());
        verify(manager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("AND d.id=:device_id"));
        assertTrue(sql.getValue().contains("WHERE EXISTS"));
        verify(query).setParameter("admin_id", adminId);
        verify(query).setParameter("device_id", deviceId);
    }

    @Test
    void emptyAndUnknownHealthAreNotFabricatedAsZeroOrOnline() {
        wire();
        Object[] row = sample();
        row[5] = null;
        row[6] = null;
        row[7] = null;
        row[8] = null;
        row[9] = null;
        row[10] = null;
        row[11] = null;
        row[12] = null;
        row[13] = null;
        row[14] = null;
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));
        var result = repo.find(adminId, deviceId).orElseThrow();
        assertNull(result.heartbeatReceivedAt());
        assertNull(result.agentVersion());
        assertNull(result.queueCount());
        assertNull(result.memoryTotalBytes());
        assertNull(result.inventoryReceivedAt());
        assertEquals("active", result.enrollmentState());
    }

    @Test
    void nonUniqueOrMalformedDbProjectionFailsClosed() {
        wire();
        when(query.getResultList()).thenReturn(List.<Object[]>of(sample(), sample()));
        assertThrows(IllegalStateException.class, () -> repo.find(adminId, deviceId));
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{"wrong"}));
        assertThrows(IllegalStateException.class, () -> repo.list(adminId, null, 1));
        assertThrows(IllegalArgumentException.class, () -> repo.list(adminId, null, 102));
    }
}
