package com.lanveriq.server.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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

    @GetMapping(path = "/api/v1/auth/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
        Object value = request.getAttribute(CsrfToken.class.getName());
        if (!(value instanceof CsrfToken token)) {
            throw new IllegalStateException("CSRF filter is not active");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("csrfToken", token.getToken()));
    }
}
