package com.lanveriq.server.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Flyway-owned read-only mapping; no passkey enrollment, credential writes,
 * authentication or MFA approval consumer is connected to this repository.
 * Never substitute in-memory state for this durable user.id identity.
 * Repository<T,ID> deliberately exposes only reads; no inherited save/delete.
 */
interface AdminPasskeyUserHandleRepository
        extends Repository<AdminPasskeyUserHandle, UUID> {
    Optional<AdminPasskeyUserHandle> findByUserHandle(byte[] userHandle);
}
