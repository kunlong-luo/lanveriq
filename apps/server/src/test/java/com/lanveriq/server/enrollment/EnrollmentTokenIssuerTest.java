package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class EnrollmentTokenIssuerTest {

    private final EnrollmentTokenRepository repository = Mockito.mock(EnrollmentTokenRepository.class);
    private final EnrollmentTokenIssuer issuer =
            new EnrollmentTokenIssuer(repository, new EnrollmentTokenCodec());
    private final UUID actor = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    @Test
    void passesOnlyVerifierAndScopedActorToPersistence() {
        UUID tokenId = UUID.randomUUID();
        when(repository.insertForScopedAdmin(eq(actor), eq(group), eq(3L), eq(900), any(byte[].class)))
                .thenReturn(tokenId);

        var issued = issuer.issue(actor, group, 3, Duration.ofMinutes(15));

        assertEquals(tokenId, issued.id());
        assertEquals(Duration.ofMinutes(15), issued.lifetime());
        assertFalse(issued.toString().contains(issued.plaintext()));
        var verifier = ArgumentCaptor.forClass(byte[].class);
        verify(repository).insertForScopedAdmin(eq(actor), eq(group), eq(3L), eq(900), verifier.capture());
        assertArrayEquals(new EnrollmentTokenCodec().verifierFor(issued.plaintext()), verifier.getValue());
    }

    @Test
    void rejectsUnapprovedLifetimeAndMissingPolicyWithoutPersisting() {
        assertThrows(IllegalArgumentException.class,
                () -> issuer.issue(actor, group, 0, Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class,
                () -> issuer.issue(actor, group, 1, Duration.ofSeconds(59)));
        assertThrows(IllegalArgumentException.class,
                () -> issuer.issue(actor, group, 1, Duration.ofSeconds(901)));
        assertThrows(IllegalArgumentException.class,
                () -> issuer.issue(actor, group, 1, Duration.ofSeconds(61).plusMillis(1)));
        assertThrows(NullPointerException.class,
                () -> issuer.issue(null, group, 1, Duration.ofMinutes(5)));
        verify(repository, never()).insertForScopedAdmin(any(), any(), any(Long.class), any(Integer.class), any());
    }

    @Test
    void deniesUnauthorizedGroupWithoutLeakingIssuedSecret() {
        when(repository.insertForScopedAdmin(eq(actor), eq(group), eq(1L), eq(300), any(byte[].class)))
                .thenThrow(new AccessDeniedException("Enrollment token creation not authorized"));
        assertThrows(AccessDeniedException.class,
                () -> issuer.issue(actor, group, 1, Duration.ofMinutes(5)));
    }
}
