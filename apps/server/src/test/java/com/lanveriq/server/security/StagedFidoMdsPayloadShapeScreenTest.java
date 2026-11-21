package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** All input below is invented data; there are no approved FIDO roots or keys. */
class StagedFidoMdsPayloadShapeScreenTest {
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");
    private static final String AAGUID = "00000000-0000-7000-8000-000000000001";

    private static Map<String, Object> entry() {
        return new HashMap<>(Map.of(
                "aaguid", AAGUID,
                "metadataStatement", Map.of("description", "SYNTHETIC-NOT-FIDO"),
                "statusReports", List.of(Map.of(
                        "status", "FIDO_CERTIFIED", "effectiveDate", "2026-11-20")),
                "timeOfLastStatusChange", "2026-11-20"));
    }

    private static void rejected(Object entries) {
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        entries, NOW));
    }

    @Test
    void structurallyValidSyntheticAndUnknownStatusReturnNoTrustDecision() {
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(), NOW));
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(entry()), NOW));
        // Unknown status must NOT be interpreted as certified. At this
        // layer it remains opaque; there is no trust/admission output.
        var unknown = entry();
        unknown.put("statusReports", List.of(Map.of(
                "status", "UNKNOWN_FUTURE_STATUS")));
        unknown.put("futureOptionalField", Map.of("version", 42));
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(unknown), NOW));
        var revoked = entry();
        revoked.put("statusReports", List.of(Map.of("status", "REVOKED")));
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(revoked), NOW));
        // The above NEVER licenses registration. All records remain untrusted.
    }

    @Test
    void rejectsMissingMalformedAndAmbiguousIdentity() {
        rejected(null);
        rejected(Map.of("aaguid", AAGUID));
        rejected(List.of("not-an-entry"));
        rejected(List.of(Map.of()));
        for (String identity : new String[]{" ", "synthetic", AAGUID + "/../",
                "00000000-0000-7000-8000-000000000001/"}) {
            var bad = entry();
            bad.put("aaguid", identity);
            rejected(List.of(bad));
        }
        var conflict = entry();
        conflict.put("aaid", "1234#5678");
        rejected(List.of(conflict));
        var missing = entry();
        missing.remove("aaguid");
        rejected(List.of(missing));

        var uaf = entry();
        uaf.remove("aaguid");
        uaf.put("aaid", "1234#5678");
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(uaf), NOW));
        var u2f = entry();
        u2f.remove("aaguid");
        u2f.put("attestationCertificateKeyIdentifiers",
                List.of("a".repeat(40)));
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(u2f), NOW));
        u2f.put("attestationCertificateKeyIdentifiers",
                List.of("A".repeat(40)));
        rejected(List.of(u2f));
    }

    @Test
    void rejectsMissingMetadataStatusReportsAndMalformedDates() {
        var bad = entry();
        bad.remove("metadataStatement");
        rejected(List.of(bad));
        bad.put("metadataStatement", Map.of());
        rejected(List.of(bad));
        bad.put("metadataStatement", "opaque JSON string");
        rejected(List.of(bad));

        bad = entry();
        bad.remove("statusReports");
        rejected(List.of(bad));
        bad.put("statusReports", Map.of("status", "FIDO_CERTIFIED"));
        rejected(List.of(bad));
        bad.put("statusReports", List.of(Map.of("effectiveDate", "2026-11-20")));
        rejected(List.of(bad));
        bad.put("statusReports", List.of(Map.of("status", " ")));
        rejected(List.of(bad));
        bad.put("statusReports", List.of(Map.of(
                "status", "REVOKED", "effectiveDate", "2026-12-01")));
        rejected(List.of(bad));
        bad.put("statusReports", List.of(Map.of("status", "REVOKED")));
        bad.remove("timeOfLastStatusChange");
        rejected(List.of(bad));
        for (String changed : new String[]{
                "2026-11-22", "not-a-date", "2026-02-30", "2026-11-21T00:00:00Z"}) {
            bad.put("timeOfLastStatusChange", changed);
            rejected(List.of(bad));
        }
    }

    @Test
    void rejectsMalformedRogueListCouplingAndOversizedCollections() {
        var bad = entry();
        bad.put("rogueListURL", "https://mds.example.invalid/rogue.json");
        rejected(List.of(bad));
        bad.put("rogueListHash", "test-only-hash");
        assertDoesNotThrow(() ->
                StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                        List.of(bad), NOW));
        bad.put("rogueListURL", "http://untrusted.invalid/rogue.json");
        rejected(List.of(bad));

        var tooManyStatuses = entry();
        var statuses = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            statuses.add(Map.of("status", "FIDO_CERTIFIED"));
        }
        tooManyStatuses.put("statusReports", statuses);
        rejected(List.of(tooManyStatuses));

        var tooManyEntries = new ArrayList<>();
        for (int i = 0; i < 50_001; i++) {
            tooManyEntries.add(entry());
        }
        rejected(tooManyEntries);
    }
}
