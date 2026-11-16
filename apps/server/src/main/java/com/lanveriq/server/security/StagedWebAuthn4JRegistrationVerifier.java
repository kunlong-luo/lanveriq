package com.lanveriq.server.security;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D2-D3 official WebAuthn4J registration verification boundary.
 * NOT a Bean, route, issuer, trusted registration repository or admin MFA.
 * It returns no credential for persistence or authentication.
 *
 * Only passing a WebAuthn4J registration ceremony with a SERVER challenge,
 * fixed HTTPS origin/RP and UV/UP is considered for a future audited
 * enrollment. Attestation trust policy, registration provenance, and a
 * production trusted credential source remain BLOCKED.
 */
final class StagedWebAuthn4JRegistrationVerifier
        implements StagedPasskeyRegistrationChallengeLedger.RegistrationProofVerifier {
    private static final String RP = "console.lanveriq.internal";
    private static final String ORIGIN = "https://" + RP;
    private final WebAuthnManager manager;

    StagedWebAuthn4JRegistrationVerifier(WebAuthnManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public void verify(StagedPasskeyRegistrationChallengeLedger.BoundRegistration input) {
        if (input == null || input.actorId() == null || input.challengeBase64Url() == null
                || input.untrustedAttestation() == null
                || input.untrustedAttestation().length < 1
                || input.untrustedAttestation().length > 32768) {
            throw denied();
        }
        try {
            byte[] challenge = Base64.getUrlDecoder().decode(input.challengeBase64Url());
            if (challenge.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(challenge).equals(input.challengeBase64Url())) {
                throw denied();
            }
            String response = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(input.untrustedAttestation())).toString();
            var parsed = manager.parseRegistrationResponseJSON(response);
            var params = new RegistrationParameters(
                    ServerProperty.builder().origin(new Origin(ORIGIN)).rpId(RP)
                            .challenge(new DefaultChallenge(challenge)).build(),
                    List.of(new PublicKeyCredentialParameters(
                            PublicKeyCredentialType.PUBLIC_KEY,
                            COSEAlgorithmIdentifier.ES256)),
                    true, true);
            manager.verify(parsed, params);
            // Intentionally NO CredentialRecordImpl, repository.save(), user
            // grants, review evidence or issued certificate. Successful
            // verification is NOT a trusted attestation provenance policy.
        } catch (CharacterCodingException | RuntimeException rejected) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted Passkey registration rejected");
    }
}
