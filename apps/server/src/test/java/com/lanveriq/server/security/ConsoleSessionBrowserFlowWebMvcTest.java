package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var sessionResponse = mvc.perform(consoleGet("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        // sendError(401) would mark a servlet error for Tomcat to redispatch
        // to /error, where denyBusinessRoutes can overwrite it as 403.
        assertFalse(sessionResponse.isCommitted(),
                "A direct 401 must not trigger Tomcat's /error redispatch");
        assertTrue(sessionResponse.getContentAsString().isEmpty(),
                "Unauthenticated session response must not disclose an error page");
        // No saved-request redirect exists for this JSON API. A hidden
        // HttpSessionRequestCache used to make the first 401 create a session
        // before the explicit CSRF bootstrap, hiding Set-Cookie on /csrf.
        var anonymousRequest = mvc.perform(consoleGet("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized()).andReturn().getRequest();
        assertTrue(anonymousRequest.getSession(false) == null,
                "Anonymous status must not create a saved-request HttpSession");
        var directoryResponse = mvc.perform(consoleGet("/api/v1/devices"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();
        assertFalse(directoryResponse.isCommitted());
        verify(directory, never()).list(any(), Mockito.anyInt());
    }

    @Test
    void http11CanonicalPublicPortReachesRealSessionChainAndWrongPortIsDenied()
            throws Exception {
        // Mirrors the trusted direct Tomcat H1 differential: mTLS transport
        // and the public Console Host are independent of the backend SNI.
        // In-process servlet simulation; not proof of the real TLS chain.
        mvc.perform(get("/api/v1/auth/session")
                        .secure(true)
                        .header("Host", "console.lanveriq.internal:443"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/auth/csrf")
                        .secure(true)
                        .header("Host", "console.lanveriq.internal:443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
        mvc.perform(get("/api/v1/auth/csrf")
                        .secure(true)
                        .header("Host", "console.lanveriq.internal:8443"))
                .andExpect(status().isForbidden());
    }

    @Test
    void http2CanonicalAuthorityWithoutHostReachesRealSessionSecurityChain() throws Exception {
        // Servlet HTTP/2 exposes the canonical :authority through serverName
        // and serverPort. It does not require an HTTP/1.1 Host header.
        // This is MockMvc filter-chain evidence, NOT a Tomcat TLS/ALPN run.
        mvc.perform(get("/api/v1/auth/session").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/auth/csrf").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());

        mvc.perform(get("/api/v1/devices").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(status().isUnauthorized());
        verify(directory, never()).list(any(), Mockito.anyInt());

        // A wrong-purpose :authority is rejected before any CSRF bootstrap
        // even with forwarded-host spoofing.
        mvc.perform(get("/api/v1/auth/csrf").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("agent.lanveriq.internal");
                    request.setServerPort(443);
                    request.addHeader("X-Forwarded-Host", "console.lanveriq.internal");
                    return request;
                }))
                .andExpect(status().isForbidden());
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
        var revokedSessionResponse = mvc.perform(
                        consoleGet("/api/v1/auth/session").session(state.session()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();
        assertFalse(revokedSessionResponse.isCommitted(),
                "Revoked session must return a direct 401, not Tomcat /error");
        var revokedDirectoryResponse = mvc.perform(
                        consoleGet("/api/v1/devices").session(state.session()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();
        assertFalse(revokedDirectoryResponse.isCommitted());
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
    void stagedWebAuthnLibraryMustNotExposeRegistrationOrAssertionRoutes() throws Exception {
        // A11-D1: the vetted Spring Security WebAuthn library may be on the
        // classpath, but NOT activated via HttpSecurity.webAuthn().
        // CSRF is intentionally satisfied: a 403 must be due to deny-all
        // authorization, not just a rejected missing-CSRF shortcut.
        for (String path : new String[]{
                "/webauthn/register/options",
                "/webauthn/register",
                "/webauthn/authenticate/options",
                "/login/webauthn"}) {
            // This WebMvc slice has no global catch-all security chain; a
            // nonexistent WebAuthn handler may return 404 instead of 403.
            // Full Boot PG18 separately requires real deny-all 403.
            mvc.perform(post(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                            .header("Host", "console.lanveriq.internal")
                            .header("Origin", "https://console.lanveriq.internal")
                            .header("Sec-Fetch-Site", "same-origin"))
                    .andExpect(status().is4xxClientError());
        }
        // Anonymous auth-chain denial may be a 401 challenge rather than
        // 403; the logged-in assertion below must still be rejected.
        for (String path : new String[]{
                "/api/v1/auth/webauthn/register",
                "/api/v1/auth/webauthn/step-up"}) {
            mvc.perform(post(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                            .header("Host", "console.lanveriq.internal")
                            .header("Origin", "https://console.lanveriq.internal")
                            .header("Sec-Fetch-Site", "same-origin"))
                    .andExpect(status().is4xxClientError());
        }
        // A valid password-login session is also not an automatic grant
        // to enroll passkeys or request a high-risk step-up proof.
        configureSyntheticLogin(0L);
        var state = bootstrap();
        login(state);
        mvc.perform(post("/api/v1/auth/webauthn/step-up")
                        .session(state.session()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Host", "console.lanveriq.internal")
                        .header("Origin", "https://console.lanveriq.internal")
                        .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isForbidden());
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
