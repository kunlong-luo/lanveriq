package com.lanveriq.server.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * D4-C2 synthetic-only screening of offline, *untrusted* FIDO-shaped JWS.
 *
 * This is NOT a FIDO MDS3 client, production trust anchor, x5c/x5u chain
 * validator, certified metadata provenance or authenticator admission.
 * The caller-provided verifier key is TEST DATA, NEVER an approved FIDO
 * signer. A cryptographically valid synthetic JWS is still NOT TRUSTED.
 * There is intentionally no Spring Bean, network loader or consumer.
 */
final class StagedOfflineSignedFidoMdsEnvelopeInspector {
    private static final int MAX_JWS_LENGTH = 128 * 1024;
    private static final Duration MAX_SYNTHETIC_AGE = Duration.ofDays(2);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final Clock clock;

    StagedOfflineSignedFidoMdsEnvelopeInspector(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    UntrustedEnvelope inspectSyntheticOnly(
            String compactJws, ECPublicKey syntheticSigningPublicKey,
            long lastObservedSequence) {
        if (compactJws == null || compactJws.length() < 20
                || compactJws.length() > MAX_JWS_LENGTH
                || syntheticSigningPublicKey == null
                || lastObservedSequence < 0
                || lastObservedSequence == Long.MAX_VALUE) {
            throw denied();
        }
        try {
            JWSObject jws = JWSObject.parse(compactJws);
            var header = jws.getHeader();
            // No automatic trust in JWT-advertised keys, kid, x5u URLs,
            // x5c certificate arrays or detached/unprotected payloads.
            if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())
                    || header.getJWK() != null
                    || header.getJWKURL() != null
                    || header.getKeyID() != null
                    || header.getX509CertURL() != null
                    || header.getX509CertChain() != null
                    || (header.getCriticalParams() != null
                        && !header.getCriticalParams().isEmpty())) {
                throw denied();
            }
            Object issuedAt = header.getCustomParam("iat");
            if (!(issuedAt instanceof Long || issuedAt instanceof Integer)) {
                throw denied();
            }
            long epochSeconds = ((Number) issuedAt).longValue();
            if (epochSeconds <= 0) {
                throw denied();
            }
            Instant now = clock.instant();
            Instant issued = Instant.ofEpochSecond(epochSeconds);
            if (issued.isAfter(now.plus(MAX_CLOCK_SKEW))
                    || issued.isBefore(now.minus(MAX_SYNTHETIC_AGE))) {
                throw denied();
            }
            if (!jws.verify(new ECDSAVerifier(syntheticSigningPublicKey))) {
                throw denied();
            }
            Map<String, Object> blob = jws.getPayload().toJSONObject();
            if (blob == null
                    || !(blob.get("legalHeader") instanceof String legal
                        && !legal.isBlank())
                    || !(blob.get("entries") instanceof List<?> entries)
                    || entries.size() > 8192) {
                throw denied();
            }
            Object number = blob.get("no");
            if (!(number instanceof Long || number instanceof Integer)) {
                throw denied();
            }
            long sequence = ((Number) number).longValue();
            // MDS serials must be consecutive. Not accepting the first no=0.
            if (sequence <= 0 || sequence != lastObservedSequence + 1) {
                throw denied();
            }
            // FIDO MDS3.1 warns against assuming nextUpdate always exists.
            if (blob.containsKey("nextUpdate")
                    && !(blob.get("nextUpdate") instanceof String)) {
                throw denied();
            }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(compactJws.getBytes(StandardCharsets.US_ASCII)));
            return new UntrustedEnvelope(sequence, issued, digest);
        } catch (ParseException | JOSEException | GeneralSecurityException
                | IllegalArgumentException error) {
            throw denied();
        }
    }

    record UntrustedEnvelope(long observedSequence, Instant syntheticIssuedAt,
                             String jwsSha256) {
        // This record deliberately has NO trusted/verified/admitted boolean.
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Offline FIDO-shaped metadata is not trusted");
    }
}
