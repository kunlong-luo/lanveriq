package com.lanveriq.server.enrollment;

import java.util.UUID;

/**
 * Independent A11-D2-C cryptographic assertion boundary. Its ONLY production
 * implementation rejects everything. Never accept a boolean "mfa_verified",
 * a consumed database challenge, possession of a nonce, or a password session
 * as proof of user-verifying WebAuthn authentication.
 *
 * Future implementation MUST check signed challenge, UV flag, HTTPS RP ID and
 * origin, credential ownership/current revocation/counter, and authenticator
 * signature with a mature official Spring Security/WebAuthn4J verifier.
 */
interface FirstPinWebAuthnAssertionVerifier {
    record BoundAssertion(
            UUID actorId, UUID reviewRequestId, UUID deviceId,
            long sessionVersion, String action, String challengeBase64Url,
            byte[] untrustedAssertion) {}

    void verify(BoundAssertion assertion);
}
