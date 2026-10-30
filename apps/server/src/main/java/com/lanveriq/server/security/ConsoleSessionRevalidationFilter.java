package com.lanveriq.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Invalidates a persisted browser session if the account was disabled,
 * removed or its session version was rotated since the login.
 * Must execute after SecurityContextHolderFilter and before CSRF/login/logout.
 */
final class ConsoleSessionRevalidationFilter extends OncePerRequestFilter {

    private final AdminAccountRepository accounts;

    ConsoleSessionRevalidationFilter(AdminAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean permitted = authentication.getPrincipal() instanceof AdminPrincipal principal
                    && principal.isEnabled()
                    && accounts.findById(principal.userId())
                        .map(a -> a.isEnabled()
                                && a.getSessionVersion() == principal.sessionVersion())
                        .orElse(false);
            if (!permitted) {
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.setHeader("Cache-Control", "no-store");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
