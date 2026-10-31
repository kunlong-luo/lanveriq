package com.lanveriq.server.enrollment;

import java.io.IOException;
import java.io.StringReader;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;

/**
 * D-07 offline-signed client leaf validation using JDK PKIX and Bouncy Castle.
 * The dedicated trust anchor comes ONLY from an independently reviewed local
 * configuration, never from an enrollment HTTP payload or supplied chain.
 *
 * This pure validator does not authorize registrations or handle revocation.
 * No runtime bean, CA key or network endpoint is provided by this class.
 */
public final class ClientCertificateProfileValidator {
    private static final String CLIENT_AUTH = "1.3.6.1.5.5.7.3.2";
    private static final int MAX_CHAIN_BYTES = 32 * 1024;
    private final X509Certificate trustedClientRoot;
    private final Clock clock;

    public ClientCertificateProfileValidator(X509Certificate trustedClientRoot, Clock clock) {
        this.trustedClientRoot = Objects.requireNonNull(trustedClientRoot);
        this.clock = Objects.requireNonNull(clock);
        try {
            boolean[] usage = trustedClientRoot.getKeyUsage();
            if (trustedClientRoot.getBasicConstraints() < 0 || usage == null
                    || usage.length <= 5 || !usage[5]
                    || !trustedClientRoot.getSubjectX500Principal()
                            .equals(trustedClientRoot.getIssuerX500Principal())) {
                throw invalid();
            }
            trustedClientRoot.checkValidity(Date.from(clock.instant()));
            trustedClientRoot.verify(trustedClientRoot.getPublicKey());
        } catch (GeneralSecurityException error) {
            throw invalid();
        }
    }

    public VerifiedLeaf validate(String leafAndIntermediatesPem,
                                 UUID deviceId, String expectedCsrSpkiSha256) {
        if (deviceId == null || deviceId.equals(new UUID(0, 0))
                || expectedCsrSpkiSha256 == null
                || !expectedCsrSpkiSha256.matches("[0-9a-f]{64}")
                || leafAndIntermediatesPem == null
                || leafAndIntermediatesPem.length() > MAX_CHAIN_BYTES
                || !leafAndIntermediatesPem.startsWith("-----BEGIN CERTIFICATE-----")
                || !leafAndIntermediatesPem.stripTrailing()
                        .endsWith("-----END CERTIFICATE-----")) {
            throw invalid();
        }
        try {
            List<X509Certificate> chain = parse(leafAndIntermediatesPem);
            X509Certificate leaf = chain.getFirst();
            Instant now = clock.instant();
            leaf.checkValidity(Date.from(now));
            boolean[] usage = leaf.getKeyUsage();
            if (leaf.getVersion() != 3 || leaf.getBasicConstraints() != -1
                    || usage == null || usage.length == 0 || !usage[0]) {
                throw invalid();
            }
            for (int i = 1; i < usage.length; i++) {
                if (usage[i]) {
                    throw invalid();
                }
            }
            if (!List.of(CLIENT_AUTH).equals(leaf.getExtendedKeyUsage())) {
                throw invalid();
            }
            var sans = leaf.getSubjectAlternativeNames();
            if (sans == null || sans.size() != 1) {
                throw invalid();
            }
            var san = sans.iterator().next();
            if (san.size() != 2 || !Integer.valueOf(6).equals(san.get(0))
                    || !("urn:uuid:" + deviceId).equals(san.get(1))) {
                throw invalid();
            }
            if (!(leaf.getPublicKey() instanceof ECPublicKey key)
                    || !isP256(key.getParams())
                    || !expectedCsrSpkiSha256.equals(sha256(leaf.getPublicKey().getEncoded()))
                    || Duration.between(leaf.getNotBefore().toInstant(),
                            leaf.getNotAfter().toInstant()).compareTo(Duration.ofDays(90)) > 0) {
                throw invalid();
            }
            var params = new PKIXParameters(Set.of(new TrustAnchor(trustedClientRoot, null)));
            // PKI/CRL/OCSP revocation and runtime credential revocation remain
            // mandatory separate deployment gates; never expose this alone.
            params.setRevocationEnabled(false);
            params.setDate(Date.from(now));
            CertPathValidator.getInstance("PKIX").validate(
                    CertificateFactory.getInstance("X.509").generateCertPath(chain), params);
            String serial = leaf.getSerialNumber().toString(16);
            if (leaf.getSerialNumber().signum() <= 0 || serial.length() > 128) {
                throw invalid();
            }
            return new VerifiedLeaf(sha256(leaf.getEncoded()),
                    sha256(trustedClientRoot.getEncoded()), serial,
                    expectedCsrSpkiSha256, leaf.getNotBefore().toInstant(),
                    leaf.getNotAfter().toInstant());
        } catch (IOException | GeneralSecurityException error) {
            throw invalid();
        }
    }

    private List<X509Certificate> parse(String pem) throws IOException {
        var certificates = new ArrayList<X509Certificate>();
        try (var parser = new PEMParser(new StringReader(pem))) {
            Object value;
            while ((value = parser.readObject()) != null) {
                if (!(value instanceof X509CertificateHolder holder)
                        || certificates.size() >= 3) {
                    throw invalid();
                }
                try {
                    X509Certificate certificate =
                            new JcaX509CertificateConverter().getCertificate(holder);
                    if (certificate.equals(trustedClientRoot)
                            || certificates.contains(certificate)) {
                        throw invalid();
                    }
                    certificates.add(certificate);
                } catch (java.security.cert.CertificateException error) {
                    throw invalid();
                }
            }
        }
        if (certificates.isEmpty()) {
            throw invalid();
        }
        return List.copyOf(certificates);
    }

    private static boolean isP256(ECParameterSpec actual) throws GeneralSecurityException {
        var parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        var expected = parameters.getParameterSpec(ECParameterSpec.class);
        return actual.getCurve().equals(expected.getCurve())
                && actual.getGenerator().equals(expected.getGenerator())
                && actual.getOrder().equals(expected.getOrder())
                && actual.getCofactor() == expected.getCofactor();
    }

    private static String sha256(byte[] value) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or untrusted device client certificate");
    }

    public record VerifiedLeaf(String fingerprint, String issuerId, String serial,
                               String csrSpkiSha256, Instant issuedAt, Instant expiresAt) {
    }
}
