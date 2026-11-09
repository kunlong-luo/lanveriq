package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Activation attempt correlation UUID is not a device identity or authorization. */
public record DeviceActivationRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("agent_version") String agentVersion,
        @JsonProperty("request_id") UUID requestId) {
}
