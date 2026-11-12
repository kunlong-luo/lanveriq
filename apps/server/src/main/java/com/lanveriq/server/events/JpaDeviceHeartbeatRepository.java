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
class JpaDeviceHeartbeatRepository implements DeviceHeartbeatRepository {

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

        if (!JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId)) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE);
        }
        // Locks above remain held until the transaction commits. The per-device
        // PostgreSQL unique key also rejects races and survives restarts.
        int accepted = manager.createNativeQuery("""
                INSERT INTO device_heartbeat_request_ids (device_id, request_id)
                VALUES (:device_id, :request_id)
                ON CONFLICT (device_id, request_id) DO NOTHING
                """)
                .setParameter("device_id", deviceId)
                .setParameter("request_id", state.requestId())
                .executeUpdate();
        if (accepted != 1) {
            throw new DeviceHeartbeatRejection(
                    DeviceHeartbeatRejection.Reason.REPLAYED_REQUEST);
        }
        // Never renew last_seen_at or replace the latest state on replay.
        manager.createNativeQuery("""
                UPDATE devices
                SET last_seen_at = GREATEST(
                    COALESCE(last_seen_at, '-infinity'::timestamptz), clock_timestamp())
                WHERE id=:device_id
                """).setParameter("device_id", deviceId).executeUpdate();

        manager.createNativeQuery("""
                INSERT INTO device_heartbeat_latest (
                    device_id, received_at, agent_sent_at, agent_version,
                    agent_status, policy_version, capabilities_json, queue_count,
                    queue_bytes, oldest_pending_at, last_successful_upload_at,
                    dropped_count, integrity_degraded, collection_errors_json)
                VALUES (
                    :device_id, clock_timestamp(), CAST(:agent_sent_at AS timestamptz),
                    :agent_version, :agent_status, :policy_version,
                    CAST(:capabilities AS jsonb), :queue_count, :queue_bytes,
                    CAST(NULLIF(:oldest_pending, '') AS timestamptz),
                    CAST(NULLIF(:last_upload, '') AS timestamptz), :dropped_count,
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

    @Override
    public boolean wasAccepted(VerifiedDeviceCertificate identity, UUID deviceId, UUID requestId) {
        if (identity == null || deviceId == null || requestId == null
                || requestId.equals(new UUID(0, 0))
                || !deviceId.equals(identity.deviceId())) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA);
        }
        // A revoked/expired/foreign leaf must not read even accepted IDs.
        // The device lock serializes this read with revoke and heartbeat writes.
        if (!JpaActiveDeviceCredentialGuard.lock(manager, identity, deviceId)) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE);
        }
        var accepted = manager.createNativeQuery("""
                SELECT r.request_id FROM device_heartbeat_request_ids r
                WHERE r.device_id=:device_id AND r.request_id=:request_id
                """)
                .setParameter("device_id", deviceId)
                .setParameter("request_id", requestId)
                .getResultList();
        return accepted.size() == 1;
    }

    private static String nullableTimestamp(java.time.Instant value) {
        // Avoid Hibernate null-parameter type ambiguity in native PostgreSQL SQL.
        return value == null ? "" : value.toString();
    }
}
