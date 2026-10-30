package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FirstAdminBootstrapServiceTest {

    private final FirstAdminBootstrapRepository repository =
            Mockito.mock(FirstAdminBootstrapRepository.class);
    private final DefaultDenySecurityConfiguration security = new DefaultDenySecurityConfiguration();
    private final FirstAdminBootstrapService service =
            new FirstAdminBootstrapService(repository, security.passwordEncoder());

    @Test
    void createsOwnerUsingCanonicalLoginAndSaltedHashNeverPlaintext() {
        var expected = new FirstAdminBootstrapRepository.BootstrappedAdmin(
                UUID.randomUUID(), UUID.randomUUID());
        when(repository.createFirstOwner(anyString(), anyString(), anyString()))
                .thenReturn(expected);
        char[] password = "a-long-synthetic-bootstrap-passphrase".toCharArray();

        assertEquals(expected, service.bootstrap("OWNER.Admin", "Engineering", password));

        var login = ArgumentCaptor.forClass(String.class);
        var hash = ArgumentCaptor.forClass(String.class);
        var group = ArgumentCaptor.forClass(String.class);
        verify(repository).createFirstOwner(login.capture(), hash.capture(), group.capture());
        assertEquals("owner.admin", login.getValue());
        assertEquals("Engineering", group.getValue());
        assertTrue(hash.getValue().startsWith("{argon2id}"));
        assertFalse(hash.getValue().contains("synthetic-bootstrap-passphrase"));
        assertTrue(security.passwordEncoder().matches(
                "a-long-synthetic-bootstrap-passphrase", hash.getValue()));
    }

    @Test
    void invalidInputCannotReachDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bootstrap("a", "Engineering",
                        "a-long-synthetic-bootstrap-passphrase".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> service.bootstrap("admin", " Invalid group",
                        "a-long-synthetic-bootstrap-passphrase".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> service.bootstrap("admin", "Engineering", "short".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> service.bootstrap("admin", "Engineering",
                        "invalid-control-password\n".toCharArray()));
        verify(repository, never()).createFirstOwner(anyString(), anyString(), anyString());
    }
}
