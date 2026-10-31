package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * D0 V0.1 Agent heartbeat wire shape. Never authenticate using deviceId or
 * sentAt from this DTO; only a separately verified mTLS peer may supply identity.
 * No HTTP controller is wired to this type.
 */
public record DeviceHeartbeat(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("sent_at") String sentAt,
        @JsonProperty("agent_version") String agentVersion,
        @JsonProperty("policy_version") long policyVersion,
        @JsonProperty("status") String status,
        @JsonProperty("capabilities") Map<String, String> capabilities,
        @JsonProperty("queue_count") long queueCount,
        @JsonProperty("queue_bytes") long queueBytes,
        @JsonProperty("oldest_pending_at") String oldestPendingAt,
        @JsonProperty("last_successful_upload_at") String lastSuccessfulUploadAt,
        @JsonProperty("dropped_count") long droppedCount,
        @JsonProperty("integrity_degraded") boolean integrityDegraded,
        @JsonProperty("collection_errors") List<String> collectionErrors) {
}
