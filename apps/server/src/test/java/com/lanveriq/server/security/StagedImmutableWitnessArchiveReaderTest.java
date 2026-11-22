package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.lanveriq.server.security.StagedImmutableWitnessArchiveReader.*;

class StagedImmutableWitnessArchiveReaderTest {
    private static final byte[] BYTES =
            "offline, synthetic witness: NOT an external WORM".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
    private static final VersionLocator LOCATOR =
            new VersionLocator("separate-audit-domain", "witness/42",
                    "immutable-version-2");
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Instant MIN = Instant.parse("2026-11-29T12:00:00Z");
    private static final Instant END = Instant.parse("2026-12-29T12:00:00Z");
    private final StagedImmutableWitnessArchiveReader reader =
            new StagedImmutableWitnessArchiveReader();

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static IndependentlyAssertedPin pin() {
        return new IndependentlyAssertedPin(LOCATOR, sha(BYTES), MIN);
    }

    private static VersionedObjectRead data() {
        return new VersionedObjectRead(LOCATOR, BYTES.length,
                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                new ByteArrayInputStream(BYTES));
    }

    private static VersionedRetentionRead retention() {
        return new VersionedRetentionRead(
                LOCATOR, ProtectionClaim.NON_BYPASSABLE_WORM, END);
    }

    private static ReadOnlyArchivePort port(
            VersionedObjectRead body, VersionedRetentionRead hold) {
        return new ReadOnlyArchivePort() {
            @Override
            public VersionedObjectRead readExactVersion(VersionLocator ref) {
                assertEquals(LOCATOR, ref);
                return body;
            }

            @Override
            public VersionedRetentionRead readExactVersionRetention(
                    VersionLocator ref) {
                assertEquals(LOCATOR, ref);
                return hold;
            }
        };
    }

    @Test
    void c18eAcceptsOnlyExactVersionMatchedUntrustedEvidence() {
        var result = reader.inspectVersionOfflineOnly(
                port(data(), retention()), pin(), NOW);
        assertEquals(LOCATOR, result.locator());
        assertEquals(sha(BYTES), result.contentSha256());
        assertEquals(END, result.claimedRetainUntil());
    }

    @Test
    void c18eRejectsMissingInvalidAndExpiredPinsBeforeProviderCall() {
        var calls = new AtomicInteger();
        ReadOnlyArchivePort zeroCalls = new ReadOnlyArchivePort() {
            public VersionedObjectRead readExactVersion(VersionLocator ref) {
                calls.incrementAndGet();
                throw new AssertionError("invalid pin reached provider");
            }
            public VersionedRetentionRead readExactVersionRetention(
                    VersionLocator ref) {
                calls.incrementAndGet();
                throw new AssertionError("invalid pin reached retention");
            }
        };
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        zeroCalls, null, NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        zeroCalls, pin(), null));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(zeroCalls,
                        new IndependentlyAssertedPin(
                                new VersionLocator("separate", "witness", "null"),
                                sha(BYTES), MIN), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(zeroCalls,
                        new IndependentlyAssertedPin(
                                new VersionLocator("separate", "witness", ""),
                                sha(BYTES), MIN), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(zeroCalls,
                        new IndependentlyAssertedPin(
                                LOCATOR, "0".repeat(64), NOW), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(zeroCalls,
                        new IndependentlyAssertedPin(LOCATOR, "BAD", MIN), NOW));
        assertEquals(0, calls.get());
    }

    @Test
    void c18eRejectsForeignVersionAndVersionlessRetention() {
        var foreignData = new VersionedObjectRead(
                new VersionLocator("separate-audit-domain", "witness/42",
                        "attacker-version"),
                BYTES.length, ProtectionClaim.NON_BYPASSABLE_WORM,
                END, new ByteArrayInputStream(BYTES));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(foreignData, retention()), pin(), NOW));
        var wrongRetention = new VersionedRetentionRead(
                new VersionLocator("separate-audit-domain", "witness/42",
                        "latest"), ProtectionClaim.NON_BYPASSABLE_WORM, END);
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(data(), wrongRetention), pin(), NOW));
    }

    @Test
    void c18eRejectsForgedLengthChangedBytesAndOversizedBody() {
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(port(
                        new VersionedObjectRead(LOCATOR, BYTES.length + 1,
                                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                                new ByteArrayInputStream(BYTES)),
                        retention()), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(port(
                        new VersionedObjectRead(LOCATOR, BYTES.length,
                                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                                new ByteArrayInputStream("tampered".getBytes())),
                        retention()), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(port(
                        new VersionedObjectRead(LOCATOR, 1024L*1024L+1,
                                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                                new ByteArrayInputStream(new byte[0])),
                        retention()), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(port(
                        new VersionedObjectRead(LOCATOR, BYTES.length,
                                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                                new ByteArrayInputStream(new byte[1024*1024+1])),
                        retention()), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(port(
                        new VersionedObjectRead(LOCATOR, 0,
                                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                                new ByteArrayInputStream(new byte[0])),
                        retention()), pin(), NOW));
    }

    @Test
    void c18eRejectsBypassableMissingExpiredOrConflictingRetention() {
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(new VersionedObjectRead(LOCATOR, BYTES.length,
                                ProtectionClaim.BYPASSABLE, END,
                                new ByteArrayInputStream(BYTES)),
                                retention()), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(data(), new VersionedRetentionRead(
                                LOCATOR, ProtectionClaim.UNKNOWN, END)),
                        pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(data(), null), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(data(), new VersionedRetentionRead(
                                LOCATOR, ProtectionClaim.NON_BYPASSABLE_WORM,
                                MIN)), pin(), NOW));
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(data(), new VersionedRetentionRead(
                                LOCATOR, ProtectionClaim.NON_BYPASSABLE_WORM,
                                END)), pin(), END.plusSeconds(1)));
    }

    @Test
    void c18eRejectsProviderErrorsAndAlwaysClosesStream() {
        var closed = new AtomicBoolean();
        InputStream bytes = new ByteArrayInputStream(BYTES) {
            @Override
            public void close() throws IOException {
                super.close();
                closed.set(true);
            }
        };
        var forged = new VersionedObjectRead(LOCATOR, BYTES.length,
                ProtectionClaim.NON_BYPASSABLE_WORM, END,
                new ByteArrayInputStream(new byte[0]));
        var read = new VersionedObjectRead(LOCATOR, BYTES.length,
                ProtectionClaim.NON_BYPASSABLE_WORM, END, bytes);
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(read, null), pin(), NOW));
        assertTrue(closed.get());

        var failing = new VersionedObjectRead(
                LOCATOR, BYTES.length, ProtectionClaim.NON_BYPASSABLE_WORM,
                END, new InputStream() {
                    @Override public int read() throws IOException {
                        throw new IOException("synthetic read interruption");
                    }
                });
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(
                        port(failing, retention()), pin(), NOW));
        ReadOnlyArchivePort portError = new ReadOnlyArchivePort() {
            public VersionedObjectRead readExactVersion(VersionLocator ref)
                    throws IOException {
                throw new IOException("synthetic absent archive");
            }
            public VersionedRetentionRead readExactVersionRetention(
                    VersionLocator ref) {
                throw new AssertionError("should not fetch retention");
            }
        };
        assertThrows(AccessDeniedException.class,
                () -> reader.inspectVersionOfflineOnly(portError, pin(), NOW));
    }
}
