package com.lanveriq.server.events;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates the WHOLE untrusted batch before any SQL. Only the V0.1
 * transport gap is allowed in production. Synthetic fixtures are test-only
 * and can never be enabled by an HTTP request.
 */
final class DeviceEventBatchPolicy {

    static final int MAX_EVENTS = 100;
    static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final Set<String> GAP_FIELDS =
            Set.of("affected_source", "reason", "range_start", "range_end");
    private static final Set<String> FIXTURE_FIELDS =
            Set.of("synthetic", "value", "counter", "text");
    private final JsonMapper mapper;
    private final boolean syntheticFixturesAllowed;

    DeviceEventBatchPolicy(JsonMapper mapper, boolean syntheticFixturesAllowed) {
        this.mapper = Objects.requireNonNull(mapper);
        this.syntheticFixturesAllowed = syntheticFixturesAllowed;
    }

    List<CanonicalEvent> validate(UUID pathDeviceId,
                                  VerifiedDeviceCertificate certificate,
                                  DeviceEventBatch batch) {
        if (pathDeviceId == null || certificate == null || batch == null
                || !pathDeviceId.equals(certificate.deviceId())
                || !pathDeviceId.equals(batch.deviceId())
                || isNil(pathDeviceId) || isNil(batch.batchId())
                || batch.schemaVersion() != 1
                || batch.events() == null || batch.events().isEmpty()
                || batch.events().size() > MAX_EVENTS) {
            throw invalid();
        }
        if (mapper.writeValueAsBytes(batch).length > MAX_BYTES) {
            throw new DeviceBatchRejection(DeviceBatchRejection.Reason.REQUEST_TOO_LARGE);
        }
        var ids = new HashSet<UUID>();
        var results = new ArrayList<CanonicalEvent>(batch.events().size());
        long previous = 0;
        for (var event : batch.events()) {
            if (event == null || isNil(event.eventId())
                    || !ids.add(event.eventId())
                    || event.deviceSequence() <= previous
                    || event.schemaVersion() != 1 || event.policyVersion() <= 0
                    || event.occurredAt() == null
                    || event.occurredAt().length() < 20 || event.occurredAt().length() > 40
                    || !event.occurredAt().endsWith("Z")
                    || event.payload() == null || !event.payload().isObject()) {
                throw invalid();
            }
            Instant occurredAt;
            try {
                occurredAt = Instant.parse(event.occurredAt());
            } catch (java.time.format.DateTimeParseException invalid) {
                throw invalid();
            }
            if (occurredAt.isBefore(Instant.EPOCH)
                    || occurredAt.isAfter(Instant.now().plusSeconds(86_400))) {
                throw invalid();
            }
            previous = event.deviceSequence();
            var payload = event.payload();
            if (mapper.writeValueAsBytes(payload).length > MAX_PAYLOAD_BYTES) {
                throw new DeviceBatchRejection(DeviceBatchRejection.Reason.REQUEST_TOO_LARGE);
            }
            if ("agent_transport".equals(event.source())
                    && "collection_gap".equals(event.eventType())) {
                validateGap(payload);
            } else if (syntheticFixturesAllowed
                    && "synthetic_test".equals(event.source())
                    && "protocol_fixture".equals(event.eventType())) {
                validateSynthetic(payload);
            } else {
                throw invalid();
            }
            // Sort all nested JSON object keys; a semantically identical retry
            // may have different input property order. All canonical envelope
            // fields, including sequence and event_id, are hashed.
            var normalizedPayload = canonicalize(payload);
            var envelope = new TreeMap<String, Object>();
            envelope.put("event_id", event.eventId().toString());
            envelope.put("device_sequence", event.deviceSequence());
            envelope.put("schema_version", event.schemaVersion());
            envelope.put("source", event.source());
            envelope.put("event_type", event.eventType());
            envelope.put("policy_version", event.policyVersion());
            envelope.put("occurred_at", occurredAt.toString());
            envelope.put("payload", normalizedPayload);
            String digest = digest(mapper.writeValueAsBytes(envelope));
            results.add(new CanonicalEvent(event.eventId(), event.deviceSequence(),
                    event.schemaVersion(), event.source(), event.eventType(),
                    event.policyVersion(), occurredAt, normalizedPayload, digest));
        }
        return List.copyOf(results);
    }

    private void validateGap(JsonNode payload) {
        if (payload.size() != GAP_FIELDS.size()
                || !payload.propertyNames().containsAll(GAP_FIELDS)) {
            throw invalid();
        }
        for (var field : GAP_FIELDS) {
            var value = payload.get(field);
            if (value == null || !value.isString()
                    || value.textValue().isBlank() || value.textValue().length() > 256) {
                throw invalid();
            }
        }
        if (!"agent_transport".equals(payload.get("affected_source").textValue())
                || !"quota_exceeded".equals(payload.get("reason").textValue())) {
            throw invalid();
        }
        if (!payload.get("range_start").textValue().endsWith("Z")
                || !payload.get("range_end").textValue().endsWith("Z")) {
            throw invalid();
        }
        try {
            var start = Instant.parse(payload.get("range_start").textValue());
            var end = Instant.parse(payload.get("range_end").textValue());
            if (end.isBefore(start) || end.isAfter(Instant.now().plusSeconds(86_400))) {
                throw invalid();
            }
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalid();
        }
    }

    private void validateSynthetic(JsonNode payload) {
        if (payload.size() == 0 || payload.size() > FIXTURE_FIELDS.size()
                || !FIXTURE_FIELDS.containsAll(payload.propertyNames())
                || !payload.has("synthetic") || !payload.get("synthetic").isBoolean()
                || !payload.get("synthetic").booleanValue()) {
            throw invalid();
        }
    }

    private Object canonicalize(JsonNode node) {
        if (node.isObject()) {
            var result = new TreeMap<String, Object>();
            for (var entry : node.properties()) {
                result.put(entry.getKey(), canonicalize(entry.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            var result = new ArrayList<Object>();
            for (var child : node) {
                result.add(canonicalize(child));
            }
            return result;
        }
        return node;
    }

    private static boolean isNil(UUID id) {
        return id == null || id.equals(new UUID(0, 0));
    }

    private static DeviceBatchRejection invalid() {
        return new DeviceBatchRejection(DeviceBatchRejection.Reason.INVALID_SCHEMA);
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM requires SHA-256", impossible);
        }
    }

    record CanonicalEvent(UUID eventId, long deviceSequence, int schemaVersion,
                          String source, String eventType, long policyVersion,
                          Instant occurredAt, Object payload, String digest) {
    }
}
