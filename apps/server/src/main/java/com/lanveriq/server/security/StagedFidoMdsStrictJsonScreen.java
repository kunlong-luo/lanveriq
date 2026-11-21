package com.lanveriq.server.security;

import java.util.Base64;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/**
 * A11-D4-C11: deny duplicate JSON object member names in the ORIGINAL
 * BASE64URL-encoded protected JWS header and payload, at every nesting depth.
 * Never use a reserialized Nimbus map to screen original signed bytes.
 *
 * This remains a package-private, unmounted and permanently UNTRUSTED source
 * preflight; there are no Spring Beans, HTTP routes, credentials, trusted roots,
 * network readers, authorization decisions or production MDS consumers.
 */
final class StagedFidoMdsStrictJsonScreen {
    private static final int MAX_HEADER_BYTES = 128 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 24 * 1024 * 1024;

    // Boot 4 already supplies Jackson 3. Reuse it instead of maintaining
    // a custom JSON tokenizer or changing the application's global mapper.
    private static final JsonFactory STRICT_JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private StagedFidoMdsStrictJsonScreen() {}

    static void requireUnambiguousCompactJws(String compactJws) {
        if (compactJws == null) {
            throw denied();
        }
        int firstDot = compactJws.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : compactJws.indexOf('.', firstDot + 1);
        if (firstDot < 1 || secondDot <= firstDot + 1
                || secondDot >= compactJws.length() - 1
                || compactJws.indexOf('.', secondDot + 1) >= 0) {
            throw denied();
        }
        try {
            // Use EXACT JWS segments rather than parsed/reserialized header
            // or payload maps: the latter can discard duplicate names.
            requireSingleJsonObject(Base64.getUrlDecoder().decode(
                    compactJws.substring(0, firstDot)), MAX_HEADER_BYTES);
            requireSingleJsonObject(Base64.getUrlDecoder().decode(
                    compactJws.substring(firstDot + 1, secondDot)),
                    MAX_PAYLOAD_BYTES);
        } catch (IllegalArgumentException invalid) {
            throw denied();
        }
    }

    static void requireSingleJsonObject(byte[] raw, int maximumBytes) {
        if (raw == null || raw.length < 2 || raw.length > maximumBytes) {
            throw denied();
        }
        try (var parser = STRICT_JSON.createParser(raw)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw denied();
            }
            // skipChildren traverses every nested object and invokes Jackson
            // duplicate detection even in fields ignored by later policy.
            parser.skipChildren();
            // Reject additional root JSON tokens and trailing garbage too.
            if (parser.nextToken() != null) {
                throw denied();
            }
        } catch (JacksonException malformedOrDuplicate) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Ambiguous offline MDS JSON object denied");
    }
}
