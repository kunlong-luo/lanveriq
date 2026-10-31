package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Default-off unit tests for the exact database authorization gate. */
class JpaActiveDeviceCredentialGuardTest {

    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query device = Mockito.mock(Query.class);
    private final Query credential = Mockito.mock(Query.class);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(deviceId, "b".repeat(64));

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) return device;
            if (sql.contains("FOR UPDATE OF c")) return credential;
            throw new AssertionError("Unapproved credential guard query");
        });
        for (Query q : List.of(device, credential)) {
            when(q.setParameter(anyString(), Mockito.any())).thenReturn(q);
        }
    }

    @Test
    void locksDeviceBeforeExactBoundActiveCertificateWithDatabaseClock() {
        wire();
        when(device.getResultList()).thenReturn(List.of(deviceId));
        when(credential.getResultList()).thenReturn(List.of(UUID.randomUUID()));

        assertTrue(JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId));
        var statements = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(2)).createNativeQuery(statements.capture());
        String first = statements.getAllValues().get(0);
        String second = statements.getAllValues().get(1);
        assertTrue(first.contains("enrollment_state='active'"));
        assertTrue(first.contains("d.revoked_at IS NULL"));
        assertTrue(first.contains("FOR UPDATE OF d"));
        assertTrue(second.contains("c.device_id=:device_id"));
        assertTrue(second.contains("c.client_cert_sha256_fingerprint=:fingerprint"));
        assertTrue(second.contains("c.status='active'"));
        assertTrue(second.contains("c.revoked_at IS NULL"));
        assertTrue(second.contains("issued_at<=clock_timestamp()"));
        assertTrue(second.contains("expires_at>clock_timestamp()"));
        assertTrue(second.contains("FOR UPDATE OF c"));
        verify(credential).setParameter("fingerprint", identity.sha256Fingerprint());
    }

    @Test
    void mismatchedOrMissingIdentityIsRejectedBeforeAnyDatabaseQuery() {
        var other = new VerifiedDeviceCertificate(UUID.randomUUID(), "c".repeat(64));
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, other, deviceId));
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, null, deviceId));
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, identity, null));
        verifyNoInteractions(manager);
    }

    @Test
    void unknownOrRevokedDeviceCannotEvenQueryTheCredential() {
        wire();
        when(device.getResultList()).thenReturn(List.of());
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId));
        verify(credential, never()).getResultList();
        verify(manager, Mockito.times(1)).createNativeQuery(anyString());
    }

    @Test
    void missingInactiveOrExpiredCertificateFailsAfterDeviceLock() {
        wire();
        when(device.getResultList()).thenReturn(List.of(deviceId));
        when(credential.getResultList()).thenReturn(List.of());
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId));
        verify(credential).getResultList();
    }

    @Test
    void ambiguousDatabaseRowsFailClosed() {
        wire();
        when(device.getResultList()).thenReturn(List.of(deviceId));
        when(credential.getResultList()).thenReturn(
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertFalse(JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId));
    }
}
