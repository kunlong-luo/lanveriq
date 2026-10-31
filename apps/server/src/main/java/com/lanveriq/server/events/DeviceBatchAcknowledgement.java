package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Construct only inside a committing Spring @Transactional service call. */
public record DeviceBatchAcknowledgement(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("accepted_sequences") List<Long> acceptedSequences,
        @JsonProperty("server_time") Instant serverTime,
        @JsonProperty("request_id") UUID requestId) {
}
