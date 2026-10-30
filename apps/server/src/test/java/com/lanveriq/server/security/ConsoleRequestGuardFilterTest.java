package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConsoleRequestGuardFilterTest {

    private final ConsoleRequestGuardFilter guard = new ConsoleRequestGuardFilter(
            new ConsoleOriginPolicy("https://console.lanveriq.internal"));

    @Test
    void onlyExactHostOriginAndSameOriginFetchReachLogin() throws Exception {
        var request = login();
        request.addHeader("Origin", "https://console.lanveriq.internal");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        var response = new MockHttpServletResponse();
        FilterChain next = Mockito.mock(FilterChain.class);

        guard.doFilter(request, response, next);

        verify(next).doFilter(request, response);
    }

    @Test
    void deniesMissingOriginCrossHostAndCrossSiteBeforeSecurityLogin() throws Exception {
        deny(login());
        var wrongHost = loginWithHost("agent.lanveriq.internal");
        wrongHost.addHeader("Origin", "https://console.lanveriq.internal");
        deny(wrongHost);

        var wrongOrigin = login();
        wrongOrigin.addHeader("Origin", "https://agent.lanveriq.internal");
        deny(wrongOrigin);

        var missingHost = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        missingHost.addHeader("Origin", "https://console.lanveriq.internal");
        deny(missingHost);

        var dupOrigin = login();
        dupOrigin.addHeader("Origin", "https://console.lanveriq.internal");
        dupOrigin.addHeader("Origin", "https://console.lanveriq.internal");
        deny(dupOrigin);

        var crossSite = login();
        crossSite.addHeader("Origin", "https://console.lanveriq.internal");
        crossSite.addHeader("Sec-Fetch-Site", "same-site");
        deny(crossSite);
    }

    @Test
    void csrfBootstrapRequiresCorrectHostAndNoCrossSiteFetch() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        request.addHeader("Host", "console.lanveriq.internal");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        var response = new MockHttpServletResponse();
        FilterChain next = Mockito.mock(FilterChain.class);
        guard.doFilter(request, response, next);
        verify(next).doFilter(request, response);

        var malicious = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        malicious.addHeader("Host", "console.lanveriq.internal");
        malicious.addHeader("Sec-Fetch-Site", "cross-site");
        deny(malicious);
    }

    private static MockHttpServletRequest login() {
        return loginWithHost("console.lanveriq.internal");
    }

    private static MockHttpServletRequest loginWithHost(String host) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Host", host);
        return request;
    }

    private void deny(MockHttpServletRequest request) throws IOException, ServletException {
        var response = new MockHttpServletResponse();
        FilterChain next = Mockito.mock(FilterChain.class);
        guard.doFilter(request, response, next);
        assertEquals(403, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(next, never()).doFilter(Mockito.any(), Mockito.any());
    }
}
