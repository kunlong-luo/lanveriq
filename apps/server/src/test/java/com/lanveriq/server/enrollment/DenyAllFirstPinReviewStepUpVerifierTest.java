package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** No real passkey provider has been integrated. Production is hard-deny. */
class DenyAllFirstPinReviewStepUpVerifierTest {
    @Test
    void validLookingChallengeStillCannotProduceAnyProductionEvidence() {
        var challenge = new FirstPinReviewStepUpVerifier.Challenge(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION);
        assertThrows(AccessDeniedException.class,
                () -> new DenyAllFirstPinReviewStepUpVerifier().verify(challenge));
    }

    @Test
    void absentOrForgedChallengeCannotBypassHardDenial() {
        assertThrows(AccessDeniedException.class,
                () -> new DenyAllFirstPinReviewStepUpVerifier().verify(null));
    }
}
