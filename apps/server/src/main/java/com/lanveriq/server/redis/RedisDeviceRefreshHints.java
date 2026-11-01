package com.lanveriq.server.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Optional, non-authoritative hints for a future scoped REST/WSS refresh path.
 * This is NOT a heartbeat store, role/scope cache, session store or outbox.
 * No production caller exists yet; enabling it does not expose an endpoint.
 *
 * Redis may evict keys (the shared spark-db uses allkeys-lru), so the absence
 * of a hint never means offline, revoked, authorized or up to date. Authoritative
 * reads must still query PostgreSQL and enforce current role/group grants.
 */
public final class RedisDeviceRefreshHints {
    static final String PREFIX = "lanveriq:v0_1:device-refetch:";
    static final Duration TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redis;

    RedisDeviceRefreshHints(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    /** Only to be invoked after the authoritative PostgreSQL transaction commits. */
    public boolean bestEffortMarkChanged(UUID deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        try {
            redis.opsForValue().set(key(deviceId), "1", TTL);
            return true;
        } catch (DataAccessException e) {
            // A transient hint must never turn an already committed DB write
            // into a client-visible failure or a fabricated acknowledgement.
            return false;
        }
    }

    /** Advisory only: false also means evicted, expired or Redis unavailable. */
    public boolean bestEffortHasHint(UUID deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        try {
            return "1".equals(redis.opsForValue().get(key(deviceId)));
        } catch (DataAccessException e) {
            return false;
        }
    }

    private static String key(UUID id) {
        return PREFIX + id;
    }
}
