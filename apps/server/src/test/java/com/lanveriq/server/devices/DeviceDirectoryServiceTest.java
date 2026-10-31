package com.lanveriq.server.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lanveriq.server.security.AdminPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DeviceDirectoryServiceTest {
    private final UUID adminId = UUID.randomUUID();
    private final UUID device1 = UUID.randomUUID();
    private final UUID device2 = UUID.randomUUID();
    private final UUID device3 = UUID.randomUUID();
    private final DeviceDirectoryRepository repo = Mockito.mock(DeviceDirectoryRepository.class);
    private final DeviceDirectoryService service = new DeviceDirectoryService(repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(boolean enabled) {
        AdminPrincipal admin = Mockito.mock(AdminPrincipal.class);
        when(admin.isEnabled()).thenReturn(enabled);
        if (enabled) when(admin.userId()).thenReturn(adminId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(admin, null, List.of()));
    }

    private static DeviceDirectoryEntry entry(UUID deviceId) {
        return new DeviceDirectoryEntry(deviceId, UUID.randomUUID(), "Test group",
                "Synthetic device", "active", null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void listRequiresAuthenticatedEnabledAdministratorAndNoDatabaseCallsOtherwise() {
        assertThrows(AccessDeniedException.class, () -> service.list(null, 10));
        login(false);
        assertThrows(AccessDeniedException.class, () -> service.list(null, 10));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("plain-string", null, List.of()));
        assertThrows(AccessDeniedException.class, () -> service.list(null, 10));
        verify(repo, never()).list(Mockito.any(), Mockito.any(), Mockito.anyInt());
    }

    @Test
    void boundedKeysetPaginationReturnsOnlyVisibleItemsAndNextDeviceCursor() {
        login(true);
        when(repo.list(adminId, null, 3)).thenReturn(
                List.of(entry(device1), entry(device2), entry(device3)));
        var first = service.list(null, 2);
        assertEquals(2, first.items().size());
        assertEquals(device2, first.nextCursor());
        assertEquals(device1, first.items().getFirst().deviceId());
        assertThrows(UnsupportedOperationException.class,
                () -> first.items().add(entry(UUID.randomUUID())));
        verify(repo).list(adminId, null, 3);

        when(repo.list(adminId, device2, 3)).thenReturn(List.of(entry(device3)));
        var last = service.list(device2, 2);
        assertEquals(device3, last.items().getFirst().deviceId());
        assertNull(last.nextCursor());
        verify(repo).list(adminId, device2, 3);
    }

    @Test
    void missingDeviceUsesNonEnumeratingEmptyResult() {
        login(true);
        when(repo.find(adminId, device1)).thenReturn(Optional.empty());
        assertFalse(service.find(device1).isPresent());
        verify(repo).find(adminId, device1);
        when(repo.find(adminId, device2)).thenReturn(Optional.of(entry(device2)));
        assertTrue(service.find(device2).isPresent());
    }

    @Test
    void limitsAndMalformedIdentifiersFailBeforeDatabase() {
        login(true);
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 101));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(new UUID(0, 0), 10));
        assertThrows(IllegalArgumentException.class, () -> service.find(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.find(new UUID(0, 0)));
        verify(repo, never()).list(Mockito.any(), Mockito.any(), Mockito.anyInt());
        verify(repo, never()).find(Mockito.any(), Mockito.any());
    }
}
