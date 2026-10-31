package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Internal v1 activation envelope; never provides or supplies client identity. */
public record DeviceActivationRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("agent_version") String agentVersion) {
}
