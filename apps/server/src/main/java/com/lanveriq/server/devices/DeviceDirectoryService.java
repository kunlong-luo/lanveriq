package com.lanveriq.server.devices;

import com.lanveriq.server.security.AdminPrincipal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WEB-01 server-side health-only read foundation, DEFAULT OFF, NO HTTP route.
 * Current DB account/group/role grants are rechecked in every native JPA query.
 */
@Service
@ConditionalOnProperty(prefix = "lanveriq.device-directory",
        name = "enabled", havingValue = "true")
public class DeviceDirectoryService {
    public static final int MAX_PAGE_SIZE = 100;
    private final DeviceDirectoryRepository repository;

    DeviceDirectoryService(DeviceDirectoryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional(readOnly = true)
    public DeviceDirectoryPage list(UUID afterDeviceId, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Device page limit must be between 1 and 100");
        }
        if (new UUID(0, 0).equals(afterDeviceId)) {
            throw new IllegalArgumentException("Invalid cursor");
        }
        UUID actor = requireAdmin();
        var fetched = repository.list(actor, afterDeviceId, limit + 1);
        if (fetched.size() <= limit) {
            return new DeviceDirectoryPage(fetched, null);
        }
        var page = fetched.subList(0, limit);
        return new DeviceDirectoryPage(page, page.getLast().deviceId());
    }

    @Transactional(readOnly = true)
    public Optional<DeviceDirectoryEntry> find(UUID deviceId) {
        if (deviceId == null || deviceId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("Canonical device ID required");
        }
        return repository.find(requireAdmin(), deviceId);
    }

    static UUID requireAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled()) {
            throw new AccessDeniedException("Authenticated administrator required");
        }
        return admin.userId();
    }
}
