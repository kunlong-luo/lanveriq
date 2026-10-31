package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceDirectoryRestControllerTest {
    private final DeviceDirectoryService directory = Mockito.mock(DeviceDirectoryService.class);
    private final DeviceDirectoryRestController controller =
            new DeviceDirectoryRestController(directory);
    private final UUID deviceId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    private DeviceDirectoryEntry summary() {
        return new DeviceDirectoryEntry(deviceId, groupId, "Synthetic Team",
                "Synthetic Endpoint", "active", null, Instant.parse("2026-10-31T08:00:00Z"),
                "0.1.0", 3L, 100L, "linux", "Test Linux", "Test CPU",
                8_000_000_000L, null);
    }

    @Test
    void listUsesSnakeCaseBoundedCursorAndNoStoreWithoutSensitiveFields()
            throws Exception {
        UUID next = UUID.randomUUID();
        when(directory.list(null, 2)).thenReturn(
                new DeviceDirectoryPage(List.of(summary()), next));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(get("/api/v1/devices").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].device_id").value(deviceId.toString()))
                .andExpect(jsonPath("$.items[0].group_id").value(groupId.toString()))
                .andExpect(jsonPath("$.items[0].heartbeat_received_at")
                        .value("2026-10-31T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].queue_count").value(3))
                .andExpect(jsonPath("$.next_cursor").value(next.toString()))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("mac_address"));
        assertFalse(response.contains("ip_address"));
        assertFalse(response.contains("credential"));
        assertFalse(response.contains("client_cert"));
        assertFalse(response.contains("event_payload"));
        verify(directory).list(null, 2);

        mvc.perform(get("/api/v1/devices").param("cursor", next.toString())
                        .param("limit", "10"))
                .andExpect(status().isOk());
        verify(directory).list(next, 10);
    }

    @Test
    void outOfScopeAndUnknownDevicesShareSameNonEnumerating404() throws Exception {
        when(directory.find(deviceId)).thenReturn(Optional.empty());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/devices/{deviceId}", deviceId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(directory).find(deviceId);
    }

    @Test
    void aVisibleDeviceHasOnlyAnExplicitlyAllowlistedHealthProjection()
            throws Exception {
        when(directory.find(deviceId)).thenReturn(Optional.of(summary()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/v1/devices/{deviceId}", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(deviceId.toString()))
                .andExpect(jsonPath("$.memory_total_bytes").value(8_000_000_000L))
                .andExpect(jsonPath("$.last_seen_at").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void invalidBoundedPageHasStable400WithoutLeakingException() throws Exception {
        when(directory.list(null, 101)).thenThrow(
                new IllegalArgumentException("synthetic sensitive debugging detail"));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(get("/api/v1/devices").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("sensitive"));
    }

    @Test
    void threeIndependentFeatureFlagsGateTheControllerRegistration() {
        var annotation = DeviceDirectoryRestController.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertNotNull(annotation);
        assertEquals("true", annotation.havingValue());
        assertEquals(java.util.Set.of(
                "lanveriq.console.auth.enabled",
                "lanveriq.device-directory.enabled",
                "lanveriq.device-directory.http-enabled"),
                java.util.Set.of(annotation.name()));
        assertTrue(DeviceDirectoryRestResponse.DeviceItem.class.isRecord());
    }
}
