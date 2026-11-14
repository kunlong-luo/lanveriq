package com.lanveriq.server.security;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A11-D2-B isolated read-only adapter for the OFFICIAL Spring Security
 * WebAuthn user-entity contract, backed by Flyway V16 + Spring Data JPA.
 *
 * NOT a @Component/@Bean, so it cannot silently enable a passkey endpoint or
 * replace Spring Security's current default. Never use it to enroll a user:
 * saving/deleting a WebAuthn identity requires a separately authorized,
 * audited, cryptographically verified lifecycle not implemented yet.
 */
final class StagedJpaWebAuthnUserEntityRepository
        implements PublicKeyCredentialUserEntityRepository {

    private final AdminPasskeyUserHandleRepository handles;
    private final AdminAccountRepository accounts;

    StagedJpaWebAuthnUserEntityRepository(
            AdminPasskeyUserHandleRepository handles, AdminAccountRepository accounts) {
        this.handles = Objects.requireNonNull(handles);
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        if (id == null || id.getBytes().length != 32) {
            return null;
        }
        return handles.findByUserHandle(id.getBytes())
                .flatMap(handle -> accounts.findById(handle.userId())
                        .filter(AdminAccount::isEnabled)
                        .map(account -> entity(handle, account)))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return accounts.findByNormalizedLogin(username)
                .filter(AdminAccount::isEnabled)
                .flatMap(account -> handles.findById(account.getId())
                        .map(handle -> entity(handle, account)))
                .orElse(null);
    }

    private static PublicKeyCredentialUserEntity entity(
            AdminPasskeyUserHandle handle, AdminAccount account) {
        var raw = handle.userHandle();
        if (raw == null || raw.length != 32) {
            throw new AccessDeniedException("Corrupt WebAuthn user handle");
        }
        // Name is for display only. The 32-byte server-stored handle, not
        // a client name or login, is the WebAuthn authorization identifier.
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(raw))
                .name(account.getNormalizedLogin())
                .displayName(account.getNormalizedLogin())
                .build();
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        throw new AccessDeniedException("WebAuthn enrollment not authorized");
    }

    @Override
    public void delete(Bytes id) {
        throw new AccessDeniedException("WebAuthn identity deletion not authorized");
    }
}
