package com.lanveriq.server.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V0.1 bounded complete asset snapshot; not heartbeat or activity evidence.
 * The device/group/identity are never authenticated by these JSON fields.
 */
public record DeviceInventorySnapshot(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("snapshot_id") UUID snapshotId,
        @JsonProperty("inventory_revision") long inventoryRevision,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("observed_at") String observedAt,
        @JsonProperty("hardware") Hardware hardware,
        @JsonProperty("os") OperatingSystem os,
        @JsonProperty("interfaces") List<NetworkInterface> interfaces,
        @JsonProperty("volumes") List<Volume> volumes,
        @JsonProperty("capabilities") Map<String, String> capabilities) {

    public record Hardware(String manufacturer, String model, Cpu cpu, Memory memory) {
    }

    public record Cpu(String model, String arch,
                      @JsonProperty("physical_cores") Integer physicalCores,
                      @JsonProperty("logical_cores") Integer logicalCores) {
    }

    public record Memory(@JsonProperty("total_bytes") Long totalBytes) {
    }

    public record OperatingSystem(String family, String name, String version, String build,
                                  @JsonProperty("kernel_release") String kernelRelease,
                                  String arch) {
    }

    public record NetworkInterface(String key, String name, String kind,
                                   @JsonProperty("mac_address") String macAddress,
                                   @JsonProperty("link_state") String linkState,
                                   @JsonProperty("is_virtual") Boolean isVirtual,
                                   List<Address> addresses) {
    }

    public record Address(String ip, @JsonProperty("prefix_length") Integer prefixLength,
                          String family, String scope) {
    }

    public record Volume(String key, @JsonProperty("mount_point") String mountPoint,
                         String filesystem, @JsonProperty("total_bytes") Long totalBytes,
                         @JsonProperty("free_bytes") Long freeBytes,
                         @JsonProperty("is_system") Boolean isSystem) {
    }
}
