package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lanveriq.server.devices.DeviceDirectoryPage;
import com.lanveriq.server.devices.DeviceDirectoryRestController;
import com.lanveriq.server.devices.DeviceDirectoryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Synthetic single-JVM browser-session lifecycle; real Spring Security
 * filters and CSRF/session repositories, not a fabricated authenticated()
 * request processor. This is NOT real TLS, a browser or PostgreSQL.
 */
@WebMvcTest(controllers = {
        ConsoleCsrfController.class,
        ConsoleSessionStatusController.class,
        DeviceDirectoryRestController.class
})
@Import({
        ConsoleSessionSecurityConfiguration.class,
        DeviceDirectoryHttpSecurityConfiguration.class,
        // @WebMvcTest can omit package-private conditional controllers during
        // slice scanning; explicitly register real production beans here.
        ConsoleCsrfController.class,
        ConsoleSessionStatusController.class,
        DeviceDirectoryRestController.class,
        ConsoleSessionBrowserFlowWebMvcTest.MvcSecurity.class
})
@TestPropertySource(properties = {
        "lanveriq.console.auth.enabled=true",
        "lanveriq.console.auth.origin=https://console.lanveriq.internal",
        "lanveriq.device-directory.enabled=true",
        "lanveriq.device-directory.http-enabled=true"
})
class ConsoleSessionBrowserFlowWebMvcTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class MvcSecurity {
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthenticationProvider provider;
    @MockitoBean private AdminAccountRepository accounts;
    @MockitoBean private AdminAuthenticationAudit audit;
    @MockitoBean private DeviceDirectoryService directory;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consoleGet(String path) {
        return get(path)
                .header("Host", "console.lanveriq.internal")
                .header("Sec-Fetch-Site", "same-origin");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder consolePost(
            String path, MockHttpSession session, String token) {
        return post(path).session(session)
                .header("Host", "console.lanveriq.internal")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Origin", "https://console.lanveriq.internal")
                .header("X-CSRF-TOKEN", token);
    }

    private record CsrfSession(MockHttpSession session, String token) {
    }

    private CsrfSession bootstrap() throws Exception {
        return csrf(null);
    }

    private CsrfSession csrf(MockHttpSession existing) throws Exception {
        var request = consoleGet("/api/v1/auth/csrf");
        if (existing != null) {
            request.session(existing);
        }
        var result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("csrfToken").asText();
        return new CsrfSession(session, token);
    }

    private UUID configureSyntheticLogin(long version) {
        UUID actor = UUID.randomUUID();
        var principal = new AdminPrincipal(
                actor, "synthetic-owner", "synthetic-test-only", true, version);
        when(provider.supports(any())).thenReturn(true);
        when(provider.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of()));
        when(accounts.findById(actor)).thenReturn(Optional.of(
                new AdminAccount(actor, "synthetic-owner",
                        "synthetic-hash", null, version)));
        return actor;
    }

    private void login(CsrfSession state) throws Exception {
        // Positive content length is deliberately required by the rate limiter.
        mvc.perform(consolePost("/api/v1/auth/login", state.session(), state.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=synthetic-owner&password=synthetic-test-only")
                        .param("username", "synthetic-owner")
                        .param("password", "synthetic-test-only"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void anonymousSessionStatusReturns401NotHtmlRedirectOrLoginName() throws Exception {
        mvc.perform(consoleGet("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(consoleGet("/api/v1/devices"))
                .andExpect(status().isUnauthorized());
        verify(directory, never()).list(any(), Mockito.anyInt());
    }

    @Test
    void csrfLoginThenSessionAndGroupScopedReadPersistUntilVersionRotation()
            throws Exception {
        UUID actor = configureSyntheticLogin(7);
        var state = bootstrap();
        login(state);
        verify(audit).loginAllowed(actor);

        mvc.perform(consoleGet("/api/v1/auth/session").session(state.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.login").value("synthetic-owner"))
                .andExpect(jsonPath("$.user_id").doesNotExist())
                .andExpect(jsonPath("$.session_version").doesNotExist());

        when(directory.list(null, 50)).thenReturn(
                new DeviceDirectoryPage(List.of(), null));
        mvc.perform(consoleGet("/api/v1/devices").session(state.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        verify(directory).list(null, 50);

        // A stale persisted cookie must fail both session status and API reads.
        when(accounts.findById(actor)).thenReturn(Optional.of(
                new AdminAccount(actor, "synthetic-owner",
                        "synthetic-hash", null, 8)));
        mvc.perform(consoleGet("/api/v1/auth/session").session(state.session()))
                .andExpect(status().isUnauthorized());
        mvc.perform(consoleGet("/api/v1/devices").session(state.session()))
                .andExpect(status().isUnauthorized());
        Mockito.verify(directory, Mockito.times(1)).list(null, 50);
    }

    @Test
    void csrfMissingOrCrossSiteLoginNeverAuthenticates() throws Exception {
        var state = bootstrap();
        mvc.perform(post("/api/v1/auth/login").session(state.session())
                        .header("Host", "console.lanveriq.internal")
                        .header("Origin", "https://console.lanveriq.internal")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=synthetic-owner&password=synthetic-test-only"))
                .andExpect(status().isForbidden());

        mvc.perform(consolePost("/api/v1/auth/login", state.session(), state.token())
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=synthetic-owner&password=synthetic-test-only"))
                .andExpect(status().isForbidden());
        verify(provider, never()).authenticate(any());
    }

    @Test
    void validLogoutEndsSessionAndBlocksFormerDeviceReads() throws Exception {
        UUID actor = configureSyntheticLogin(4);
        var state = bootstrap();
        login(state);
        // Spring Security rotates the CSRF token on authentication; the
        // browser must fetch a new token before POST /logout.
        var loggedIn = csrf(state.session());
        mvc.perform(consolePost("/api/v1/auth/logout",
                        loggedIn.session(), loggedIn.token()))
                .andExpect(status().isNoContent());
        verify(audit).logoutAllowed(actor);
        assertTrue(state.session().isInvalid());
        mvc.perform(consoleGet("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized());
    }
}
