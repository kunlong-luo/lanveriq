package com.lanveriq.server.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Limited read-only repository: deliberately no inherited save/delete, no
 * Spring Security CredentialRecord or WebAuthn login interface.
 */
interface AdminPasskeyCredentialRecordStagingRepository
        extends Repository<AdminPasskeyCredentialRecordStaging, byte[]> {
    Optional<AdminPasskeyCredentialRecordStaging> findById(byte[] credentialId);
    List<AdminPasskeyCredentialRecordStaging> findByUserId(UUID userId);
}
