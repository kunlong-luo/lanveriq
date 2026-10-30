package com.lanveriq.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after CSRF validation but before UsernamePasswordAuthenticationFilter. */
final class ConsoleLoginRateLimitFilter extends OncePerRequestFilter {

    private final ConsoleLoginRateLimiter limiter;
    private final AdminAuthenticationAudit audit;

    ConsoleLoginRateLimitFilter(ConsoleLoginRateLimiter limiter, AdminAuthenticationAudit audit) {
        this.limiter = limiter;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) &&
                "/api/v1/auth/login".equals(request.getServletPath())) {
            long length = request.getContentLengthLong();
            if (length <= 0 || length > 8192) {
                response.setHeader("Cache-Control", "no-store");
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            if (!limiter.allow(request.getParameter("username"), request.getRemoteAddr())) {
                try {
                    audit.loginThrottled();
                } catch (RuntimeException e) {
                    response.setHeader("Cache-Control", "no-store");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Retry-After", "60");
                response.sendError(429);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
