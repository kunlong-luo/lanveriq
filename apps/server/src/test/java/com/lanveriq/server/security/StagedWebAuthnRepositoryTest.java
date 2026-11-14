package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.webauthn.api.Bytes;

/**
 * Offline L1 official-interface contract tests. Mocked JPA reads only, no
 * authenticator, no registration ceremony and no HTTP route.
 */
class StagedWebAuthnRepositoryTest {
    private final AdminPasskeyUserHandleRepository handles =
            mock(AdminPasskeyUserHandleRepository.class);
    private final AdminAccountRepository accounts = mock(AdminAccountRepository.class);
    private final StagedJpaWebAuthnUserEntityRepository userAdapter =
            new StagedJpaWebAuthnUserEntityRepository(handles, accounts);

    private static byte[] handle() {
        return UUID.randomUUID().toString().replace("-", "").getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
    }

    @Test
    void exactPersistedOpaqueHandleAndCurrentEnabledAccountAreRequired() {
        var userId = UUID.randomUUID();
        var id = handle();
        var row = mock(AdminPasskeyUserHandle.class);
        when(row.userId()).thenReturn(userId);
        when(row.userHandle()).thenReturn(id);
        var account = new AdminAccount(userId, "a11-owner", "synthetic", null, 5);
        when(handles.findByUserHandle(any(byte[].class)))
                .thenReturn(Optional.of(row));
        when(handles.findById(userId)).thenReturn(Optional.of(row));
        when(accounts.findById(userId)).thenReturn(Optional.of(account));
        when(accounts.findByNormalizedLogin("a11-owner")).thenReturn(Optional.of(account));

        var byHandle = userAdapter.findById(new Bytes(id));
        assertEquals("a11-owner", byHandle.getName());
        assertArrayEquals(id, byHandle.getId().getBytes());
        assertArrayEquals(id, userAdapter.findByUsername("a11-owner")
                .getId().getBytes());

        when(accounts.findById(userId)).thenReturn(Optional.of(
                new AdminAccount(userId, "a11-owner", "synthetic",
                        java.time.Instant.now(), 6)));
        when(accounts.findByNormalizedLogin("a11-owner")).thenReturn(Optional.of(
                new AdminAccount(userId, "a11-owner", "synthetic",
                        java.time.Instant.now(), 6)));
        assertNull(userAdapter.findById(new Bytes(id)));
        assertNull(userAdapter.findByUsername("a11-owner"));
    }

    @Test
    void malformedOrAbsentHandleOrLoginCannotBecomeAnIdentity() {
        assertNull(userAdapter.findById(null));
        assertNull(userAdapter.findById(new Bytes(new byte[0])));
        assertNull(userAdapter.findById(new Bytes(new byte[31])));
        assertNull(userAdapter.findByUsername(null));
        assertNull(userAdapter.findByUsername("  "));
        assertNull(userAdapter.findByUsername("not-found"));
    }

    @Test
    void bothOfficialRepositoriesRejectRegistrationMutationAndAuthentication() {
        var credentialAdapter = new StagedDenyAllWebAuthnCredentialRepository();
        var id = new Bytes(handle());
        assertNull(credentialAdapter.findByCredentialId(id));
        assertTrue(credentialAdapter.findByUserId(id).isEmpty());
        assertThrows(AccessDeniedException.class,
                () -> credentialAdapter.save(null));
        assertThrows(AccessDeniedException.class,
                () -> credentialAdapter.delete(id));
        assertThrows(AccessDeniedException.class,
                () -> userAdapter.save(null));
        assertThrows(AccessDeniedException.class,
                () -> userAdapter.delete(id));
        verifyNoInteractions(handles, accounts);
    }
}
