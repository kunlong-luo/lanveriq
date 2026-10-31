package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class DeviceInventoryServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(deviceId, "a".repeat(64));
    private final DeviceInventoryRepository repository =
            Mockito.mock(DeviceInventoryRepository.class);
    private final JsonMapper mapper = new JsonMapper();
    private final DeviceInventoryService service =
            new DeviceInventoryService(repository, mapper, CLOCK);

    private DeviceInventorySnapshot valid() {
        return new DeviceInventorySnapshot(1, UUID.randomUUID(), 17, deviceId,
                "2026-10-31T11:59:00Z",
                new DeviceInventorySnapshot.Hardware("ExampleVendor", "ExampleBook",
                        new DeviceInventorySnapshot.Cpu("Example CPU", "x86_64", 8, 16),
                        new DeviceInventorySnapshot.Memory(32_000_000_000L)),
                new DeviceInventorySnapshot.OperatingSystem("linux", "Pop!_OS",
                        "24.04", null, "synthetic-kernel", "x86_64"),
                List.of(new DeviceInventorySnapshot.NetworkInterface(
                                "nic-01", "eth0", "ethernet", "02:00:00:00:00:01",
                                "up", false, List.of(
                                new DeviceInventorySnapshot.Address("192.0.2.10", 24, "ipv4", "private"),
                                new DeviceInventorySnapshot.Address("2001:db8::1", 64, "ipv6", "global"))),
                        new DeviceInventorySnapshot.NetworkInterface(
                                "nic-02", "wlan0", "wifi", null, "down", false, List.of())),
                List.of(new DeviceInventorySnapshot.Volume("root", "/", "ext4",
                        500_000_000_000L, 250_000_000_000L, true)),
                Map.of("gpu_inventory", "not_collected",
                       "desktop_session", "unavailable"));
    }

    @Test
    void validMultiNicSnapshotComputesDigestAndCallsRepositoryOnce() {
        var snapshot = valid();
        var accepted = new DeviceInventoryReceipt(1, deviceId, snapshot.snapshotId(),
                snapshot.inventoryRevision(), DeviceInventoryReceipt.Outcome.applied,
                CLOCK.instant());
        when(repository.persist(any(), any(), any(), any(), any(), any()))
                .thenReturn(accepted);
        assertEquals(accepted, service.ingest(identity, deviceId, snapshot));
        var digest = ArgumentCaptor.forClass(String.class);
        verify(repository).persist(Mockito.eq(identity), Mockito.eq(deviceId),
                Mockito.eq(snapshot), digest.capture(), Mockito.anyString(),
                Mockito.eq(Instant.parse(snapshot.observedAt())));
        assertEquals(64, digest.getValue().length());
    }

    @Test
    void wrongIdentityAndMalformedInputsNeverReachPersistence() {
        var original = valid();
        var wrong = new VerifiedDeviceCertificate(UUID.randomUUID(), "a".repeat(64));
        assertEquals(DeviceInventoryRejection.Reason.INVALID_REQUEST,
                assertThrows(DeviceInventoryRejection.class,
                        () -> service.ingest(wrong, deviceId, original)).reason());
        assertEquals(DeviceInventoryRejection.Reason.INVALID_REQUEST,
                assertThrows(DeviceInventoryRejection.class,
                        () -> service.ingest(identity, UUID.randomUUID(), original)).reason());
        var forgedPath = new DeviceInventorySnapshot(original.schemaVersion(),
                original.snapshotId(), original.inventoryRevision(), UUID.randomUUID(),
                original.observedAt(), original.hardware(), original.os(),
                original.interfaces(), original.volumes(), original.capabilities());
        assertEquals(DeviceInventoryRejection.Reason.INVALID_REQUEST,
                assertThrows(DeviceInventoryRejection.class,
                        () -> service.ingest(identity, deviceId, forgedPath)).reason());
        verify(repository, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidIpMacCapacityOrFutureTimestampAreRefused() {
        var good = valid();
        var wrongAddress = new DeviceInventorySnapshot.NetworkInterface("nic-01",
                "eth0", "ethernet", "02:00:00:00:00:01", "up", false,
                List.of(new DeviceInventorySnapshot.Address("evil.example", 24, "ipv4", "private")));
        var invalidIp = new DeviceInventorySnapshot(good.schemaVersion(), good.snapshotId(),
                good.inventoryRevision(), deviceId, good.observedAt(), good.hardware(), good.os(),
                List.of(wrongAddress), good.volumes(), good.capabilities());
        assertThrows(DeviceInventoryRejection.class,
                () -> service.ingest(identity, deviceId, invalidIp));
        var future = new DeviceInventorySnapshot(1, good.snapshotId(), 1, deviceId,
                "2026-11-01T00:00:00Z", good.hardware(), good.os(),
                good.interfaces(), good.volumes(), good.capabilities());
        assertThrows(DeviceInventoryRejection.class,
                () -> service.ingest(identity, deviceId, future));
        var hugeRam = new DeviceInventorySnapshot(1, good.snapshotId(), 1, deviceId,
                good.observedAt(),
                new DeviceInventorySnapshot.Hardware(null, null,
                        new DeviceInventorySnapshot.Cpu(null, null, -1, 2),
                        new DeviceInventorySnapshot.Memory(-1L)),
                good.os(), good.interfaces(), good.volumes(), good.capabilities());
        assertThrows(DeviceInventoryRejection.class,
                () -> service.ingest(identity, deviceId, hugeRam));
        verify(repository, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void duplicateNicKeysAndUnknownOrNullCapabilityAreRejected() {
        var good = valid();
        var repeat = new DeviceInventorySnapshot(1, good.snapshotId(), 17, deviceId,
                good.observedAt(), good.hardware(), good.os(),
                List.of(good.interfaces().getFirst(), good.interfaces().getFirst()),
                good.volumes(), good.capabilities());
        assertThrows(DeviceInventoryRejection.class,
                () -> service.ingest(identity, deviceId, repeat));
        var wrongCapability = new DeviceInventorySnapshot(1, good.snapshotId(), 17, deviceId,
                good.observedAt(), good.hardware(), good.os(), good.interfaces(),
                good.volumes(), Map.of("gpu_inventory", "enabled_by_default"));
        assertThrows(DeviceInventoryRejection.class,
                () -> service.ingest(identity, deviceId, wrongCapability));
        verify(repository, never()).persist(any(), any(), any(), any(), any(), any());
    }
}
