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

    // Apache-2.0 upstream WebAuthn4J's own signed FIDO2 regression vectors:
    // webauthn4j-core/src/test/java/com/webauthn4j/WebAuthnManagerTest.java.
    // These are OFFLINE synthetic localhost test values, never an enrollment
    // into Lanveriq or real production credentials.
    private static final String UPSTREAM_REGISTRATION = "{\"authenticatorAttachment\":\"cross-platform\",\"clientExtensionResults\":{},\"id\":\"1a_exOrrbfy4tDQtwyJo_JVAumhCHXfc2PiDpPTqlxM\",\"rawId\":\"1a_exOrrbfy4tDQtwyJo_JVAumhCHXfc2PiDpPTqlxM\",\"response\":{\"attestationObject\":\"o2NmbXRmcGFja2VkZ2F0dFN0bXSjY2FsZyZjc2lnWEcwRQIgdwS7FNbN3LRg0AQRfDoEqB3dbBhzuUcGhlH6kfXz4eoCIQCzCWskxcpYWa-T1NNUGWv2Pz9PJytnXQSOZN7Z6QpU9GN4NWOBWQHZMIIB1TCCAXqgAwIBAgIBATAKBggqhkjOPQQDAjBgMQswCQYDVQQGEwJVUzERMA8GA1UECgwIQ2hyb21pdW0xIjAgBgNVBAsMGUF1dGhlbnRpY2F0b3IgQXR0ZXN0YXRpb24xGjAYBgNVBAMMEUJhdGNoIENlcnRpZmljYXRlMB4XDTE3MDcxNDAyNDAwMFoXDTQ1MDMwODEyNDQwM1owYDELMAkGA1UEBhMCVVMxETAPBgNVBAoMCENocm9taXVtMSIwIAYDVQQLDBlBdXRoZW50aWNhdG9yIEF0dGVzdGF0aW9uMRowGAYDVQQDDBFCYXRjaCBDZXJ0aWZpY2F0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABI1hfmXJUI5kvMVnOsgqZ5naPBRGaCwljEY__99Y39L6Pmw3i1PXlcSk3_tBme3Xhi8jq68CA7S4kRugVpmU4QGjJTAjMAwGA1UdEwEB_wQCMAAwEwYLKwYBBAGC5RwCAQEEBAMCBSAwCgYIKoZIzj0EAwIDSQAwRgIhAJY79PwboVL1bAPx3L7FTthAceD0XPofn7PzdQo2_zOrAiEApqQqSRUDK4zxcGI9LIUJ__3_9dx3HKPOsGZnQpiviNpoYXV0aERhdGFYpEmWDeWIDoxodDQXD2R2YFuP5K65ooYyx5lc87qDHZdjRQAAAAEBAgMEBQYHCAECAwQFBgcIACDVr97E6utt_Li0NC3DImj8lUC6aEIdd9zY-IOk9OqXE6UBAgMmIAEhWCC5L7pslGp6939rlywpAsF6nsC7AFCyHEa_NtZBX2xGsSJYIPn4dDkPWgF8-8QxTn7cKVHGjWWJ4Z558DO2sqw9mwOM\",\"authenticatorData\":\"SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NFAAAAAQECAwQFBgcIAQIDBAUGBwgAINWv3sTq6238uLQ0LcMiaPyVQLpoQh133Nj4g6T06pcTpQECAyYgASFYILkvumyUanr3f2uXLCkCwXqewLsAULIcRr821kFfbEaxIlgg-fh0OQ9aAXz7xDFOftwpUcaNZYnhnnnwM7ayrD2bA4w\",\"clientDataJSON\":\"eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiUHlad3lFX1hSYjI1UHM1WHViWmZGUSIsIm9yaWdpbiI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImNyb3NzT3JpZ2luIjpmYWxzZX0\",\"publicKey\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuS-6bJRqevd_a5csKQLBep7AuwBQshxGvzbWQV9sRrH5-HQ5D1oBfPvEMU5-3ClRxo1lieGeefAztrKsPZsDjA\",\"publicKeyAlgorithm\":-7,\"transports\":[\"usb\"]},\"type\":\"public-key\"}\n";
    private static final String UPSTREAM_ASSERTION = "{\"authenticatorAttachment\":\"cross-platform\",\"clientExtensionResults\":{},\"id\":\"1a_exOrrbfy4tDQtwyJo_JVAumhCHXfc2PiDpPTqlxM\",\"rawId\":\"1a_exOrrbfy4tDQtwyJo_JVAumhCHXfc2PiDpPTqlxM\",\"response\":{\"authenticatorData\":\"SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAg\",\"clientDataJSON\":\"eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiUHlad3lFX1hSYjI1UHM1WHViWmZGUSIsIm9yaWdpbiI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MCIsImNyb3NzT3JpZ2luIjpmYWxzZX0\",\"signature\":\"MEUCIQC11ymTM1B0WNHdxgFWxCTnrILBRoLkFMFnusjldBd0yAIgEqwhnPvuIDZczbbxO4BRXNgvX-86PsOMwVpovmhsLFY\",\"userHandle\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"},\"type\":\"public-key\"}";

    @Test
    void officialWebAuthn4JVerifiesRealSignedUpstreamFixtureButNotLanveriqOrigin() {
        var manager = WebAuthnManager.createNonStrictWebAuthnManager();
        var registration = manager.parseRegistrationResponseJSON(UPSTREAM_REGISTRATION);
        var authenticator = new com.webauthn4j.credential.CredentialRecordImpl(
                registration.getAttestationObject(),
                registration.getCollectedClientData(),
                registration.getClientExtensions(),
                registration.getTransports());
        var signedAssertion = manager.parseAuthenticationResponseJSON(UPSTREAM_ASSERTION);
        var expectedChallenge = signedAssertion.getCollectedClientData().getChallenge();

        // The historical upstream fixture is bound to localhost; this
        // ONLY proves the maintained library checks its real signed bytes.
        var localhost = com.webauthn4j.server.ServerProperty.builder()
                .origin(new com.webauthn4j.data.client.Origin("http://localhost:8080"))
                .rpId("localhost").challenge(expectedChallenge).build();
        manager.verify(signedAssertion, new com.webauthn4j.data.AuthenticationParameters(
                localhost, authenticator, java.util.List.of(signedAssertion.getCredentialId()),
                true, true));

        // The same *validly signed* fixture is invalid for Lanveriq's
        // fixed https origin/RP ID; a caller cannot override the origin
        // with forged request headers or its own clientData JSON.
        var bridge = new StagedWebAuthn4JAssertionVerifier(manager,
                (actor, id) -> Optional.of(
                        new StagedWebAuthn4JAssertionVerifier.RegisteredCredential(
                                actor, id, signedAssertion.getUserHandle(),
                                "console.lanveriq.internal",
                                authenticator.getCounter(), authenticator, false)));
        var bound = request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(expectedChallenge.getValue()),
                UPSTREAM_ASSERTION.getBytes(StandardCharsets.UTF_8));
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(bound));
    }

    @Test
    void registrationTimeCounterCannotReplaceLatestPersistedCounter() {
        var manager = mock(WebAuthnManager.class);
        var registration = WebAuthnManager.createNonStrictWebAuthnManager()
                .parseRegistrationResponseJSON(UPSTREAM_REGISTRATION);
        var staleAuthenticator = new com.webauthn4j.credential.CredentialRecordImpl(
                registration.getAttestationObject(),
                registration.getCollectedClientData(),
                registration.getClientExtensions(),
                registration.getTransports());
        var parsed = WebAuthnManager.createNonStrictWebAuthnManager()
                .parseAuthenticationResponseJSON(UPSTREAM_ASSERTION);
        when(manager.parseAuthenticationResponseJSON(UPSTREAM_ASSERTION)).thenReturn(parsed);
        var bridge = new StagedWebAuthn4JAssertionVerifier(manager,
                (actor, id) -> Optional.of(
                        new StagedWebAuthn4JAssertionVerifier.RegisteredCredential(
                                actor, id, parsed.getUserHandle(), "console.lanveriq.internal",
                                staleAuthenticator.getCounter() + 1, staleAuthenticator, false)));
        assertThrows(AccessDeniedException.class, () -> bridge.inspect(
                request(FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(
                                parsed.getCollectedClientData().getChallenge().getValue()),
                        UPSTREAM_ASSERTION.getBytes(StandardCharsets.UTF_8))));
        // A stale registration-time record may not enter cryptographic
        // verification with a misleading older counter.
        verify(manager, never()).verify(any(AuthenticationData.class),
                any(com.webauthn4j.data.AuthenticationParameters.class));
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
