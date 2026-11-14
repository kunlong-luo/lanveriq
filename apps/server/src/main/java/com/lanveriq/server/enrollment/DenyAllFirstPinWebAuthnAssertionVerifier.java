package com.lanveriq.server.enrollment;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Production fail-closed verifier. Merely issuing or consuming a challenge
 * never authorizes a reviewer vote. No authenticator ceremony is implemented.
 */
@Component
final class DenyAllFirstPinWebAuthnAssertionVerifier
        implements FirstPinWebAuthnAssertionVerifier {
    @Override
    public void verify(BoundAssertion assertion) {
        throw new AccessDeniedException("Verified WebAuthn reviewer assertion unavailable");
    }
}
