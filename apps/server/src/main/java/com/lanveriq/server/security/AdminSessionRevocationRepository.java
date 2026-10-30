package com.lanveriq.server.security;

import java.util.UUID;

/** Internal, transactional group-scoped session epoch rotation. */
interface AdminSessionRevocationRepository {
    long rotateAllSessions(UUID authenticatedOwnerId, UUID targetUserId, UUID deviceGroupId);
}
