package com.lanveriq.server.security;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same-origin CSRF bootstrap for the currently disabled console-auth slice.
 * Browser sends this server-issued value in X-CSRF-TOKEN on form login/logout.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "lanveriq.console.auth", name = "enabled", havingValue = "true")
final class ConsoleCsrfController {
    @Schema(name = "ConsoleCsrfToken")
    record CsrfResponse(@JsonProperty("csrfToken") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String csrfToken) {
    }

    @Operation(summary = "Get the current session CSRF token",
            description = "Same-origin session bootstrap; refresh the token after login. " +
                    "Never cache, persist or send the token to another origin.")
    @ApiResponse(responseCode = "200", description = "CSRF token for this session")
    @GetMapping(path = "/api/v1/auth/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CsrfResponse> csrf(HttpServletRequest request) {
        Object value = request.getAttribute(CsrfToken.class.getName());
        if (!(value instanceof CsrfToken token)) {
            throw new IllegalStateException("CSRF filter is not active");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getToken()));
    }
}
