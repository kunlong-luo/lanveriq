package com.lanveriq.server.devices;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Authorization is part of every SQL query, not a UI-side post-filter. */
interface DeviceDirectoryRepository {
    List<DeviceDirectoryEntry> list(UUID authenticatedAdminId, UUID afterDeviceId,
                                    int maxRows, DeviceDirectoryListQuery query);

    /** Compatibility helper: still uses exactly the same scope-checked query. */
    default List<DeviceDirectoryEntry> list(
            UUID authenticatedAdminId, UUID afterDeviceId, int maxRows) {
        return list(authenticatedAdminId, afterDeviceId,
                maxRows, DeviceDirectoryListQuery.unfiltered());
    }

    Optional<DeviceDirectoryEntry> find(UUID authenticatedAdminId, UUID deviceId);
}
