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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class JpaEventIngestionRepositoryTest {

    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query auth = Mockito.mock(Query.class);
    private final Query credentialLock = Mockito.mock(Query.class);
    private final Query insert = Mockito.mock(Query.class);
    private final Query compare = Mockito.mock(Query.class);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate credential =
            new VerifiedDeviceCertificate(deviceId, "e".repeat(64));
    private final JsonMapper mapper = JsonMapper.builder().build();

    private DeviceEventBatchPolicy.CanonicalEvent event(long sequence) {
        return new DeviceEventBatchPolicy.CanonicalEvent(
                UUID.randomUUID(), sequence, 1,
                "agent_transport", "collection_gap", 1,
                Instant.parse("2026-10-30T09:00:00Z"),
                mapper.readTree("{\"reason\":\"quota_exceeded\"}"), "a".repeat(64));
    }

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) return auth;
            if (sql.contains("FOR UPDATE OF c")) return credentialLock;
            if (sql.contains("INSERT INTO endpoint_events")) {
                return insert;
            }
            return compare;
        });
        for (Query q : List.of(auth, credentialLock, insert, compare)) {
            when(q.setParameter(anyString(), Mockito.any())).thenReturn(q);
        }
    }

    @Test
    void trustedDeviceIsLockedAndIdempotentBatchIsRechecked() {
        wire();
        var e = event(7);
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(credentialLock.getResultList()).thenReturn(List.of(deviceId));
        when(compare.getResultList()).thenReturn(
                Collections.singletonList(new Object[]{7L, e.eventId(), e.digest()}));

        new JpaEventIngestionRepository(manager, mapper)
                .appendOrVerify(credential, List.of(e));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(4)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("enrollment_state = 'active'"));
        assertTrue(sql.getAllValues().get(0).contains("FOR UPDATE OF d"));
        assertTrue(sql.getAllValues().get(1).contains("FOR UPDATE OF c"));
        assertTrue(sql.getAllValues().get(1).contains("client_cert_sha256_fingerprint"));
        assertTrue(sql.getAllValues().get(1).contains("expires_at>clock_timestamp()"));
        assertTrue(sql.getAllValues().get(2).contains("jsonb_to_recordset"));
        assertTrue(sql.getAllValues().get(2).contains("ON CONFLICT DO NOTHING"));
        verify(credentialLock).setParameter("fingerprint", credential.sha256Fingerprint());
        verify(insert).executeUpdate();
        verify(compare).setParameter("device_sequence", 7L);
        verify(compare).setParameter("event_id", e.eventId());
    }

    @Test
    void unknownRevokedOrExpiredCertificateFailsBeforeAnyInsert() {
        wire();
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(credentialLock.getResultList()).thenReturn(List.of());
        var error = assertThrows(DeviceBatchRejection.class, () ->
                new JpaEventIngestionRepository(manager, mapper)
                        .appendOrVerify(credential, List.of(event(1))));
        assertEquals(DeviceBatchRejection.Reason.REVOKED_DEVICE, error.reason());
        verify(insert, never()).executeUpdate();
    }

    @Test
    void sameSequenceChangedPayloadCannotBeAcknowledged() {
        wire();
        var e = event(2);
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(compare.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{2L, e.eventId(), "b".repeat(64)}));
        var error = assertThrows(DeviceBatchRejection.class, () ->
                new JpaEventIngestionRepository(manager, mapper)
                        .appendOrVerify(credential, List.of(e)));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, error.reason());
    }

    @Test
    void reusedEventIdWithDifferentSequenceAlsoConflicts() {
        wire();
        var e = event(3);
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(compare.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{99L, e.eventId(), e.digest()}));
        var error = assertThrows(DeviceBatchRejection.class, () ->
                new JpaEventIngestionRepository(manager, mapper)
                        .appendOrVerify(credential, List.of(e)));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, error.reason());
    }

    @Test
    void twoIndependentMatchingRowsForEitherUniqueKeyAreConflicts() {
        wire();
        var e = event(3);
        when(auth.getResultList()).thenReturn(List.of(deviceId));
        when(compare.getResultList()).thenReturn(List.of(
                new Object[]{3L, UUID.randomUUID(), e.digest()},
                new Object[]{9L, e.eventId(), e.digest()}));
        var error = assertThrows(DeviceBatchRejection.class, () ->
                new JpaEventIngestionRepository(manager, mapper)
                        .appendOrVerify(credential, List.of(e)));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, error.reason());
    }
}
