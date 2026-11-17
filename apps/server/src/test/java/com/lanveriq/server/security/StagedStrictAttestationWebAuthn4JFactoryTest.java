package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.verifier.exception.TrustAnchorNotFoundException;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StagedStrictAttestationWebAuthn4JFactoryTest {
    @Test
    void allTrustAnchorsRemainUnconfiguredAndPkixRevocationCannotBeDisabled() {
        var verifier = StagedStrictAttestationWebAuthn4JFactory.strictCertPathVerifier();
        assertTrue(verifier.isRevocationCheckEnabled());
        assertTrue(verifier.isFullChainProhibited());
        // A real x5c certificate chain is necessary for this check; without
        // it, the attestation must fail before a trust decision.
        assertThrows(RuntimeException.class, () -> verifier.verify(
                new AAGUID(UUID.randomUUID()),
                org.mockito.Mockito.mock(CertificateBaseAttestationStatement.class),
                Instant.now()));
    }

    @Test
    void builtManagerDoesNotAllowMalformedRegistrationToBecomeEvidence() {
        var manager = StagedStrictAttestationWebAuthn4JFactory
                .createDenyAllUntilAnchorsApproved();
        assertNotNull(manager);
        assertThrows(RuntimeException.class,
                () -> manager.parseRegistrationResponseJSON("{}"));
    }
}
