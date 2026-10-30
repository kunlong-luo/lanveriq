package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lanveriq.server.enrollment.AuthenticatedEnrollmentTokenIssuer;
import com.lanveriq.server.enrollment.EnrollmentTokenIssuer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedEnrollmentTokenIssuerTest {

    private final AdminScopeService scopes = Mockito.mock(AdminScopeService.class);
    private final EnrollmentTokenIssuer issuer = Mockito.mock(EnrollmentTokenIssuer.class);
    private final AuthenticatedEnrollmentTokenIssuer facade =
            new AuthenticatedEnrollmentTokenIssuer(scopes, issuer);
    private final UUID admin = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousAndForgedStringIdentityAreDeniedBeforeIssuance() {
        assertThrows(AccessDeniedException.class,
                () -> facade.issue(group, 1, Duration.ofMinutes(5)));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        admin.toString(), null, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> facade.issue(group, 1, Duration.ofMinutes(5)));
        verify(issuer, never()).issue(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any());
    }

    @Test
    void usesOnlyAuthenticatedPrincipalUuidAndRevalidatesRole() {
        var principal = new AdminPrincipal(admin, "owner", "{argon2id}dummy", true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        assertNull(facade.issue(group, 1, Duration.ofMinutes(5)));

        verify(scopes).require(admin, group, AdminAction.ISSUE_ENROLLMENT);
        verify(issuer).issue(admin, group, 1, Duration.ofMinutes(5));
    }

    @Test
    void disabledPrincipalNeverEntersRepository() {
        var principal = new AdminPrincipal(admin, "disabled", "{argon2id}dummy", false);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> facade.issue(group, 1, Duration.ofMinutes(5)));
        verify(issuer, never()).issue(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any());
    }
}
