package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

class ClientCertificateProfileValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void approvedOfflineClientLeafValidatesAndReturnsOnlyPublicFingerprintMetadata()
            throws Exception {
        Fixture f = fixture(true, true, 60, false);
        var accepted = new ClientCertificateProfileValidator(f.root, CLOCK)
                .validate(pem(f.leaf), f.deviceId, sha(f.deviceKey.getPublic().getEncoded()));
        assertEquals(64, accepted.fingerprint().length());
        assertEquals(64, accepted.issuerId().length());
        assertEquals(sha(f.leaf.getEncoded()), accepted.fingerprint());
        assertEquals(sha(f.deviceKey.getPublic().getEncoded()), accepted.csrSpkiSha256());
        assertNotNull(accepted.expiresAt());
    }

    @Test
    void incorrectDeviceUriAndCsrPublicKeyAreDenied() throws Exception {
        Fixture f = fixture(true, true, 60, false);
        var validator = new ClientCertificateProfileValidator(f.root, CLOCK);
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(pem(f.leaf), UUID.randomUUID(),
                        sha(f.deviceKey.getPublic().getEncoded())));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(pem(f.leaf), f.deviceId, "f".repeat(64)));
    }

    @Test
    void serverAuthenticationLeafAndOverlongValidityAreDenied() throws Exception {
        Fixture server = fixture(false, true, 60, false);
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(server.root, CLOCK).validate(
                        pem(server.leaf), server.deviceId,
                        sha(server.deviceKey.getPublic().getEncoded())));
        Fixture longLived = fixture(true, true, 100, false);
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(longLived.root, CLOCK).validate(
                        pem(longLived.leaf), longLived.deviceId,
                        sha(longLived.deviceKey.getPublic().getEncoded())));
    }

    @Test
    void wrongCaAndWrongKeyUsageAreDenied() throws Exception {
        Fixture f = fixture(true, true, 60, false);
        Fixture other = fixture(true, true, 60, false);
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(other.root, CLOCK).validate(
                        pem(f.leaf), f.deviceId,
                        sha(f.deviceKey.getPublic().getEncoded())));
        Fixture wrongUsage = fixture(true, false, 60, false);
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(wrongUsage.root, CLOCK).validate(
                        pem(wrongUsage.leaf), wrongUsage.deviceId,
                        sha(wrongUsage.deviceKey.getPublic().getEncoded())));
    }

    @Test
    void expiredLeafAndUntrustedInputDoNotPass() throws Exception {
        Fixture expired = fixture(true, true, 60, true);
        assertThrows(IllegalArgumentException.class, () ->
                new ClientCertificateProfileValidator(expired.root, CLOCK).validate(
                        pem(expired.leaf), expired.deviceId,
                        sha(expired.deviceKey.getPublic().getEncoded())));
        Fixture f = fixture(true, true, 60, false);
        var validator = new ClientCertificateProfileValidator(f.root, CLOCK);
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate("garbage", f.deviceId,
                        sha(f.deviceKey.getPublic().getEncoded())));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(pem(f.root) + pem(f.leaf), f.deviceId,
                        sha(f.deviceKey.getPublic().getEncoded())));
    }

    private static Fixture fixture(boolean clientAuth, boolean digitalSignature,
                                   int validDays, boolean expired) throws Exception {
        var id = UUID.randomUUID();
        var caKeys = keys();
        var agentKeys = keys();
        var caName = new X500Name("CN=Lanveriq Synthetic Client CA");
        var before = Date.from(NOW.minusSeconds(86_400L * (expired ? 100 : 1)));
        var after = Date.from(NOW.plusSeconds(86_400L * (expired ? -1 : validDays - 1L)));
        var ca = new JcaX509v3CertificateBuilder(caName, BigInteger.ONE,
                Date.from(NOW.minusSeconds(86_400L * 2)), Date.from(NOW.plusSeconds(86_400L * 365)),
                caName, caKeys.getPublic());
        ca.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        ca.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        X509Certificate root = certificate(ca, caKeys);
        var leaf = new JcaX509v3CertificateBuilder(caName, BigInteger.valueOf(42),
                before, after, new X500Name("CN=untrusted-csr-subject"),
                agentKeys.getPublic());
        leaf.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        leaf.addExtension(Extension.keyUsage, true,
                new KeyUsage(digitalSignature ? KeyUsage.digitalSignature : KeyUsage.keyEncipherment));
        leaf.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(clientAuth ? KeyPurposeId.id_kp_clientAuth
                        : KeyPurposeId.id_kp_serverAuth));
        leaf.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier,
                        "urn:uuid:" + id)));
        return new Fixture(id, agentKeys, root, certificate(leaf, caKeys));
    }

    private static X509Certificate certificate(JcaX509v3CertificateBuilder builder,
                                               KeyPair signer) throws Exception {
        var signed = builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                .build(signer.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(signed);
    }

    private static KeyPair keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String pem(X509Certificate certificate) {
        try {
            var text = new StringWriter();
            try (var writer = new JcaPEMWriter(text)) {
                writer.writeObject(certificate);
            }
            return text.toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(UUID deviceId, KeyPair deviceKey,
                           X509Certificate root, X509Certificate leaf) {
    }
}
