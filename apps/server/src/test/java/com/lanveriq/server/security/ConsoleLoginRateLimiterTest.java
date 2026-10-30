package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ConsoleLoginRateLimiterTest {

    @Test
    void fiveAttemptsPerNormalizedLoginAndPeerAreAllowedWithinTenMinutes() {
        var limiter = new ConsoleLoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allow("OwNeR", "127.0.0.1"));
        }
        assertFalse(limiter.allow("owner", "127.0.0.1"));
        // A distinct account must not automatically be frozen by that account budget.
        assertTrue(limiter.allow("different", "127.0.0.1"));
    }

    @Test
    void transportPeerHasConservativeOverallBudgetEvenAcrossManyNames() {
        var limiter = new ConsoleLoginRateLimiter();
        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.allow("user" + i, "127.0.0.1"));
        }
        assertFalse(limiter.allow("user-new", "127.0.0.1"));
        // Forwarded-for is deliberately not an argument to this API.
        assertTrue(limiter.allow("another-user", "127.0.0.2"));
    }

    @Test
    void paddedInvalidLoginCannotExhaustValidAccountBudget() {
        var limiter = new ConsoleLoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allow(" owner ", "127.0.0.1"));
        }
        assertFalse(limiter.allow(" owner ", "127.0.0.1"));
        // Actual UserDetailsService rejects whitespace, so the genuine
        // lowercased username must remain a separate rate-limit key.
        assertTrue(limiter.allow("OWNER", "127.0.0.1"));
    }

    @Test
    void missingLoginAndUnknownPeerAlsoConsumeBudget() {
        var limiter = new ConsoleLoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allow(null, null));
        }
        assertFalse(limiter.allow("", null));
    }
}
