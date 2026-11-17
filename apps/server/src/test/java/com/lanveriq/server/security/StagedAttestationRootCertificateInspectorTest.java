package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StagedAttestationRootCertificateInspectorTest {
    private final StagedAttestationRootCertificateInspector inspector =
            new StagedAttestationRootCertificateInspector(Clock.systemUTC());

    @Test
    void refusesMissingTinyAndOversizedUntrustedDer() {
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(null));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[0]));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[255]));
        assertThrows(AccessDeniedException.class, () -> inspector.inspectUntrustedDer(new byte[8193]));
    }

    @Test
    void refusesRandomBytesAndNonCanonicalCertificatePayload() {
        byte[] random = new byte[512];
        new Random(42).nextBytes(random);
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectUntrustedDer(random));
        byte[] malformed = new byte[512];
        malformed[0] = 0x30;
        assertThrows(AccessDeniedException.class,
                () -> inspector.inspectUntrustedDer(malformed));
    }
}
