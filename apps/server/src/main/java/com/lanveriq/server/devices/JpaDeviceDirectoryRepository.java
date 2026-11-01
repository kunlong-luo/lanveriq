package com.lanveriq.server.devices;

import com.lanveriq.server.security.AdminAction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Single-statement scope-filtered projections. EXISTS avoids duplicates for
 * administrators with multiple roles in the same device group. No network
 * joins, credentials, source events or sensitive inventory columns are read.
 *
 * This is not a REST endpoint. Session/CSRF/Host/TLS gates remain separate.
 */
@Repository
class JpaDeviceDirectoryRepository implements DeviceDirectoryRepository {
    private static final String SCOPED_HEALTH_QUERY = """
            SELECT d.id, d.group_id, g.name,
                   COALESCE(NULLIF(d.device_label,''), 'Unlabeled device'),
                   d.enrollment_state,
                   CAST(EXTRACT(EPOCH FROM d.last_seen_at) AS BIGINT),
                   CAST(EXTRACT(EPOCH FROM h.received_at) AS BIGINT),
                   h.agent_version, h.queue_count, h.queue_bytes,
                   i.os_family, i.os_name, i.cpu_model, i.memory_total_bytes,
                   CAST(EXTRACT(EPOCH FROM i.received_at) AS BIGINT)
            FROM devices AS d
            JOIN device_groups AS g ON g.id=d.group_id
            LEFT JOIN device_heartbeat_latest AS h ON h.device_id=d.id
            LEFT JOIN device_inventory_current AS i ON i.device_id=d.id
            WHERE EXISTS (
                SELECT 1 FROM users AS u
                JOIN user_role_scopes AS s ON s.user_id=u.id
                JOIN roles AS r ON r.id=s.role_id
                WHERE u.id=:admin_id AND u.disabled_at IS NULL
                  AND s.device_group_id=d.group_id
                  AND r.code IN (:health_roles)
            )
            """;

    private final EntityManager manager;

    JpaDeviceDirectoryRepository(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public List<DeviceDirectoryEntry> list(UUID authenticatedAdminId,
                                           UUID afterDeviceId, int maxRows) {
        Objects.requireNonNull(authenticatedAdminId);
        if (maxRows < 1 || maxRows > DeviceDirectoryService.MAX_PAGE_SIZE + 1) {
            throw new IllegalArgumentException("Invalid bounded page");
        }
        String sql = SCOPED_HEALTH_QUERY
                + (afterDeviceId == null ? "" : " AND d.id>:cursor\n")
                + "ORDER BY d.id ASC";
        Query query = manager.createNativeQuery(sql);
        scope(query, authenticatedAdminId);
        if (afterDeviceId != null) {
            query.setParameter("cursor", afterDeviceId);
        }
        query.setMaxResults(maxRows);
        var result = new ArrayList<DeviceDirectoryEntry>();
        for (Object row : query.getResultList()) {
            result.add(map((Object[]) row));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<DeviceDirectoryEntry> find(UUID authenticatedAdminId,
                                               UUID deviceId) {
        Objects.requireNonNull(authenticatedAdminId);
        Objects.requireNonNull(deviceId);
        Query query = manager.createNativeQuery(SCOPED_HEALTH_QUERY + " AND d.id=:device_id");
        scope(query, authenticatedAdminId);
        query.setParameter("device_id", deviceId);
        var rows = query.getResultList();
        if (rows.isEmpty()) {
            // Wrong-group and unknown device are intentionally indistinguishable.
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException("Non-unique canonical device");
        }
        return Optional.of(map((Object[]) rows.getFirst()));
    }

    private static void scope(Query query, UUID actor) {
        query.setParameter("admin_id", actor);
        query.setParameter("health_roles", AdminAction.VIEW_HEALTH.allowedRoleCodes());
    }

    private static Instant asInstant(Object value) {
        return value == null ? null : Instant.ofEpochSecond(((Number) value).longValue());
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static DeviceDirectoryEntry map(Object[] row) {
        if (row.length != 15) {
            throw new IllegalStateException("Unexpected device projection shape");
        }
        return new DeviceDirectoryEntry(
                (UUID) row[0], (UUID) row[1], (String) row[2], (String) row[3],
                (String) row[4], asInstant(row[5]), asInstant(row[6]),
                (String) row[7], asLong(row[8]), asLong(row[9]),
                (String) row[10], (String) row[11], (String) row[12],
                asLong(row[13]), asInstant(row[14]));
    }
}
