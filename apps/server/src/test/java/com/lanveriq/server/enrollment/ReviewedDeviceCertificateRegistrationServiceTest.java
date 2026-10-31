package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lanveriq.server.security.AdminPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ReviewedDeviceCertificateRegistrationServiceTest {

    private final UUID deviceId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final DeviceCertificateRegistrationRepository repository =
            Mockito.mock(DeviceCertificateRegistrationRepository.class);
    private final ClientCertificateProfileValidator validator =
            Mockito.mock(ClientCertificateProfileValidator.class);
    private final ReviewedDeviceCertificateRegistrationService service =
            new ReviewedDeviceCertificateRegistrationService(repository, validator);

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAdmin(boolean enabled) {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(enabled);
        if (enabled) when(principal.userId()).thenReturn(adminId);
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void authenticatedOwnerRequiresDatabaseScopeAndStoredSpkiBeforeAnyInsert() {
        authenticateAdmin(true);
        var context = new DeviceCertificateRegistrationRepository.PendingReview(
                deviceId, UUID.randomUUID(), "a".repeat(64));
        var verified = new ClientCertificateProfileValidator.VerifiedLeaf(
                "b".repeat(64), "c".repeat(64), "42", "a".repeat(64),
                Instant.parse("2026-10-30T00:00:00Z"),
                Instant.parse("2026-12-30T00:00:00Z"));
        when(repository.lockPendingForScopedAdmin(adminId, deviceId)).thenReturn(context);
        when(validator.validate("signed-test-public-leaf", deviceId, context.csrSpkiSha256()))
                .thenReturn(verified);

        var registered = service.register(deviceId, "signed-test-public-leaf");

        assertEquals(deviceId, registered.deviceId());
        assertEquals(verified.fingerprint(), registered.fingerprint());
        assertEquals(verified.expiresAt(), registered.expiresAt());
        var order = Mockito.inOrder(repository, validator);
        order.verify(repository).lockPendingForScopedAdmin(adminId, deviceId);
        order.verify(validator).validate("signed-test-public-leaf",
                deviceId, context.csrSpkiSha256());
        order.verify(repository).registerReviewed(adminId, deviceId, verified);
    }

    @Test
    void unauthenticatedOrDisabledAdministratorNeverQueriesOrRegisters() {
        assertThrows(AccessDeniedException.class,
                () -> service.register(deviceId, "signed-test-public-leaf"));
        authenticateAdmin(false);
        assertThrows(AccessDeniedException.class,
                () -> service.register(deviceId, "signed-test-public-leaf"));
        verify(repository, never()).lockPendingForScopedAdmin(
                Mockito.any(), Mockito.any());
    }

    @Test
    void invalidCertificateCannotRegisterEvenIfAdminHasScope() {
        authenticateAdmin(true);
        var pending = new DeviceCertificateRegistrationRepository.PendingReview(
                deviceId, UUID.randomUUID(), "a".repeat(64));
        when(repository.lockPendingForScopedAdmin(adminId, deviceId))
                .thenReturn(pending);
        when(validator.validate("untrusted", deviceId, pending.csrSpkiSha256()))
                .thenThrow(new IllegalArgumentException("Invalid leaf"));

        assertThrows(IllegalArgumentException.class,
                () -> service.register(deviceId, "untrusted"));
        verify(repository, never()).registerReviewed(Mockito.any(),
                Mockito.any(), Mockito.any());
    }
}
