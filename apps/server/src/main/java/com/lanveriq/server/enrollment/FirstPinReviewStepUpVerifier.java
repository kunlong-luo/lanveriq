package com.lanveriq.server.enrollment;

import java.time.Instant;
import java.util.UUID;

/**
 * A11-C verification boundary for independent, phishing-resistant reviewer
 * reauthentication. There is intentionally NO production WebAuthn/passkey or
 * IdP implementation yet. Never accept Boolean "mfa_verified", browser-provided
 * evidence IDs, timestamps, HTTP headers or cached ROLE claims as proof.
 *
 * The verifier must bind the authenticated principal, exact review action,
 * request, device and current session version to a fresh, single-use proof.
 * The return value is diagnostic evidence, NEVER permission to issue a
 * certificate or authorize an Agent pin. Test fakes live in test source only.
 */
interface FirstPinReviewStepUpVerifier {
    String REVIEW_ACTION = "first_pin.review";

    record Challenge(UUID actorId, UUID requestId, UUID deviceId,
                     long sessionVersion, String action) {}

    record VerifiedEvidence(
            UUID proofId, UUID actorId, UUID requestId, UUID deviceId,
            long sessionVersion, String action, Instant verifiedAt,
            String method) {}

    VerifiedEvidence verify(Challenge challenge);
}
