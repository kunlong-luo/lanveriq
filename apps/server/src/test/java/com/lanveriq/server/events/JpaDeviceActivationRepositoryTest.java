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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class JpaDeviceActivationRepositoryTest {

    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query deviceLock = Mockito.mock(Query.class);
    private final Query credentialLock = Mockito.mock(Query.class);
    private final Query credentialUpdate = Mockito.mock(Query.class);
    private final Query deviceUpdate = Mockito.mock(Query.class);
    private final UUID id = UUID.randomUUID();
    private final VerifiedDeviceCertificate peer = new VerifiedDeviceCertificate(id, "f".repeat(64));

    private void wire() {
        when(deviceLock.getResultList()).thenReturn(List.of(id));
        when(manager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) {
                return deviceLock;
            }
            if (sql.contains("FOR UPDATE OF c")) {
                return credentialLock;
            }
            if (sql.contains("UPDATE device_credentials")) {
                return credentialUpdate;
            }
            return deviceUpdate;
        });
        for (Query q : List.of(deviceLock, credentialLock, credentialUpdate, deviceUpdate)) {
            when(q.setParameter(anyString(), Mockito.any())).thenReturn(q);
        }
    }

    @Test
    void pendingReviewedCertificateActivatesBothRowsWithinOneTransaction() {
        wire();
        when(credentialLock.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{"pending_certificate", "pending"}));
        when(credentialUpdate.executeUpdate()).thenReturn(1);
        when(deviceUpdate.executeUpdate()).thenReturn(1);
        new JpaDeviceActivationRepository(manager).activate(peer, id);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(4)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("FOR UPDATE OF d"));
        assertTrue(sql.getAllValues().get(1).contains("FOR UPDATE OF c"));
        assertTrue(sql.getAllValues().get(1).contains("r.pending_expires_at>clock_timestamp()"));
        assertTrue(sql.getAllValues().get(1).contains("r.reviewed_by IS NOT NULL"));
        assertTrue(sql.getAllValues().get(1).contains("c.csr_spki_digest=r.csr_spki_digest"));
        assertTrue(sql.getAllValues().get(1).contains("c.revoked_at IS NULL"));
        assertTrue(sql.getAllValues().get(1).contains("c.expires_at>clock_timestamp()"));
        verify(credentialLock).setParameter("fingerprint", peer.sha256Fingerprint());
        verify(credentialUpdate).executeUpdate();
        verify(deviceUpdate).executeUpdate();
    }

    @Test
    void sameActiveFingerprintRetryIsIdempotentAndPerformsNoWrites() {
        wire();
        when(credentialLock.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{"active", "active"}));
        new JpaDeviceActivationRepository(manager).activate(peer, id);
        verify(credentialUpdate, never()).executeUpdate();
        verify(deviceUpdate, never()).executeUpdate();
    }

    @Test
    void unknownExpiredRevokedOrUnreviewedCertificateDeniesWithoutWrites() {
        wire();
        when(credentialLock.getResultList()).thenReturn(List.of());
        var error = assertThrows(DeviceActivationRejection.class,
                () -> new JpaDeviceActivationRepository(manager).activate(peer, id));
        assertEquals(DeviceActivationRejection.Reason.DENIED, error.reason());
        verify(credentialUpdate, never()).executeUpdate();
        verify(deviceUpdate, never()).executeUpdate();
    }

    @Test
    void credentialUpdateConflictDeniesBeforeChangingDevice() {
        wire();
        when(credentialLock.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{"pending_certificate", "pending"}));
        when(credentialUpdate.executeUpdate()).thenReturn(0);
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> new JpaDeviceActivationRepository(manager).activate(peer, id)).reason());
        verify(deviceUpdate, never()).executeUpdate();
    }

    @Test
    void wrongDeviceStateDeniesWithoutWrites() {
        wire();
        when(credentialLock.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{"active", "pending"}));
        assertEquals(DeviceActivationRejection.Reason.DENIED,
                assertThrows(DeviceActivationRejection.class,
                        () -> new JpaDeviceActivationRepository(manager).activate(peer, id)).reason());
        verify(credentialUpdate, never()).executeUpdate();
    }
}
