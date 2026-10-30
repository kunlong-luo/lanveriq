package com.lanveriq.server.enrollment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * 256-bit single-use pairing secrets. The database stores a SHA-256 verifier,
 * never the plaintext token. Tokens are not ongoing Agent credentials.
 */
public final class EnrollmentTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final int ENCODED_LENGTH = 43;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecureRandom random;

    public EnrollmentTokenCodec() {
        this(new SecureRandom());
    }

    EnrollmentTokenCodec(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public IssuedSecret generate() {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        try {
            return new IssuedSecret(ENCODER.encodeToString(raw), sha256(raw));
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    /** Reject noncanonical encodings rather than silently normalizing tokens. */
    public byte[] verifierFor(String token) {
        if (token == null || token.length() != ENCODED_LENGTH ||
                !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid enrollment token format");
        }
        final byte[] raw;
        try {
            raw = DECODER.decode(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid enrollment token format", e);
        }
        try {
            if (raw.length != TOKEN_BYTES || !ENCODER.encodeToString(raw).equals(token)) {
                throw new IllegalArgumentException("Invalid enrollment token format");
            }
            return sha256(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required JDK SHA-256 implementation unavailable", e);
        }
    }

    /** String representation is always redacted to avoid accidental secret logs. */
    public static final class IssuedSecret {
        private final String plaintext;
        private final byte[] verifier;

        private IssuedSecret(String plaintext, byte[] verifier) {
            this.plaintext = plaintext;
            this.verifier = verifier.clone();
        }

        /** Display exactly once to an already authenticated and authorized admin. */
        public String plaintext() {
            return plaintext;
        }

        public byte[] verifier() {
            return verifier.clone();
        }

        @Override
        public String toString() {
            return "IssuedSecret[REDACTED]";
        }
    }
}
