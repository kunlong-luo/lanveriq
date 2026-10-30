package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Exercises the actual Spring Security provider, not a custom password comparison. */
class AdminDaoAuthenticationProviderTest {

    private final DefaultDenySecurityConfiguration config =
            new DefaultDenySecurityConfiguration();

    @Test
    void databaseBackedArgon2PasswordAuthenticatesAndRejectsWrongPassword() {
        var repo = Mockito.mock(AdminAccountRepository.class);
        var encoder = config.passwordEncoder();
        UUID id = UUID.randomUUID();
        String encoded = encoder.encode("synthetic-only-test-passphrase");
        when(repo.findByNormalizedLogin("operator")).thenReturn(Optional.of(
                new AdminAccount(id, "operator", encoded, null)));

        var provider = config.adminAuthenticationProvider(
                new AdminIdentityUserDetailsService(repo), encoder);
        var manager = new ProviderManager(provider);
        var result = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                "operator", "synthetic-only-test-passphrase"));

        assertTrue(result.isAuthenticated());
        assertEquals(id, assertInstanceOf(AdminPrincipal.class, result.getPrincipal()).userId());
        assertThrows(BadCredentialsException.class,
                () -> manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                        "operator", "invalid-passphrase")));
    }

    @Test
    void disabledAccountIsRejectedBeforeAnySessionCreation() {
        var repo = Mockito.mock(AdminAccountRepository.class);
        var encoder = config.passwordEncoder();
        when(repo.findByNormalizedLogin("disabled")).thenReturn(Optional.of(
                new AdminAccount(UUID.randomUUID(), "disabled",
                        encoder.encode("synthetic-only-test-passphrase"),
                        Instant.parse("2026-10-30T00:00:00Z"))));
        var manager = new ProviderManager(config.adminAuthenticationProvider(
                new AdminIdentityUserDetailsService(repo), encoder));

        assertThrows(DisabledException.class,
                () -> manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                        "disabled", "synthetic-only-test-passphrase")));
    }
}
