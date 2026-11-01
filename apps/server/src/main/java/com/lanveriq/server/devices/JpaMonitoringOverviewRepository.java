package com.lanveriq.server.devices;

import com.lanveriq.server.security.AdminAction;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * One READ COMMITTED statement; MATERIALIZED snapshot avoids counting row
 * age against multiple wall-clock instants. EXISTS removes multi-role fanout.
 * No network/credentials/events or groups outside the live user scope are read.
 */
@Repository
class JpaMonitoringOverviewRepository implements MonitoringOverviewRepository {
    private static final String SQL = """
            WITH snapshot AS MATERIALIZED (
                SELECT clock_timestamp() AS as_of
            ), visible AS MATERIALIZED (
                SELECT d.enrollment_state, d.revoked_at,
                       h.received_at, s.as_of
                FROM devices AS d
                LEFT JOIN device_heartbeat_latest AS h ON h.device_id=d.id
                CROSS JOIN snapshot AS s
                WHERE EXISTS (
                    SELECT 1 FROM users AS u
                    JOIN user_role_scopes AS scope ON scope.user_id=u.id
                    JOIN roles AS role ON role.id=scope.role_id
                    WHERE u.id=:admin_id AND u.disabled_at IS NULL
                      AND scope.device_group_id=d.group_id
                      AND role.code IN (:health_roles)
                )
            ), states AS (
                SELECT CASE
                    WHEN revoked_at IS NOT NULL OR enrollment_state='revoked'
                        THEN 'revoked'
                    WHEN enrollment_state='retired' THEN 'retired'
                    WHEN enrollment_state='pending_certificate' THEN 'pending'
                    WHEN enrollment_state='active' AND received_at IS NOT NULL
                      AND received_at<=as_of
                      AND received_at>=as_of-INTERVAL '90 seconds' THEN 'online'
                    WHEN enrollment_state='active' AND received_at IS NOT NULL
                      AND received_at<=as_of
                      AND received_at>=as_of-INTERVAL '300 seconds' THEN 'stale'
                    ELSE 'offline'
                END AS connectivity
                FROM visible
            )
            SELECT CAST(EXTRACT(EPOCH FROM snapshot.as_of) AS BIGINT),
                   count(connectivity) AS total,
                   count(*) FILTER (WHERE connectivity='online'),
                   count(*) FILTER (WHERE connectivity='stale'),
                   count(*) FILTER (WHERE connectivity='offline'),
                   count(*) FILTER (WHERE connectivity='revoked'),
                   count(*) FILTER (WHERE connectivity='pending'),
                   count(*) FILTER (WHERE connectivity='retired')
            FROM snapshot LEFT JOIN states ON TRUE
            GROUP BY snapshot.as_of
            """;

    private final EntityManager manager;

    JpaMonitoringOverviewRepository(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public MonitoringOverview aggregate(UUID authenticatedAdminId) {
        Objects.requireNonNull(authenticatedAdminId);
        Object[] row = (Object[]) manager.createNativeQuery(SQL)
                .setParameter("admin_id", authenticatedAdminId)
                .setParameter("health_roles", AdminAction.VIEW_HEALTH.allowedRoleCodes())
                .getSingleResult();
        if (row.length != 8) {
            throw new IllegalStateException("Unexpected monitoring projection shape");
        }
        return new MonitoringOverview(
                Instant.ofEpochSecond(number(row[0])),
                number(row[1]), number(row[2]), number(row[3]), number(row[4]),
                number(row[5]), number(row[6]), number(row[7]));
    }

    private static long number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Invalid monitoring aggregate value");
        }
        return number.longValue();
    }
}
