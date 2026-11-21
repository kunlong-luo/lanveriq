package com.lanveriq.server.security;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C9: fail-closed STRUCTURAL screening of entries carried inside an
 * already cryptographically verified FIDO MDS-shaped BLOB. Never grants
 * metadata trust, authenticator admission, registration or MFA.
 *
 * FIDO MDS 3.1.1 requires a model identifier, metadataStatement object,
 * statusReports array and timeOfLastStatusChange. This only rejects broken
 * shapes; it does NOT decide whether an authenticator status is acceptable.
 * Unknown status codes remain opaque, not silently treated as certified.
 * Package-private, non-Bean, no networking, cache, secrets or database.
 */
final class StagedFidoMdsPayloadShapeScreen {
    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_STATUS_REPORTS = 64;
    private static final int MAX_CERT_IDENTIFIERS = 32;

    private StagedFidoMdsPayloadShapeScreen() {}

    static void requireUntrustedEntryShapes(Object rawEntries, Instant checkedAt) {
        if (!(rawEntries instanceof List<?> entries)
                || entries.size() > MAX_ENTRIES || checkedAt == null) {
            throw denied();
        }
        LocalDate observedDay = checkedAt.atZone(ZoneOffset.UTC).toLocalDate();
        for (Object raw : entries) {
            if (!(raw instanceof Map<?, ?> entry)) {
                throw denied();
            }
            // FIDO UAF -> AAID, FIDO2 -> AAGUID, U2F -> certificate
            // key identifiers. Ambiguous/missing identity cannot be bound to
            // a single model in a later relying-party policy.
            int identifiers = 0;
            if (entry.containsKey("aaid")) {
                if (!(entry.get("aaid") instanceof String aaid)
                        || !aaid.matches("[0-9a-fA-F]{4}#[0-9a-fA-F]{4}")) {
                    throw denied();
                }
                identifiers++;
            }
            if (entry.containsKey("aaguid")) {
                if (!(entry.get("aaguid") instanceof String aaguid)
                        || !aaguid.matches(
                        "(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
                    throw denied();
                }
                identifiers++;
            }
            if (entry.containsKey("attestationCertificateKeyIdentifiers")) {
                if (!(entry.get("attestationCertificateKeyIdentifiers")
                        instanceof List<?> fingerprints)
                        || fingerprints.isEmpty()
                        || fingerprints.size() > MAX_CERT_IDENTIFIERS) {
                    throw denied();
                }
                for (Object fingerprint : fingerprints) {
                    if (!(fingerprint instanceof String value)
                            || !value.matches("[0-9a-f]{40}")) {
                        throw denied();
                    }
                }
                identifiers++;
            }
            if (identifiers != 1
                    || !(entry.get("metadataStatement") instanceof Map<?, ?> statement)
                    || statement.isEmpty()
                    || !(entry.get("statusReports") instanceof List<?> reports)
                    || reports.size() > MAX_STATUS_REPORTS
                    || !(entry.get("timeOfLastStatusChange") instanceof String lastChange)
                    || !validDate(lastChange, observedDay)) {
                throw denied();
            }
            for (Object rawStatus : reports) {
                if (!(rawStatus instanceof Map<?, ?> report)
                        || !(report.get("status") instanceof String status)
                        || status.isBlank() || status.length() > 128) {
                    throw denied();
                }
                // Do not turn an unfamiliar status into FIDO_CERTIFIED.
                // Policy review (including REVOKED and compromise) remains
                // a *separate*, mandatory acceptance gate.
                if (report.containsKey("effectiveDate")
                        && (!(report.get("effectiveDate") instanceof String date)
                            || !validDate(date, observedDay))) {
                    throw denied();
                }
            }
            // There must be a hash when a rogue list is referenced.
            // NEVER retrieve URLs while performing this offline screen.
            if (entry.containsKey("rogueListURL")) {
                if (!(entry.get("rogueListURL") instanceof String url)
                        || !url.startsWith("https://")
                        || !(entry.get("rogueListHash") instanceof String hash)
                        || hash.isBlank() || hash.length() > 1024) {
                    throw denied();
                }
            }
        }
    }

    private static boolean validDate(String date, LocalDate observedDay) {
        try {
            return date != null && date.length() == 10
                    && !LocalDate.parse(date).isAfter(observedDay);
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted MDS payload entry shape denied");
    }
}
