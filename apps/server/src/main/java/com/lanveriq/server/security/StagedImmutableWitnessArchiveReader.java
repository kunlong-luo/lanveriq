package com.lanveriq.server.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C18e: provider-neutral, offline, untrusted WORM evidence screen.
 *
 * This does NOT establish independent custody, actual retention enforcement,
 * a trusted clock, an external WORM system, FIDO root trust or a CA/Agent
 * authorization. A caller-provided port could lie about every response.
 * No Spring bean, network/client construction, vendor SDK or write API.
 */
final class StagedImmutableWitnessArchiveReader {
    private static final int MAX_BYTES = 1024 * 1024;

    /** Opaque version token: never default to latest or auto-discover. */
    record VersionLocator(
            String administrativeDomain, String objectKey, String versionToken) {}

    /** Must be retained OUTSIDE the DB/application administrator domain. */
    record IndependentlyAssertedPin(
            VersionLocator locator, String contentSha256,
            Instant minimumRetainUntil) {}

    /** Claims here are UNTRUSTED until a separately approved provider attests. */
    enum ProtectionClaim { NON_BYPASSABLE_WORM, BYPASSABLE, UNKNOWN }

    /**
     * Data and header claims for the requested specific version. The stream
     * MUST be closed even on invalid metadata, oversized body or I/O failure.
     */
    record VersionedObjectRead(
            VersionLocator locator, long declaredLength,
            ProtectionClaim headerProtection, Instant headerRetainUntil,
            InputStream bytes) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (bytes != null) {
                bytes.close();
            }
        }
    }

    /** Must be fetched for precisely the same version as the object body. */
    record VersionedRetentionRead(
            VersionLocator locator, ProtectionClaim protection,
            Instant retainUntil) {}

    /** No SDK, cloud API or privileged network behavior on this interface. */
    interface ReadOnlyArchivePort {
        VersionedObjectRead readExactVersion(VersionLocator locator)
                throws IOException;

        VersionedRetentionRead readExactVersionRetention(
                VersionLocator locator) throws IOException;
    }

    /** NEVER promotes a witness, root, WebAuthn credential or certificate. */
    record UntrustedArchiveMatch(
            VersionLocator locator, String contentSha256,
            Instant claimedRetainUntil) {}

    UntrustedArchiveMatch inspectVersionOfflineOnly(
            ReadOnlyArchivePort port, IndependentlyAssertedPin independentPin,
            Instant callerObservedTime) {
        // Reject before invoking a potentially network-capable provider port.
        if (port == null || independentPin == null
                || !validLocator(independentPin.locator())
                || !validSha(independentPin.contentSha256())
                || independentPin.minimumRetainUntil() == null
                || callerObservedTime == null
                || !independentPin.minimumRetainUntil()
                        .isAfter(callerObservedTime)) {
            throw denied();
        }

        VersionLocator locator = independentPin.locator();
        String contentSha;
        ProtectionClaim headerProtection;
        Instant headerRetainUntil;
        try (var result = port.readExactVersion(locator)) {
            if (result == null || result.bytes() == null
                    || !locator.equals(result.locator())
                    || result.declaredLength() < 1
                    || result.declaredLength() > MAX_BYTES) {
                throw denied();
            }
            headerProtection = result.headerProtection();
            headerRetainUntil = result.headerRetainUntil();

            // Bounded by MAX_BYTES+1 regardless of untrusted length header.
            // A single additional byte proves a response is too large.
            byte[] bytes = result.bytes().readNBytes(MAX_BYTES + 1);
            if (bytes.length != result.declaredLength()
                    || bytes.length > MAX_BYTES) {
                throw denied();
            }
            contentSha = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!contentSha.equals(independentPin.contentSha256())) {
                throw denied();
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            throw denied();
        }

        try {
            var retention = port.readExactVersionRetention(locator);
            if (retention == null
                    || !locator.equals(retention.locator())
                    || headerProtection != ProtectionClaim.NON_BYPASSABLE_WORM
                    || retention.protection()
                            != ProtectionClaim.NON_BYPASSABLE_WORM
                    || headerRetainUntil == null
                    || retention.retainUntil() == null
                    || !headerRetainUntil.equals(retention.retainUntil())
                    || retention.retainUntil().isBefore(
                            independentPin.minimumRetainUntil())
                    || !retention.retainUntil().isAfter(callerObservedTime)) {
                throw denied();
            }
            return new UntrustedArchiveMatch(
                    locator, contentSha, retention.retainUntil());
        } catch (IOException | RuntimeException e) {
            throw denied();
        }
    }

    private static boolean validLocator(VersionLocator locator) {
        return locator != null
                && validToken(locator.administrativeDomain())
                && validToken(locator.objectKey())
                && validToken(locator.versionToken())
                && !"null".equalsIgnoreCase(locator.versionToken());
    }

    private static boolean validToken(String token) {
        return token != null && !token.isBlank()
                && token.length() <= 1024
                && token.equals(token.strip());
    }

    private static boolean validSha(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Untrusted exact-version immutable witness evidence rejected");
    }
}
