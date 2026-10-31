package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class JpaDeviceInventoryRepositoryTest {
    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query verifiedLock = Mockito.mock(Query.class);
    private final Query latest = Mockito.mock(Query.class);
    private final Query insert = Mockito.mock(Query.class);
    private final Query remove = Mockito.mock(Query.class);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate peer =
            new VerifiedDeviceCertificate(deviceId, "a".repeat(64));

    private DeviceInventorySnapshot snapshot(long revision) {
        return new DeviceInventorySnapshot(1, UUID.randomUUID(), revision, deviceId,
                "2026-10-31T12:00:00Z",
                new DeviceInventorySnapshot.Hardware(null, null, null, null),
                new DeviceInventorySnapshot.OperatingSystem("linux", "Test", null,
                        null, null, null), List.of(), List.of(), java.util.Map.of());
    }

    private void mockQueries() {
        when(manager.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FOR UPDATE OF d, c")) return verifiedLock;
            if (sql.contains("SELECT inventory_revision")) return latest;
            if (sql.contains("DELETE FROM device_inventory_current")) return remove;
            return insert;
        });
        for (Query query : List.of(verifiedLock, latest, insert, remove)) {
            when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        }
        when(verifiedLock.getResultList()).thenReturn(List.of(deviceId));
    }

    @Test
    void newerRevisionWritesUnderDeviceAndCredentialLock() {
        mockQueries();
        when(latest.getResultList()).thenReturn(List.of());
        when(insert.getResultList()).thenReturn(List.of(Instant.now()));
        var snap = snapshot(17);
        var receipt = new JpaDeviceInventoryRepository(manager)
                .persist(peer, deviceId, snap, "b".repeat(64), "{}",
                        Instant.parse(snap.observedAt()));
        assertEquals(DeviceInventoryReceipt.Outcome.applied, receipt.outcome());
        verify(insert).getResultList();
        verify(remove, never()).executeUpdate();
        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(3)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().getFirst().contains("FOR UPDATE OF d, c"));
        assertTrue(sql.getAllValues().getFirst().contains("c.status='active'"));
        assertTrue(sql.getAllValues().getFirst().contains("c.revoked_at IS NULL"));
    }

    @Test
    void olderRevisionIsAckedWithoutReplacingAnything() {
        mockQueries();
        UUID currentId = UUID.randomUUID();
        when(latest.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{19L, currentId, "b".repeat(64)}));
        var snap = snapshot(18);
        var receipt = new JpaDeviceInventoryRepository(manager)
                .persist(peer, deviceId, snap, "c".repeat(64), "{}", Instant.now());
        assertEquals(DeviceInventoryReceipt.Outcome.stale_ignored, receipt.outcome());
        assertEquals(19L, receipt.inventoryRevision());
        assertEquals(currentId, receipt.snapshotId());
        verify(remove, never()).executeUpdate();
        verify(insert, never()).getResultList();
    }

    @Test
    void sameRevisionDifferentDigestCannotOverwriteLatest() {
        mockQueries();
        var snap = snapshot(19);
        when(latest.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{19L, snap.snapshotId(), "a".repeat(64)}));
        var error = assertThrows(DeviceInventoryRejection.class, () ->
                new JpaDeviceInventoryRepository(manager).persist(
                        peer, deviceId, snap, "b".repeat(64), "{}", Instant.now()));
        assertEquals(DeviceInventoryRejection.Reason.REVISION_CONFLICT, error.reason());
        verify(remove, never()).executeUpdate();
        verify(insert, never()).getResultList();
    }

    @Test
    void identicalRetryIsIdempotent() {
        mockQueries();
        var snap = snapshot(19);
        when(latest.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{19L, snap.snapshotId(), "b".repeat(64)}));
        var receipt = new JpaDeviceInventoryRepository(manager).persist(
                peer, deviceId, snap, "b".repeat(64), "{}", Instant.now());
        assertEquals(DeviceInventoryReceipt.Outcome.duplicate, receipt.outcome());
        verify(remove, never()).executeUpdate();
        verify(insert, never()).getResultList();
    }

    @Test
    void revokedOrUnregisteredCertificateCannotReadOrReplaceState() {
        mockQueries();
        when(verifiedLock.getResultList()).thenReturn(List.of());
        var snap = snapshot(19);
        var error = assertThrows(DeviceInventoryRejection.class, () ->
                new JpaDeviceInventoryRepository(manager).persist(
                        peer, deviceId, snap, "b".repeat(64), "{}", Instant.now()));
        assertEquals(DeviceInventoryRejection.Reason.REVOKED_DEVICE, error.reason());
        verify(latest, never()).getResultList();
        verify(remove, never()).executeUpdate();
        verify(insert, never()).getResultList();
    }
}
