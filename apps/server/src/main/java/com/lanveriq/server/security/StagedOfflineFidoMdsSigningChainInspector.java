package com.lanveriq.server.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C3 isolated offline PKIX + CRL *regression fixture* for FIDO-shaped
 * metadata signing certificates. This is NOT an MDS signer source of trust.
 *
 * Caller supplies an ephemeral synthetic anchor AND already-loaded CRLs.
 * Neither is an organization/FIDO-approved signer or revocation source.
 * A valid synthetic chain only returns UntrustedChain; it NEVER creates a
 * WebAuthn4J TrustAnchor, credential, MFA, or production signing authority.
 * Package-private, deliberately NOT a Spring Bean and no network retrieval.
 */
final class StagedOfflineFidoMdsSigningChainInspector {
    private final Clock clock;

    StagedOfflineFidoMdsSigningChainInspector(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    UntrustedChain inspectSyntheticOfflineOnly(List<X509Certificate> signerFirstChain,
            X509Certificate syntheticAnchor, List<X509CRL> offlineCrlEvidence) {
        // No implicit trust from a JWS x5c/x5u, system CA store, V22 proposal,
        // or a downloaded certificate; only a caller-provided SYNTHETIC root.
        if (syntheticAnchor == null || signerFirstChain == null
                || signerFirstChain.isEmpty() || signerFirstChain.size() > 4
                || offlineCrlEvidence == null || offlineCrlEvidence.isEmpty()
                || offlineCrlEvidence.size() > 8
                || signerFirstChain.stream().anyMatch(Objects::isNull)
                || offlineCrlEvidence.stream().anyMatch(Objects::isNull)) {
            throw denied();
        }
        try {
            Instant now = clock.instant();
            Date checkedAt = Date.from(now);
            syntheticAnchor.checkValidity(checkedAt);
            if (syntheticAnchor.getBasicConstraints() < 0
                    || !usagePermits(syntheticAnchor, 5)
                    || syntheticAnchor.hasUnsupportedCriticalExtension()
                    || !syntheticAnchor.getSubjectX500Principal()
                            .equals(syntheticAnchor.getIssuerX500Principal())) {
                throw denied();
            }
            syntheticAnchor.verify(syntheticAnchor.getPublicKey());

            for (int i = 0; i < signerFirstChain.size(); i++) {
                X509Certificate cert = signerFirstChain.get(i);
                cert.checkValidity(checkedAt);
                if (cert.hasUnsupportedCriticalExtension()
                        || cert.equals(syntheticAnchor)) {
                    throw denied();
                }
                if (i == 0) {
                    // The JWS signer must be an end-entity signing certificate.
                    if (cert.getBasicConstraints() >= 0 || !usagePermits(cert, 0)) {
                        throw denied();
                    }
                } else if (cert.getBasicConstraints() < 0
                        || !usagePermits(cert, 5)) {
                    throw denied();
                }
            }

            List<X509Certificate> issuers = new ArrayList<>(signerFirstChain);
            issuers.add(syntheticAnchor);
            // Reject invalid or stale caller-provided status objects before
            // giving JDK PKIX a bounded, purely in-memory CertStore.
            for (X509CRL crl : offlineCrlEvidence) {
                if (crl.hasUnsupportedCriticalExtension()
                        || crl.getThisUpdate() == null || crl.getNextUpdate() == null
                        || crl.getThisUpdate().after(checkedAt)
                        || !crl.getNextUpdate().after(checkedAt)) {
                    throw denied();
                }
                boolean signedByIssuer = false;
                for (X509Certificate issuer : issuers) {
                    if (issuer.getSubjectX500Principal().equals(crl.getIssuerX500Principal())
                            && usagePermits(issuer, 6)) {
                        crl.verify(issuer.getPublicKey());
                        signedByIssuer = true;
                        break;
                    }
                }
                if (!signedByIssuer) {
                    throw denied();
                }
            }

            // Java 25 built-in RFC5280 path validator + actual revocation
            // checker. CRLs ONLY, NO OCSP fallback and absolutely NO SOFT_FAIL.
            var validator = CertPathValidator.getInstance("PKIX");
            var parameters = new PKIXParameters(
                    Set.of(new TrustAnchor(syntheticAnchor, null)));
            parameters.setDate(checkedAt);
            parameters.setRevocationEnabled(false);
            parameters.addCertStore(CertStore.getInstance("Collection",
                    new CollectionCertStoreParameters(offlineCrlEvidence)));
            var checker = (PKIXRevocationChecker) validator.getRevocationChecker();
            checker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.PREFER_CRLS,
                    PKIXRevocationChecker.Option.NO_FALLBACK));
            parameters.addCertPathChecker(checker);
            var path = CertificateFactory.getInstance("X.509")
                    .generateCertPath(signerFirstChain);
            validator.validate(path, parameters);

            byte[] fingerprint = MessageDigest.getInstance("SHA-256")
                    .digest(signerFirstChain.getFirst().getEncoded());
            return new UntrustedChain(HexFormat.of().formatHex(fingerprint), now);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw denied();
        }
    }

    private static boolean usagePermits(X509Certificate cert, int bit) {
        boolean[] usage = cert.getKeyUsage();
        return usage != null && usage.length > bit && usage[bit];
    }

    record UntrustedChain(String syntheticLeafSha256, Instant observedAt) {
        // No approved / trusted / PKIX-authorized / admission flag.
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Synthetic MDS signing certificate is not trusted");
    }
}
