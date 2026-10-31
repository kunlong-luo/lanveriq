package com.lanveriq.server.devices;

import java.time.Instant;
import java.util.UUID;

/**
 * Health-only V0.1 device projection for a future scoped console API.
 * Deliberately contains no MAC, IP, NIC, asset tag, event or credential data.
 * Missing heartbeat/inventory is null, NEVER a fabricated zero or "online".
 */
public record DeviceDirectoryEntry(
        UUID deviceId, UUID groupId, String groupName, String label,
        String enrollmentState, Instant lastSeenAt,
        Instant heartbeatReceivedAt, String agentVersion,
        Long queueCount, Long queueBytes,
        String osFamily, String osName, String cpuModel,
        Long memoryTotalBytes, Instant inventoryReceivedAt) {
}
