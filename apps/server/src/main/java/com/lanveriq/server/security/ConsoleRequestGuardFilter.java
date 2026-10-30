package com.lanveriq.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defense in depth in addition to Spring Security CSRF; ingress must also
 * enforce purpose-separated TLS vhosts and strip injected proxy assertions.
 */
final class ConsoleRequestGuardFilter extends OncePerRequestFilter {

    private final ConsoleOriginPolicy policy;

    ConsoleRequestGuardFilter(ConsoleOriginPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var hosts = Collections.list(request.getHeaders("Host"));
        var origins = Collections.list(request.getHeaders("Origin"));
        var fetchSites = Collections.list(request.getHeaders("Sec-Fetch-Site"));
        String method = request.getMethod();
        boolean safe = "GET".equals(method) || "HEAD".equals(method) ||
                "OPTIONS".equals(method);
        boolean allowed = hosts.size() == 1 && policy.acceptsHost(hosts.getFirst()) &&
                origins.size() <= 1 &&
                (origins.isEmpty() ? safe : policy.acceptsOrigin(origins.getFirst())) &&
                fetchSites.size() <= 1 &&
                (fetchSites.isEmpty() || policy.acceptsFetchSite(fetchSites.getFirst()));
        if (!allowed) {
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
