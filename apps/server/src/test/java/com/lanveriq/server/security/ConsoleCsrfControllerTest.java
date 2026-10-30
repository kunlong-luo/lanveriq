package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class ConsoleCsrfControllerTest {

    private final ConsoleCsrfController controller = new ConsoleCsrfController();

    @Test
    void returnsNoStoreTokenOnlyWhenSpringCsrfFilterProvidedIt() {
        var req = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-csrf-only");
        req.setAttribute(CsrfToken.class.getName(), token);
        var response = controller.csrf(req);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("synthetic-csrf-only", response.getBody().get("csrfToken"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void rejectsMissingCsrfFilterOrToken() {
        var req = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
        assertThrows(IllegalStateException.class, () -> controller.csrf(req));
    }
}
