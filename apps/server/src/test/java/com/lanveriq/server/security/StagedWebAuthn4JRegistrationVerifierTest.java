package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.webauthn4j.WebAuthnManager;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * L1 fail-before-cryptography negative coverage for the unregistered,
 * non-Bean WebAuthn4J registration verifier. Never grants trusted credentials.
 */
class StagedWebAuthn4JRegistrationVerifierTest {
    private final WebAuthnManager manager = mock(WebAuthnManager.class);
    private final StagedWebAuthn4JRegistrationVerifier verifier =
            new StagedWebAuthn4JRegistrationVerifier(manager);
    private final UUID actor = UUID.randomUUID();
    private final String nonce = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    private StagedPasskeyRegistrationChallengeLedger.BoundRegistration bound(
            String challenge, byte[] attestation) {
        return new StagedPasskeyRegistrationChallengeLedger.BoundRegistration(
                actor, 0L, challenge, attestation);
    }

    @Test
    void rejectsMissingIdentityAndEmptyOrOversizedAttestationBeforeParsing() {
        assertThrows(AccessDeniedException.class, () -> verifier.verify(null));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(new StagedPasskeyRegistrationChallengeLedger
                        .BoundRegistration(null, 0L, nonce, "{}".getBytes(StandardCharsets.UTF_8))));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(nonce, null)));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(nonce, new byte[0])));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(nonce, new byte[32769])));
        verifyNoInteractions(manager);
    }

    @Test
    void rejectsNegativeActorSessionVersionBeforeParsing() {
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(new StagedPasskeyRegistrationChallengeLedger.BoundRegistration(
                        actor, -1L, nonce, "{}".getBytes(StandardCharsets.UTF_8))));
        verifyNoInteractions(manager);
    }

    @Test
    void rejectsNoncanonicalOrWrongLengthNonceBeforeParsing() {
        byte[] fixture = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(null, fixture)));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound("", fixture)));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound("!" + nonce, fixture)));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(nonce + "=", fixture)));
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(new byte[31]), fixture)));
        verifyNoInteractions(manager);
    }

    @Test
    void rejectsMalformedUtf8BeforeWebAuthn4jParsing() {
        assertThrows(AccessDeniedException.class,
                () -> verifier.verify(bound(nonce, new byte[] {(byte) 0xc3, (byte) 0x28})));
        verifyNoInteractions(manager);
    }
}
