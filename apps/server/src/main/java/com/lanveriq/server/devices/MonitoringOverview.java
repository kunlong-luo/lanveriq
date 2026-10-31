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
        @JsonProperty("as_of") Instant asOf,
        @JsonProperty("total") long total,
        @JsonProperty("online") long online,
        @JsonProperty("stale") long stale,
        @JsonProperty("offline") long offline,
        @JsonProperty("revoked") long revoked,
        @JsonProperty("pending") long pending,
        @JsonProperty("retired") long retired) {
    public MonitoringOverview {
        if (asOf == null || total < 0 || online < 0 || stale < 0 ||
                offline < 0 || revoked < 0 || pending < 0 || retired < 0 ||
                online + stale + offline + revoked + pending + retired != total) {
            throw new IllegalArgumentException("Invalid scoped monitoring totals");
        }
    }
}
