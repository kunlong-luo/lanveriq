package com.lanveriq.server.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C9: offline cryptographic source-checking primitive for FIDO MDS3
 * shaped JWS with x5c, explicit pinned root, signed time and CRL evidence.\n * V28 uses a domain-separated v2 time signature over the EXACT signed JWS,
 * approved-shaped anchor digest, sorted CRL DER digest, time-key digest,
 * timestamp AND monotonic sequence. Old unbound v1 proofs are rejected.
 *
 * IMPORTANT: This is NOT an approved MDS trust store or trusted time source.
 * All external approvals, root/time key custody, CRL acquisition and durable
 * anti-rollback state are ABSENT. This package-private non-Bean has no HTTP,
 * database, WebAuthn4J, issuer or Agent consumer. Its evidence-only result is
 * NEVER a grant to register authenticators, approve admins or issue certs.
 */
final class StagedFidoMdsSourceVerifier {
    private static final int MAX_JWS_LENGTH = 32 * 1024 * 1024;
    private static final long MAX_IAT_AGE_SECONDS = 60L * 86400L;
    private static final String TIME_DOMAIN = "lanveriq/fido-mds/source-time/v2\n";

    record ExternalApproval(X509Certificate anchor, String anchorSha256,
                            PublicKey timeKey, String timeKeySha256) {}

    record SignedTimeEvidence(Instant checkedAt, long sequence, byte[] signature) {
        SignedTimeEvidence {
            signature = signature == null ? null : signature.clone();
        }
        @Override public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    record UntrustedSourceEvidence(long blobNumber, long signedTimeSequence,
                                   Instant checkedAt, String leafSha256,
                                   String blobSha256, String rootSha256,
                                   String crlEvidenceSha256,
                                   String timeKeySha256,
                                   String signedTimeProofSha256) {
        // Intentionally NO trusted, attestation-admission or MFA permission.
    }

    UntrustedSourceEvidence inspectOfflineOnly(
            String compactJws, ExternalApproval claimedOutOfBandApproval,
            SignedTimeEvidence time, List<X509CRL> offlineCrls,
            long lastObservedBlobNumber, long lastObservedTimeSequence) {
        if (compactJws == null || compactJws.length() < 20
                || compactJws.length() > MAX_JWS_LENGTH
                || claimedOutOfBandApproval == null || time == null
                || time.checkedAt() == null || time.signature() == null
                || time.signature().length == 0 || time.sequence() <= 0
                || time.sequence() <= lastObservedTimeSequence
                || claimedOutOfBandApproval.anchor() == null
                || claimedOutOfBandApproval.timeKey() == null
                || offlineCrls == null || offlineCrls.isEmpty()
                || offlineCrls.size() > 16 || offlineCrls.stream().anyMatch(Objects::isNull)
                || lastObservedBlobNumber < 0 || lastObservedBlobNumber == Long.MAX_VALUE
                || lastObservedTimeSequence < 0
                || lastObservedTimeSequence == Long.MAX_VALUE) {
            throw denied();
        }

        try {
            X509Certificate anchor = claimedOutOfBandApproval.anchor();
            String rootFingerprint = sha256(anchor.getEncoded());
            String timeKeyFingerprint =
                    sha256(claimedOutOfBandApproval.timeKey().getEncoded());
            // Both independently approved fingerprints must be provisioned
            // out-of-band; input values are NOT an approval by themselves.
            if (!validPin(claimedOutOfBandApproval.anchorSha256())
                    || !validPin(claimedOutOfBandApproval.timeKeySha256())
                    || !rootFingerprint.equals(claimedOutOfBandApproval.anchorSha256())
                    || !timeKeyFingerprint.equals(
                            claimedOutOfBandApproval.timeKeySha256())) {
                throw denied();
            }
            Instant checkedAt = time.checkedAt();
            Date at = Date.from(checkedAt);
            anchor.checkValidity(at);
            if (anchor.getBasicConstraints() < 0 || !permits(anchor, 5)
                    || anchor.hasUnsupportedCriticalExtension()
                    || !anchor.getSubjectX500Principal().equals(
                            anchor.getIssuerX500Principal())) {
                throw denied();
            }
            anchor.verify(anchor.getPublicKey());

            // D4-C11: parse ORIGINAL compact segments strictly before Nimbus
            // maps can collapse repeated header/payload JSON member names.
            // Signature, PKIX/CRL, checked time and provenance gates remain
            // independent and mandatory; this is only a structural denial.
            StagedFidoMdsStrictJsonScreen.requireUnambiguousCompactJws(compactJws);
            JWSObject jws = JWSObject.parse(compactJws);
            var header = jws.getHeader();
            if ((!JWSAlgorithm.ES256.equals(header.getAlgorithm())
                    && !JWSAlgorithm.RS256.equals(header.getAlgorithm()))
                    || header.getJWK() != null || header.getJWKURL() != null
                    || header.getKeyID() != null || header.getX509CertURL() != null
                    || (header.getCriticalParams() != null
                        && !header.getCriticalParams().isEmpty())
                    || header.getX509CertChain() == null
                    || header.getX509CertChain().isEmpty()
                    || header.getX509CertChain().size() > 5) {
                // x5u requires separately audited, same-origin retrieval and
                // is deliberately denied in this offline-only first slice.
                throw denied();
            }
            Object iatValue = header.getCustomParam("iat");
            if (!(iatValue instanceof Long || iatValue instanceof Integer)) {
                throw denied();
            }
            long iat = ((Number) iatValue).longValue();
            if (iat <= 0 || iat > checkedAt.getEpochSecond() + 300
                    || iat < checkedAt.getEpochSecond() - MAX_IAT_AGE_SECONDS) {
                throw denied();
            }

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> signerFirstChain = new ArrayList<>();
            for (var raw : header.getX509CertChain()) {
                byte[] der = raw.decode();
                if (der.length < 64 || der.length > 16384) {
                    throw denied();
                }
                var bytes = new ByteArrayInputStream(der);
                X509Certificate cert =
                        (X509Certificate) factory.generateCertificate(bytes);
                if (bytes.available() != 0 || !Arrays.equals(cert.getEncoded(), der)
                        || cert.hasUnsupportedCriticalExtension()
                        || cert.equals(anchor)) {
                    throw denied();
                }
                cert.checkValidity(at);
                if (signerFirstChain.isEmpty()) {
                    if (cert.getBasicConstraints() >= 0 || !permits(cert, 0)) {
                        throw denied();
                    }
                    // GlobalSign R46 is a general-purpose public CA.
                    // An arbitrary certificate under it is NOT a FIDO signer.
                    // Verify the exact official MDS DNS identity in the SAN
                    // and the expected TLS signer EKU; CN is insufficient.
                    boolean officialDnsName = false;
                    var names = cert.getSubjectAlternativeNames();
                    if (names != null) {
                        for (var name : names) {
                            if (name.size() >= 2 && Integer.valueOf(2).equals(name.get(0))
                                    && name.get(1) instanceof String dns
                                    && "mds.fidoalliance.org".equalsIgnoreCase(dns)) {
                                officialDnsName = true;
                            }
                        }
                    }
                    var extendedUsages = cert.getExtendedKeyUsage();
                    if (!officialDnsName || extendedUsages == null
                            || !extendedUsages.contains("1.3.6.1.5.5.7.3.1")) {
                        throw denied();
                    }
                } else if (cert.getBasicConstraints() < 0 || !permits(cert, 5)) {
                    throw denied();
                }
                signerFirstChain.add(cert);
            }

            List<X509Certificate> possibleIssuers =
                    new ArrayList<>(signerFirstChain);
            possibleIssuers.add(anchor);
            var crlHashes = new ArrayList<String>();
            for (X509CRL crl : offlineCrls) {
                if (crl.hasUnsupportedCriticalExtension()
                        || crl.getThisUpdate() == null || crl.getNextUpdate() == null
                        || crl.getThisUpdate().after(at)
                        || !crl.getNextUpdate().after(at)) {
                    throw denied();
                }
                boolean issuerVerified = false;
                for (X509Certificate issuer : possibleIssuers) {
                    if (issuer.getSubjectX500Principal().equals(
                            crl.getIssuerX500Principal()) && permits(issuer, 6)) {
                        crl.verify(issuer.getPublicKey());
                        issuerVerified = true;
                        break;
                    }
                }
                if (!issuerVerified) {
                    throw denied();
                }
                crlHashes.add(sha256(crl.getEncoded()));
            }

            // The 2026 R46/R3 transitional x5c may include a third cross
            // certificate that is not on the path to an approved R46 root.
            // Validate a signer-first prefix to the EXPLICIT approved anchor.
            // The JDK PKIX checker enforces revocation without network/soft-fail.
            boolean validPath = false;
            var validator = CertPathValidator.getInstance("PKIX");
            for (int prefix = 1; prefix <= signerFirstChain.size(); prefix++) {
                var chainPath = signerFirstChain.subList(0, prefix);
                PKIXParameters params =
                        new PKIXParameters(Set.of(new TrustAnchor(anchor, null)));
                params.setDate(at);
                params.setRevocationEnabled(false);
                params.addCertStore(CertStore.getInstance("Collection",
                        new CollectionCertStoreParameters(offlineCrls)));
                var checker =
                        (PKIXRevocationChecker) validator.getRevocationChecker();
                checker.setOptions(EnumSet.of(
                        PKIXRevocationChecker.Option.PREFER_CRLS,
                        PKIXRevocationChecker.Option.NO_FALLBACK));
                params.addCertPathChecker(checker);
                try {
                    validator.validate(factory.generateCertPath(chainPath), params);
                    validPath = true;
                    break;
                } catch (CertPathValidatorException rejectedPrefix) {
                    // Try the next ordered x5c prefix; never try another root.
                }
            }
            if (!validPath) {
                throw denied();
            }

            PublicKey signerKey = signerFirstChain.getFirst().getPublicKey();
            boolean signatureValid = (JWSAlgorithm.ES256.equals(header.getAlgorithm())
                    && signerKey instanceof ECPublicKey ec
                    && jws.verify(new ECDSAVerifier(ec)))
                    || (JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    && signerKey instanceof RSAPublicKey rsa
                    && jws.verify(new RSASSAVerifier(rsa)));
            if (!signatureValid) {
                throw denied();
            }

            Map<String, Object> payload = jws.getPayload().toJSONObject();
            if (payload == null || !(payload.get("no") instanceof Integer
                    || payload.get("no") instanceof Long)
                    || !(payload.get("legalHeader") instanceof String legal
                        && !legal.isBlank())
                    || !(payload.get("entries") instanceof List<?> entries)
                    || entries.size() > 50000) {
                throw denied();
            }
            long blobNo = ((Number) payload.get("no")).longValue();
            if (blobNo <= lastObservedBlobNumber) {
                throw denied();
            }
            // D4-C9: even an ES256/RS256-signed, PKIX/CRL-checked BLOB
            // must not carry silently malformed authenticator entries.
            // Structural checks are NOT metadata status/admission policy.
            StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes(
                    entries, checkedAt);
            // MDS 3.1.1 deprecates nextUpdate: optional but validated if set.
            if (payload.containsKey("nextUpdate")) {
                if (!(payload.get("nextUpdate") instanceof String date)
                        || LocalDate.parse(date).isBefore(
                                checkedAt.atZone(ZoneOffset.UTC).toLocalDate())) {
                    throw denied();
                }
            }
            crlHashes.sort(String::compareTo);
            String crlEvidenceSha = sha256(String.join(":", crlHashes).getBytes(
                    StandardCharsets.US_ASCII));
            String jwsSha = sha256(compactJws.getBytes(StandardCharsets.US_ASCII));
            if (!verifySignedTime(claimedOutOfBandApproval.timeKey(),
                    time, jwsSha, rootFingerprint, crlEvidenceSha,
                    timeKeyFingerprint)) {
                throw denied();
            }
            return new UntrustedSourceEvidence(blobNo, time.sequence(), checkedAt,
                    sha256(signerFirstChain.getFirst().getEncoded()),
                    jwsSha, rootFingerprint, crlEvidenceSha,
                    timeKeyFingerprint, sha256(time.signature()));
        } catch (GeneralSecurityException | ParseException | JOSEException
                | IllegalArgumentException | DateTimeException error) {
            throw denied();
        }
    }

    /**
     * C12 opt-in EXTRA scrutiny for already untrusted, synthetic evidence.
     * The two governance verification keys MUST come from a separate,
     * preapproved custody channel, not from the submitted MDS BLOB or
     * from the same untrusted caller. There are deliberately NO provisioned
     * real governance keys or approved official R46 root.
     *
     * This method cannot promote the result beyond UntrustedSourceEvidence.
     * The older inspectOfflineOnly remains a test primitive and MUST NEVER
     * be reused as a production authority or trust source.
     */
    UntrustedSourceEvidence inspectWithIndependentReviewOfflineOnly(
            String compactJws, ExternalApproval claimedOutOfBandApproval,
            SignedTimeEvidence time, List<X509CRL> offlineCrls,
            long lastObservedBlobNumber, long lastObservedTimeSequence,
            StagedFidoMdsIndependentReviewSeal.PinnedReviewKeys
                    independentlyProvisionedGovernanceKeys,
            StagedFidoMdsIndependentReviewSeal.SignedReview signedReview,
            long lastObservedGovernanceReviewSequence) {
        // First verify JWS, pinned synthetic PKIX path, revocation, status
        // structure and exact-JWS signed time. No result escapes yet.
        UntrustedSourceEvidence untrusted = inspectOfflineOnly(
                compactJws, claimedOutOfBandApproval, time, offlineCrls,
                lastObservedBlobNumber, lastObservedTimeSequence);
        // Even crypto-valid untrusted evidence is withheld until BOTH
        // independent pinned reviewer keys validate the same exact data.
        StagedFidoMdsIndependentReviewSeal.requireIndependentReviewOnly(
                independentlyProvisionedGovernanceKeys,
                signedReview, untrusted, lastObservedGovernanceReviewSequence);
        return untrusted;
    }

    /**
     * D4-C13 opt-in PUBLIC REFERENCE comparison before the separate C12
     * reviewer signatures. A claimant cannot replace published R46 with a
     * self-issued root, even if they forged a self-consistent signed JWS,
     * CRL, time proof and two disposable governance signatures.
     *
     * Never treat this offline test primitive or R46 public certificate as
     * official source custody or a production-approved FIDO trust store.
     */
    UntrustedSourceEvidence inspectWithPublishedR46ReferenceOfflineOnly(
            byte[] candidateOfficialRootDer,
            String compactJws, ExternalApproval claimedOutOfBandApproval,
            SignedTimeEvidence time, List<X509CRL> offlineCrls,
            long lastObservedBlobNumber, long lastObservedTimeSequence,
            StagedFidoMdsIndependentReviewSeal.PinnedReviewKeys
                    independentlyProvisionedGovernanceKeys,
            StagedFidoMdsIndependentReviewSeal.SignedReview signedReview,
            long lastObservedGovernanceReviewSequence) {
        if (claimedOutOfBandApproval == null
                || claimedOutOfBandApproval.anchor() == null
                || time == null || time.checkedAt() == null) {
            throw denied();
        }
        var candidate = StagedFidoMdsR46PublicReferenceScreen
                .inspectReferenceOnly(candidateOfficialRootDer, time.checkedAt());
        try {
            // Neither a caller-supplied SHA matching a caller-supplied fake
            // root nor a valid R46 reference paired with a different root
            // can pass. Do not accept an R3 cross-certificate as R46.
            if (!candidate.rootSha256().equals(
                    claimedOutOfBandApproval.anchorSha256())
                    || !Arrays.equals(candidateOfficialRootDer,
                            claimedOutOfBandApproval.anchor().getEncoded())) {
                throw denied();
            }
        } catch (GeneralSecurityException invalidCertificate) {
            throw denied();
        }
        return inspectWithIndependentReviewOfflineOnly(
                compactJws, claimedOutOfBandApproval, time, offlineCrls,
                lastObservedBlobNumber, lastObservedTimeSequence,
                independentlyProvisionedGovernanceKeys, signedReview,
                lastObservedGovernanceReviewSequence);
    }

    private static boolean verifySignedTime(
            PublicKey signedBy, SignedTimeEvidence evidence,
            String jwsSha, String rootSha, String crlSha, String timeKeySha)
            throws GeneralSecurityException {
        if (!"Ed25519".equalsIgnoreCase(signedBy.getAlgorithm())
                && !"EdDSA".equalsIgnoreCase(signedBy.getAlgorithm())) {
            return false;
        }
        // A time claim without binding to precisely the verified materials
        // could be replayed with another valid BLOB or a newer/different CRL.
        // All values are lowercase SHA-256 hex, except UTC ISO-8601 Instant.
        String transcript = TIME_DOMAIN + evidence.checkedAt() + "\n"
                + evidence.sequence() + "\n" + jwsSha + "\n"
                + rootSha + "\n" + crlSha + "\n" + timeKeySha + "\n";
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(signedBy);
        verifier.update(transcript.getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(evidence.signature());
    }

    private static boolean validPin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean permits(X509Certificate cert, int bit) {
        boolean[] usage = cert.getKeyUsage();
        return usage != null && usage.length > bit && usage[bit];
    }

    private static String sha256(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("FIDO MDS source is not approved or verified");
    }
}
