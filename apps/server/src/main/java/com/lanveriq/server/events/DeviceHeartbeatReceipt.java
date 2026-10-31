package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Returned to an internal caller only after the transaction proxy commits. */
public record DeviceHeartbeatReceipt(
        @JsonProperty("server_time") Instant serverTime,
        @JsonProperty("request_id") UUID requestId) {
}
