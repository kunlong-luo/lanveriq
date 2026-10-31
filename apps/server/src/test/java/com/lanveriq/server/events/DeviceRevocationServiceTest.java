package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lanveriq.server.security.AdminPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DeviceRevocationServiceTest {
    private final DeviceRevocationRepository repository =
            Mockito.mock(DeviceRevocationRepository.class);
    private final DeviceRevocationService service = new DeviceRevocationService(repository);
    private final UUID deviceId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticatedAdmin(boolean enabled) {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(enabled);
        if (enabled) when(principal.userId()).thenReturn(adminId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void rejectsAbsentAndDisabledAdministratorBeforeDatabase() {
        assertThrows(AccessDeniedException.class, () -> service.revoke(deviceId));
        authenticatedAdmin(false);
        assertThrows(AccessDeniedException.class, () -> service.revoke(deviceId));
        verify(repository, never()).revoke(Mockito.any(), Mockito.any());
    }

    @Test
    void failsClosedOnNonAdminPrincipalAndUnauthenticatedToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("untrusted-user", null));
        assertThrows(AccessDeniedException.class, () -> service.revoke(deviceId));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("untrusted-user", null, List.of()));
        assertThrows(AccessDeniedException.class, () -> service.revoke(deviceId));
        verify(repository, never()).revoke(Mockito.any(), Mockito.any());
    }

    @Test
    void rejectsMissingOrNilDeviceIdentifierBeforeAnyAuthorizationLookup() {
        authenticatedAdmin(true);
        assertThrows(IllegalArgumentException.class, () -> service.revoke(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.revoke(new UUID(0, 0)));
        verify(repository, never()).revoke(Mockito.any(), Mockito.any());
    }

    @Test
    void authenticatedAdministratorStillNeedsFreshDatabaseRoleAndGroupScope() {
        authenticatedAdmin(true);
        when(repository.revoke(adminId, deviceId))
                .thenThrow(new AccessDeniedException("not authorized"));
        assertThrows(AccessDeniedException.class, () -> service.revoke(deviceId));
        verify(repository).revoke(adminId, deviceId);
    }

    @Test
    void firstRevocationIsDistinctFromAuthorizedIdempotentRetry() {
        authenticatedAdmin(true);
        when(repository.revoke(adminId, deviceId)).thenReturn(true, false);
        assertTrue(service.revoke(deviceId));
        assertFalse(service.revoke(deviceId));
        verify(repository, Mockito.times(2)).revoke(adminId, deviceId);
    }
}
