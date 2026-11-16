package com.lanveriq.server.enrollment;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D2-D2: internal-only native WebAuthn4J assertion-verification boundary.
 *
 * Not a Spring bean, not a FirstPinReviewStepUpVerifier, not a login or signing
 * endpoint. No production TrustedCredentialSource is implemented or wired:
 * arbitrary V16/V18 staging rows must NEVER be promoted to trusted records.
 * Only an independently verified and audited registration ceremony can
 * create a source backed by known authenticator keys and CURRENT signCount.
 *
 * This component relies on WebAuthn4J for parsing and cryptographic checking.
 * Do not substitute a JDK Signature implementation or trust client-supplied
 * RP, origin, userHandle, credential ID, counters, or "verified" flags.
 */
final class StagedWebAuthn4JAssertionVerifier {
    private static final String RP_ID = "console.lanveriq.internal";
    private static final String ORIGIN = "https://" + RP_ID;
    private static final String REVIEW_ACTION = FirstPinReviewStepUpVerifier.REVIEW_ACTION;

    interface TrustedCredentialSource {
        Optional<RegisteredCredential> lookup(UUID currentlyAuthenticatedActor,
                byte[] parsedCredentialId);
    }

    record RegisteredCredential(
            UUID actorId, byte[] credentialId, byte[] userHandle, String rpId,
            long currentStoredCounter, CredentialRecord webAuthn4JRecord,
            boolean revoked) {
        RegisteredCredential {
            credentialId = credentialId == null ? null : credentialId.clone();
            userHandle = userHandle == null ? null : userHandle.clone();
        }

        @Override
        public byte[] credentialId() {
            return credentialId == null ? null : credentialId.clone();
        }

        @Override
        public byte[] userHandle() {
            return userHandle == null ? null : userHandle.clone();
        }
    }

    /** Only a diagnostic after WebAuthn4J verification, NEVER a review proof. */
    record CheckedAssertion(UUID actorId, byte[] credentialId,
            long expectedStoredCounter, long authenticatorCounter) {
        CheckedAssertion {
            credentialId = credentialId.clone();
        }

        @Override
        public byte[] credentialId() {
            return credentialId.clone();
        }
    }

    private final WebAuthnManager manager;
    private final TrustedCredentialSource trustedCredentials;

    StagedWebAuthn4JAssertionVerifier(
            WebAuthnManager manager, TrustedCredentialSource trustedCredentials) {
        this.manager = Objects.requireNonNull(manager);
        this.trustedCredentials = Objects.requireNonNull(trustedCredentials);
    }

    CheckedAssertion inspect(FirstPinWebAuthnAssertionVerifier.BoundAssertion request) {
        if (request == null || request.actorId() == null
                || request.reviewRequestId() == null || request.deviceId() == null
                || request.sessionVersion() < 0 || !REVIEW_ACTION.equals(request.action())
                || request.untrustedAssertion() == null
                || request.untrustedAssertion().length < 1
                || request.untrustedAssertion().length > 16_384) {
            throw denied();
        }
        byte[] challenge;
        try {
            challenge = Base64.getUrlDecoder().decode(request.challengeBase64Url());
            if (challenge.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(challenge).equals(request.challengeBase64Url())) {
                throw denied();
            }
        } catch (IllegalArgumentException | NullPointerException badChallenge) {
            throw denied();
        }

        try {
            // The library, not our application, parses and validates the
            // standard PublicKeyCredential assertion JSON and its signature.
            String response = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(request.untrustedAssertion())).toString();
            var parsed = manager.parseAuthenticationResponseJSON(response);
            byte[] credentialId = parsed.getCredentialId();
            byte[] userHandle = parsed.getUserHandle();
            if (credentialId == null || credentialId.length < 16
                    || credentialId.length > 1024 || userHandle == null
                    || userHandle.length != 32) {
                throw denied();
            }
            // Future authoritative loader MUST use current PG account status,
            // verified enrollment provenance, and a locked credential row.
            var stored = trustedCredentials.lookup(request.actorId(), credentialId)
                    .orElseThrow(StagedWebAuthn4JAssertionVerifier::denied);
            if (!request.actorId().equals(stored.actorId()) || stored.revoked()
                    || !RP_ID.equals(stored.rpId())
                    || stored.userHandle() == null
                    || !MessageDigest.isEqual(userHandle, stored.userHandle())
                    || !MessageDigest.isEqual(credentialId, stored.credentialId())
                    || stored.currentStoredCounter() < 0
                    || stored.currentStoredCounter() > 4_294_967_295L
                    || stored.webAuthn4JRecord() == null
                    || stored.webAuthn4JRecord().getAttestedCredentialData() == null
                    || !MessageDigest.isEqual(credentialId, stored.webAuthn4JRecord()
                            .getAttestedCredentialData().getCredentialId())
                    || stored.webAuthn4JRecord().getCounter()
                            != stored.currentStoredCounter()) {
                throw denied();
            }

            ServerProperty serverProperty = ServerProperty.builder()
                    .origin(new Origin(ORIGIN))
                    .rpId(RP_ID)
                    .challenge(new DefaultChallenge(challenge))
                    .build(); // No cross-origin iframe, no wildcards, no HTTP.
            var parameters = new AuthenticationParameters(
                    serverProperty, stored.webAuthn4JRecord(),
                    List.of(credentialId), true, true); // UV and UP mandatory.
            manager.verify(parsed, parameters);

            long observedCounter = parsed.getAuthenticatorData().getSignCount();
            if (observedCounter < 0 || observedCounter > 4_294_967_295L
                    || (observedCounter != 0
                        && observedCounter <= stored.currentStoredCounter())
                    || (observedCounter == 0 && stored.currentStoredCounter() != 0)) {
                throw denied();
            }
            // 0/0 has no clone-detection signal, but only the WebAuthn4J
            // signature over the exact server challenge can establish
            // freshness. This result does NOT mutate DB or authorize a vote.
            return new CheckedAssertion(request.actorId(), credentialId,
                    stored.currentStoredCounter(), observedCounter);
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Staged WebAuthn assertion rejected");
    }
}
