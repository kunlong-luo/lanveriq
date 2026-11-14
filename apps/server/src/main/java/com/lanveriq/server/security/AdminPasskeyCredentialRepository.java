package com.lanveriq.server.security;

import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Persistent inventory only. No unverified row is an authentication result.
 * A future WebAuthn4J adapter must verify challenge, UV, origin and RP ID
 * before using an assertion, and re-check account disable/session revocation.
 */
interface AdminPasskeyCredentialRepository
        extends Repository<AdminPasskeyCredential, byte[]> {
    List<AdminPasskeyCredential> findByUserId(java.util.UUID userId);
}
