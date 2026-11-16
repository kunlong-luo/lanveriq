package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.data.AuthenticationData;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * No test uses a real authenticator or claims successful WebAuthn verification.
 * Mocked parsing only proves that untrusted identity cannot reach a credential
 * record or signature verifier. A complete real FIDO2 test vector is needed
 * before deploying a trusted production registration source.
 */
class StagedWebAuthn4JAssertionVerifierTest {

    private byte[] random(int length) {
        var out = new byte[length];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private FirstPinWebAuthnAssertionVerifier.BoundAssertion request(
            String action, String nonce, byte[] assertion) {
        return new FirstPinWebAuthnAssertionVerifier.BoundAssertion(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                0L, action, nonce, assertion);
    }

    private String nonce() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32));
    }

    @Test
    void malformedOrNoncanonicalChallengeCannotReachWebAuthn4JParser() {
        var manager = mock(WebAuthnManager.class);
        var bridge = new StagedWebAuthn4JAssertionVerifier(manager,
                (actor, id) -> Optional.empty());
        var untrusted = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(
                request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        "wrong", untrusted)));
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(
                request("first_pin.issue", nonce(), untrusted)));
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(
                request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        nonce(), new byte[0])));
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(
                request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        nonce(), new byte[16_385])));
        verify(manager, never()).parseAuthenticationResponseJSON(any(String.class));
    }

    @Test
    void libraryRejectsNonFido2JsonWithoutAnyCredentialSource() {
        var manager = WebAuthnManager.createNonStrictWebAuthnManager();
        var bridge = new StagedWebAuthn4JAssertionVerifier(manager,
                (actor, id) -> {
                    throw new AssertionError("Invalid JSON cannot resolve a credential");
                });
        assertThrows(AccessDeniedException.class, () ->
                bridge.inspect(request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        nonce(), "{not fido2}".getBytes(StandardCharsets.UTF_8))));
        assertThrows(AccessDeniedException.class, () ->
                bridge.inspect(request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        nonce(), new byte[]{(byte) 0xc3, (byte) 0x28})));
    }

    @Test
    void parsedButUnregisteredCredentialCannotReachSignatureVerification() {
        var manager = mock(WebAuthnManager.class);
        var parsed = mock(AuthenticationData.class);
        var id = random(64);
        var handle = random(32);
        when(parsed.getCredentialId()).thenReturn(id);
        when(parsed.getUserHandle()).thenReturn(handle);
        when(manager.parseAuthenticationResponseJSON("{}")).thenReturn(parsed);
        var bridge = new StagedWebAuthn4JAssertionVerifier(manager,
                (actor, requestedId) -> {
                    assertEquals(64, requestedId.length);
                    return Optional.empty();
                });
        assertThrows(AccessDeniedException.class, () ->
                bridge.inspect(request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        nonce(), "{}".getBytes(StandardCharsets.UTF_8))));
        verify(manager, never()).verify(any(AuthenticationData.class),
                any(com.webauthn4j.data.AuthenticationParameters.class));
    }
}
