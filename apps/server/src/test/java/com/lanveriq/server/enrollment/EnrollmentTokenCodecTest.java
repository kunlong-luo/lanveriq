package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EnrollmentTokenCodecTest {

    private final EnrollmentTokenCodec codec = new EnrollmentTokenCodec();

    @Test
    void generatesUniqueCanonical256BitTokensAndOnlyHashedVerifier() {
        var first = codec.generate();
        var second = codec.generate();

        assertEquals(43, first.plaintext().length());
        assertNotEquals(first.plaintext(), second.plaintext());
        assertEquals(32, first.verifier().length);
        assertArrayEquals(first.verifier(), codec.verifierFor(first.plaintext()));
        assertFalse(Arrays.equals(first.plaintext().getBytes(), first.verifier()));
    }

    @Test
    void doesNotLeakSecretThroughToStringOrPermitMutableVerifier() {
        var secret = codec.generate();
        byte[] provided = secret.verifier();
        Arrays.fill(provided, (byte) 0);
        assertArrayEquals(codec.verifierFor(secret.plaintext()), secret.verifier());
        assertEquals("IssuedSecret[REDACTED]", secret.toString());
        assertFalse(secret.toString().contains(secret.plaintext()));
    }

    @Test
    void rejectsTamperedNoncanonicalAndWhitespaceCredentials() {
        var issued = codec.generate();
        assertThrows(IllegalArgumentException.class, () -> codec.verifierFor(null));
        assertThrows(IllegalArgumentException.class, () -> codec.verifierFor(""));
        assertThrows(IllegalArgumentException.class, () -> codec.verifierFor(" " + issued.plaintext()));
        assertThrows(IllegalArgumentException.class, () -> codec.verifierFor(issued.plaintext() + "="));
        assertThrows(IllegalArgumentException.class, () -> codec.verifierFor(".".repeat(43)));

        // A 32-byte base64url encoding has exactly two unused low bits.
        // Mutating the last character to a noncanonical spelling must fail.
        String canonical = issued.plaintext();
        char c = canonical.charAt(42);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int value = alphabet.indexOf(c);
        assertTrue(value >= 0);
        char nonCanonical = alphabet.charAt(value ^ 1);
        assertThrows(IllegalArgumentException.class,
                () -> codec.verifierFor(canonical.substring(0, 42) + nonCanonical));
    }
}
