package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AdminIdentityUserDetailsServiceTest {

    private final AdminAccountRepository accounts = Mockito.mock(AdminAccountRepository.class);
    private final AdminIdentityUserDetailsService service =
            new AdminIdentityUserDetailsService(accounts);

    @Test
    void loadsOnlyJPAIdentityNeverGlobalAdminRolesAndErasesPassword() {
        UUID id = UUID.randomUUID();
        when(accounts.findByNormalizedLogin("sec.owner")).thenReturn(
                Optional.of(new AdminAccount(id, "sec.owner", "{argon2id}test-hash", null)));

        var principal = (AdminPrincipal) service.loadUserByUsername("SEC.Owner");

        assertEquals(id, principal.userId());
        assertEquals("sec.owner", principal.getUsername());
        assertEquals("{argon2id}test-hash", principal.getPassword());
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().isEmpty());
        assertFalse(principal.toString().contains("test-hash"));
        principal.eraseCredentials();
        assertNull(principal.getPassword());
        verify(accounts).findByNormalizedLogin("sec.owner");
    }

    @Test
    void disabledAccountRemainsDisabledForSpringAuthentication() {
        when(accounts.findByNormalizedLogin("disabled")).thenReturn(
                Optional.of(new AdminAccount(UUID.randomUUID(), "disabled",
                        "{argon2id}hash", Instant.parse("2026-10-30T00:00:00Z"))));
        assertFalse(service.loadUserByUsername("disabled").isEnabled());
    }

    @Test
    void unknownAndMalformedNamesFailClosedWithoutQueryLeakage() {
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing"));
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername(null));
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("a"));
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername(" admin "));
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("admin' OR TRUE"));
        verify(accounts, never()).findByNormalizedLogin("admin' OR TRUE");
    }
}
