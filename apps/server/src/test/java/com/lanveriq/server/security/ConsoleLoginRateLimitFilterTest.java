package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConsoleLoginRateLimitFilterTest {

    private final AdminAuthenticationAudit audit = Mockito.mock(AdminAuthenticationAudit.class);
    private final ConsoleLoginRateLimitFilter filter =
            new ConsoleLoginRateLimitFilter(new ConsoleLoginRateLimiter(), audit);

    @Test
    void rejectsExhaustedLoginAndRecordsOnlyAnonymizedRateLimitedEvent() throws Exception {
        for (int i = 0; i < 5; i++) {
            var request = login("admin");
            var response = new MockHttpServletResponse();
            FilterChain chain = Mockito.mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        var request = login("admin");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(audit).loginThrottled();
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void rejectsUnknownLengthAndOversizedLoginBeforeParsingParameters() throws Exception {
        var missingLength = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        missingLength.setServletPath("/api/v1/auth/login");
        var response = new MockHttpServletResponse();
        filter.doFilter(missingLength, response, Mockito.mock(FilterChain.class));
        assertEquals(413, response.getStatus());

        var oversized = login("admin");
        oversized.setContent(new byte[8193]);
        var largeResponse = new MockHttpServletResponse();
        filter.doFilter(oversized, largeResponse, Mockito.mock(FilterChain.class));
        assertEquals(413, largeResponse.getStatus());
        verify(audit, never()).loginThrottled();
    }

    @Test
    void simultaneousPasswordChecksAreBoundedAndReturnShortRetry() throws Exception {
        var saturated = new ConsoleLoginRateLimitFilter(
                new ConsoleLoginRateLimiter(), audit, new Semaphore(0));
        var request = login("operator");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        saturated.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        verify(audit).loginThrottled();
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void concurrentPermitIsReleasedEvenIfNextFilterThrows() {
        var capacity = new Semaphore(1);
        var bounded = new ConsoleLoginRateLimitFilter(
                new ConsoleLoginRateLimiter(), audit, capacity);
        var request = login("operator");
        var response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("synthetic downstream error");
        };

        assertThrows(ServletException.class,
                () -> bounded.doFilter(request, response, failingChain));
        assertEquals(1, capacity.availablePermits());
    }

    @Test
    void otherRoutesAreNotAffectedByLoginBudget() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        request.setServletPath("/api/v1/auth/csrf");
        var response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest login(String username) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent(("username=" + username + "&password=synthetic-only")
                .getBytes(StandardCharsets.UTF_8));
        request.setParameter("username", username);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
