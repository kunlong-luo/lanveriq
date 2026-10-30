package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectivityStatusClassifierTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void appliesInclusiveOnlineAndStaleThresholds() {
        assertEquals(ConnectivityStatus.ONLINE, statusAt(0));
        assertEquals(ConnectivityStatus.ONLINE, statusAt(90));
        assertEquals(ConnectivityStatus.STALE, statusAt(91));
        assertEquals(ConnectivityStatus.STALE, statusAt(300));
        assertEquals(ConnectivityStatus.OFFLINE, statusAt(301));
    }

    @Test
    void noHeartbeatIsOfflineAndRevocationOverridesHeartbeat() {
        assertEquals(ConnectivityStatus.OFFLINE, ConnectivityStatusClassifier.classify(NOW, null, false));
        assertEquals(ConnectivityStatus.REVOKED, ConnectivityStatusClassifier.classify(NOW, NOW, true));
        assertEquals(ConnectivityStatus.REVOKED, ConnectivityStatusClassifier.classify(NOW, null, true));
    }

    @Test
    void refusesFutureServerReceiptInsteadOfClaimingHealthy() {
        assertThrows(IllegalArgumentException.class,
                () -> ConnectivityStatusClassifier.classify(NOW, NOW.plusSeconds(1), false));
    }

    private static ConnectivityStatus statusAt(long secondsAgo) {
        return ConnectivityStatusClassifier.classify(NOW, NOW.minusSeconds(secondsAgo), false);
    }
}
