package com.lanveriq.server.events;

import com.lanveriq.server.security.AdminPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default OFF, internal console-admin-only revocation; no HTTP controller.
 * Must be enabled only after the organizational and Caddy/DB acceptance gates.
 * Never accepts a client-supplied admin UUID or raw bearer credential.
 */
@Service
@ConditionalOnProperty(prefix = "lanveriq.device-revocation",
        name = "enabled", havingValue = "true")
public class DeviceRevocationService {
    private final DeviceRevocationRepository repository;

    DeviceRevocationService(DeviceRevocationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional
    public boolean revoke(UUID deviceId) {
        if (deviceId == null || deviceId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("Canonical device ID required");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled()) {
            throw denied();
        }
        return repository.revoke(admin.userId(), deviceId);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Device revocation is not authorized");
    }
}
