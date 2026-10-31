package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class DeviceEventBatchPolicyTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate credential =
            new VerifiedDeviceCertificate(deviceId, "a".repeat(64));

    private DeviceEventBatch.Event event(long sequence) {
        return new DeviceEventBatch.Event(UUID.randomUUID(), sequence, 1,
                "agent_transport", "collection_gap", 1,
                "2026-10-30T09:00:00Z",
                mapper.readTree("""
                        {"reason":"quota_exceeded","range_end":"2026-10-30T09:00:00Z",
                         "affected_source":"agent_transport","range_start":"2026-10-30T08:57:00Z"}
                        """));
    }

    private DeviceEventBatch request(DeviceEventBatch.Event... events) {
        return new DeviceEventBatch(1, UUID.randomUUID(), deviceId, List.of(events));
    }

    @Test
    void approvedMinimalOperationalGapIsAcceptedInProduction() {
        var parsed = new DeviceEventBatchPolicy(mapper, false)
                .validate(deviceId, credential, request(event(1), event(3)));
        assertEquals(List.of(1L, 3L), parsed.stream()
                .map(DeviceEventBatchPolicy.CanonicalEvent::deviceSequence).toList());
        assertEquals(64, parsed.getFirst().digest().length());
    }

    @Test
    void propertyOrderingCannotAlterIdenticalRetryFingerprint() {
        var first = event(3);
        var reversed = new DeviceEventBatch.Event(first.eventId(), first.deviceSequence(),
                first.schemaVersion(), first.source(), first.eventType(),
                first.policyVersion(), first.occurredAt(), mapper.readTree("""
                        {"range_start":"2026-10-30T08:57:00Z",
                         "affected_source":"agent_transport","range_end":"2026-10-30T09:00:00Z",
                         "reason":"quota_exceeded"}
                        """));
        var policy = new DeviceEventBatchPolicy(mapper, false);
        var before = policy.validate(deviceId, credential, request(first)).getFirst();
        var after = policy.validate(deviceId, credential, request(reversed)).getFirst();
        assertEquals(before.digest(), after.digest());
        var changed = new DeviceEventBatch.Event(first.eventId(), first.deviceSequence(),
                1, first.source(), first.eventType(), 2, first.occurredAt(), first.payload());
        assertNotEquals(before.digest(),
                policy.validate(deviceId, credential, request(changed)).getFirst().digest());
    }

    @Test
    void rejectsWrongDeviceAndWrongCertificateBeforeDatabaseAccess() {
        var repo = Mockito.mock(EventIngestionRepository.class);
        var service = new DeviceEventIngestionService(repo, mapper);
        assertThrows(DeviceBatchRejection.class,
                () -> service.ingest(credential, UUID.randomUUID(), request(event(1))));
        assertThrows(DeviceBatchRejection.class,
                () -> service.ingest(
                        new VerifiedDeviceCertificate(UUID.randomUUID(), "a".repeat(64)),
                        deviceId, request(event(1))));
        verifyNoInteractions(repo);
    }

    @Test
    void malformedEventsDuplicateUuidAndOrderFailBeforeDatabaseWrite() {
        var policy = new DeviceEventBatchPolicy(mapper, false);
        var first = event(2);
        for (var wrong : List.of(
                request(first, first),
                request(event(3), event(2)),
                request(event(0)),
                request(),
                new DeviceEventBatch(2, UUID.randomUUID(), deviceId, List.of(first)),
                new DeviceEventBatch(1, new UUID(0, 0), deviceId, List.of(first)))) {
            assertThrows(DeviceBatchRejection.class,
                    () -> policy.validate(deviceId, credential, wrong));
        }
        var sameEventIdDifferentSeq = new DeviceEventBatch.Event(
                first.eventId(), 5, 1, first.source(), first.eventType(),
                first.policyVersion(), first.occurredAt(), first.payload());
        assertThrows(DeviceBatchRejection.class, () -> policy.validate(
                deviceId, credential, request(first, sameEventIdDifferentSeq)));
    }

    @Test
    void rejectsUnapprovedSourceUnknownFieldsAndBadGapWindow() {
        var policy = new DeviceEventBatchPolicy(mapper, false);
        var valid = event(1);
        var fixture = new DeviceEventBatch.Event(valid.eventId(), 1, 1,
                "synthetic_test", "protocol_fixture", 1,
                valid.occurredAt(), mapper.readTree("{\"synthetic\":true}"));
        assertThrows(DeviceBatchRejection.class,
                () -> policy.validate(deviceId, credential, request(fixture)));
        assertEquals(1, new DeviceEventBatchPolicy(mapper, true)
                .validate(deviceId, credential, request(fixture)).size());

        for (var payload : List.of(
                "{\"reason\":\"quota_exceeded\",\"range_end\":\"2026-10-30T09:00:00Z\",\"affected_source\":\"agent_transport\",\"range_start\":\"2026-10-30T08:57:00Z\",\"secret\":\"bad\"}",
                "{\"reason\":\"quota_exceeded\",\"range_end\":\"2026-10-30T08:00:00Z\",\"affected_source\":\"agent_transport\",\"range_start\":\"2026-10-30T09:00:00Z\"}",
                "{\"reason\":\"raw_screen\",\"range_end\":\"2026-10-30T09:00:00Z\",\"affected_source\":\"agent_transport\",\"range_start\":\"2026-10-30T08:57:00Z\"}")) {
            var changed = new DeviceEventBatch.Event(valid.eventId(), 1, 1,
                    valid.source(), valid.eventType(), 1, valid.occurredAt(),
                    mapper.readTree(payload));
            assertThrows(DeviceBatchRejection.class,
                    () -> policy.validate(deviceId, credential, request(changed)));
        }
    }

    @Test
    void onlyStrictZuluUtcWireTimestampsAreAccepted() {
        var valid = event(1);
        var offset = new DeviceEventBatch.Event(valid.eventId(), 1, 1,
                valid.source(), valid.eventType(), 1,
                "2026-10-30T10:00:00+01:00", valid.payload());
        assertThrows(DeviceBatchRejection.class, () ->
                new DeviceEventBatchPolicy(mapper, false)
                        .validate(deviceId, credential, request(offset)));

        var invalid = new DeviceEventBatch.Event(valid.eventId(), 1, 1,
                valid.source(), valid.eventType(), 1,
                "garbled-timestamp", valid.payload());
        assertThrows(DeviceBatchRejection.class, () ->
                new DeviceEventBatchPolicy(mapper, false)
                        .validate(deviceId, credential, request(invalid)));
    }

    @Test
    void capsEventCountBeforeInsert() {
        var policy = new DeviceEventBatchPolicy(mapper, false);
        var events = new ArrayList<DeviceEventBatch.Event>();
        for (int i = 1; i <= 101; i++) {
            events.add(event(i));
        }
        assertThrows(DeviceBatchRejection.class, () ->
                policy.validate(deviceId, credential,
                        new DeviceEventBatch(1, UUID.randomUUID(), deviceId, events)));
    }

    @Test
    void serviceDoesNotAckBeforeRepositoryVerification() {
        var repo = Mockito.mock(EventIngestionRepository.class);
        var service = new DeviceEventIngestionService(repo, mapper);
        var batch = request(event(1), event(2));
        var ack = service.ingest(credential, deviceId, batch);
        assertEquals(1, ack.schemaVersion());
        assertEquals(List.of(1L, 2L), ack.acceptedSequences());
        verify(repo).appendOrVerify(Mockito.eq(credential), Mockito.anyList());

        Mockito.doThrow(new DeviceBatchRejection(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT))
                .when(repo).appendOrVerify(Mockito.eq(credential), Mockito.anyList());
        var failed = assertThrows(DeviceBatchRejection.class,
                () -> service.ingest(credential, deviceId, batch));
        assertEquals(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT, failed.reason());
    }
}
