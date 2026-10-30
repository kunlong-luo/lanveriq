package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordEncodingSecurityTest {

    private final DefaultDenySecurityConfiguration config =
            new DefaultDenySecurityConfiguration();

    @Test
    void usesSaltedAdaptiveArgon2idAndNoPlaintextFallback() {
        var encoder = config.passwordEncoder();
        String password = "test-only-random-password-26-chars!";
        String first = encoder.encode(password);
        String second = encoder.encode(password);

        assertTrue(first.startsWith("{argon2id}"));
        assertNotEquals(first, second);
        assertTrue(encoder.matches(password, first));
        assertFalse(encoder.matches("incorrect", first));
        assertThrows(IllegalArgumentException.class,
                () -> encoder.matches("hello", "{noop}hello"));
        assertThrows(IllegalArgumentException.class,
                () -> encoder.matches(password, "hello"));
    }
}
