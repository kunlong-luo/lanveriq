package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real PostgreSQL latest-only heartbeat state. The active device row lock
 * serializes a heartbeat with same-device ingestion and revocation checks.
 * No caller-supplied receive time or self-reported online status is trusted.
 */
@Repository
final class JpaDeviceHeartbeatRepository implements DeviceHeartbeatRepository {

    private final EntityManager manager;
    private final JsonMapper mapper;

    JpaDeviceHeartbeatRepository(EntityManager manager, JsonMapper mapper) {
        this.manager = Objects.requireNonNull(manager);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void record(VerifiedDeviceCertificate identity, UUID deviceId,
                       DeviceHeartbeatPolicy.ValidHeartbeat state) {
        if (identity == null || deviceId == null || state == null
                || !deviceId.equals(identity.deviceId())) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA);
        }

        var active = manager.createNativeQuery("""
                SELECT d.id FROM devices AS d
                WHERE d.id=:device_id AND d.enrollment_state='active'
                  AND d.revoked_at IS NULL
                  AND EXISTS (
                      SELECT 1 FROM device_credentials AS c
                      WHERE c.device_id=d.id
                        AND c.client_cert_sha256_fingerprint=:fingerprint
                        AND c.status='active' AND c.revoked_at IS NULL
                        AND c.issued_at<=CURRENT_TIMESTAMP
                        AND c.expires_at>CURRENT_TIMESTAMP
                  )
                FOR UPDATE OF d
                """)
                .setParameter("device_id", deviceId)
                .setParameter("fingerprint", identity.sha256Fingerprint())
                .getResultList();
        if (active.size() != 1) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE);
        }
        manager.createNativeQuery("""
                UPDATE devices
                SET last_seen_at = GREATEST(
                    COALESCE(last_seen_at, '-infinity'::timestamptz), CURRENT_TIMESTAMP)
                WHERE id=:device_id
                """).setParameter("device_id", deviceId).executeUpdate();

        manager.createNativeQuery("""
                INSERT INTO device_heartbeat_latest (
                    device_id, received_at, agent_sent_at, agent_version,
                    agent_status, policy_version, capabilities_json, queue_count,
                    queue_bytes, oldest_pending_at, last_successful_upload_at,
                    dropped_count, integrity_degraded, collection_errors_json)
                VALUES (
                    :device_id, CURRENT_TIMESTAMP, CAST(:agent_sent_at AS timestamptz),
                    :agent_version, :agent_status, :policy_version,
                    CAST(:capabilities AS jsonb), :queue_count, :queue_bytes,
                    CAST(:oldest_pending AS timestamptz),
                    CAST(:last_upload AS timestamptz), :dropped_count,
                    :integrity_degraded, CAST(:collection_errors AS jsonb))
                ON CONFLICT (device_id) DO UPDATE SET
                    received_at=EXCLUDED.received_at,
                    agent_sent_at=EXCLUDED.agent_sent_at,
                    agent_version=EXCLUDED.agent_version,
                    agent_status=EXCLUDED.agent_status,
                    policy_version=EXCLUDED.policy_version,
                    capabilities_json=EXCLUDED.capabilities_json,
                    queue_count=EXCLUDED.queue_count,
                    queue_bytes=EXCLUDED.queue_bytes,
                    oldest_pending_at=EXCLUDED.oldest_pending_at,
                    last_successful_upload_at=EXCLUDED.last_successful_upload_at,
                    dropped_count=EXCLUDED.dropped_count,
                    integrity_degraded=EXCLUDED.integrity_degraded,
                    collection_errors_json=EXCLUDED.collection_errors_json
                """)
                .setParameter("device_id", deviceId)
                .setParameter("agent_sent_at", state.agentSentAt().toString())
                .setParameter("agent_version", state.agentVersion())
                .setParameter("agent_status", state.agentStatus())
                .setParameter("policy_version", state.policyVersion())
                .setParameter("capabilities", mapper.writeValueAsString(state.capabilities()))
                .setParameter("queue_count", state.queueCount())
                .setParameter("queue_bytes", state.queueBytes())
                .setParameter("oldest_pending", nullableTimestamp(state.oldestPendingAt()))
                .setParameter("last_upload", nullableTimestamp(state.lastSuccessfulUploadAt()))
                .setParameter("dropped_count", state.droppedCount())
                .setParameter("integrity_degraded", state.integrityDegraded())
                .setParameter("collection_errors", mapper.writeValueAsString(state.collectionErrors()))
                .executeUpdate();
    }

    private static String nullableTimestamp(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
