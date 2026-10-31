package com.lanveriq.server.events;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Internal-only parser for a prospective Caddy-verified peer assertion.
 *
 * IMPORTANT: Headers and loopback alone NEVER prove mTLS! This policy MUST
 * remain unused by public controllers until a reviewed Caddy configuration,
 * exclusive upstream isolation and live negative probes are accepted.
 * Actual per-request DB certificate status is rechecked by the service.
 */
final class AgentIngressAssertionPolicy {
    static final String FINGERPRINT_HEADER =
            "X-Lanveriq-Verified-Client-Fingerprint";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Set<String> ROUTE_SUFFIXES =
            Set.of("activate", "heartbeats", "event-batches", "inventory");

    private final String expectedHost;

    AgentIngressAssertionPolicy(String expectedHost) {
        if (expectedHost == null
                || !expectedHost.matches("[a-z0-9][a-z0-9.-]{2,250}")
                || !expectedHost.startsWith("agent.")
                || expectedHost.endsWith(".")
                || expectedHost.contains("..")) {
            throw new IllegalArgumentException("Explicit canonical Agent host required");
        }
        this.expectedHost = expectedHost;
    }

    VerifiedDeviceCertificate resolve(HttpServletRequest request, UUID pathDeviceId,
                                      String endpointSuffix) {
        if (request == null || pathDeviceId == null || endpointSuffix == null
                || pathDeviceId.equals(new UUID(0, 0))
                || !ROUTE_SUFFIXES.contains(endpointSuffix)
                || !"POST".equals(request.getMethod())
                || !"127.0.0.1".equals(request.getRemoteAddr())
                || !expectedHost.equals(singleHeader(request, "Host"))
                || !("/api/v1/devices/" + pathDeviceId + "/" + endpointSuffix)
                        .equals(request.getRequestURI())
                || request.getQueryString() != null) {
            throw denied();
        }

        // Reject lookalike aliases and extra assertions. HTTP headers are
        // case-insensitive; their spelling must not create a second identity.
        var names = request.getHeaderNames();
        if (names == null) {
            throw denied();
        }
        for (String name : Collections.list(names)) {
            if (name == null) {
                throw denied();
            }
            String lower = name.toLowerCase(Locale.ROOT);
            String canonical = lower.replace('_', '-');
            if (canonical.startsWith("x-lanveriq-verified-client-")
                    && !lower.equals(FINGERPRINT_HEADER.toLowerCase(Locale.ROOT))) {
                throw denied();
            }
            if (canonical.equals("x-forwarded-client-cert")
                    || canonical.equals("ssl-client-cert")
                    || canonical.equals("x-lanveriq-verified-ingress")) {
                throw denied();
            }
        }

        String fingerprint = singleHeader(request, FINGERPRINT_HEADER);
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                || EMPTY_SHA256.equals(fingerprint)) {
            throw denied();
        }
        return new VerifiedDeviceCertificate(pathDeviceId, fingerprint);
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> headers = request.getHeaders(name);
        if (headers == null || !headers.hasMoreElements()) {
            return null;
        }
        String value = headers.nextElement();
        if (value == null || headers.hasMoreElements() || value.indexOf(',') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw denied();
        }
        return value;
    }

    private static SecurityException denied() {
        return new SecurityException("Untrusted Agent ingress assertion");
    }
}
