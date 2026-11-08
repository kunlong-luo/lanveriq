package com.lanveriq.server.devices;

import java.util.UUID;

/**
 * NXT-20 additive, default-OFF device-directory query contract.
 *
 * The sort order is deliberately pinned to canonical UUID ascending: a
 * UUID cursor is NOT sufficient for globally sorting by label or group.
 * Filters are an intersection with live database role/group authorization.
 */
public record DeviceDirectoryListQuery(UUID groupId, String labelPrefix, String sort) {
    public static final String UUID_ASC = "device_id_asc";
    public static final int MAX_PREFIX_CODE_POINTS = 64;
    private static final UUID NIL = new UUID(0, 0);

    public DeviceDirectoryListQuery {
        if (NIL.equals(groupId)) {
            throw new IllegalArgumentException("A non-nil device group UUID is required");
        }
        if (!UUID_ASC.equals(sort)) {
            throw new IllegalArgumentException("Only device_id_asc keyset order is supported");
        }
        if (labelPrefix != null) {
            // Inspect raw input BEFORE stripping so control characters cannot
            // be hidden as "whitespace" by normalization.
            for (int i = 0; i < labelPrefix.length(); ) {
                int point = labelPrefix.codePointAt(i);
                if (Character.isISOControl(point) ||
                        (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
                    throw new IllegalArgumentException("Invalid device label prefix character");
                }
                i += Character.charCount(point);
            }
            labelPrefix = labelPrefix.strip();
            if (labelPrefix.isEmpty() ||
                    labelPrefix.codePointCount(0, labelPrefix.length()) > MAX_PREFIX_CODE_POINTS) {
                throw new IllegalArgumentException("Device label prefix must be 1-64 characters");
            }
        }
    }

    public static DeviceDirectoryListQuery unfiltered() {
        return new DeviceDirectoryListQuery(null, null, UUID_ASC);
    }

    public boolean hasFilters() {
        return groupId != null || labelPrefix != null;
    }

    /** PostgreSQL LIKE metacharacters are literal, never wildcards. */
    String labelPattern() {
        if (labelPrefix == null) {
            throw new IllegalStateException("No prefix filter");
        }
        return labelPrefix.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
