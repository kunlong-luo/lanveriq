package com.lanveriq.server.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Durable database result; returned only after the transaction commits. */
public record DeviceInventoryReceipt(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("snapshot_id") UUID snapshotId,
        @JsonProperty("inventory_revision") long inventoryRevision,
        @JsonProperty("outcome") Outcome outcome,
        @JsonProperty("server_time") Instant serverTime) {

    public enum Outcome {
        applied, duplicate, stale_ignored
    }
}
