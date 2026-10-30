package com.lanveriq.server.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Locale;

/**
 * Single-node LAN pilot guard. Caffeine bounds memory, while Bucket4j
 * atomically enforces both a source budget and account budget. IP is taken
 * ONLY from the transport peer, never user-controlled forwarded headers.
 * Multi-instance deployments require a reviewed distributed backend.
 */
final class ConsoleLoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final Cache<String, Bucket> accounts = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(WINDOW).build();
    private final Cache<String, Bucket> peers = Caffeine.newBuilder()
            .maximumSize(2_000).expireAfterAccess(WINDOW).build();

    boolean allow(String suppliedLogin, String transportPeer) {
        // Unknown/bad names also consume a per-account budget. No username
        // gets written to an audit row or response body.
        String login = suppliedLogin == null ? "" :
                suppliedLogin.strip().toLowerCase(Locale.ROOT);
        if (login.length() > 190) {
            login = login.substring(0, 190);
        }
        String peer = transportPeer == null ? "unknown" : transportPeer;
        // All Caddy clients may appear as the same local peer; intentionally
        // protect that entire ingress with a conservative global cap.
        Bucket source = peers.get(peer, ignored -> Bucket.builder()
                .addLimit(limit -> limit.capacity(30)
                        .refillGreedy(30, Duration.ofMinutes(1)))
                .build());
        Bucket account = accounts.get(login, ignored -> Bucket.builder()
                .addLimit(limit -> limit.capacity(5)
                        .refillGreedy(5, WINDOW))
                .build());
        boolean allowedBySource = source.tryConsume(1);
        boolean allowedByAccount = account.tryConsume(1);
        return allowedBySource && allowedByAccount;
    }
}
