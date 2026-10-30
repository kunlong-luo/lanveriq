package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

class CertificateSigningRequestValidatorTest {

    private final CertificateSigningRequestValidator validator =
            new CertificateSigningRequestValidator();

    @Test
    void validatesSignatureAndDerivesStableDigestFromRealP256Csr() throws Exception {
        KeyPair key = generate("secp256r1");
        String pem = pemCsr(key, key);
        var result = validator.validate(pem);
        assertEquals(64, result.csrSha256Digest().length());
        assertEquals(64, result.spkiSha256Digest().length());
        assertEquals(pem, result.publicPem());
        assertEquals(result.csrSha256Digest(), validator.validate(pem).csrSha256Digest());
        assertEquals(result.spkiSha256Digest(), validator.validate(pem).spkiSha256Digest());
        assertEquals("ValidatedCsr[REDACTED]", result.toString());
        assertFalse(result.toString().contains(pem));
    }

    @Test
    void rejectsValidButUnapprovedP384Key() throws Exception {
        KeyPair p384 = generate("secp384r1");
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(pemCsr(p384, p384)));
    }

    @Test
    void rejectsProofSignedByWrongPrivateKey() throws Exception {
        KeyPair claimed = generate("secp256r1");
        KeyPair signer = generate("secp256r1");
        assertNotEquals(claimed.getPublic(), signer.getPublic());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(pemCsr(claimed, signer)));
    }

    @Test
    void rejectsMultipleOrMalformedRequestsAndOversizedPayload() throws Exception {
        KeyPair key = generate("secp256r1");
        String pem = pemCsr(key, key);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(""));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("not a CSR"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("x".repeat(16385)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(pem + pem));
    }

    private static KeyPair generate(String curve) throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec(curve));
        return gen.generateKeyPair();
    }

    private static String pemCsr(KeyPair claimed, KeyPair signer) throws Exception {
        var builder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=untrusted-claimed-name"), claimed.getPublic());
        PKCS10CertificationRequest csr = builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(signer.getPrivate()));
        StringWriter out = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(csr);
        }
        return out.toString();
    }
}
