package com.lanveriq.server.events;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict operational telemetry only. Source coverage is NOT inferred from
 * transport connectivity. Arbitrary error text/IP/hostnames are not accepted.
 */
final class DeviceHeartbeatPolicy {

    private static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> STATUS = Set.of("running", "degraded", "stopping");
    private static final Set<String> CAPABILITY = Set.of(
            "supported", "not_collected", "collecting", "paused_by_policy",
            "permission_required", "permission_denied", "degraded",
            "unsupported", "unavailable");
    private final JsonMapper mapper;
    private final Clock clock;

    DeviceHeartbeatPolicy(JsonMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    ValidHeartbeat validate(VerifiedDeviceCertificate identity, UUID pathId, DeviceHeartbeat body) {
        if (identity == null || pathId == null || body == null
                || !pathId.equals(identity.deviceId())
                || !pathId.equals(body.deviceId()) || body.schemaVersion() != 1
                || !STATUS.contains(body.status())
                || body.agentVersion() == null
                || !body.agentVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || body.policyVersion() <= 0 || body.queueCount() < 0
                || body.queueBytes() < 0 || body.droppedCount() < 0
                || body.capabilities() == null || body.capabilities().size() > 32
                || body.collectionErrors() == null || body.collectionErrors().size() > 16) {
            throw invalid();
        }
        for (var entry : body.capabilities().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z][a-z0-9_]{0,63}")
                    || !CAPABILITY.contains(entry.getValue())) {
                throw invalid();
            }
        }
        for (String code : body.collectionErrors()) {
            if (code == null || !code.matches("[a-z][a-z0-9_]{0,63}")) {
                throw invalid();
            }
        }
        if (mapper.writeValueAsBytes(body).length > MAX_BYTES) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.REQUEST_TOO_LARGE);
        }
        var agentSentAt = checkedUtc(body.sentAt(), false);
        var oldestPendingAt = checkedUtc(body.oldestPendingAt(), true);
        var lastUploadAt = checkedUtc(body.lastSuccessfulUploadAt(), true);
        return new ValidHeartbeat(agentSentAt, body.agentVersion(), body.status(),
                body.policyVersion(), Map.copyOf(body.capabilities()),
                body.queueCount(), body.queueBytes(), oldestPendingAt, lastUploadAt,
                body.droppedCount(), body.integrityDegraded(),
                List.copyOf(body.collectionErrors()));
    }

    private Instant checkedUtc(String timestamp, boolean nullable) {
        if (timestamp == null && nullable) {
            return null;
        }
        if (timestamp == null || timestamp.length() < 20 || timestamp.length() > 40
                || !timestamp.endsWith("Z")) {
            throw invalid();
        }
        try {
            Instant value = Instant.parse(timestamp);
            if (value.isBefore(Instant.EPOCH)
                    || value.isAfter(clock.instant().plusSeconds(86_400))) {
                throw invalid();
            }
            return value;
        } catch (DateTimeParseException error) {
            throw invalid();
        }
    }

    private static DeviceHeartbeatRejection invalid() {
        return new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA);
    }

    record ValidHeartbeat(Instant agentSentAt, String agentVersion, String agentStatus,
                          long policyVersion, Map<String, String> capabilities,
                          long queueCount, long queueBytes, Instant oldestPendingAt,
                          Instant lastSuccessfulUploadAt, long droppedCount,
                          boolean integrityDegraded, List<String> collectionErrors) {
    }
}
