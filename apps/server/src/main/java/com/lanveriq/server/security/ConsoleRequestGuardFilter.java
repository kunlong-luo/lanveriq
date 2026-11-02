package com.lanveriq.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defense in depth in addition to Spring Security CSRF; ingress must also
 * enforce purpose-separated TLS vhosts and strip injected proxy assertions.
 */
final class ConsoleRequestGuardFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleRequestGuardFilter.class);
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
        boolean hostOk = acceptsTransportHost(request, hosts);
        boolean originOk = origins.size() <= 1 &&
                (origins.isEmpty() ? safe : policy.acceptsOrigin(origins.getFirst()));
        boolean fetchOk = fetchSites.size() <= 1 &&
                (fetchSites.isEmpty() || policy.acceptsFetchSite(fetchSites.getFirst()));
        boolean allowed = hostOk && originOk && fetchOk;
        // This DEBUG-only diagnostic deliberately logs NO hostnames, paths,
        // credentials, cookies, tokens or raw request headers. The private
        // synthetic CI runner classifies these fixed booleans and destroys
        // its local Spring log on exit. Off under default INFO production.
        if (LOG.isDebugEnabled()) {
            LOG.debug("LANVERIQ_CONSOLE_GUARD_RESULT host={} origin={} fetch={} allowed={}",
                    hostOk, originOk, fetchOk, allowed);
        }
        if (!allowed) {
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * HTTP/2 uses the :authority pseudo-header, which a Servlet container
     * exposes through getServerName/getServerPort rather than necessarily
     * repeating it as an HTTP/1.1 Host header. Require the canonical
     * HTTPS authority (including port 443), not an inferred forwarded Host.
     * For HTTP/1.x retain the existing exactly-one-Host rule unchanged.
     *
     * An explicit Host sent alongside :authority must independently match;
     * a conflicting ordinary header can never override HTTP/2 authority.
     */
    private boolean acceptsTransportHost(HttpServletRequest request,
                                         java.util.List<String> hosts) {
        if (!"HTTP/2.0".equals(request.getProtocol())) {
            return hosts.size() == 1 && policy.acceptsHost(hosts.getFirst());
        }
        return request.isSecure()
                && "https".equals(request.getScheme())
                && request.getServerPort() == 443
                && policy.acceptsHost(request.getServerName() + ":443")
                && hosts.size() <= 1
                && (hosts.isEmpty() || policy.acceptsHost(hosts.getFirst()));
    }
}
