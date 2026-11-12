package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Positive, authenticated, current-credential evidence of a durable
 * heartbeat request-id ledger entry. Server time is lookup time, NOT the
 * original commit time. Absence cannot prove non-delivery.
 */
public record DeviceHeartbeatLookupReceipt(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("committed") boolean committed,
        @JsonProperty("server_time") Instant serverTime) {
}
