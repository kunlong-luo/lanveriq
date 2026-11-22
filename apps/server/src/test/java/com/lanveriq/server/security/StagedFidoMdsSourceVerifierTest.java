package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64;
import java.math.BigInteger;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StagedFidoMdsSourceVerifierTest {
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");
    private final StagedFidoMdsSourceVerifier verifier =
            new StagedFidoMdsSourceVerifier();

    private static KeyPair key(String algorithm) throws Exception {
        var g = KeyPairGenerator.getInstance(algorithm);
        if ("EC".equals(algorithm)) {
            g.initialize(new ECGenParameterSpec("secp256r1"));
        } else if ("RSA".equals(algorithm)) {
            g.initialize(2048);
        }
        return g.generateKeyPair();
    }

    private static BigInteger serial() {
        return new BigInteger(80, new SecureRandom()).add(BigInteger.ONE);
    }

    private static X509Certificate certificate(
            X500Name issuer, PrivateKey signer, X500Name subject,
            KeyPair holder, boolean ca, int usage, String dnsName) throws Exception {
        var builder = new JcaX509v3CertificateBuilder(
                issuer, serial(), Date.from(NOW.minusSeconds(86400)),
                Date.from(NOW.plusSeconds(86400 * 20)), subject, holder.getPublic());
        builder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(usage));
        if (!ca) {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName)));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        }
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .build(signer)));
    }

    private static X509CRL crl(X500Name issuer, PrivateKey signer,
                               BigInteger revoked, Instant notAfter)
            throws Exception {
        var builder = new X509v2CRLBuilder(issuer,
                Date.from(NOW.minusSeconds(3600)));
        builder.setNextUpdate(Date.from(notAfter));
        if (revoked != null) {
            builder.addCRLEntry(revoked, Date.from(NOW.minusSeconds(300)),
                    CRLReason.keyCompromise);
        }
        return new JcaX509CRLConverter().getCRL(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .build(signer)));
    }

    private record Fixture(KeyPair signerKey, KeyPair caKey, KeyPair timeKey,
                           X500Name issuer, X509Certificate root,
                           X509Certificate signerCert, boolean rsa) {
        StagedFidoMdsSourceVerifier.ExternalApproval claimedPolicy()
                throws Exception {
            return new StagedFidoMdsSourceVerifier.ExternalApproval(
                    root, fingerprint(root.getEncoded()), timeKey.getPublic(),
                    fingerprint(timeKey.getPublic().getEncoded()));
        }

        StagedFidoMdsSourceVerifier.SignedTimeEvidence time(
                long seq, Instant at, String compactJws, List<X509CRL> crls)
                throws Exception {
            var crlHashes = new java.util.ArrayList<String>();
            for (X509CRL current : crls) {
                crlHashes.add(fingerprint(current.getEncoded()));
            }
            crlHashes.sort(String::compareTo);
            var crlEvidence = fingerprint(
                    String.join(":", crlHashes).getBytes(StandardCharsets.US_ASCII));
            var transcript = "lanveriq/fido-mds/source-time/v2\n"
                    + at + "\n" + seq + "\n"
                    + fingerprint(compactJws.getBytes(StandardCharsets.US_ASCII)) + "\n"
                    + fingerprint(root.getEncoded()) + "\n"
                    + crlEvidence + "\n"
                    + fingerprint(timeKey.getPublic().getEncoded()) + "\n";
            var sig = Signature.getInstance("Ed25519");
            sig.initSign(timeKey.getPrivate());
            sig.update(transcript.getBytes(StandardCharsets.US_ASCII));
            return new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                    at, seq, sig.sign());
        }

        StagedFidoMdsSourceVerifier.SignedTimeEvidence unboundLegacyTime(
                long seq, Instant at) throws Exception {
            var sig = Signature.getInstance("Ed25519");
            sig.initSign(timeKey.getPrivate());
            sig.update(("lanveriq/fido-mds/trusted-time/v1\n"
                    + at.getEpochSecond() + "\n" + seq)
                    .getBytes(StandardCharsets.US_ASCII));
            return new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                    at, seq, sig.sign());
        }

        X509CRL freshCrl() throws Exception {
            return crl(issuer, caKey.getPrivate(), null, NOW.plusSeconds(86400));
        }

        String jws(long blobNo, boolean withX5u) throws Exception {
            return jwsWithEntries(blobNo, withX5u, "[]");
        }

        String jwsWithEntries(long blobNo, boolean withX5u, String entriesJson)
                throws Exception {
            var header = new JWSHeader.Builder(
                    rsa ? JWSAlgorithm.RS256 : JWSAlgorithm.ES256)
                    .customParam("iat", NOW.getEpochSecond())
                    .x509CertChain(List.of(Base64.encode(signerCert.getEncoded())));
            if (withX5u) {
                header.x509CertURL(URI.create("https://attacker.invalid/ca.cer"));
            }
            var jws = new JWSObject(header.build(), new Payload(
                    "{\"legalHeader\":\"test-only terms\",\"no\":" + blobNo
                    + ",\"entries\":" + entriesJson
                    + ",\"nextUpdate\":\"2026-11-22\"}"));
            if (rsa) {
                jws.sign(new RSASSASigner((RSAPrivateKey) signerKey.getPrivate()));
            } else {
                jws.sign(new ECDSASigner((ECPrivateKey) signerKey.getPrivate()));
            }
            return jws.serialize();
        }
    }

    private static String fingerprint(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private static Fixture fixture(boolean rsa) throws Exception {
        KeyPair ca = key("EC");
        KeyPair signer = key(rsa ? "RSA" : "EC");
        KeyPair time = key("Ed25519");
        X500Name issuer = new X500Name("CN=TEST-ONLY-NOT-FIDO-APPROVED");
        var root = certificate(issuer, ca.getPrivate(), issuer, ca,
                true, KeyUsage.keyCertSign | KeyUsage.cRLSign, null);
        var leaf = certificate(issuer, ca.getPrivate(),
                new X500Name("CN=TEST-ONLY-MDS-SIGNER"), signer,
                false, KeyUsage.digitalSignature, "mds.fidoalliance.org");
        return new Fixture(signer, ca, time, issuer, root, leaf, rsa);
    }

    private StagedFidoMdsSourceVerifier.UntrustedSourceEvidence inspect(
            Fixture f, String jws, X509CRL crl, long lastNo, long lastTime)
            throws Exception {
        return verifier.inspectOfflineOnly(jws, f.claimedPolicy(),
                f.time(9, NOW, jws, List.of(crl)), List.of(crl),
                lastNo, lastTime);
    }

    @Test
    void ecdsaAndRsaSignaturesWithAnchoredPkixAndFreshCrlOnlyReturnUntrustedEvidence()
            throws Exception {
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            var evidence = inspect(f, f.jws(41, false), f.freshCrl(), 40, 8);
            assertEquals(41, evidence.blobNumber());
            assertEquals(9, evidence.signedTimeSequence());
            assertEquals(NOW, evidence.checkedAt());
            assertEquals(64, evidence.rootSha256().length());
            assertTrue(evidence.toString().contains("UntrustedSourceEvidence"));
        }
    }

    @Test
    void signedValidFidoShapedEntriesRemainUntrustedAfterStructureScreen()
            throws Exception {
        String entry = """
                [{"aaguid":"00000000-0000-7000-8000-000000000001",
                  "metadataStatement":{"description":"EPHEMERAL-TEST-NOT-FIDO"},
                  "statusReports":[{"status":"REVOKED",
                                     "effectiveDate":"2026-11-20"}],
                  "timeOfLastStatusChange":"2026-11-20"}]
                """;
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            String jws = f.jwsWithEntries(41, false, entry);
            var result = inspect(f, jws, f.freshCrl(), 40, 8);
            assertEquals(41, result.blobNumber());
            assertTrue(result.toString().contains("UntrustedSourceEvidence"));
            // REVOKED is intentionally NOT elevated to a grant merely
            // because this offline signature and structure checked out.
        }
    }

    @Test
    void cryptographicallyValidButMalformedEntryShapesAreDenied()
            throws Exception {
        Fixture f = fixture(false);
        var crl = f.freshCrl();
        String[] malformed = {
                "[null]",
                "[{}]",
                "[{\"aaguid\":\"00000000-0000-7000-8000-000000000001\"}]",
                """
                [{"aaguid":"00000000-0000-7000-8000-000000000001",
                  "metadataStatement":{"description":"SYNTHETIC"},
                  "statusReports":[{"effectiveDate":"2026-11-20"}],
                  "timeOfLastStatusChange":"2026-11-20"}]
                """,
                """
                [{"aaguid":"00000000-0000-7000-8000-000000000001",
                  "metadataStatement":{"description":"SYNTHETIC"},
                  "statusReports":[{"status":"FIDO_CERTIFIED"}],
                  "timeOfLastStatusChange":"2026-12-01"}]
                """
        };
        for (String entries : malformed) {
            String jws = f.jwsWithEntries(41, false, entries);
            var correctlySignedTime = f.time(9, NOW, jws, List.of(crl));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectOfflineOnly(jws, f.claimedPolicy(),
                            correctlySignedTime, List.of(crl), 40, 8));
        }
    }


    @Test
    void cryptographicallyValidDuplicateModelIdsStillRejectBeforeUntrustedEvidence()
            throws Exception {
        // Each envelope is correctly signed by a disposable MDS-shaped
        // signer; the Ed25519 time proof also binds the exact JWS and CRL.
        // Repeated identifiers must fail even with valid PKIX/JWS evidence.
        String guidEntry = """
                {"aaguid":"ABCDEF12-0000-7000-8000-000000000001",
                 "metadataStatement":{"description":"SYNTHETIC"},
                 "statusReports":[{"status":"REVOKED"}],
                 "timeOfLastStatusChange":"2026-11-20"}
                """.strip();
        String guidCase = guidEntry.replace("ABCDEF12", "abcdef12");
        String u2fEntry = """
                {"attestationCertificateKeyIdentifiers":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],
                 "metadataStatement":{"description":"SYNTHETIC-U2F"},
                 "statusReports":[],
                 "timeOfLastStatusChange":"2026-11-20"}
                """.strip();
        String[] repeated = {
                "[" + guidEntry + "," + guidCase + "]",
                "[" + u2fEntry + "," + u2fEntry + "]"
        };
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            X509CRL crl = f.freshCrl();
            for (String entries : repeated) {
                String jws = f.jwsWithEntries(43, false, entries);
                var signedTime = f.time(9, NOW, jws, List.of(crl));
                assertThrows(AccessDeniedException.class, () ->
                        verifier.inspectOfflineOnly(jws, f.claimedPolicy(),
                                signedTime, List.of(crl), 42, 8));
            }
            // Distinct models still yield UNTRUSTED evidence, never a grant.
            String distinct = "[" + guidEntry + ","
                    + guidEntry.replace("ABCDEF12", "12345678") + "]";
            String jws = f.jwsWithEntries(43, false, distinct);
            assertEquals(43, inspect(f, jws, crl, 42, 8).blobNumber());
        }
    }


    @Test
    void signedJwsWithDuplicateJsonFieldsAtAnyDepthRejectsEvenWithValidTimeAndCrl()
            throws Exception {
        String guid = "00000000-0000-7000-8000-000000000001";
        String entry = "{\"aaguid\":\"" + guid + "\","
                + "\"metadataStatement\":{\"description\":\"SYNTHETIC\"},"
                + "\"statusReports\":[],"
                + "\"timeOfLastStatusChange\":\"2026-11-20\"}";
        // All payloads below are signed by genuine disposable ES256/RS256
        // test keys and bound to an exact JWS+CRL+root Ed25519 time proof.
        String[] ambiguous = {
                // Two top-level 'entries' + 'no' members:
                "[],\"no\":44,\"entries\":[]",
                // Duplicate member within the same metadata entry:
                "[{\"aaguid\":\"" + guid + "\",\"aaguid\":\"" + guid + "\","
                        + "\"metadataStatement\":{\"description\":\"SYNTHETIC\"},"
                        + "\"statusReports\":[],"
                        + "\"timeOfLastStatusChange\":\"2026-11-20\"}]",
                // Status names are both syntactically valid; parser must
                // not discard a REVOKED report through last-wins semantics.
                "[{\"aaguid\":\"" + guid + "\","
                        + "\"metadataStatement\":{\"description\":\"SYNTHETIC\"},"
                        + "\"statusReports\":[{\"status\":\"REVOKED\","
                        + "\"status\":\"FIDO_CERTIFIED\"}],"
                        + "\"timeOfLastStatusChange\":\"2026-11-20\"}]",
                // Duplicate property in a previously ignored extension:
                "[" + entry.substring(0, entry.length() - 1)
                        + ",\"futureExtension\":{\"foo\":1,\"foo\":2}}]"
        };
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            X509CRL crl = f.freshCrl();
            for (String entries : ambiguous) {
                String jws = f.jwsWithEntries(48, false, entries);
                var signedTime = f.time(9, NOW, jws, List.of(crl));
                assertThrows(AccessDeniedException.class, () ->
                        verifier.inspectOfflineOnly(jws, f.claimedPolicy(),
                                signedTime, List.of(crl), 47, 8));
            }
            // A signed unambiguous entry is still untrusted evidence only.
            String valid = f.jwsWithEntries(48, false, "[" + entry + "]");
            assertEquals(48, inspect(f, valid, crl, 47, 8).blobNumber());
        }
    }


    private static StagedFidoMdsIndependentReviewSeal.ReviewClaim reviewClaim(
            StagedFidoMdsSourceVerifier.UntrustedSourceEvidence evidence,
            String source, Instant validFrom, Instant expiresAt, long sequence) {
        return new StagedFidoMdsIndependentReviewSeal.ReviewClaim(
                source, evidence.checkedAt(), validFrom, expiresAt,
                sequence, evidence.blobNumber(), evidence.signedTimeSequence(),
                evidence.leafSha256(), evidence.blobSha256(),
                evidence.rootSha256(), evidence.crlEvidenceSha256(),
                evidence.timeKeySha256(), evidence.signedTimeProofSha256());
    }

    private static StagedFidoMdsIndependentReviewSeal.SignedReview signReview(
            StagedFidoMdsIndependentReviewSeal.ReviewClaim claim,
            PrivateKey first, PrivateKey second) throws Exception {
        byte[] message = StagedFidoMdsIndependentReviewSeal.transcript(claim);
        Signature a = Signature.getInstance("Ed25519");
        a.initSign(first);
        a.update(message);
        Signature b = Signature.getInstance("Ed25519");
        b.initSign(second);
        b.update(message);
        return new StagedFidoMdsIndependentReviewSeal.SignedReview(
                claim, a.sign(), b.sign());
    }

    @Test
    void independentPinnedReviewerKeysBindExactUntrustedSourceAndRejectSelfAssertion()
            throws Exception {
        // Both JWS algorithms exercise the ACTUAL PKIX, signed CRL and time
        // verifier before the new independent provenance guard returns.
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            X509CRL crl = f.freshCrl();
            String jws = f.jws(51, false);
            var time = f.time(9, NOW, jws, List.of(crl));
            var rawUntrusted = verifier.inspectOfflineOnly(
                    jws, f.claimedPolicy(), time, List.of(crl), 50, 8);
            KeyPair reviewerA = key("Ed25519");
            KeyPair reviewerB = key("Ed25519");
            KeyPair attacker = key("Ed25519");
            var separatelyPinned = new StagedFidoMdsIndependentReviewSeal
                    .PinnedReviewKeys(reviewerA.getPublic(),
                    fingerprint(reviewerA.getPublic().getEncoded()),
                    reviewerB.getPublic(),
                    fingerprint(reviewerB.getPublic().getEncoded()));
            var claimed = reviewClaim(rawUntrusted,
                    StagedFidoMdsIndependentReviewSeal.EXPECTED_MDS_SOURCE,
                    NOW.minusSeconds(90), NOW.plusSeconds(3600), 7);
            var signed = signReview(claimed, reviewerA.getPrivate(),
                    reviewerB.getPrivate());
            assertEquals(51, verifier.inspectWithIndependentReviewOfflineOnly(
                    jws, f.claimedPolicy(), time, List.of(crl), 50, 8,
                    separatelyPinned, signed, 6).blobNumber());

            // Caller can self-assert a fake root and even correctly sign
            // the JWS/time, but cannot forge either separately pinned key.
            var forgedByCaller = signReview(claimed, attacker.getPrivate(),
                    attacker.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, forgedByCaller, 6));
            var justOneRealKey = signReview(claimed, reviewerA.getPrivate(),
                    attacker.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, justOneRealKey, 6));
            var duplicatedSigner = new StagedFidoMdsIndependentReviewSeal
                    .PinnedReviewKeys(reviewerA.getPublic(),
                    fingerprint(reviewerA.getPublic().getEncoded()),
                    reviewerA.getPublic(),
                    fingerprint(reviewerA.getPublic().getEncoded()));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            duplicatedSigner, signed, 6));
            var wrongPin = new StagedFidoMdsIndependentReviewSeal
                    .PinnedReviewKeys(reviewerA.getPublic(), "0".repeat(64),
                    reviewerB.getPublic(),
                    fingerprint(reviewerB.getPublic().getEncoded()));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            wrongPin, signed, 6));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            null, signed, 6));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, null, 6));
            // Freshness/anti-replay is a conditional local check only:
            // there is no durable official review ledger configured.
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, signed, 7));

            var expired = signReview(reviewClaim(rawUntrusted,
                    StagedFidoMdsIndependentReviewSeal.EXPECTED_MDS_SOURCE,
                    NOW.minusSeconds(3600), NOW.minusSeconds(1), 8),
                    reviewerA.getPrivate(), reviewerB.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, expired, 6));
            var overlong = signReview(reviewClaim(rawUntrusted,
                    StagedFidoMdsIndependentReviewSeal.EXPECTED_MDS_SOURCE,
                    NOW.minusSeconds(60), NOW.plusSeconds(86400 * 8L), 8),
                    reviewerA.getPrivate(), reviewerB.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, overlong, 6));
            var wrongSource = signReview(reviewClaim(rawUntrusted,
                    "https://attacker.invalid/", NOW.minusSeconds(90),
                    NOW.plusSeconds(3600), 8),
                    reviewerA.getPrivate(), reviewerB.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), time, List.of(crl), 50, 8,
                            separatelyPinned, wrongSource, 6));

            // Both JWSs are independently genuinely signed and time-bound,
            // but the old review's signature cannot bless a new BLOB.
            String secondJws = f.jws(52, false);
            var secondTime = f.time(10, NOW, secondJws, List.of(crl));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(secondJws,
                            f.claimedPolicy(), secondTime, List.of(crl),
                            51, 9, separatelyPinned, signed, 6));
            // A replaced valid CRL with independently renewed signed-time
            // evidence likewise cannot reuse the previous review approval.
            X509CRL replacement = f.freshCrl();
            assertTrue(!fingerprint(crl.getEncoded()).equals(
                    fingerprint(replacement.getEncoded())));
            var replacedTime = f.time(10, NOW, jws, List.of(replacement));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithIndependentReviewOfflineOnly(jws,
                            f.claimedPolicy(), replacedTime,
                            List.of(replacement), 50, 9,
                            separatelyPinned, signed, 6));

            byte[] mutableCopy = signed.firstSignature();
            mutableCopy[0] ^= (byte) 0xff;
            assertEquals(51, verifier.inspectWithIndependentReviewOfflineOnly(
                    jws, f.claimedPolicy(), time, List.of(crl), 50, 8,
                    separatelyPinned, signed, 6).blobNumber());
        }
    }

    private static byte[] officialR46TestReferenceDer() throws Exception {
        // Public certificate copied from the GlobalSign certificate page
        // and independently matched by SHA-256 to Mozilla CCADB R00000947.
        // This is NEVER a preapproved organizational trust-store resource.
        try (var stream = StagedFidoMdsSourceVerifierTest.class
                .getResourceAsStream("/security/untrusted-reference-only/"
                        + "globalsign-root-r46.pem")) {
            if (stream == null) {
                throw new IllegalStateException("Missing public root fixture");
            }
            var certificate = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(stream);
            return certificate.getEncoded();
        }
    }

    @Test
    void publishedGlobalSignR46DerIsOnlyUntrustedReferenceNotTrustAuthorization()
            throws Exception {
        byte[] reference = officialR46TestReferenceDer();
        var candidate = StagedFidoMdsR46PublicReferenceScreen
                .inspectReferenceOnly(reference, NOW);
        assertEquals(StagedFidoMdsR46PublicReferenceScreen
                .PUBLIC_REFERENCE_SHA256, candidate.rootSha256());
        assertTrue(candidate.toString().contains("Untrusted"));
        // The known root's identity is not an MDS approval or an authority.
        byte[] altered = reference.clone();
        altered[altered.length - 3] ^= (byte) 0x01;
        for (byte[] invalid : new byte[][]{
                altered, Arrays.copyOf(reference, reference.length - 1),
                Arrays.copyOf(reference, reference.length + 1),
                new byte[reference.length]}) {
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsR46PublicReferenceScreen
                            .inspectReferenceOnly(invalid, NOW));
        }
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsR46PublicReferenceScreen
                        .inspectReferenceOnly(null, NOW));
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsR46PublicReferenceScreen
                        .inspectReferenceOnly(reference, null));
        assertThrows(AccessDeniedException.class, () ->
                StagedFidoMdsR46PublicReferenceScreen.inspectReferenceOnly(
                        reference, Instant.parse("2047-01-01T00:00:00Z")));
    }

    @Test
    void realPublishedR46ReferenceRejectsEvenFullySignedSyntheticFakeRoot()
            throws Exception {
        byte[] publicR46 = officialR46TestReferenceDer();
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            X509CRL crl = f.freshCrl();
            String syntheticJws = f.jws(60, false);
            var signedTime = f.time(9, NOW, syntheticJws, List.of(crl));
            var syntheticEvidence = verifier.inspectOfflineOnly(
                    syntheticJws, f.claimedPolicy(), signedTime,
                    List.of(crl), 59, 8);
            var a = key("Ed25519");
            var b = key("Ed25519");
            var governancePins = new StagedFidoMdsIndependentReviewSeal
                    .PinnedReviewKeys(a.getPublic(),
                    fingerprint(a.getPublic().getEncoded()),
                    b.getPublic(), fingerprint(b.getPublic().getEncoded()));
            var fullySignedReview = signReview(reviewClaim(
                    syntheticEvidence,
                    StagedFidoMdsIndependentReviewSeal.EXPECTED_MDS_SOURCE,
                    NOW.minusSeconds(90), NOW.plusSeconds(3600), 11),
                    a.getPrivate(), b.getPrivate());

            // Original C12 lane handles synthetic signed evidence as
            // UNTRUSTED, but it cannot independently identify official R46.
            assertEquals(60, verifier.inspectWithIndependentReviewOfflineOnly(
                    syntheticJws, f.claimedPolicy(), signedTime,
                    List.of(crl), 59, 8, governancePins,
                    fullySignedReview, 10).blobNumber());

            // New C13 lane denies a caller-owned fake root even with both
            // correctly signed governance proofs and synthetic PKIX/CRL/time.
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithPublishedR46ReferenceOfflineOnly(
                            f.root().getEncoded(), syntheticJws,
                            f.claimedPolicy(), signedTime, List.of(crl),
                            59, 8, governancePins, fullySignedReview, 10));
            // Also deny public R46 bytes paired with the fake declared
            // anchor; a URL or DER alone cannot replace actual chain review.
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithPublishedR46ReferenceOfflineOnly(
                            publicR46, syntheticJws, f.claimedPolicy(),
                            signedTime, List.of(crl), 59, 8,
                            governancePins, fullySignedReview, 10));

            var parsedR46 = (X509Certificate) CertificateFactory
                    .getInstance("X.509").generateCertificate(
                            new ByteArrayInputStream(publicR46));
            var officialShapedClaim =
                    new StagedFidoMdsSourceVerifier.ExternalApproval(
                            parsedR46,
                            StagedFidoMdsR46PublicReferenceScreen
                                    .PUBLIC_REFERENCE_SHA256,
                            f.timeKey().getPublic(),
                            fingerprint(f.timeKey().getPublic().getEncoded()));
            // A real PUBLIC R46 root still cannot validate a leaf issued
            // by a disposable synthetic CA: PKIX must independently fail.
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithPublishedR46ReferenceOfflineOnly(
                            publicR46, syntheticJws, officialShapedClaim,
                            signedTime, List.of(crl), 59, 8,
                            governancePins, fullySignedReview, 10));
        }
    }


    private static StagedFidoMdsAcquisitionReceiptScreen.SignedAcquisition
            signAcquisition(StagedFidoMdsAcquisitionReceiptScreen.AcquisitionClaim claim,
                            PrivateKey key) throws Exception {
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(StagedFidoMdsAcquisitionReceiptScreen.transcript(claim));
        return new StagedFidoMdsAcquisitionReceiptScreen.SignedAcquisition(
                claim, signer.sign());
    }

    private static StagedFidoMdsAcquisitionReceiptScreen.AcquisitionClaim
            acquisitionClaim(
                StagedFidoMdsSourceVerifier.UntrustedSourceEvidence evidence,
                long sequence, Instant fetched, String tlsVersion,
                int httpStatus, boolean redirect, String crlSourceUrl,
                String actualCrlSha) {
        return new StagedFidoMdsAcquisitionReceiptScreen.AcquisitionClaim(
                StagedFidoMdsAcquisitionReceiptScreen.MDS_URL,
                tlsVersion, httpStatus, redirect, fetched,
                evidence.checkedAt(), sequence, evidence.blobNumber(),
                evidence.signedTimeSequence(), evidence.blobSha256(),
                evidence.leafSha256(), evidence.rootSha256(),
                evidence.crlEvidenceSha256(), evidence.timeKeySha256(),
                evidence.signedTimeProofSha256(),
                List.of(new StagedFidoMdsAcquisitionReceiptScreen.CrlArtifact(
                        crlSourceUrl, actualCrlSha)));
    }

    @Test
    void signedAcquisitionReceiptsBindExactJwsCrlAndOfflineCustodyAssertions()
            throws Exception {
        // This URL is synthetic: a signed label does not prove a real TLS
        // exchange or establish an official CRL distribution point.
        String crlUrl = "https://crl.example.invalid/synthetic-issuer.crl";
        for (boolean rsa : new boolean[]{false, true}) {
            Fixture f = fixture(rsa);
            X509CRL crl = f.freshCrl();
            String jws = f.jws(61, false);
            var time = f.time(9, NOW, jws, List.of(crl));
            var untrusted = verifier.inspectOfflineOnly(
                    jws, f.claimedPolicy(), time, List.of(crl), 60, 8);
            KeyPair reviewerA = key("Ed25519");
            KeyPair reviewerB = key("Ed25519");
            KeyPair acquirer = key("Ed25519");
            KeyPair attacker = key("Ed25519");
            var reviewPins = new StagedFidoMdsIndependentReviewSeal
                    .PinnedReviewKeys(reviewerA.getPublic(),
                    fingerprint(reviewerA.getPublic().getEncoded()),
                    reviewerB.getPublic(),
                    fingerprint(reviewerB.getPublic().getEncoded()));
            var review = signReview(reviewClaim(untrusted,
                    StagedFidoMdsIndependentReviewSeal.EXPECTED_MDS_SOURCE,
                    NOW.minusSeconds(100), NOW.plusSeconds(3600), 8),
                    reviewerA.getPrivate(), reviewerB.getPrivate());
            var acquisitionPin = new StagedFidoMdsAcquisitionReceiptScreen
                    .PinnedAcquisitionKey(acquirer.getPublic(),
                    fingerprint(acquirer.getPublic().getEncoded()),
                    List.of(crlUrl));
            var claim = acquisitionClaim(untrusted, 13, NOW.minusSeconds(30),
                    "TLSv1.3", 200, false, crlUrl,
                    fingerprint(crl.getEncoded()));
            var receipt = signAcquisition(claim, acquirer.getPrivate());

            assertEquals(61, verifier.inspectWithAcquisitionReceiptOfflineOnly(
                    jws, f.claimedPolicy(), time, List.of(crl), 60, 8,
                    reviewPins, review, 7, acquisitionPin, receipt, 12)
                    .blobNumber());
            assertTrue(StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                    acquisitionPin, receipt, List.of(crl), untrusted, 12)
                    .toString().contains("Untrusted"));

            // A separately signed claim cannot silently substitute an
            // unrelated signed-time or leaf-certificate digest, even when
            // the JWS and CRL hashes are unchanged.
            var forgedProofClaim = new StagedFidoMdsAcquisitionReceiptScreen
                    .AcquisitionClaim(claim.mdsUrl(), claim.tlsVersion(),
                    claim.httpStatus(), claim.followedRedirect(),
                    claim.fetchedAt(), claim.checkedAt(), claim.sequence(),
                    claim.blobNumber(), claim.signedTimeSequence(),
                    claim.jwsSha256(), claim.signerLeafSha256(),
                    claim.rootSha256(), claim.crlEvidenceSha256(),
                    claim.timeKeySha256(), "0".repeat(64), claim.crls());
            var signedWrongProof = signAcquisition(forgedProofClaim,
                    acquirer.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                            acquisitionPin, signedWrongProof, List.of(crl),
                            untrusted, 12));

            // A caller's matching self-declared acquirer key is insufficient
            // against a DIFFERENT out-of-band pinned verification key.
            var forged = signAcquisition(claim, attacker.getPrivate());
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithAcquisitionReceiptOfflineOnly(
                            jws, f.claimedPolicy(), time, List.of(crl), 60, 8,
                            reviewPins, review, 7, acquisitionPin, forged, 12));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithAcquisitionReceiptOfflineOnly(
                            jws, f.claimedPolicy(), time, List.of(crl), 60, 8,
                            reviewPins, review, 7, acquisitionPin, receipt, 13));
            assertThrows(AccessDeniedException.class, () ->
                    verifier.inspectWithAcquisitionReceiptOfflineOnly(
                            jws, f.claimedPolicy(), time, List.of(crl), 60, 8,
                            reviewPins, review, 7, null, receipt, 12));

            String[] badUrls = {
                    "http://crl.example.invalid/synthetic-issuer.crl",
                    "https://crl.example.invalid@attacker.invalid/issuer.crl",
                    "https://crl.example.invalid/synthetic-issuer.crl#fragment",
                    "https://crl.example.invalid/../issuer.crl",
                    "https://evil.invalid/synthetic-issuer.crl"
            };
            for (String url : badUrls) {
                var altered = signAcquisition(acquisitionClaim(untrusted,
                        14, NOW.minusSeconds(30), "TLSv1.3", 200, false,
                        url, fingerprint(crl.getEncoded())),
                        acquirer.getPrivate());
                assertThrows(AccessDeniedException.class, () ->
                        StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                                acquisitionPin, altered, List.of(crl),
                                untrusted, 12));
            }
            for (String tls : List.of("TLSv1.2", "", "TLSv1.3-renegotiated")) {
                var altered = signAcquisition(acquisitionClaim(untrusted,
                        14, NOW.minusSeconds(30), tls, 200, false,
                        crlUrl, fingerprint(crl.getEncoded())),
                        acquirer.getPrivate());
                assertThrows(AccessDeniedException.class, () ->
                        StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                                acquisitionPin, altered, List.of(crl),
                                untrusted, 12));
            }
            var redirect = signAcquisition(acquisitionClaim(untrusted, 14,
                    NOW.minusSeconds(30), "TLSv1.3", 200, true,
                    crlUrl, fingerprint(crl.getEncoded())),
                    acquirer.getPrivate());
            var badHttp = signAcquisition(acquisitionClaim(untrusted, 14,
                    NOW.minusSeconds(30), "TLSv1.3", 304, false,
                    crlUrl, fingerprint(crl.getEncoded())),
                    acquirer.getPrivate());
            var stale = signAcquisition(acquisitionClaim(untrusted, 14,
                    NOW.minusSeconds(86401), "TLSv1.3", 200, false,
                    crlUrl, fingerprint(crl.getEncoded())),
                    acquirer.getPrivate());
            var future = signAcquisition(acquisitionClaim(untrusted, 14,
                    NOW.plusSeconds(1), "TLSv1.3", 200, false,
                    crlUrl, fingerprint(crl.getEncoded())),
                    acquirer.getPrivate());
            for (var invalid : List.of(redirect, badHttp, stale, future)) {
                assertThrows(AccessDeniedException.class, () ->
                        StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                                acquisitionPin, invalid, List.of(crl),
                                untrusted, 12));
            }

            // A new legitimately signed BLOB/time pair cannot reuse the
            // old signed acquisition proof.
            String updatedJws = f.jws(62, false);
            var updatedTime = f.time(10, NOW, updatedJws, List.of(crl));
            var updatedEvidence = verifier.inspectOfflineOnly(
                    updatedJws, f.claimedPolicy(), updatedTime,
                    List.of(crl), 61, 9);
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                            acquisitionPin, receipt, List.of(crl),
                            updatedEvidence, 12));

            // The newly issued CRL changes its DER fingerprint, even when
            // certificate issuer and validity windows remain credible.
            X509CRL newCrl = f.freshCrl();
            assertTrue(!fingerprint(crl.getEncoded())
                    .equals(fingerprint(newCrl.getEncoded())));
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                            acquisitionPin, receipt, List.of(newCrl),
                            untrusted, 12));

            var otherPin = new StagedFidoMdsAcquisitionReceiptScreen
                    .PinnedAcquisitionKey(attacker.getPublic(),
                    fingerprint(attacker.getPublic().getEncoded()),
                    List.of(crlUrl));
            assertThrows(AccessDeniedException.class, () ->
                    StagedFidoMdsAcquisitionReceiptScreen.inspectUntrustedOnly(
                            otherPin, receipt, List.of(crl), untrusted, 12));

            byte[] extracted = receipt.signature();
            extracted[0] ^= (byte) 0xff;
            assertEquals(13, StagedFidoMdsAcquisitionReceiptScreen
                    .inspectUntrustedOnly(acquisitionPin, receipt, List.of(crl),
                            untrusted, 12).sequence());
        }
    }

    @Test
    void rejectsMissingApprovalMismatchedPinAndNoRevocationEvidence()
            throws Exception {
        Fixture f = fixture(false);
        String jws = f.jws(1, false);
        var policy = f.claimedPolicy();
        var wrongPin = new StagedFidoMdsSourceVerifier.ExternalApproval(
                policy.anchor(), "0".repeat(64), policy.timeKey(),
                policy.timeKeySha256());
        var sourceCrl = f.freshCrl();
        var proof = f.time(9, NOW, jws, List.of(sourceCrl));
        assertThrows(AccessDeniedException.class, () ->
                verifier.inspectOfflineOnly(jws, null, proof,
                        List.of(sourceCrl), 0, 8));
        assertThrows(AccessDeniedException.class, () ->
                verifier.inspectOfflineOnly(jws, wrongPin, proof,
                        List.of(sourceCrl), 0, 8));
        assertThrows(AccessDeniedException.class, () ->
                verifier.inspectOfflineOnly(jws, policy, proof,
                        List.of(), 0, 8));
    }

    @Test
    void revokedSignerOrExpiredCrlOrWrongIssuerAlwaysReject()
            throws Exception {
        Fixture f = fixture(false);
        String jws = f.jws(10, false);
        X509CRL revoked = crl(f.issuer(), f.caKey().getPrivate(),
                f.signerCert().getSerialNumber(), NOW.plusSeconds(86400));
        X509CRL expired = crl(f.issuer(), f.caKey().getPrivate(),
                null, NOW.minusSeconds(1));
        Fixture other = fixture(false);
        for (X509CRL invalid : List.of(revoked, expired, other.freshCrl())) {
            assertThrows(AccessDeniedException.class, () ->
                    inspect(f, jws, invalid, 9, 8));
        }
    }

    @Test
    void replayedBlobStaleTimeAndUnsignedHeaderCertificateLocationsReject()
            throws Exception {
        Fixture f = fixture(false);
        assertThrows(AccessDeniedException.class, () ->
                inspect(f, f.jws(10, false), f.freshCrl(), 10, 8));
        assertThrows(AccessDeniedException.class, () ->
                inspect(f, f.jws(10, false), f.freshCrl(), 9, 9));
        assertThrows(AccessDeniedException.class, () ->
                inspect(f, f.jws(10, true), f.freshCrl(), 9, 8));
        assertThrows(AccessDeniedException.class, () ->
                verifier.inspectOfflineOnly(f.jws(10, false),
                        f.claimedPolicy(),
                        new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                                NOW, 9, new byte[]{1, 2, 3}),
                        List.of(f.freshCrl()), 9, 8));
    }

    @Test
    void threeCertificateR46CrossChainUsesVerifiedApprovedRootPath()
            throws Exception {
        KeyPair newRoot = key("EC");
        KeyPair intermediate = key("EC");
        KeyPair leaf = key("EC");
        KeyPair legacyRoot = key("EC");
        KeyPair timeKey = key("Ed25519");
        X500Name r46 = new X500Name("CN=SYNTHETIC-R46-NOT-OFFICIAL");
        X500Name r3 = new X500Name("CN=SYNTHETIC-R3-NOT-OFFICIAL");
        X500Name issuingCa = new X500Name("CN=SYNTHETIC-MDS-ISSUER");
        var root = certificate(r46, newRoot.getPrivate(), r46, newRoot, true,
                KeyUsage.keyCertSign | KeyUsage.cRLSign, null);
        var intermediateCert = certificate(r46, newRoot.getPrivate(),
                issuingCa, intermediate, true,
                KeyUsage.keyCertSign | KeyUsage.cRLSign, null);
        var leafCert = certificate(issuingCa, intermediate.getPrivate(),
                new X500Name("CN=mds.fidoalliance.org"), leaf, false,
                KeyUsage.digitalSignature, "mds.fidoalliance.org");
        // The x5c[2] cross certificate is issued by a separate legacy root.
        // A path anchored to an independently approved new root ends at
        // x5c[1], without trusting this optional compatibility certificate.
        var crossCert = certificate(r3, legacyRoot.getPrivate(), r46, newRoot,
                true, KeyUsage.keyCertSign | KeyUsage.cRLSign, null);
        Fixture f = new Fixture(leaf, newRoot, timeKey, r46,
                root, leafCert, false);
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .customParam("iat", NOW.getEpochSecond())
                .x509CertChain(List.of(Base64.encode(leafCert.getEncoded()),
                        Base64.encode(intermediateCert.getEncoded()),
                        Base64.encode(crossCert.getEncoded()))).build();
        var jws = new JWSObject(header, new Payload(
                "{\"legalHeader\":\"test-only terms\",\"no\":2,\"entries\":[]}"));
        jws.sign(new ECDSASigner((ECPrivateKey) leaf.getPrivate()));
        var issuerCrl = crl(issuingCa, intermediate.getPrivate(),
                null, NOW.plusSeconds(86400));
        var rootCrl = crl(r46, newRoot.getPrivate(),
                null, NOW.plusSeconds(86400));
        var evidence = verifier.inspectOfflineOnly(jws.serialize(),
                f.claimedPolicy(), f.time(9, NOW, jws.serialize(),
                        List.of(issuerCrl, rootCrl)),
                List.of(issuerCrl, rootCrl), 1, 8);
        assertEquals(2, evidence.blobNumber());
        assertTrue(evidence.toString().contains("Untrusted"));
    }

    @Test
    void publicCaChainWithWrongMdsDnsSanCannotImpersonateFidoSigner()
            throws Exception {
        Fixture f = fixture(false);
        var fake = certificate(f.issuer(), f.caKey().getPrivate(),
                new X500Name("CN=mds.fidoalliance.org"), f.signerKey(),
                false, KeyUsage.digitalSignature, "attacker.invalid");
        var impersonator = new Fixture(f.signerKey(), f.caKey(), f.timeKey(),
                f.issuer(), f.root(), fake, false);
        // Even a valid signature and otherwise valid PKIX/CRL chain cannot
        // pass based on a CN or public CA alone.
        assertThrows(AccessDeniedException.class, () ->
                inspect(impersonator, impersonator.jws(3, false),
                        impersonator.freshCrl(), 2, 8));
    }

    @Test
    void differentSignedJwsCertificateOrTamperedPayloadReject()
            throws Exception {
        Fixture f = fixture(false);
        Fixture other = fixture(false);
        assertThrows(AccessDeniedException.class, () ->
                inspect(f, other.jws(3, false), f.freshCrl(), 2, 8));

        String valid = f.jws(3, false);
        String altered = f.jws(4, false);
        String[] original = valid.split("\\.");
        String[] replacement = altered.split("\\.");
        String forged = original[0] + "." + replacement[1] + "." + original[2];
        assertThrows(AccessDeniedException.class, () ->
                inspect(f, forged, f.freshCrl(), 2, 8));
    }

    @Test
    void sourceTimeV2BindsExactJwsEvenWhenBothJwsSignaturesAreValid()
            throws Exception {
        Fixture f=fixture(false);
        String first=f.jws(41,false);
        String second=f.jws(42,false);
        X509CRL crl=f.freshCrl();
        var original=f.time(9,NOW,first,List.of(crl));
        assertEquals(41,verifier.inspectOfflineOnly(first,f.claimedPolicy(),
                original,List.of(crl),40,8).blobNumber());
        // The old v1 proof contained only time and sequence, so could be
        // replayed against a different still-valid JWS. v2 must fail.
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(second,f.claimedPolicy(),
                        original,List.of(crl),40,8));
        assertEquals(42,verifier.inspectOfflineOnly(second,f.claimedPolicy(),
                f.time(10,NOW,second,List.of(crl)),
                List.of(crl),41,9).blobNumber());
    }

    @Test
    void sourceTimeV2BindsExactCrlBytesAndOrderIndependentCrlSet()
            throws Exception {
        Fixture f=fixture(false);
        String jws=f.jws(41,false);
        X509CRL first=f.freshCrl();
        X509CRL second=f.freshCrl();
        // A valid reissued CRL is not interchangeable with the CRL
        // whose DER fingerprint was covered by the signed time proof.
        assertTrue(!fingerprint(first.getEncoded())
                .equals(fingerprint(second.getEncoded())));
        var original=f.time(9,NOW,jws,List.of(first));
        assertEquals(41,verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                original,List.of(first),40,8).blobNumber());
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                        original,List.of(second),40,8));
        var multi=f.time(10,NOW,jws,List.of(first,second));
        assertEquals(41,verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                multi,List.of(second,first),40,9).blobNumber());
    }

    @Test
    void sourceTimeV2BindsRootFingerprintDespiteSameRootKeyAndIssuer()
            throws Exception {
        Fixture f=fixture(false);
        String jws=f.jws(41,false);
        X509CRL crl=f.freshCrl();
        X509Certificate reissuedRoot=certificate(f.issuer(),
                f.caKey().getPrivate(),f.issuer(),f.caKey(),true,
                KeyUsage.keyCertSign | KeyUsage.cRLSign,null);
        assertTrue(!fingerprint(f.root().getEncoded())
                .equals(fingerprint(reissuedRoot.getEncoded())));
        var reissuedPolicy=new StagedFidoMdsSourceVerifier.ExternalApproval(
                reissuedRoot,fingerprint(reissuedRoot.getEncoded()),
                f.timeKey().getPublic(),
                fingerprint(f.timeKey().getPublic().getEncoded()));
        var original=f.time(9,NOW,jws,List.of(crl));
        assertEquals(41,verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                original,List.of(crl),40,8).blobNumber());
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(jws,reissuedPolicy,
                        original,List.of(crl),40,8));
        var reissuedFixture=new Fixture(f.signerKey(),f.caKey(),f.timeKey(),
                f.issuer(),reissuedRoot,f.signerCert(),false);
        assertEquals(41,verifier.inspectOfflineOnly(jws,reissuedPolicy,
                reissuedFixture.time(9,NOW,jws,List.of(crl)),
                List.of(crl),40,8).blobNumber());
    }

    @Test
    void sourceTimeV2RejectsLegacyUnboundTimestampSequenceMutationAndMutableProof()
            throws Exception {
        Fixture f=fixture(false);
        String jws=f.jws(41,false);
        X509CRL crl=f.freshCrl();
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                        f.unboundLegacyTime(9,NOW),List.of(crl),40,8));
        var valid=f.time(9,NOW,jws,List.of(crl));
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                        new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                                NOW.plusSeconds(1),9,valid.signature()),
                        List.of(crl),40,8));
        assertThrows(AccessDeniedException.class,() ->
                verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                        new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                                NOW,10,valid.signature()),
                        List.of(crl),40,8));

        byte[] extracted=valid.signature();
        extracted[0]^=(byte)0xff;
        // Record accessor returns a defensive copy: no caller can change
        // what was signed after verification begins.
        assertEquals(41,verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                valid,List.of(crl),40,8).blobNumber());
        var array=valid.signature();
        var copied=new StagedFidoMdsSourceVerifier.SignedTimeEvidence(
                NOW,9,array);
        array[0]^=(byte)0xff;
        assertEquals(41,verifier.inspectOfflineOnly(jws,f.claimedPolicy(),
                copied,List.of(crl),40,8).blobNumber());
    }
}
