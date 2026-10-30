package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ConsoleSessionRevalidationFilterTest {

    private final AdminAccountRepository accounts = Mockito.mock(AdminAccountRepository.class);
    private final ConsoleSessionRevalidationFilter filter =
            new ConsoleSessionRevalidationFilter(accounts);
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentVersionAndEnabledAccountKeepSession() throws Exception {
        loginContext(4);
        when(accounts.findById(id)).thenReturn(
                Optional.of(new AdminAccount(id, "owner", "hash", null, 4)));
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rotatedSessionVersionImmediatelyInvalidatesStaleCookieSession() throws Exception {
        loginContext(2);
        when(accounts.findById(id)).thenReturn(
                Optional.of(new AdminAccount(id, "owner", "hash", null, 3)));
        reject();
    }

    @Test
    void disabledOrDeletedUserImmediatelyInvalidatesSession() throws Exception {
        loginContext(0);
        when(accounts.findById(id)).thenReturn(Optional.of(
                new AdminAccount(id, "owner", "hash", Instant.now(), 0)));
        reject();

        clear();
        loginContext(0);
        when(accounts.findById(id)).thenReturn(Optional.empty());
        reject();
    }

    @Test
    void anonymousRequestDoesNotNeedAnAccountLookup() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(accounts, never()).findById(Mockito.any());
    }

    private void reject() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        var session = new MockHttpSession();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrueInvalid(session);
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    private static void assertTrueInvalid(MockHttpSession session) {
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    }

    private void loginContext(long version) {
        var principal = new AdminPrincipal(id, "owner", "synthetic-hash", true, version);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
