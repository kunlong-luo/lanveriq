package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsoleOriginPolicyTest {
    @Test
    void acceptsOnlyExactHttpsOriginAndMatchingConsoleHost() {
        var policy = new ConsoleOriginPolicy("https://console.lanveriq.internal");
        assertTrue(policy.acceptsOrigin("https://console.lanveriq.internal"));
        assertTrue(policy.acceptsHost("console.lanveriq.internal"));
        assertTrue(policy.acceptsHost("console.lanveriq.internal:443"));
        assertFalse(policy.acceptsOrigin("http://console.lanveriq.internal"));
        assertFalse(policy.acceptsOrigin("https://agent.lanveriq.internal"));
        assertFalse(policy.acceptsOrigin("https://console.lanveriq.internal.evil.test"));
        assertFalse(policy.acceptsOrigin("https://console.lanveriq.internal/"));
        assertFalse(policy.acceptsHost("agent.lanveriq.internal"));
        assertFalse(policy.acceptsHost("console.lanveriq.internal:8080"));
    }

    @Test
    void rejectsNonCanonicalOrUntrustedConsoleOriginsAtStartup() {
        for (String bad : new String[] {
                "", "http://console.lanveriq.internal", "https://localhost",
                "https://127.0.0.1", "https://CONSOLE.lanveriq.internal",
                "https://console.lanveriq.internal:444",
                "https://console.lanveriq.internal:443",
                "https://user@console.lanveriq.internal",
                "https://console.lanveriq.internal/path",
                "https://console.lanveriq.internal?x=1",
                "https://console.lanveriq.internal#fragment",
                "https://console.lanveriq.internal/"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConsoleOriginPolicy(bad), bad);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ConsoleOriginPolicy(null));
    }

    @Test
    void deniesCrossSiteFetchMetadata() {
        var policy = new ConsoleOriginPolicy("https://console.lanveriq.internal");
        assertTrue(policy.acceptsFetchSite(null));
        assertTrue(policy.acceptsFetchSite("same-origin"));
        assertTrue(policy.acceptsFetchSite("none"));
        assertFalse(policy.acceptsFetchSite("same-site"));
        assertFalse(policy.acceptsFetchSite("cross-site"));
        assertFalse(policy.acceptsFetchSite("unexpected"));
    }
}
