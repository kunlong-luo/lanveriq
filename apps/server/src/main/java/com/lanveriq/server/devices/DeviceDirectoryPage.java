package com.lanveriq.server.devices;

import java.util.List;
import java.util.UUID;

/** Keyset cursor is the final visible device ID, not a bearer secret. */
public record DeviceDirectoryPage(List<DeviceDirectoryEntry> items, UUID nextCursor) {
    public DeviceDirectoryPage {
        items = List.copyOf(items);
    }
}
