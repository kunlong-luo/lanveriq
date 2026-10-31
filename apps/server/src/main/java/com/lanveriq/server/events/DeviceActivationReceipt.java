package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Only returned after the Spring transaction proxy successfully commits. */
public record DeviceActivationReceipt(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("enrollment_state") String enrollmentState,
        @JsonProperty("server_time") Instant serverTime,
        @JsonProperty("request_id") UUID requestId) {
}
