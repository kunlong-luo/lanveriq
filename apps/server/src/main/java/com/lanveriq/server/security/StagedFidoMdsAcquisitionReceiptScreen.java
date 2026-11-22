package com.lanveriq.server.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C14: OFFLINE-only custody manifest bound to exactly the already
 * cryptographically verified *untrusted* MDS JWS and offline CRL bytes.
 *
 * A signature authenticates a provisioned acquisition-key holder's ASSERTION,
 * not the TLS exchange, actual download location, official CRL issuer source,
 * trusted clock, FIDO permission, administrator identity or production origin.
 *
 * Package private, no Spring Bean, no downloader, socket, persisted seal,
 * issuer, WebAuthn integration, production key or runtime configuration.
 */
final class StagedFidoMdsAcquisitionReceiptScreen {
    static final String MDS_URL = "https://mds3.fidoalliance.org/";
    private static final String DOMAIN = "lanveriq/fido-mds/acquisition-receipt/v1\n";
    private static final Duration MAX_FETCH_AGE = Duration.ofHours(24);

    private StagedFidoMdsAcquisitionReceiptScreen() {}

    record CrlArtifact(String sourceUrl, String derSha256) {}

    record PinnedAcquisitionKey(PublicKey key, String keySha256,
                                List<String> allowedCrlSourceUrls) {
        PinnedAcquisitionKey {
            allowedCrlSourceUrls = allowedCrlSourceUrls == null
                    ? null : List.copyOf(allowedCrlSourceUrls);
        }
    }

    record AcquisitionClaim(String mdsUrl, String tlsVersion,
                            int httpStatus, boolean followedRedirect,
                            Instant fetchedAt, Instant checkedAt,
                            long sequence, long blobNumber, long signedTimeSequence,
                            String jwsSha256, String rootSha256,
                            String crlEvidenceSha256, List<CrlArtifact> crls) {
        AcquisitionClaim {
            crls = crls == null ? null : List.copyOf(crls);
        }
    }

    record SignedAcquisition(AcquisitionClaim claim, byte[] signature) {
        SignedAcquisition {
            signature = signature == null ? null : signature.clone();
        }
        @Override public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    record UntrustedAcquisitionEvidence(long sequence, String jwsSha256,
                                        String crlEvidenceSha256,
                                        String acquisitionKeySha256) {}

    static UntrustedAcquisitionEvidence inspectUntrustedOnly(
            PinnedAcquisitionKey independentlyProvisionedKey,
            SignedAcquisition signed, List<X509CRL> actualOfflineCrls,
            StagedFidoMdsSourceVerifier.UntrustedSourceEvidence
                    cryptographicallyVerifiedButUntrusted,
            long lastObservedAcquisitionSequence) {
        if (independentlyProvisionedKey == null || signed == null
                || signed.claim() == null || actualOfflineCrls == null
                || cryptographicallyVerifiedButUntrusted == null
                || lastObservedAcquisitionSequence < 0
                || lastObservedAcquisitionSequence == Long.MAX_VALUE) {
            throw denied();
        }
        try {
            var claim = signed.claim();
            var trustedKey = independentlyProvisionedKey.key();
            var allowedUrls = independentlyProvisionedKey.allowedCrlSourceUrls();
            if (trustedKey == null
                    || !isEd25519(trustedKey)
                    || !validHash(independentlyProvisionedKey.keySha256())
                    || !independentlyProvisionedKey.keySha256()
                            .equals(sha256(trustedKey.getEncoded()))
                    || signed.signature() == null
                    || signed.signature().length != 64
                    || !MDS_URL.equals(claim.mdsUrl())
                    || !"TLSv1.3".equals(claim.tlsVersion())
                    || claim.httpStatus() != 200 || claim.followedRedirect()
                    || claim.fetchedAt() == null || claim.checkedAt() == null
                    || claim.fetchedAt().isAfter(claim.checkedAt())
                    || Duration.between(claim.fetchedAt(), claim.checkedAt())
                            .compareTo(MAX_FETCH_AGE) > 0
                    || claim.sequence() <= lastObservedAcquisitionSequence
                    || claim.sequence() <= 0
                    || claim.blobNumber() <= 0
                    || claim.signedTimeSequence() <= 0
                    || !validHash(claim.jwsSha256())
                    || !validHash(claim.rootSha256())
                    || !validHash(claim.crlEvidenceSha256())
                    || !Objects.equals(claim.jwsSha256(),
                            cryptographicallyVerifiedButUntrusted.blobSha256())
                    || !Objects.equals(claim.rootSha256(),
                            cryptographicallyVerifiedButUntrusted.rootSha256())
                    || !Objects.equals(claim.crlEvidenceSha256(),
                            cryptographicallyVerifiedButUntrusted.crlEvidenceSha256())
                    || !Objects.equals(claim.checkedAt(),
                            cryptographicallyVerifiedButUntrusted.checkedAt())
                    || claim.blobNumber()
                            != cryptographicallyVerifiedButUntrusted.blobNumber()
                    || claim.signedTimeSequence()
                            != cryptographicallyVerifiedButUntrusted.signedTimeSequence()
                    || claim.crls() == null || claim.crls().isEmpty()
                    || claim.crls().size() > 16
                    || actualOfflineCrls.size() != claim.crls().size()
                    || allowedUrls == null
                    || allowedUrls.size() != claim.crls().size()) {
                throw denied();
            }

            // A source URL comes from the independent policy allowlist, NOT
            // the BLOB or a certificate-provided attacker-selected URL.
            // URLs in the manifest are mere asserted custody locations; no
            // HTTPS connection is performed or proven by this method.
            var configuredSources = new HashSet<String>();
            for (String url : allowedUrls) {
                if (!safeHttpsUrl(url) || !configuredSources.add(url)) {
                    throw denied();
                }
            }
            var actualSources = new HashSet<String>();
            var actualDerHashes = new ArrayList<String>();
            for (X509CRL crl : actualOfflineCrls) {
                if (crl == null || crl.getThisUpdate() == null
                        || crl.getNextUpdate() == null
                        || crl.getThisUpdate().toInstant()
                                .isAfter(claim.fetchedAt())
                        || !crl.getNextUpdate().toInstant()
                                .isAfter(claim.checkedAt())) {
                    throw denied();
                }
                actualDerHashes.add(sha256(crl.getEncoded()));
            }
            var claimedDerHashes = new ArrayList<String>();
            for (CrlArtifact item : claim.crls()) {
                if (item == null || !safeHttpsUrl(item.sourceUrl())
                        || !configuredSources.contains(item.sourceUrl())
                        || !actualSources.add(item.sourceUrl())
                        || !validHash(item.derSha256())) {
                    throw denied();
                }
                claimedDerHashes.add(item.derSha256());
            }
            actualDerHashes.sort(String::compareTo);
            claimedDerHashes.sort(String::compareTo);
            if (!actualDerHashes.equals(claimedDerHashes)
                    || !actualSources.equals(configuredSources)) {
                throw denied();
            }
            var crlSetSha256 = sha256(
                    String.join(":", actualDerHashes)
                            .getBytes(StandardCharsets.US_ASCII));
            if (!crlSetSha256.equals(claim.crlEvidenceSha256())) {
                throw denied();
            }

            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(trustedKey);
            verifier.update(transcript(claim));
            if (!verifier.verify(signed.signature())) {
                throw denied();
            }
            return new UntrustedAcquisitionEvidence(
                    claim.sequence(), claim.jwsSha256(),
                    claim.crlEvidenceSha256(),
                    independentlyProvisionedKey.keySha256());
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw denied();
        }
    }

    /**
     * Fixed-domain, ordered, line-delimited signed evidence for test fixtures.
     * All variable CRL URLs are validated not to contain CR/LF, and the
     * signed list is ordered by URL to avoid accidental order ambiguity.
     */
    static byte[] transcript(AcquisitionClaim claim) {
        if (claim == null || claim.crls() == null
                || claim.crls().isEmpty() || claim.crls().size() > 16) {
            throw denied();
        }
        var sorted = new ArrayList<CrlArtifact>(claim.crls());
        for (CrlArtifact item : sorted) {
            if (item == null || !safeHttpsUrl(item.sourceUrl())
                    || !validHash(item.derSha256())) {
                throw denied();
            }
        }
        sorted.sort((a, b) -> a.sourceUrl().compareTo(b.sourceUrl()));
        var message = new StringBuilder(DOMAIN)
                .append(claim.mdsUrl()).append('\n')
                .append(claim.tlsVersion()).append('\n')
                .append(claim.httpStatus()).append('\n')
                .append(claim.followedRedirect()).append('\n')
                .append(claim.fetchedAt()).append('\n')
                .append(claim.checkedAt()).append('\n')
                .append(claim.sequence()).append('\n')
                .append(claim.blobNumber()).append('\n')
                .append(claim.signedTimeSequence()).append('\n')
                .append(claim.jwsSha256()).append('\n')
                .append(claim.rootSha256()).append('\n')
                .append(claim.crlEvidenceSha256()).append('\n')
                .append(sorted.size()).append('\n');
        for (CrlArtifact crl : sorted) {
            message.append(crl.sourceUrl()).append('\n')
                    .append(crl.derSha256()).append('\n');
        }
        return message.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean safeHttpsUrl(String url) {
        if (url == null || url.length() > 2048
                || url.indexOf('\n') >= 0 || url.indexOf('\r') >= 0) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawPath() != null
                    && !uri.getRawPath().isBlank()
                    && !uri.getRawPath().contains("..")
                    && uri.getRawQuery() == null
                    && url.equals(uri.toASCIIString());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean isEd25519(PublicKey key) {
        return "Ed25519".equalsIgnoreCase(key.getAlgorithm())
                || "EdDSA".equalsIgnoreCase(key.getAlgorithm());
    }

    private static String sha256(byte[] raw) throws GeneralSecurityException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Offline FIDO MDS acquisition provenance receipt denied");
    }
}
