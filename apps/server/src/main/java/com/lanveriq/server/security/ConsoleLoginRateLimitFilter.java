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

    ConsoleLoginRateLimitFilter(ConsoleLoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) &&
                "/api/v1/auth/login".equals(request.getServletPath())) {
            if (request.getContentLengthLong() > 8192 ||
                    !limiter.allow(request.getParameter("username"), request.getRemoteAddr())) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Retry-After", "60");
                response.sendError(429);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
