package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MonitoringOverviewTest {
    private final UUID adminId = UUID.randomUUID();
    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query query = Mockito.mock(Query.class);
    private final JpaMonitoringOverviewRepository jpa = new JpaMonitoringOverviewRepository(manager);
    private final MonitoringOverviewRepository repo = Mockito.mock(MonitoringOverviewRepository.class);
    private final MonitoringOverviewService service = new MonitoringOverviewService(repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.userId()).thenReturn(adminId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void queryUsesOneScopedSqlSnapshotAndRoleExistsWithoutSensitiveTables() {
        when(manager.createNativeQuery(Mockito.anyString())).thenReturn(query);
        when(query.setParameter(Mockito.anyString(), Mockito.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[] {
                1_800_000_000L, 7L, 1L, 1L, 2L, 1L, 1L, 1L
        });
        var result = jpa.aggregate(adminId);
        assertEquals(Instant.ofEpochSecond(1_800_000_000L), result.asOf());
        assertEquals(7L, result.total());
        assertEquals(2L, result.offline());
        assertEquals(1L, result.retired());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager).createNativeQuery(sql.capture());
        String source = sql.getValue();
        assertTrue(source.contains("WITH snapshot AS MATERIALIZED"));
        assertTrue(source.contains("LEFT JOIN states ON TRUE"));
        assertTrue(source.contains("count(connectivity) AS total"));
        assertTrue(source.contains("scope.device_group_id=d.group_id"));
        assertTrue(source.contains("u.disabled_at IS NULL"));
        assertTrue(source.contains("role.code IN (:health_roles)"));
        assertTrue(source.contains("received_at>=as_of-INTERVAL '90 seconds'"));
        assertTrue(source.contains("received_at>=as_of-INTERVAL '300 seconds'"));
        assertTrue(!source.contains("device_inventory_addresses")
                && !source.contains("device_credentials")
                && !source.contains("mac_address"));
        verify(query).setParameter("admin_id", adminId);
    }

    @Test
    void emptyScopeIsZeroNotAnInventedOnlineDevice() {
        when(manager.createNativeQuery(Mockito.anyString())).thenReturn(query);
        when(query.setParameter(Mockito.anyString(), Mockito.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[] {
                1_800_000_000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
        });
        var empty = jpa.aggregate(adminId);
        assertEquals(0L, empty.total());
        assertEquals(0L, empty.online());
        assertEquals(0L, empty.offline());
    }

    @Test
    void malformedAggregatesFailClosedRatherThanMisreporting() {
        Instant t = Instant.parse("2026-10-31T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new MonitoringOverview(t, 2, 2, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MonitoringOverview(t, 1, -1, 0, 0, 0, 1, 0));
    }

    @Test
    void serviceRequiresAdminAndUsesCurrentIdentity() {
        assertThrows(AccessDeniedException.class, service::overview);
        login();
        var snapshot = new MonitoringOverview(
                Instant.parse("2026-10-31T00:00:00Z"), 0, 0, 0, 0, 0, 0, 0);
        when(repo.aggregate(adminId)).thenReturn(snapshot);
        assertEquals(snapshot, service.overview());
        verify(repo).aggregate(adminId);
    }

    @Test
    void getIsNoStoreAndOnlyReturnsTheEightAllowlistedKeys() throws Exception {
        var snapshot = new MonitoringOverview(
                Instant.parse("2026-10-31T00:00:00Z"), 7, 1, 1, 2, 1, 1, 1);
        MonitoringOverviewService mockService = Mockito.mock(MonitoringOverviewService.class);
        when(mockService.overview()).thenReturn(snapshot);
        var controller = new MonitoringOverviewRestController(mockService);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var result = mvc.perform(get("/api/v1/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.online").value(1))
                .andExpect(jsonPath("$.stale").value(1))
                .andExpect(jsonPath("$.offline").value(2))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.retired").value(1))
                .andReturn().getResponse().getContentAsString();
        assertTrue(!result.contains("device_id") && !result.contains("mac")
                && !result.contains("ip_address") && !result.contains("user"));
        verify(mockService).overview();
    }
}
