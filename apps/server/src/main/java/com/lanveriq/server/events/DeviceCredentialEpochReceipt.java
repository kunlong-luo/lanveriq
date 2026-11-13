package com.lanveriq.server.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Future explicitly gated, mTLS-only, read-only credential observation.
 * This class does NOT expose a route or represent a signed/portable
 * credential lease, enrollment approval, retry token or quarantine release.
 */
public record DeviceCredentialEpochReceipt(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("credential_epoch_id") UUID credentialEpochId,
        @JsonProperty("client_cert_sha256_fingerprint") String clientCertSha256Fingerprint,
        @JsonProperty("server_time") Instant serverTime) {
}
