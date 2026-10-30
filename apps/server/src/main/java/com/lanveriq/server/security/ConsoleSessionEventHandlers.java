package com.lanveriq.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * Explicit HTTP responses and durable (transactional) security audit.
 * A successful login is never acknowledged if the audit write fails.
 */
final class ConsoleSessionEventHandlers {

    private final AdminAuthenticationAudit audit;

    ConsoleSessionEventHandlers(AdminAuthenticationAudit audit) {
        this.audit = audit;
    }

    AuthenticationSuccessHandler loginSuccess() {
        return (request, response, authentication) -> {
            if (!(authentication.getPrincipal() instanceof AdminPrincipal principal)
                    || !principal.isEnabled()) {
                rejectAndInvalidate(request, response);
                return;
            }
            try {
                audit.loginAllowed(principal.userId());
            } catch (RuntimeException e) {
                rejectAndInvalidate(request, response);
                return;
            }
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
    }

    AuthenticationFailureHandler loginFailure() {
        return (request, response, exception) -> {
            try {
                audit.loginDenied();
            } catch (RuntimeException e) {
                response.setHeader("Cache-Control", "no-store");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    LogoutSuccessHandler logoutSuccess() {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
                try {
                    audit.logoutAllowed(principal.userId());
                } catch (RuntimeException e) {
                    // LogoutFilter has already invalidated the session.
                    response.setHeader("Cache-Control", "no-store");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
            }
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
    }

    private static void rejectAndInvalidate(HttpServletRequest request,
                                            HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control", "no-store");
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
