package com.lanveriq.server.enrollment;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Production fail-closed default. Never replace this with a synthetic/local
 * bypass: a future WebAuthn/IdP verifier requires separate security review,
 * hardware-backed credential binding, challenge replay protection and L2/L3.
 */
@Component
final class DenyAllFirstPinReviewStepUpVerifier implements FirstPinReviewStepUpVerifier {
    @Override
    public VerifiedEvidence verify(Challenge challenge) {
        throw new AccessDeniedException("Trusted first-pin reviewer reauthentication unavailable");
    }
}
