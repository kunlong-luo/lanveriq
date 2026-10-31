package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** V0.1 wire fields; never derive device identity from this untrusted DTO. */
public record DeviceEventBatch(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("batch_id") UUID batchId,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("events") List<Event> events) {

    public record Event(
            @JsonProperty("event_id") UUID eventId,
            @JsonProperty("device_sequence") long deviceSequence,
            @JsonProperty("schema_version") int schemaVersion,
            @JsonProperty("source") String source,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("policy_version") long policyVersion,
            @JsonProperty("occurred_at") Instant occurredAt,
            @JsonProperty("payload") JsonNode payload) {
    }
}
