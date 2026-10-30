package com.lanveriq.server.security;

import java.net.URI;
import java.util.Objects;

/**
 * Exact, HTTPS-only browser origin and Host allowlist for a single console
 * virtual host. Never infer trust from forwarded headers or a LAN IP.
 */
final class ConsoleOriginPolicy {

    private final String origin;
    private final String hostname;

    ConsoleOriginPolicy(String configuredOrigin) {
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            throw new IllegalArgumentException("Console origin must be explicitly configured");
        }
        final URI uri;
        try {
            uri = URI.create(configuredOrigin);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid console HTTPS origin", e);
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null ||
                !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") ||
                !host.contains(".") ||
                uri.getUserInfo() != null || uri.getPort() != -1 ||
                !Objects.equals(uri.getRawPath(), "") ||
                uri.getRawQuery() != null || uri.getRawFragment() != null ||
                !configuredOrigin.equals("https://" + host)) {
            throw new IllegalArgumentException(
                    "Console origin must be canonical https://<dns-hostname> (port 443)");
        }
        this.origin = configuredOrigin;
        this.hostname = host;
    }

    String origin() {
        return origin;
    }

    boolean acceptsHost(String suppliedHost) {
        return hostname.equals(suppliedHost) || (hostname + ":443").equals(suppliedHost);
    }

    boolean acceptsOrigin(String suppliedOrigin) {
        return origin.equals(suppliedOrigin);
    }

    boolean acceptsFetchSite(String fetchSite) {
        return fetchSite == null || "same-origin".equals(fetchSite) || "none".equals(fetchSite);
    }
}
