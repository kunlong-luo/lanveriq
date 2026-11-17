package com.lanveriq.server.security;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.verifier.attestation.statement.packed.PackedAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import java.security.cert.TrustAnchor;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

/**
 * D4-B2 internal strict WebAuthn4J registration manager, never a Spring bean.
 * No organizational attestation root or metadata source has been approved.
 * Therefore this factory ALWAYS returns an untrusted-root, fail-closed
 * verifier. It MUST NOT be used to grant enrollment, authentication or MFA.
 *
 * For high-privilege registrations, disallow unsupported and no-op statement
 * verifiers, none/self attestation, and unchecked certificate revocation.
 * Future real trust-anchor admission requires a separate reviewed workflow.
 */
final class StagedStrictAttestationWebAuthn4JFactory {
    private StagedStrictAttestationWebAuthn4JFactory() {}

    private static final TrustAnchorRepository NO_APPROVED_ROOTS =
            new TrustAnchorRepository() {
                @Override
                public Set<TrustAnchor> find(AAGUID aaguid) {
                    return Set.of();
                }

                @Override
                public Set<TrustAnchor> find(byte[] attestationCertificateKeyIdentifier) {
                    return Set.of();
                }
            };

    static DefaultCertPathTrustworthinessVerifier strictCertPathVerifier() {
        var verifier = new DefaultCertPathTrustworthinessVerifier(NO_APPROVED_ROOTS);
        // A root would still not be sufficient: PKIX must have fresh and
        // reliable certificate status information. No CA or CRL/OCSP
        // integration has been approved, so roots remain empty.
        verifier.setRevocationCheckEnabled(true);
        verifier.setFullChainProhibited(true);
        return verifier;
    }

    static WebAuthnManager createDenyAllUntilAnchorsApproved() {
        return new WebAuthnManager(
                List.of(new PackedAttestationStatementVerifier()),
                strictCertPathVerifier(),
                statement -> {
                    throw new AccessDeniedException("Self attestation is not trusted");
                });
    }
}
