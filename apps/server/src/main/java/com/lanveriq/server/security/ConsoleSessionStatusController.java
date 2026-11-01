package com.lanveriq.server.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimum UI session-status contract; username only, no password, grants,
 * network attributes, session version, CSRF value or bearer cookie.
 * The auth security chain revalidates DB account state on every request.
 */
@RestController
@SecurityRequirement(name = "consoleSession")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "lanveriq.console.auth",
        name = "enabled", havingValue = "true")
public final class ConsoleSessionStatusController {
    @Schema(name = "ConsoleSessionInfo")
    public record SessionInfo(@JsonProperty("login") String login) {
    }

    @Operation(summary = "Read the current authenticated administrator session",
            description = "No role/grant, cookie, credential or lifetime claims")
    @ApiResponse(responseCode = "200", description = "Current active session")
    @ApiResponse(responseCode = "401", description = "No active administrator session")
    @GetMapping(path = "/api/v1/auth/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessionInfo> current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated administrator session required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionInfo(admin.getUsername()));
    }
}
