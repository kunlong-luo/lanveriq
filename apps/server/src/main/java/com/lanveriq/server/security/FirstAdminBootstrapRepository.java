package com.lanveriq.server.security;

import java.util.UUID;

/** Executed only through the explicit offline CLI bootstrap, never via HTTP. */
interface FirstAdminBootstrapRepository {
    BootstrappedAdmin createFirstOwner(String normalizedLogin, String passwordHash, String groupName);

    record BootstrappedAdmin(UUID userId, UUID groupId) {
    }
}
