package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ConsoleSessionEventHandlersTest {

    private final AdminAuthenticationAudit audit = Mockito.mock(AdminAuthenticationAudit.class);
    private final ConsoleSessionEventHandlers handlers = new ConsoleSessionEventHandlers(audit);
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulAuthenticationMustRecordKnownPrincipalBeforeAcknowledgment() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handlers.loginSuccess().onAuthenticationSuccess(request, response, identity());

        verify(audit).loginAllowed(id);
        assertEquals(204, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void auditFailureInvalidatesSessionRatherThanClaimingLoginSuccess() throws Exception {
        Mockito.doThrow(new IllegalStateException("db unavailable")).when(audit).loginAllowed(id);
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        request.setSession(session);
        var response = new MockHttpServletResponse();

        handlers.loginSuccess().onAuthenticationSuccess(request, response, identity());

        assertEquals(503, response.getStatus());
        assertTrue(session.isInvalid());
    }

    @Test
    void genericInvalidPasswordAuditNeverReceivesSubmittedLogin() throws Exception {
        var response = new MockHttpServletResponse();
        handlers.loginFailure().onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("generic denied"));
        assertEquals(401, response.getStatus());
        verify(audit).loginDenied();
    }

    @Test
    void logoutAuditIsRecordedWithAuthenticatedUuid() throws Exception {
        var response = new MockHttpServletResponse();
        handlers.logoutSuccess().onLogoutSuccess(new MockHttpServletRequest(), response, identity());
        assertEquals(204, response.getStatus());
        verify(audit).logoutAllowed(id);
    }

    private UsernamePasswordAuthenticationToken identity() {
        var principal = new AdminPrincipal(id, "owner", "synthetic", true, 0);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
