package com.lanveriq.server.devices;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure V0.1 device-connectivity rule. Collection coverage is a separate state.
 * No client-provided timestamp participates in this calculation.
 */
public final class ConnectivityStatusClassifier {

    private static final Duration ONLINE_MAX_AGE = Duration.ofSeconds(90);
    private static final Duration STALE_MAX_AGE = Duration.ofSeconds(300);

    private ConnectivityStatusClassifier() {
    }

    public static ConnectivityStatus classify(
            Instant serverNow, Instant lastAuthenticatedHeartbeatReceivedAt, boolean revoked) {
        Objects.requireNonNull(serverNow, "serverNow");

        if (revoked) {
            return ConnectivityStatus.REVOKED;
        }
        if (lastAuthenticatedHeartbeatReceivedAt == null) {
            return ConnectivityStatus.OFFLINE;
        }
        if (lastAuthenticatedHeartbeatReceivedAt.isAfter(serverNow)) {
            throw new IllegalArgumentException("Server receipt must not lie in the future");
        }

        Duration age = Duration.between(lastAuthenticatedHeartbeatReceivedAt, serverNow);
        if (age.compareTo(ONLINE_MAX_AGE) <= 0) {
            return ConnectivityStatus.ONLINE;
        }
        if (age.compareTo(STALE_MAX_AGE) <= 0) {
            return ConnectivityStatus.STALE;
        }
        return ConnectivityStatus.OFFLINE;
    }
}
