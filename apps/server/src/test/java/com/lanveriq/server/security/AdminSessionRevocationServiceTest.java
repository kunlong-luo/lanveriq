package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminSessionRevocationServiceTest {

    private final AdminSessionRevocationRepository repo =
            Mockito.mock(AdminSessionRevocationRepository.class);
    private final AdminSessionRevocationService service = new AdminSessionRevocationService(repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousCannotRevokeAnySessions() {
        assertThrows(AccessDeniedException.class,
                () -> service.revokeAll(UUID.randomUUID(), UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "forged administrator", null, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> service.revokeAll(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(repo);
    }

    @Test
    void validatedPrincipalIdIsOnlySourceForAccountability() {
        UUID issuer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        var principal = new AdminPrincipal(issuer, "owner", "redacted", true, 4);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        when(repo.rotateAllSessions(issuer, target, group)).thenReturn(5L);

        assertEquals(5L, service.revokeAll(target, group));

        verify(repo).rotateAllSessions(issuer, target, group);
    }
}
