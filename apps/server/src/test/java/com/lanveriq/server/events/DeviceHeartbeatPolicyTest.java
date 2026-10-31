package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeviceHeartbeatPolicyTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC);
    private final UUID deviceId = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(deviceId, "a".repeat(64));
    private final DeviceHeartbeatPolicy policy =
            new DeviceHeartbeatPolicy(JsonMapper.builder().build(), CLOCK);

    private DeviceHeartbeat heartbeat() {
        return new DeviceHeartbeat(1, deviceId, "2026-10-30T23:59:00Z",
                "0.1.0", 1, "running",
                Map.of("hardware_inventory", "not_collected", "app_activity", "unsupported"),
                0, 0, null, null, 0, false, List.of());
    }

    @Test
    void validHeartbeatPreservesUnknownCoverageAndMissingTimestamps() {
        var state = policy.validate(identity, deviceId, heartbeat());
        assertEquals("not_collected", state.capabilities().get("hardware_inventory"));
        assertEquals("unsupported", state.capabilities().get("app_activity"));
        assertNull(state.oldestPendingAt());
        assertNull(state.lastSuccessfulUploadAt());
        assertEquals(0, state.queueCount());
    }

    @Test
    void doesNotTrustUnboundDeviceOrUnrecognizedSchema() {
        assertEquals(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        policy.validate(identity, UUID.randomUUID(), heartbeat())).reason());
        var other = new DeviceHeartbeat(2, deviceId, "2026-10-30T23:59:00Z",
                "0.1.0", 1, "running", Map.of(), 0, 0, null, null, 0, false, List.of());
        assertEquals(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        policy.validate(identity, deviceId, other)).reason());
    }

    @Test
    void rejectsArbitraryErrorTextRatherThanStoringSensitiveInformation() {
        var original = heartbeat();
        var unsafe = new DeviceHeartbeat(1, deviceId, original.sentAt(), "0.1.0", 1,
                "running", Map.of("hardware_inventory", "supported"),
                0, 0, null, null, 0, false,
                List.of("file /home/private_user/secret.txt could not be opened"));
        assertEquals(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        policy.validate(identity, deviceId, unsafe)).reason());
    }

    @Test
    void rejectsNegativeCountsFakeCapabilityAndFutureDeviceClock() {
        var negative = new DeviceHeartbeat(1, deviceId, "2026-10-30T23:59:00Z",
                "0.1.0", 1, "running", Map.of(), -1, 0, null, null, 0, false, List.of());
        var invented = new DeviceHeartbeat(1, deviceId, "2026-10-30T23:59:00Z",
                "0.1.0", 1, "running", Map.of("app_activity", "fully_monitored"),
                0, 0, null, null, 0, false, List.of());
        var future = new DeviceHeartbeat(1, deviceId, "2026-11-02T00:00:00Z",
                "0.1.0", 1, "running", Map.of(), 0, 0, null, null, 0, false, List.of());
        for (var invalid : List.of(negative, invented, future)) {
            assertEquals(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA,
                    assertThrows(DeviceHeartbeatRejection.class, () ->
                            policy.validate(identity, deviceId, invalid)).reason());
        }
    }

    @Test
    void rejectsOversizedCapabilitiesBeforeAnyDatabaseWrite() {
        var many = new java.util.HashMap<String, String>();
        for (int i = 0; i < 33; i++) {
            many.put("cap_" + i, "not_collected");
        }
        var invalid = new DeviceHeartbeat(1, deviceId, "2026-10-30T23:59:00Z",
                "0.1.0", 1, "running", many, 0, 0, null, null, 0, false, List.of());
        assertEquals(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        policy.validate(identity, deviceId, invalid)).reason());
    }
}
