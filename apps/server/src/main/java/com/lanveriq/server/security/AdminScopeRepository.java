package com.lanveriq.server.security;

import java.util.UUID;

interface AdminScopeRepository {
    boolean hasCurrentGrant(UUID authenticatedAdminId, UUID deviceGroupId, AdminAction action);
}
