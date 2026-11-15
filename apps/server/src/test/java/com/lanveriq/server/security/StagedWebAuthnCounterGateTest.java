package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Fast L1 fail-closed input checks for the unregistered, non-Bean staging gate.
 * A green test is not WebAuthn signature, UV or administrator MFA acceptance.
 */
class StagedWebAuthnCounterGateTest {
    private final EntityManager manager = mock(EntityManager.class);
    private final StagedWebAuthnCounterGate gate = new StagedWebAuthnCounterGate(manager);
    private final UUID actor = UUID.randomUUID();
    private final byte[] credential = new byte[32];
    private final String rpId = "console.lanveriq.internal";

    @Test
    void rejectsInvalidIdentityBeforeAnyDatabaseAccess() {
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(null, credential, rpId, 0, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, null, rpId, 0, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, new byte[15], rpId, 0, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, new byte[1025], rpId, 0, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, null, 0, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, " ", 0, 1, false));
        verifyNoInteractions(manager);
    }

    @Test
    void rejectsCounterRollbackReplayAndOutOfRangeBeforeAnyDatabaseAccess() {
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, -1, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, 1, -1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, 1, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, 2, 1, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, 2, 0, false));
        assertThrows(AccessDeniedException.class,
                () -> gate.compareAndAdvance(actor, credential, rpId, 0, 4_294_967_296L, false));
        verifyNoInteractions(manager);
    }
}
