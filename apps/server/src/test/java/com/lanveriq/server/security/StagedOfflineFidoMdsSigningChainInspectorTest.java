package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Synthetic-only X.509 and signed CRL fixtures: never real CA/MDS keys. */
class StagedOfflineFidoMdsSigningChainInspectorTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private final StagedOfflineFidoMdsSigningChainInspector inspector =
            new StagedOfflineFidoMdsSigningChainInspector(
                    Clock.fixed(NOW, ZoneOffset.UTC));

    private static KeyPair ephemeralKey() throws Exception {
        var g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static BigInteger serial() {
        return new BigInteger(80, new SecureRandom()).add(BigInteger.ONE);
    }

    private static X509Certificate certificate(X500Name issuer, PrivateKey issuerKey,
            X500Name subject, KeyPair subjectKey, boolean ca, int keyUsage,
            Instant notBefore, Instant notAfter) throws Exception {
        var builder = new JcaX509v3CertificateBuilder(issuer, serial(),
                Date.from(notBefore), Date.from(notAfter), subject, subjectKey.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .build(issuerKey)));
    }

    private static X509CRL crl(X500Name name, PrivateKey signerKey,
            BigInteger revokedSerial, Instant thisUpdate, Instant nextUpdate)
            throws Exception {
        var builder = new X509v2CRLBuilder(name, Date.from(thisUpdate));
        builder.setNextUpdate(Date.from(nextUpdate));
        if (revokedSerial != null) {
            builder.addCRLEntry(revokedSerial, Date.from(thisUpdate),
                    CRLReason.keyCompromise);
        }
        return new JcaX509CRLConverter().getCRL(builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(signerKey)));
    }

    private record Fixture(X500Name issuer, KeyPair caKey,
            X509Certificate root, X509Certificate leaf) {}

    private static Fixture validFixture() throws Exception {
        KeyPair ca = ephemeralKey();
        KeyPair signer = ephemeralKey();
        X500Name issuer = new X500Name("CN=SYNTHETIC-NOT-FIDO-TRUSTED-CA");
        X509Certificate root = certificate(issuer, ca.getPrivate(), issuer, ca,
                true, KeyUsage.keyCertSign | KeyUsage.cRLSign,
                NOW.minusSeconds(86400), NOW.plusSeconds(86400 * 10));
        X509Certificate leaf = certificate(issuer, ca.getPrivate(),
                new X500Name("CN=SYNTHETIC-MDS-SIGNER"), signer, false,
                KeyUsage.digitalSignature, NOW.minusSeconds(3600),
                NOW.plusSeconds(86400));
        return new Fixture(issuer, ca, root, leaf);
    }

    private static X509CRL freshCrl(Fixture f) throws Exception {
        return crl(f.issuer(), f.caKey().getPrivate(), null,
                NOW.minusSeconds(3600), NOW.plusSeconds(86400));
    }

    @Test
    void validSyntheticPkixAndSignedFreshCrlStillReturnOnlyUntrustedChain()
            throws Exception {
        Fixture f = validFixture();
        var receipt = inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), f.root(),
                List.of(freshCrl(f)));
        assertEquals(64, receipt.syntheticLeafSha256().length());
        assertEquals(NOW, receipt.observedAt());
        assertTrue(receipt.toString().contains("UntrustedChain"));
    }

    @Test
    void missingCrlAndWrongAnchorCannotSatisfyPkixStatus() throws Exception {
        Fixture f = validFixture();
        Fixture other = validFixture();
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), f.root(),
                        List.of()));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), other.root(),
                        List.of(freshCrl(f))));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), f.root(),
                        List.of(freshCrl(other))));
    }

    @Test
    void revokedSignerOrExpiredOrFutureCrlCannotPass() throws Exception {
        Fixture f = validFixture();
        var revoked = crl(f.issuer(), f.caKey().getPrivate(), f.leaf().getSerialNumber(),
                NOW.minusSeconds(1800), NOW.plusSeconds(86400));
        var expired = crl(f.issuer(), f.caKey().getPrivate(), null,
                NOW.minusSeconds(86400 * 3), NOW.minusSeconds(1));
        var future = crl(f.issuer(), f.caKey().getPrivate(), null,
                NOW.plusSeconds(3600), NOW.plusSeconds(86400));
        for (var evidence : List.of(revoked, expired, future)) {
            assertThrows(AccessDeniedException.class, () ->
                    inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), f.root(),
                            List.of(evidence)));
        }
    }

    @Test
    void expiredOrMisusedSignerCannotPassCertificatePath() throws Exception {
        Fixture f = validFixture();
        KeyPair signer = ephemeralKey();
        var expired = certificate(f.issuer(), f.caKey().getPrivate(),
                new X500Name("CN=EXPIRED-SYNTHETIC-SIGNER"), signer, false,
                KeyUsage.digitalSignature, NOW.minusSeconds(86400 * 4),
                NOW.minusSeconds(86400));
        var wrongUsage = certificate(f.issuer(), f.caKey().getPrivate(),
                new X500Name("CN=WRONG-USAGE-SYNTHETIC-SIGNER"), signer, false,
                KeyUsage.keyEncipherment, NOW.minusSeconds(3600),
                NOW.plusSeconds(86400));
        for (var bad : List.of(expired, wrongUsage, f.root())) {
            assertThrows(AccessDeniedException.class, () ->
                    inspector.inspectSyntheticOfflineOnly(List.of(bad), f.root(),
                            List.of(freshCrl(f))));
        }
    }

    @Test
    void forgedCrlSignatureCannotBypassJdkPkix() throws Exception {
        Fixture f = validFixture();
        var forged = crl(f.issuer(), ephemeralKey().getPrivate(), null,
                NOW.minusSeconds(3600), NOW.plusSeconds(86400));
        assertThrows(AccessDeniedException.class, () ->
                inspector.inspectSyntheticOfflineOnly(List.of(f.leaf()), f.root(),
                        List.of(forged)));
    }
}
