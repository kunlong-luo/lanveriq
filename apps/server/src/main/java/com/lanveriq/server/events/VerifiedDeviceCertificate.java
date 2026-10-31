package com.lanveriq.server.events;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * INTERNAL ONLY. This is not proof of a TLS handshake by itself.
 * A future Caddy-backed ingress adapter must populate it exclusively from
 * a verified client certificate fingerprint, never request JSON or headers.
 * No HTTP controller or adapter exposes this service today.
 */
record VerifiedDeviceCertificate(UUID deviceId, String sha256Fingerprint) {

    VerifiedDeviceCertificate {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(sha256Fingerprint, "fingerprint");
        if (deviceId.equals(new UUID(0, 0)) ||
                !sha256Fingerprint.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Invalid verified device certificate evidence");
        }
        sha256Fingerprint = sha256Fingerprint.toLowerCase(Locale.ROOT);
    }
}
