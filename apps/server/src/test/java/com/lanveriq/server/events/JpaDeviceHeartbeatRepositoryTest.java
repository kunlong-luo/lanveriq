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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class JpaDeviceHeartbeatRepositoryTest {

    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query auth = Mockito.mock(Query.class);
    private final Query credentialLock = Mockito.mock(Query.class);
    private final Query deviceUpdate = Mockito.mock(Query.class);
    private final Query snapshot = Mockito.mock(Query.class);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(deviceId, "b".repeat(64));

    private DeviceHeartbeatPolicy.ValidHeartbeat heartbeat() {
        return new DeviceHeartbeatPolicy.ValidHeartbeat(
                Instant.parse("2026-10-30T23:59:00Z"), "0.1.0", "running", 1,
                Map.of("hardware_inventory", "not_collected"), 2, 512,
                null, null, 0, false, List.of());
    }

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) return auth;
            if (sql.contains("FOR UPDATE OF c")) return credentialLock;
            if (sql.contains("UPDATE devices")) {
                return deviceUpdate;
            }
            return snapshot;
        });
        for (Query query : List.of(auth, credentialLock, deviceUpdate, snapshot)) {
            when(query.setParameter(anyString(), Mockito.nullable(Object.class))).thenReturn(query);
        }
    }

    @Test
    void registeredActiveDeviceIsLockedBeforeAnyHeartbeatWrite() {
        wire();
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(credentialLock.getResultList()).thenReturn(List.of(deviceId));

        new JpaDeviceHeartbeatRepository(manager, JsonMapper.builder().build())
                .record(identity, deviceId, heartbeat());

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(4)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("enrollment_state='active'"));
        assertTrue(sql.getAllValues().get(0).contains("FOR UPDATE OF d"));
        assertTrue(sql.getAllValues().get(1).contains("FOR UPDATE OF c"));
        assertTrue(sql.getAllValues().get(1).contains("client_cert_sha256_fingerprint"));
        assertTrue(sql.getAllValues().get(1).contains("expires_at>clock_timestamp()"));
        assertTrue(sql.getAllValues().get(2).contains("clock_timestamp()"));
        assertTrue(sql.getAllValues().get(3).contains("ON CONFLICT (device_id) DO UPDATE"));
        verify(credentialLock).setParameter("fingerprint", identity.sha256Fingerprint());
        verify(deviceUpdate).executeUpdate();
        verify(snapshot).setParameter("capabilities", "{\"hardware_inventory\":\"not_collected\"}");
        verify(snapshot).executeUpdate();
    }

    @Test
    void forgedExpiredOrRevokedCredentialCannotUpdateLastSeen() {
        wire();
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(credentialLock.getResultList()).thenReturn(List.of());
        var error = assertThrows(DeviceHeartbeatRejection.class, () ->
                new JpaDeviceHeartbeatRepository(manager, JsonMapper.builder().build())
                        .record(identity, deviceId, heartbeat()));
        assertEquals(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE, error.reason());
        verify(deviceUpdate, never()).executeUpdate();
        verify(snapshot, never()).executeUpdate();
    }
}
