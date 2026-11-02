package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

    @Test
    void acceptsExactHttpsHttp2AuthorityWithoutHttp11HostHeader() throws Exception {
        // Tomcat HTTP/2 maps :authority onto Servlet serverName/Port,
        // not necessarily onto getHeaders("Host"). Host still must not be
        // guessed from Forwarded or X-Forwarded-*.
        var request = http2Authority("console.lanveriq.internal", 443);
        var response = new MockHttpServletResponse();
        FilterChain next = Mockito.mock(FilterChain.class);
        guard.doFilter(request, response, next);
        verify(next).doFilter(request, response);
    }

    @Test
    void http2AuthorityRequiresHttpsPort443AndMatchingAnyExplicitHost() throws Exception {
        deny(http2Authority("agent.lanveriq.internal", 443));
        deny(http2Authority("console.lanveriq.internal", 8443));

        // Spring's mock derives secure transport semantics from the scheme;
        // never fake an HTTPS request as insecure using only setSecure(false).
        // A real non-HTTPS scheme is always rejected below.
        var wrongScheme = http2Authority("console.lanveriq.internal", 443);
        wrongScheme.setScheme("http");
        deny(wrongScheme);

        var spoofed = http2Authority("agent.lanveriq.internal", 443);
        spoofed.addHeader("Host", "console.lanveriq.internal");
        // Spring's Mock request rewrites serverName when addHeader("Host")
        // runs. Keep the independent HTTP/2 :authority value visible through
        // Servlet getServerName(), as a real HTTP/2 parser does.
        deny(withAuthority(spoofed, "agent.lanveriq.internal"));

        var mismatchedHeader = http2Authority("console.lanveriq.internal", 443);
        mismatchedHeader.addHeader("Host", "agent.lanveriq.internal");
        deny(withAuthority(mismatchedHeader, "console.lanveriq.internal"));

        var duplicate = http2Authority("console.lanveriq.internal", 443);
        duplicate.addHeader("Host", "console.lanveriq.internal");
        duplicate.addHeader("Host", "console.lanveriq.internal");
        deny(duplicate);

        var forwarded = http2Authority("agent.lanveriq.internal", 443);
        forwarded.addHeader("X-Forwarded-Host", "console.lanveriq.internal");
        deny(forwarded);
    }

    @Test
    void keepsSingleExactHostForHttp11AndRejectsHttp2LookalikeProtocols() throws Exception {
        var missingH1 = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        deny(missingH1);
        var inventedH2 = http2Authority("console.lanveriq.internal", 443);
        inventedH2.setProtocol("HTTP/2");
        deny(inventedH2);
    }

    private static MockHttpServletRequest http2Authority(String host, int port) {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setProtocol("HTTP/2.0");
        request.setSecure(true);
        request.setScheme("https");
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    private static HttpServletRequest withAuthority(MockHttpServletRequest request,
                                                    String canonicalAuthority) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getServerName() {
                return canonicalAuthority;
            }

            @Override
            public int getServerPort() {
                return 443;
            }
        };
    }

    private static MockHttpServletRequest login() {
        return loginWithHost("console.lanveriq.internal");
    }

    private static MockHttpServletRequest loginWithHost(String host) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Host", host);
        return request;
    }

    private void deny(HttpServletRequest request) throws IOException, ServletException {
        var response = new MockHttpServletResponse();
        FilterChain next = Mockito.mock(FilterChain.class);
        guard.doFilter(request, response, next);
        assertEquals(403, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(next, never()).doFilter(Mockito.any(), Mockito.any());
    }
}
