package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StagedOfflineSignedFidoMdsEnvelopeInspectorTest {
    private static final Instant NOW = Instant.parse("2026-11-20T09:00:00Z");
    private final StagedOfflineSignedFidoMdsEnvelopeInspector inspector =
            new StagedOfflineSignedFidoMdsEnvelopeInspector(
                    Clock.fixed(NOW, ZoneOffset.UTC));

    private static KeyPair ephemeralSigner() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String syntheticBlob(KeyPair signer, long iat, String payload)
            throws Exception {
        return syntheticBlobWithHeader(signer,
                new JWSHeader.Builder(JWSAlgorithm.ES256).customParam("iat", iat).build(),
                payload);
    }

    private static String syntheticBlobWithHeader(KeyPair signer, JWSHeader header,
                                                  String payload) throws Exception {
        JWSObject jws = new JWSObject(header, new Payload(payload));
        jws.sign(new ECDSASigner((ECPrivateKey) signer.getPrivate()));
        return jws.serialize();
    }

    private static String payload(long sequence) {
        return "{\"legalHeader\":\"synthetic terms not FIDO-authorized\","
                + "\"no\":" + sequence + ",\"entries\":[]}";
    }

    @Test
    void realEs256SignatureOnlyProducesUntrustedSyntheticEnvelope() throws Exception {
        KeyPair key = ephemeralSigner();
        var envelope = inspector.inspectSyntheticOnly(
                syntheticBlob(key, NOW.getEpochSecond(), payload(7)),
                (ECPublicKey) key.getPublic(), 6);
        assertEquals(7, envelope.observedSequence());
        assertEquals(NOW, envelope.syntheticIssuedAt());
        assertEquals(64, envelope.jwsSha256().length());
        assertTrue(envelope.toString().contains("UntrustedEnvelope"));
    }

    @Test
    void refusesWrongSignerAndTamperedPayload() throws Exception {
        KeyPair signer = ephemeralSigner();
        KeyPair wrong = ephemeralSigner();
        String jws = syntheticBlob(signer, NOW.getEpochSecond(), payload(1));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(jws, (ECPublicKey) wrong.getPublic(), 0));
        String[] parts = jws.split("\\.");
        String other = syntheticBlob(signer, NOW.getEpochSecond(), payload(2));
        String[] otherParts = other.split("\\.");
        String tampered = parts[0] + "." + otherParts[1] + "." + parts[2];
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(tampered, (ECPublicKey) signer.getPublic(), 0));
    }

    @Test
    void refusesReplaySkipExpiredAndFutureMetadataEvenWithValidSignature() throws Exception {
        KeyPair key = ephemeralSigner();
        ECPublicKey pub = (ECPublicKey) key.getPublic();
        String accepted = syntheticBlob(key, NOW.getEpochSecond(), payload(3));
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectSyntheticOnly(accepted, pub, 3));
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectSyntheticOnly(accepted, pub, 1));
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectSyntheticOnly(
                        syntheticBlob(key, NOW.minusSeconds(3 * 86400L).getEpochSecond(),
                                payload(3)), pub, 2));
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectSyntheticOnly(
                        syntheticBlob(key, NOW.plusSeconds(3600).getEpochSecond(),
                                payload(3)), pub, 2));
    }

    @Test
    void refusesHeaderKeyInjectionAndMissingIat() throws Exception {
        KeyPair key = ephemeralSigner();
        ECPublicKey pub = (ECPublicKey) key.getPublic();
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(syntheticBlobWithHeader(key,
                        new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("attacker-key")
                                .customParam("iat", NOW.getEpochSecond()).build(),
                        payload(1)), pub, 0));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(syntheticBlobWithHeader(key,
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .x509CertURL(URI.create("https://example.invalid/forged.crt"))
                                .customParam("iat", NOW.getEpochSecond()).build(),
                        payload(1)), pub, 0));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(syntheticBlobWithHeader(key,
                        new JWSHeader.Builder(JWSAlgorithm.ES256).build(),
                        payload(1)), pub, 0));
    }

    @Test
    void refusesInvalidPayloadFieldsMalformedAndOversizedInputs() throws Exception {
        KeyPair key = ephemeralSigner();
        ECPublicKey pub = (ECPublicKey) key.getPublic();
        for (String body : new String[]{
                "{\"no\":1,\"entries\":[]}",
                "{\"legalHeader\":\"x\",\"no\":0,\"entries\":[]}",
                "{\"legalHeader\":\"x\",\"no\":1,\"entries\":{}}",
                "{\"legalHeader\":\"x\",\"no\":1,\"entries\":[],\"nextUpdate\":0}"
        }) {
            String blob = syntheticBlob(key, NOW.getEpochSecond(), body);
            assertThrows(AccessDeniedException.class,
                    () -> inspector.inspectSyntheticOnly(blob, pub, 0));
        }
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly(null, pub, 0));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly("x".repeat(131073), pub, 0));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOnly("not.a.jwt", pub, 0));
    }
}
