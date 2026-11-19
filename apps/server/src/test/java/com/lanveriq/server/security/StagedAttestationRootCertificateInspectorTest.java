package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StagedAttestationRootCertificateInspectorTest {
    private final StagedAttestationRootCertificateInspector inspector =
            new StagedAttestationRootCertificateInspector(Clock.systemUTC());

    @Test
    void refusesMissingTinyAndOversizedUntrustedDer() {
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(null));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[0]));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[255]));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[8193]));
    }

    @Test
    void refusesRandomBytesAndNonCanonicalCertificatePayload() {
        byte[] random = new byte[512];
        new Random(42).nextBytes(random);
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectUntrustedDer(random));
        byte[] malformed = new byte[512];
        malformed[0] = 0x30;
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectUntrustedDer(malformed));
    }

    private static byte[] offlineSyntheticCa(boolean unknownCritical, boolean allowSigning)
            throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var subject = new X500Name("CN=Disposable untrusted staging root");
        var builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(42),
                Date.from(Instant.parse("2025-01-01T00:00:00Z")),
                Date.from(Instant.parse("2030-01-01T00:00:00Z")),
                subject, pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(allowSigning ? KeyUsage.keyCertSign : KeyUsage.digitalSignature));
        if (unknownCritical) {
            builder.addExtension(new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.123.456"),
                    true, DERNull.INSTANCE);
        }
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .build(pair.getPrivate())));
        return cert.getEncoded();
    }

    @Test
    void acceptsOnlyStructurallyValidSelfSignedCaAsUntrustedBytes() throws Exception {
        byte[] untrusted = offlineSyntheticCa(false, true);
        assertArrayEquals(untrusted, inspector.inspectUntrustedDer(untrusted));
    }

    @Test
    void rejectsUnknownCriticalExtensionBeforeOfflineProposalStorage() throws Exception {
        byte[] untrusted = offlineSyntheticCa(true, true);
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(untrusted));
    }

    @Test
    void rejectsMissingCaSigningUsageAndTrailingDer() throws Exception {
        byte[] wrongUsage = offlineSyntheticCa(false, false);
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(wrongUsage));
        byte[] valid = offlineSyntheticCa(false, true);
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(trailing));
    }
}
