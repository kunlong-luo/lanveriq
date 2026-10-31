package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class JpaDeviceRevocationRepositoryTest {
    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query authorizedLock = Mockito.mock(Query.class);
    private final Query revokeCertificates = Mockito.mock(Query.class);
    private final Query revokeDevice = Mockito.mock(Query.class);
    private final Query audit = Mockito.mock(Query.class);
    private final UUID deviceId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) return authorizedLock;
            if (sql.contains("UPDATE device_credentials")) return revokeCertificates;
            if (sql.contains("UPDATE devices")) return revokeDevice;
            if (sql.contains("INSERT INTO admin_access_log")) return audit;
            throw new AssertionError("Unexpected SQL in revocation flow");
        });
        for (Query query : List.of(authorizedLock, revokeCertificates, revokeDevice, audit)) {
            when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        }
    }

    @Test
    void freshAdminGroupAuthorizationThenRevokeCredentialDeviceAndAudit() {
        wire();
        when(authorizedLock.getResultList()).thenReturn(List.of("active"));
        when(revokeDevice.executeUpdate()).thenReturn(1);
        assertTrue(new JpaDeviceRevocationRepository(manager).revoke(adminId, deviceId));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(4)).createNativeQuery(sql.capture());
        var statements = sql.getAllValues();
        assertTrue(statements.get(0).contains("FOR UPDATE OF d"));
        assertTrue(statements.get(0).contains("u.disabled_at IS NULL"));
        assertTrue(statements.get(0).contains("owner_security_admin"));
        assertTrue(statements.get(0).contains("s.device_group_id=d.group_id"));
        assertTrue(statements.get(1).contains("status='revoked'"));
        assertTrue(statements.get(1).contains("revoked_at=COALESCE"));
        assertTrue(statements.get(2).contains("enrollment_state='revoked'"));
        assertTrue(statements.get(3).contains("'device.revoke','allowed'"));
        verify(authorizedLock).setParameter("admin_id", adminId);
        verify(authorizedLock).setParameter("device_id", deviceId);
        verify(revokeCertificates).executeUpdate();
        verify(revokeDevice).executeUpdate();
        verify(audit).executeUpdate();
    }

    @Test
    void unauthorizedOrUnknownDeviceDeniesBeforeWritesAndAvoidsEnumeration() {
        wire();
        when(authorizedLock.getResultList()).thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceRevocationRepository(manager).revoke(adminId, deviceId));
        verify(revokeCertificates, never()).executeUpdate();
        verify(revokeDevice, never()).executeUpdate();
        verify(audit, never()).executeUpdate();
    }

    @Test
    void sameAdminCanRetryAlreadyRevokedWithoutAnyNewWritesOrAudit() {
        wire();
        when(authorizedLock.getResultList()).thenReturn(List.of("revoked"));
        assertFalse(new JpaDeviceRevocationRepository(manager).revoke(adminId, deviceId));
        verify(revokeCertificates, never()).executeUpdate();
        verify(revokeDevice, never()).executeUpdate();
        verify(audit, never()).executeUpdate();
    }

    @Test
    void retiredDeviceIsNeverSilentlyTransitionedToRevoked() {
        wire();
        when(authorizedLock.getResultList()).thenReturn(List.of("retired"));
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceRevocationRepository(manager).revoke(adminId, deviceId));
        verify(revokeCertificates, never()).executeUpdate();
    }

    @Test
    void failedConditionalDeviceUpdateNeverCreatesAuditEntry() {
        wire();
        when(authorizedLock.getResultList()).thenReturn(List.of("pending_certificate"));
        when(revokeDevice.executeUpdate()).thenReturn(0);
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceRevocationRepository(manager).revoke(adminId, deviceId));
        verify(audit, never()).executeUpdate();
    }
}
