package com.lanveriq.server.enrollment;

import java.io.IOException;
import java.io.StringReader;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.interfaces.ECPublicKey;
import java.util.HexFormat;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

/**
 * Validates the cryptographic proof of possession in a bounded PKCS#10 CSR.
 * CSR subject/SAN are strictly untrusted; the offline issuer supplies the
 * canonical device UUID in the certificate after reviewing server records.
 */
public final class CertificateSigningRequestValidator {

    public static final int MAX_PEM_BYTES = 16 * 1024;

    public ValidatedCsr validate(String publicPem) {
        if (publicPem == null || publicPem.isBlank() ||
                publicPem.length() > MAX_PEM_BYTES ||
                !publicPem.startsWith("-----BEGIN CERTIFICATE REQUEST-----") ||
                !publicPem.stripTrailing().endsWith("-----END CERTIFICATE REQUEST-----")) {
            throw invalid();
        }
        try (var reader = new PEMParser(new StringReader(publicPem))) {
            Object value = reader.readObject();
            if (!(value instanceof PKCS10CertificationRequest csr) || reader.readObject() != null) {
                throw invalid();
            }
            if (!X9ObjectIdentifiers.ecdsa_with_SHA256.equals(
                    csr.getSignatureAlgorithm().getAlgorithm())) {
                throw invalid();
            }
            PublicKey key = new JcaPKCS10CertificationRequest(csr).getPublicKey();
            if (!(key instanceof ECPublicKey ecKey) || !isP256(ecKey.getParams())) {
                throw invalid();
            }
            if (!csr.isSignatureValid(
                    new JcaContentVerifierProviderBuilder().build(csr.getSubjectPublicKeyInfo()))) {
                throw invalid();
            }
            return new ValidatedCsr(
                    publicPem,
                    hexSha256(csr.getEncoded()),
                    hexSha256(csr.getSubjectPublicKeyInfo().getEncoded()));
        } catch (IOException | GeneralSecurityException |
                 org.bouncycastle.pkcs.PKCSException |
                 org.bouncycastle.operator.OperatorCreationException e) {
            throw invalid();
        }
    }

    private static boolean isP256(ECParameterSpec params) throws GeneralSecurityException {
        AlgorithmParameters expectedParameters = AlgorithmParameters.getInstance("EC");
        expectedParameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = expectedParameters.getParameterSpec(ECParameterSpec.class);
        return params.getCurve().equals(expected.getCurve())
                && params.getGenerator().equals(expected.getGenerator())
                && params.getOrder().equals(expected.getOrder())
                && params.getCofactor() == expected.getCofactor();
    }

    private static String hexSha256(byte[] value) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid device certificate signing request");
    }

    public record ValidatedCsr(String publicPem, String csrSha256Digest, String spkiSha256Digest) {
        @Override
        public String toString() {
            return "ValidatedCsr[REDACTED]";
        }
    }
}
