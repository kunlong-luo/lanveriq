package com.lanveriq.server.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C12: independent, two-key offline provenance *screen*.
 *
 * Governance keys and pins must be provisioned independently of any JWS,
 * MDS certificate, CRL, timestamp, request, or asserted root. This deliberately
 * cannot bootstrap such approval: there is no configured production key,
 * real MDS downloader, automatic CRL source, trusted clock, Spring Bean,
 * WebAuthn4J trust root, issuance capability or HTTP caller.
 *
 * Even two synthetically valid key signatures prove only the exact scope
 * and bytes signed by the supplied keys; they do not prove FIDO origin,
 * source custody, two distinct human reviewers, or production authorization.
 */
final class StagedFidoMdsIndependentReviewSeal {
    static final String EXPECTED_MDS_SOURCE = "https://mds3.fidoalliance.org/";
    private static final String DOMAIN = "lanveriq/fido-mds/independent-source-review/v1\n";
    private static final Duration MAX_REVIEW_WINDOW = Duration.ofDays(7);

    private StagedFidoMdsIndependentReviewSeal() {}

    record PinnedReviewKeys(PublicKey first, String firstSha256,
                            PublicKey second, String secondSha256) {}

    record ReviewClaim(String source, Instant checkedAt,
                       Instant validFrom, Instant expiresAt,
                       long reviewSequence, long blobNumber, long timeSequence,
                       String signerLeafSha256, String blobSha256,
                       String rootSha256, String crlEvidenceSha256,
                       String timeKeySha256, String signedTimeProofSha256) {}

    record SignedReview(ReviewClaim claim, byte[] firstSignature,
                        byte[] secondSignature) {
        SignedReview {
            firstSignature = firstSignature == null ? null : firstSignature.clone();
            secondSignature = secondSignature == null ? null : secondSignature.clone();
        }

        @Override public byte[] firstSignature() {
            return firstSignature == null ? null : firstSignature.clone();
        }

        @Override public byte[] secondSignature() {
            return secondSignature == null ? null : secondSignature.clone();
        }
    }

    static void requireIndependentReviewOnly(
            PinnedReviewKeys independentlyProvisioned,
            SignedReview signed, StagedFidoMdsSourceVerifier.UntrustedSourceEvidence
                    verifiedButUntrusted,
            long lastObservedReviewSequence) {
        if (independentlyProvisioned == null || signed == null
                || signed.claim() == null || verifiedButUntrusted == null
                || lastObservedReviewSequence < 0
                || lastObservedReviewSequence == Long.MAX_VALUE) {
            throw denied();
        }

        try {
            ReviewClaim claim = signed.claim();
            if (!EXPECTED_MDS_SOURCE.equals(claim.source())
                    || claim.checkedAt() == null || claim.validFrom() == null
                    || claim.expiresAt() == null
                    || claim.reviewSequence() <= lastObservedReviewSequence
                    || claim.reviewSequence() <= 0
                    || claim.blobNumber() <= 0 || claim.timeSequence() <= 0
                    || claim.validFrom().isAfter(claim.checkedAt())
                    || !claim.expiresAt().isAfter(claim.checkedAt())
                    || !claim.expiresAt().isAfter(claim.validFrom())
                    || Duration.between(claim.validFrom(), claim.expiresAt())
                            .compareTo(MAX_REVIEW_WINDOW) > 0) {
                throw denied();
            }

            // The signature scope is the EXACT crypto-verified evidence, not
            // a caller-provided JWS parser map or a self-declared root hash.
            if (!Objects.equals(claim.checkedAt(), verifiedButUntrusted.checkedAt())
                    || claim.blobNumber() != verifiedButUntrusted.blobNumber()
                    || claim.timeSequence() != verifiedButUntrusted.signedTimeSequence()
                    || !Objects.equals(claim.signerLeafSha256(),
                            verifiedButUntrusted.leafSha256())
                    || !Objects.equals(claim.blobSha256(),
                            verifiedButUntrusted.blobSha256())
                    || !Objects.equals(claim.rootSha256(),
                            verifiedButUntrusted.rootSha256())
                    || !Objects.equals(claim.crlEvidenceSha256(),
                            verifiedButUntrusted.crlEvidenceSha256())
                    || !Objects.equals(claim.timeKeySha256(),
                            verifiedButUntrusted.timeKeySha256())
                    || !Objects.equals(claim.signedTimeProofSha256(),
                            verifiedButUntrusted.signedTimeProofSha256())
                    || !validSha256(claim.blobSha256())
                    || !validSha256(claim.rootSha256())
                    || !validSha256(claim.crlEvidenceSha256())
                    || !validSha256(claim.timeKeySha256())
                    || !validSha256(claim.signerLeafSha256())
                    || !validSha256(claim.signedTimeProofSha256())) {
                throw denied();
            }

            PublicKey firstKey = independentlyProvisioned.first();
            PublicKey secondKey = independentlyProvisioned.second();
            if (firstKey == null || secondKey == null
                    || !validSha256(independentlyProvisioned.firstSha256())
                    || !validSha256(independentlyProvisioned.secondSha256())
                    || independentlyProvisioned.firstSha256().equals(
                            independentlyProvisioned.secondSha256())
                    || !independentlyProvisioned.firstSha256().equals(
                            sha256(firstKey.getEncoded()))
                    || !independentlyProvisioned.secondSha256().equals(
                            sha256(secondKey.getEncoded()))
                    || !ed25519Only(firstKey) || !ed25519Only(secondKey)
                    || signed.firstSignature() == null
                    || signed.secondSignature() == null
                    || signed.firstSignature().length != 64
                    || signed.secondSignature().length != 64) {
                throw denied();
            }

            byte[] transcript = transcript(claim);
            if (!verifyEd25519(firstKey, transcript, signed.firstSignature())
                    || !verifyEd25519(secondKey, transcript,
                            signed.secondSignature())) {
                throw denied();
            }
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw denied();
        }
    }

    // Package-private test signing contract: strictly versioned,
    // domain-separated, ordered and newline-delimited fixed-type values.
    static byte[] transcript(ReviewClaim claim) {
        if (claim == null) {
            throw denied();
        }
        return (DOMAIN + claim.source() + "\n"
                + claim.checkedAt() + "\n"
                + claim.validFrom() + "\n"
                + claim.expiresAt() + "\n"
                + claim.reviewSequence() + "\n"
                + claim.blobNumber() + "\n"
                + claim.timeSequence() + "\n"
                + claim.signerLeafSha256() + "\n"
                + claim.blobSha256() + "\n"
                + claim.rootSha256() + "\n"
                + claim.crlEvidenceSha256() + "\n"
                + claim.timeKeySha256() + "\n"
                + claim.signedTimeProofSha256() + "\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean ed25519Only(PublicKey key) {
        return "Ed25519".equalsIgnoreCase(key.getAlgorithm())
                || "EdDSA".equalsIgnoreCase(key.getAlgorithm());
    }

    private static boolean verifyEd25519(PublicKey key, byte[] transcript,
                                         byte[] signature)
            throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(transcript);
        return verifier.verify(signature);
    }

    private static boolean validSha256(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static String sha256(byte[] data) throws GeneralSecurityException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Independent FIDO MDS source-review evidence missing or invalid");
    }
}
