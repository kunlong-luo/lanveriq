package com.lanveriq.server.security;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * B4-A: strictly parses a proposed X.509 CA certificate using the JDK
 * certificate stack. This only admits structurally valid OFFLINE PROPOSALS.
 * It does NOT authorize an attestation root, trust its issuer, assess FIDO
 * metadata, provide revocation, or establish a trusted production clock.
 */
final class StagedAttestationRootCertificateInspector {
    private final Clock clock;

    StagedAttestationRootCertificateInspector(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    byte[] inspectUntrustedDer(byte[] untrustedDer) {
        if (untrustedDer == null || untrustedDer.length < 256
                || untrustedDer.length > 8192) {
            throw denied();
        }
        try {
            var parsed = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(untrustedDer));
            if (!(parsed instanceof X509Certificate cert)
                    || !Arrays.equals(untrustedDer, cert.getEncoded())
                    || cert.getVersion() != 3 || cert.getBasicConstraints() < 0
                    || !cert.getSubjectX500Principal()
                            .equals(cert.getIssuerX500Principal())) {
                throw denied();
            }
            boolean[] usage = cert.getKeyUsage();
            if (usage == null || usage.length <= 5 || !usage[5]) {
                throw denied();
            }
            // Java's X509Certificate.verify checks only the signature, not
            // whether a critical extension has been understood. A proposed
            // CA carrying unknown critical semantics must fail closed even
            // in this permanently untrusted offline staging lane.
            if (cert.hasUnsupportedCriticalExtension()) {
                throw denied();
            }
            cert.checkValidity(Date.from(clock.instant()));
            cert.verify(cert.getPublicKey()); // proof of self-signature, NOT provenance
            return untrustedDer.clone();
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Offline attestation root candidate rejected");
    }
}
