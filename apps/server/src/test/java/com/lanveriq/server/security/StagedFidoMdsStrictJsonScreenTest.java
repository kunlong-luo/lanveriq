package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Disposable offline JSON; no official FIDO data or external access. */
class StagedFidoMdsStrictJsonScreenTest {

    private static byte[] utf8(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String compact(String header, String payload) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(utf8(header)) + "."
                + encoder.encodeToString(utf8(payload)) + ".synthetic";
    }

    @Test
    void distinctNestedMemberNamesRemainStructurallyAdmissibleNotTrusted() {
        assertDoesNotThrow(() -> StagedFidoMdsStrictJsonScreen
                .requireUnambiguousCompactJws(compact(
                        "{\"alg\":\"ES256\",\"x5c\":[\"synthetic\"]}",
                        "{\"no\":15,\"entries\":[{\"aaguid\":\"synthetic\","
                        + "\"statusReports\":[{\"status\":\"REVOKED\"}]}]}")));
        // This returns void only; it CANNOT permit registration, MFA or trust.
    }

    @Test
    void duplicateFieldsAtHeaderRootPayloadRootAndDeepStatusAreDenied() {
        String header = "{\"alg\":\"ES256\",\"alg\":\"RS256\"}";
        String payload = "{\"no\":2,\"entries\":[]}";
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(
                        compact(header, payload)));
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(
                        compact("{\"alg\":\"ES256\"}",
                                "{\"no\":2,\"no\":3,\"entries\":[]}")));
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(
                        compact("{\"alg\":\"ES256\"}",
                                "{\"entries\":[{\"statusReports\":["
                                + "{\"status\":\"REVOKED\","
                                + "\"status\":\"FIDO_CERTIFIED\"}]}]}")));
    }

    @Test
    void duplicateEscapedJsonNameAndTrailingRootDataAreDenied() {
        // Escaped JSON member keys must be compared after JSON decoding,
        // not with regex/text-level key matching.
        String sameAfterUnescaping = "{\"no\":1,\"n\\u006f\":2}";
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(
                        compact("{\"alg\":\"ES256\"}", sameAfterUnescaping)));
        for (String extra : new String[]{"{}{}", "{} true", "[]", "null", "{"}) {
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(
                            compact("{\"alg\":\"ES256\"}", extra)));
        }
    }

    @Test
    void invalidCompactAndBase64UrlBytesAreDenied() {
        for (String invalid : new String[]{
                null, "", "missing.dots", "a.b.", ".b.signature",
                "header..signature", "a.b.signature.extra",
                "!.e30.signature", "e30.!.signature"}) {
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsStrictJsonScreen
                            .requireUnambiguousCompactJws(invalid));
        }
    }
}
