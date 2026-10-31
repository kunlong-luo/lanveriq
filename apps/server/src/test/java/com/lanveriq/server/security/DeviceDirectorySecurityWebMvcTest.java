package com.lanveriq.server.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lanveriq.server.devices.DeviceDirectoryRestController;
import com.lanveriq.server.devices.DeviceDirectoryPage;
import com.lanveriq.server.devices.DeviceDirectoryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Servlet-level Spring Security negative tests; no Docker, real users,
 * local network, Caddy or TLS handshake. Not a production security approval.
 */
@WebMvcTest(controllers = DeviceDirectoryRestController.class)
@Import({DeviceDirectoryHttpSecurityConfiguration.class,
        DeviceDirectorySecurityWebMvcTest.EnableSecurityInMvcSlice.class})
@TestPropertySource(properties = {
        "lanveriq.console.auth.enabled=true",
        "lanveriq.console.auth.origin=https://console.lanveriq.internal",
        "lanveriq.device-directory.enabled=true",
        "lanveriq.device-directory.http-enabled=true"
})
class DeviceDirectorySecurityWebMvcTest {
    // Spring Boot's MVC slice deliberately excludes the production security
    // auto-configuration; enable the native HttpSecurity builder ONLY here.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class EnableSecurityInMvcSlice {
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private DeviceDirectoryService directory;
    @MockitoBean private AdminAccountRepository accounts;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consoleGet() {
        return get("/api/v1/devices")
                .header("Host", "console.lanveriq.internal")
                .header("Sec-Fetch-Site", "same-origin");
    }

    @Test
    void noBrowserSessionCannotReadEvenWhenAllFlagsAreEnabled() throws Exception {
        mvc.perform(consoleGet()).andExpect(status().isUnauthorized());
        verify(directory, never()).list(Mockito.any(), Mockito.anyInt());
    }

    @Test
    void wrongHostOriginOrCrossSiteRequestFailsBeforeDirectoryService() throws Exception {
        mvc.perform(get("/api/v1/devices")
                .header("Host", "agent.lanveriq.internal")
                .header("Origin", "https://console.lanveriq.internal"))
                .andExpect(status().isForbidden());
        mvc.perform(consoleGet().header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
        mvc.perform(consoleGet().header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        verify(directory, never()).list(Mockito.any(), Mockito.anyInt());
    }

    @Test
    void currentEnabledSessionMayReadOnlyThroughExactConsoleOrigin() throws Exception {
        UUID actor = UUID.randomUUID();
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.userId()).thenReturn(actor);
        when(principal.sessionVersion()).thenReturn(3L);
        when(accounts.findById(actor)).thenReturn(
                Optional.of(new AdminAccount(actor, "synthetic-admin", "mock-hash", null, 3L)));
        when(directory.list(null, 50)).thenReturn(new DeviceDirectoryPage(List.of(), null));

        mvc.perform(consoleGet().with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()))))
                .andExpect(status().isOk());
        verify(directory).list(null, 50);
    }

    @Test
    void changedAccountSessionVersionBlocksAuthenticatedStaleCookie() throws Exception {
        UUID actor = UUID.randomUUID();
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.userId()).thenReturn(actor);
        when(principal.sessionVersion()).thenReturn(1L);
        when(accounts.findById(actor)).thenReturn(
                Optional.of(new AdminAccount(actor, "synthetic-admin", "mock-hash", null, 2L)));

        mvc.perform(consoleGet().with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()))))
                .andExpect(status().isUnauthorized());
        verify(directory, never()).list(Mockito.any(), Mockito.anyInt());
    }

    @Test
    void sameOriginPostNeverBecomesAWriteRoute() throws Exception {
        mvc.perform(post("/api/v1/devices")
                .header("Host", "console.lanveriq.internal")
                .header("Origin", "https://console.lanveriq.internal")
                .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isForbidden());
        verify(directory, never()).list(Mockito.any(), Mockito.anyInt());
    }
}
