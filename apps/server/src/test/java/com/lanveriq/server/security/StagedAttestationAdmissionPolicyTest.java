package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StagedAttestationAdmissionPolicyTest {
    private static final String ROOT = "a".repeat(64);
    private static final String OTHER_ROOT = "b".repeat(64);

    private StagedAttestationAdmissionPolicy.VerifiedAttestation evidence(
            StagedAttestationAdmissionPolicy.AttestationKind kind, String root,
            boolean chain, boolean metadata, boolean revocation,
            boolean up, boolean uv) {
        return new StagedAttestationAdmissionPolicy.VerifiedAttestation(
                kind, root, "synthetic-aaguid", chain, metadata, revocation, up, uv);
    }

    @Test
    void noConfiguredTrustRootAlwaysDeniesEvenSyntheticVerifiedEvidence() {
        var policy = new StagedAttestationAdmissionPolicy(Set.of());
        assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(
                evidence(StagedAttestationAdmissionPolicy.AttestationKind.BASIC,
                        ROOT, true, true, true, true, true)));
    }

    @Test
    void noneAndSelfAttestationCannotBePromotedEvenWithApprovedRoot() {
        var policy = new StagedAttestationAdmissionPolicy(Set.of(ROOT));
        for (var kind : new StagedAttestationAdmissionPolicy.AttestationKind[] {
                StagedAttestationAdmissionPolicy.AttestationKind.NONE,
                StagedAttestationAdmissionPolicy.AttestationKind.SELF}) {
            assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(
                    evidence(kind, ROOT, true, true, true, true, true)));
        }
    }

    @Test
    void unknownRootAndMissingChainMetadataRevocationUpUvAreDenied() {
        var policy = new StagedAttestationAdmissionPolicy(Set.of(ROOT));
        assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(null));
        assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(
                evidence(StagedAttestationAdmissionPolicy.AttestationKind.BASIC,
                        OTHER_ROOT, true, true, true, true, true)));
        for (int missing = 0; missing < 5; missing++) {
            int i = missing;
            assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(
                    evidence(StagedAttestationAdmissionPolicy.AttestationKind.BASIC,
                            ROOT, i != 0, i != 1, i != 2, i != 3, i != 4)));
        }
    }

    @Test
    void malformedRootConfigurationAndEvidenceAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StagedAttestationAdmissionPolicy(Set.of("invalid")));
        var policy = new StagedAttestationAdmissionPolicy(Set.of(ROOT));
        assertThrows(AccessDeniedException.class, () -> policy.requireTrustedSource(
                evidence(StagedAttestationAdmissionPolicy.AttestationKind.BASIC,
                        "not-hex", true, true, true, true, true)));
    }

    @Test
    void syntheticCompleteEvidencePassesOnlyThePolicyShapeNotCryptography() {
        var policy = new StagedAttestationAdmissionPolicy(Set.of(ROOT));
        // This is an isolated policy-shape test, NOT a trusted WebAuthn4J
        // attestation or permission to persist credentials.
        assertDoesNotThrow(() -> policy.requireTrustedSource(
                evidence(StagedAttestationAdmissionPolicy.AttestationKind.BASIC,
                        ROOT, true, true, true, true, true)));
    }
}
