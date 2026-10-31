package com.lanveriq.server.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lanveriq.server.devices.MonitoringOverview;
import com.lanveriq.server.devices.MonitoringOverviewRestController;
import com.lanveriq.server.devices.MonitoringOverviewService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MonitoringOverviewRestController.class)
@Import({MonitoringOverviewHttpSecurityConfiguration.class,
        MonitoringOverviewSecurityWebMvcTest.MvcOnlySecurity.class})
@TestPropertySource(properties = {
        "lanveriq.console.auth.enabled=true",
        "lanveriq.console.auth.origin=https://console.lanveriq.internal",
        "lanveriq.device-directory.enabled=true",
        "lanveriq.monitoring.overview-http-enabled=true"
})
class MonitoringOverviewSecurityWebMvcTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class MvcOnlySecurity {
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private MonitoringOverviewService overview;
    @MockitoBean private AdminAccountRepository accounts;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder read() {
        return get("/api/v1/monitoring/overview")
                .header("Host", "console.lanveriq.internal")
                .header("Sec-Fetch-Site", "same-origin");
    }

    @Test
    void anonymousAndCrossSiteCannotGetGlobalCounts() throws Exception {
        mvc.perform(read()).andExpect(status().isUnauthorized());
        mvc.perform(read().header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(read().header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/monitoring/overview")
                .header("Host", "agent.lanveriq.internal"))
                .andExpect(status().isForbidden());
        verify(overview, never()).overview();
    }

    @Test
    void currentSessionReadsButRotatedOrDisabledSessionFails() throws Exception {
        UUID actor = UUID.randomUUID();
        AdminPrincipal admin = Mockito.mock(AdminPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);
        when(admin.userId()).thenReturn(actor);
        when(admin.sessionVersion()).thenReturn(4L);
        when(accounts.findById(actor)).thenReturn(Optional.of(
                new AdminAccount(actor, "synthetic", "synthetic-hash", null, 4L)));
        when(overview.overview()).thenReturn(new MonitoringOverview(
                Instant.parse("2026-10-31T00:00:00Z"), 0, 0, 0, 0, 0, 0, 0));
        var session = UsernamePasswordAuthenticationToken.authenticated(
                admin, null, List.of());
        mvc.perform(read().with(authentication(session))).andExpect(status().isOk());
        verify(overview).overview();

        when(accounts.findById(actor)).thenReturn(Optional.of(
                new AdminAccount(actor, "synthetic", "synthetic-hash", null, 5L)));
        mvc.perform(read().with(authentication(session)))
                .andExpect(status().isUnauthorized());
        verify(overview).overview();
    }

    @Test
    void overviewIsNeverAWritableMonitoringEndpoint() throws Exception {
        mvc.perform(post("/api/v1/monitoring/overview")
                .header("Host", "console.lanveriq.internal")
                .header("Origin", "https://console.lanveriq.internal"))
                .andExpect(status().isForbidden());
        verify(overview, never()).overview();
    }

    @Test
    void chainAndControllerRequireTheSameThreeExplicitFlags() {
        var a = MonitoringOverviewHttpSecurityConfiguration.class
                .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        var b = MonitoringOverviewRestController.class
                .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(a.name()),
                java.util.Set.of(b.name()));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(
                "lanveriq.console.auth.enabled", "lanveriq.device-directory.enabled",
                "lanveriq.monitoring.overview-http-enabled"), java.util.Set.of(a.name()));
    }
}
