package com.lanveriq.server.devices;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Mutually exclusive server-derived fleet states in the caller's CURRENT
 * authorized groups; pending and retired devices are not called "offline".
 */
@Schema(name = "ScopedMonitoringOverview")
public record MonitoringOverview(
        @JsonProperty("as_of") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant asOf,
        @JsonProperty("total") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
        @JsonProperty("online") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long online,
        @JsonProperty("stale") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long stale,
        @JsonProperty("offline") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long offline,
        @JsonProperty("revoked") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long revoked,
        @JsonProperty("pending") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long pending,
        @JsonProperty("retired") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long retired) {
    public MonitoringOverview {
        if (asOf == null || total < 0 || online < 0 || stale < 0 ||
                offline < 0 || revoked < 0 || pending < 0 || retired < 0 ||
                online + stale + offline + revoked + pending + retired != total) {
            throw new IllegalArgumentException("Invalid scoped monitoring totals");
        }
    }
}
