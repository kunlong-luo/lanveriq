package com.lanveriq.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after CSRF validation but before UsernamePasswordAuthenticationFilter. */
final class ConsoleLoginRateLimitFilter extends OncePerRequestFilter {

    private final ConsoleLoginRateLimiter limiter;
    private final AdminAuthenticationAudit audit;
    private final Semaphore concurrentPasswordChecks;

    ConsoleLoginRateLimitFilter(ConsoleLoginRateLimiter limiter, AdminAuthenticationAudit audit) {
        this(limiter, audit, new Semaphore(2, true));
    }

    ConsoleLoginRateLimitFilter(ConsoleLoginRateLimiter limiter,
                                AdminAuthenticationAudit audit,
                                Semaphore concurrentPasswordChecks) {
        this.limiter = Objects.requireNonNull(limiter);
        this.audit = Objects.requireNonNull(audit);
        this.concurrentPasswordChecks = Objects.requireNonNull(concurrentPasswordChecks);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean isLogin = "POST".equals(request.getMethod()) &&
                "/api/v1/auth/login".equals(request.getServletPath());
        if (isLogin) {
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
            // Argon2id is intentionally memory-hard; at most two simultaneous
            // login password checks on this single-node LAN pilot.
            if (!concurrentPasswordChecks.tryAcquire()) {
                try {
                    audit.loginThrottled();
                } catch (RuntimeException e) {
                    response.setHeader("Cache-Control", "no-store");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Retry-After", "1");
                response.sendError(429);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (isLogin) {
                concurrentPasswordChecks.release();
            }
        }
    }
}
