package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceDirectoryListQueryTest {
    @Test
    void defaultContractIsUuidAscendingOnlyAndUnfiltered() {
        var query = DeviceDirectoryListQuery.unfiltered();
        assertFalse(query.hasFilters());
        assertEquals("device_id_asc", query.sort());
        assertEquals(null, query.groupId());
        assertEquals(null, query.labelPrefix());
    }

    @Test
    void rejectsNilGroupAndGlobalSortThatCannotUseUuidKeyset() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceDirectoryListQuery(new UUID(0, 0), null, "device_id_asc"));
        for (String order : new String[]{"", "label_asc", "group_asc", "device_id_desc"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DeviceDirectoryListQuery(null, null, order));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceDirectoryListQuery(null, null, null));
    }

    @Test
    void boundsPrefixAndRejectsControlsWithoutDamagingValidUnicode() {
        for (String prefix : new String[]{"  ", "A".repeat(65), "abc\n", "\u0000"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DeviceDirectoryListQuery(null, prefix, "device_id_asc"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceDirectoryListQuery(null, "\uD800", "device_id_asc"));
        var valid = new DeviceDirectoryListQuery(UUID.randomUUID(),
                "  研发机器🚀  ", "device_id_asc");
        assertTrue(valid.hasFilters());
        assertEquals("研发机器🚀", valid.labelPrefix());
        assertEquals("研发机器🚀%", valid.labelPattern());
    }

    @Test
    void escapesLikeWildcardsAndEscapeCharacterAsLiteralPrefix() {
        var q = new DeviceDirectoryListQuery(null, "host%_!x", "device_id_asc");
        assertEquals("host!%!_!!x%", q.labelPattern());
        assertEquals("device_id_asc", q.sort());
    }
}
