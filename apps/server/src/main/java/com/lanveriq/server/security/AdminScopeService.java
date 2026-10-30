package com.lanveriq.server.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminScopeService {

    private final AdminScopeRepository repository;

    AdminScopeService(AdminScopeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Transactional(readOnly = true)
    public boolean allowed(UUID authenticatedAdminId, UUID deviceGroupId, AdminAction action) {
        return repository.hasCurrentGrant(authenticatedAdminId, deviceGroupId, action);
    }

    @Transactional(readOnly = true)
    public void require(UUID authenticatedAdminId, UUID deviceGroupId, AdminAction action) {
        if (!repository.hasCurrentGrant(authenticatedAdminId, deviceGroupId, action)) {
            throw new AccessDeniedException("Administrator not authorized for requested scope");
        }
    }
}
