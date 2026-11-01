package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL 18 implementation. A row lock on the ACTIVE device serializes
 * all batches for that device (including duplicate retries). The credential
 * fingerprint is a value from an independently verified mTLS peer, never
 * request JSON/X-Forwarded-Client-Cert. All statements share one Spring TX.
 *
 * One INSERT .. ON CONFLICT DO NOTHING plus per-event identity/digest
 * verification gives atomic identical duplicate ACK and full rollback on
 * conflict. PostgreSQL uniqueness protects both event_id and sequence.
 */
@Repository
class JpaEventIngestionRepository implements EventIngestionRepository {

    private final EntityManager entityManager;
    private final JsonMapper mapper;

    JpaEventIngestionRepository(EntityManager entityManager, JsonMapper mapper) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void appendOrVerify(VerifiedDeviceCertificate identity,
                               List<DeviceEventBatchPolicy.CanonicalEvent> events) {
        Objects.requireNonNull(identity);
        if (events == null || events.isEmpty() || events.size() > 100) {
            throw new DeviceBatchRejection(DeviceBatchRejection.Reason.INVALID_SCHEMA);
        }

        if (!JpaActiveDeviceCredentialGuard.lock(
                entityManager, identity, identity.deviceId())) {
            // Never distinguish whether a device ID or fingerprint exists.
            throw new DeviceBatchRejection(DeviceBatchRejection.Reason.REVOKED_DEVICE);
        }

        // Bulk PostgreSQL JSONB expansion, not a hand-written JDBC loop.
        // The typed prevalidated content is the only source of query values.
        var rows = new ArrayList<Map<String, Object>>(events.size());
        for (var event : events) {
            var row = new HashMap<String, Object>();
            row.put("event_id", event.eventId().toString());
            row.put("device_sequence", event.deviceSequence());
            row.put("schema_version", event.schemaVersion());
            row.put("source", event.source());
            row.put("event_type", event.eventType());
            row.put("occurred_at", event.occurredAt().toString());
            row.put("policy_version", event.policyVersion());
            row.put("payload_json", event.payload());
            row.put("content_digest", event.digest());
            rows.add(row);
        }
        String json = mapper.writeValueAsString(rows);
        entityManager.createNativeQuery("""
                INSERT INTO endpoint_events
                    (device_id, device_sequence, event_id, schema_version, source,
                     event_type, occurred_at, policy_version, payload_json,
                     content_digest)
                SELECT :device_id, e.device_sequence, e.event_id, e.schema_version,
                       e.source, e.event_type, e.occurred_at, e.policy_version,
                       e.payload_json, e.content_digest
                FROM jsonb_to_recordset(CAST(:events_json AS jsonb)) AS e (
                    device_sequence bigint, event_id uuid, schema_version integer,
                    source text, event_type text, occurred_at timestamptz,
                    policy_version bigint, payload_json jsonb, content_digest text
                )
                ON CONFLICT DO NOTHING
                """)
                .setParameter("device_id", identity.deviceId())
                .setParameter("events_json", json)
                .executeUpdate();

        // Ignored conflicts are safe ONLY if both independent unique keys
        // resolve to the same immutable row with exactly the same digest.
        // If any comparison fails, @Transactional rolls back all new inserts.
        for (var event : events) {
            List<?> matches = entityManager.createNativeQuery("""
                    SELECT device_sequence, event_id, TRIM(content_digest)
                    FROM endpoint_events
                    WHERE device_id = :device_id
                      AND (device_sequence = :device_sequence OR event_id = :event_id)
                    """)
                    .setParameter("device_id", identity.deviceId())
                    .setParameter("device_sequence", event.deviceSequence())
                    .setParameter("event_id", event.eventId())
                    .getResultList();
            if (matches.size() != 1) {
                throw new DeviceBatchRejection(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT);
            }
            Object[] stored = (Object[]) matches.getFirst();
            if (((Number) stored[0]).longValue() != event.deviceSequence()
                    || !event.eventId().equals(stored[1])
                    || !event.digest().equals(stored[2])) {
                throw new DeviceBatchRejection(DeviceBatchRejection.Reason.SEQUENCE_CONFLICT);
            }
        }
    }
}
