package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DenyAllFirstPinWebAuthnAssertionVerifierTest {
    @Test
    void noClientAssertionOrInternalSyntheticNonceCanBypassProductionVerifier() {
        var verifier = new DenyAllFirstPinWebAuthnAssertionVerifier();
        for (var payload : new byte[][]{
                new byte[0], new byte[]{1, 2, 3},
                new byte[256]}) {
            var assertion = new FirstPinWebAuthnAssertionVerifier.BoundAssertion(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    0L, FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                    "synthetic-unverified-nonce", payload);
            assertThrows(AccessDeniedException.class, () -> verifier.verify(assertion));
        }
        assertThrows(AccessDeniedException.class, () -> verifier.verify(null));
    }
}
