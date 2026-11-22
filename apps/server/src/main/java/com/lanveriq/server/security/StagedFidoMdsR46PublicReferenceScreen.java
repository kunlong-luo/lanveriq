package com.lanveriq.server.security;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import javax.security.auth.x500.X500Principal;
import org.springframework.security.access.AccessDeniedException;

/**
 * D4-C13: strict, offline identity check for the PUBLICLY DOCUMENTED
 * GlobalSign Root R46 self-signed certificate; NEVER a trust-store adapter.
 *
 * The SHA-256 reference is independently cross-listed in Mozilla CCADB
 * R00000947 and GlobalSign's published certificate disclosures. FIDO's
 * August 2026 migration identifies R46, not an arbitrary claimant root.
 *
 * Merely matching a published public certificate does NOT prove download
 * provenance, organization-authorized trust, trusted time, fresh CRLs,
 * independent human reviewers or status admission. No Bean, fetcher, JVM
 * truststore mutation, Spring/WebAuthn consumer, CA grant or network.
 */
final class StagedFidoMdsR46PublicReferenceScreen {
    static final String PUBLIC_REFERENCE_SHA256 =
            "4fa3126d8d3a11d1c4855a4f807cbad6cf919d3a5a88b03bea2c6372d93c40c9";
    private static final BigInteger PUBLISHED_SERIAL =
            new BigInteger("11d2bbb9d723189e405f0a9d2dd0df2567d1", 16);
    private static final X500Principal PUBLISHED_SUBJECT = new X500Principal(
            "CN=GlobalSign Root R46,O=GlobalSign nv-sa,C=BE");

    record UntrustedPublishedRootReference(String rootSha256) {}

    private StagedFidoMdsR46PublicReferenceScreen() {}

    static UntrustedPublishedRootReference inspectReferenceOnly(
            byte[] candidateDer, Instant externalCheckedAt) {
        if (candidateDer == null || candidateDer.length < 1000
                || candidateDer.length > 8192 || externalCheckedAt == null) {
            throw denied();
        }
        try {
            byte[] copy = candidateDer.clone();
            var source = new ByteArrayInputStream(copy);
            var parsed = CertificateFactory.getInstance("X.509")
                    .generateCertificate(source);
            if (!(parsed instanceof X509Certificate cert)
                    || source.available() != 0
                    || !Arrays.equals(cert.getEncoded(), copy)
                    || !PUBLIC_REFERENCE_SHA256.equals(sha256(copy))
                    || cert.getVersion() != 3
                    || !PUBLISHED_SERIAL.equals(cert.getSerialNumber())
                    || !PUBLISHED_SUBJECT.equals(cert.getSubjectX500Principal())
                    || !PUBLISHED_SUBJECT.equals(cert.getIssuerX500Principal())
                    || cert.hasUnsupportedCriticalExtension()
                    || cert.getBasicConstraints() < 0
                    || !("SHA384withRSA").equalsIgnoreCase(
                            cert.getSigAlgName())
                    || !(cert.getPublicKey() instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() != 4096
                    || !permits(cert, 5) || !permits(cert, 6)) {
                throw denied();
            }
            cert.checkValidity(Date.from(externalCheckedAt));
            cert.verify(cert.getPublicKey());
            return new UntrustedPublishedRootReference(sha256(copy));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw denied();
        }
    }

    private static boolean permits(X509Certificate cert, int bit) {
        boolean[] usage = cert.getKeyUsage();
        return usage != null && usage.length > bit && usage[bit];
    }

    private static String sha256(byte[] raw) throws GeneralSecurityException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Public FIDO R46 root reference candidate is not verified");
    }
}
