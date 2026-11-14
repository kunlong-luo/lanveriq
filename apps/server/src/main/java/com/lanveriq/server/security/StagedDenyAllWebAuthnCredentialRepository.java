package com.lanveriq.server.security;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * A11-D2-B official Spring Security credential contract, deliberately
 * fail-closed until a real cryptographically verified WebAuthn4J ceremony
 * and complete CredentialRecord persistence are independently accepted.
 *
 * V16's synthetic COSE bytes and minimal inventory rows cannot be converted
 * into verified CredentialRecord objects. Even an existing V16 credential
 * MUST NOT enable login, MFA step-up or certificate issuance.
 * This is deliberately neither @Component nor @Bean.
 */
final class StagedDenyAllWebAuthnCredentialRepository implements UserCredentialRepository {
    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return null;
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return List.of();
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        throw new AccessDeniedException("Verified WebAuthn credential lifecycle unavailable");
    }

    @Override
    public void delete(Bytes credentialId) {
        throw new AccessDeniedException("Verified WebAuthn credential lifecycle unavailable");
    }
}
