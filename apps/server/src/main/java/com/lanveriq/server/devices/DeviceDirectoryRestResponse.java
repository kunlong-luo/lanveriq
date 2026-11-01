package com.lanveriq.server.devices;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Health-only public wire projection. Every field is deliberately allowlisted.
 * Never serialize a JPA entity or a caller-supplied group/role as authorization.
 */
public final class DeviceDirectoryRestResponse {
    private DeviceDirectoryRestResponse() {
    }

    @Schema(name = "DeviceHealthSummary")
    public record DeviceItem(
            @JsonProperty("device_id") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID deviceId,
            @JsonProperty("group_id") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID groupId,
            @JsonProperty("group_name") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String groupName,
            @JsonProperty("label") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @JsonProperty("enrollment_state") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String enrollmentState,
            @JsonProperty("last_seen_at") Instant lastSeenAt,
            @JsonProperty("heartbeat_received_at") Instant heartbeatReceivedAt,
            @JsonProperty("agent_version") String agentVersion,
            @JsonProperty("queue_count") Long queueCount,
            @JsonProperty("queue_bytes") Long queueBytes,
            @JsonProperty("os_family") String osFamily,
            @JsonProperty("os_name") String osName,
            @JsonProperty("cpu_model") String cpuModel,
            @JsonProperty("memory_total_bytes") Long memoryTotalBytes,
            @JsonProperty("inventory_received_at") Instant inventoryReceivedAt) {

        public static DeviceItem from(DeviceDirectoryEntry source) {
            return new DeviceItem(
                    source.deviceId(), source.groupId(), source.groupName(),
                    source.label(), source.enrollmentState(), source.lastSeenAt(),
                    source.heartbeatReceivedAt(), source.agentVersion(),
                    source.queueCount(), source.queueBytes(), source.osFamily(),
                    source.osName(), source.cpuModel(), source.memoryTotalBytes(),
                    source.inventoryReceivedAt());
        }
    }

    @Schema(name = "ScopedDeviceDirectoryPage")
    public record Page(
            @JsonProperty("items") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DeviceItem> items,
            @JsonProperty("next_cursor") UUID nextCursor) {

        public Page {
            items = List.copyOf(items);
        }

        public static Page from(DeviceDirectoryPage page) {
            return new Page(page.items().stream().map(DeviceItem::from).toList(),
                    page.nextCursor());
        }
    }
}
