package com.lanveriq.server.security;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D2-D4-B: internal, deliberately UNWIRED attestation-policy admission guard.
 *
 * This is NOT a verifier of attestation signatures, certificate paths, FIDO
 * metadata or revocation. Only a future independently authenticated strict
 * WebAuthn4J attestation verifier may supply a VerifiedAttestation. Do not
 * deserialize it from JSON, expose it as an API, or wire this as a Spring bean.
 * An allow decision never creates credentials, grants or trusted evidence.
 */
final class StagedAttestationAdmissionPolicy {
    enum AttestationKind { NONE, SELF, BASIC, ATT_CA, ANON_CA, ENTERPRISE }

    record VerifiedAttestation(AttestationKind kind, String trustAnchorSha256Hex,
            String aaguid, boolean certificatePathVerified,
            boolean metadataTrusted, boolean revocationChecked,
            boolean userPresence, boolean userVerified) {}

    private final Set<String> approvedRootFingerprints;

    StagedAttestationAdmissionPolicy(Set<String> approvedRootFingerprints) {
        Objects.requireNonNull(approvedRootFingerprints);
        // No trust anchor is bundled or enabled by default.
        this.approvedRootFingerprints = Set.copyOf(approvedRootFingerprints);
        if (this.approvedRootFingerprints.stream().anyMatch(
                root -> root == null || !root.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Invalid SHA-256 root fingerprint");
        }
    }

    void requireTrustedSource(VerifiedAttestation attestation) {
        if (attestation == null
                || (attestation.kind() != AttestationKind.BASIC
                    && attestation.kind() != AttestationKind.ATT_CA
                    && attestation.kind() != AttestationKind.ANON_CA
                    && attestation.kind() != AttestationKind.ENTERPRISE)
                || !attestation.certificatePathVerified()
                || !attestation.metadataTrusted()
                || !attestation.revocationChecked()
                || !attestation.userPresence()
                || !attestation.userVerified()
                || attestation.aaguid() == null || attestation.aaguid().isBlank()
                || attestation.trustAnchorSha256Hex() == null
                || !attestation.trustAnchorSha256Hex().matches("[0-9a-f]{64}")) {
            throw denied();
        }
        // Constant-time compare is a defense in depth; fingerprint presence
        // alone NEVER validates a chain or revocation status.
        byte[] supplied = java.util.HexFormat.of()
                .parseHex(attestation.trustAnchorSha256Hex());
        boolean matched = false;
        for (String root : approvedRootFingerprints) {
            matched |= MessageDigest.isEqual(supplied,
                    java.util.HexFormat.of().parseHex(root));
        }
        if (!matched) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Attestation source is not trusted");
    }
}
