package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AgentIngressAssertionConfigurationTest {

    private final AgentIngressAssertionConfiguration configuration =
            new AgentIngressAssertionConfiguration();

    private static MockEnvironment configuredTlsEnvironment() {
        return new MockEnvironment()
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.ssl.client-auth", "need")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8443")
                .withProperty("server.ssl.enabled-protocols", "TLSv1.3")
                .withProperty("server.forward-headers-strategy", "none")
                .withProperty("server.ssl.key-store", "/synthetic-only/backend-server.p12")
                .withProperty("server.ssl.trust-store", "/synthetic-only/caddy-client-ca.p12")
                .withProperty("lanveriq.agent.ingress.expected-host",
                        "agent.lanveriq.internal")
                .withProperty("lanveriq.agent.ingress.trusted-proxy-cert-sha256",
                        "a".repeat(64));
    }

    @Test
    void requiresExplicitBackendMutualTlsConfiguration() {
        var disabledTls = configuredTlsEnvironment()
                .withProperty("server.ssl.enabled", "false");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(disabledTls));

        var optionalCert = configuredTlsEnvironment()
                .withProperty("server.ssl.client-auth", "want");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(optionalCert));

        var noTrust = configuredTlsEnvironment()
                .withProperty("server.ssl.trust-store", "");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(noTrust));
    }

    @Test
    void neverAcceptsPublicOrAlternativeListenerForAgentIdentity() {
        for (String bind : new String[]{"0.0.0.0", "::", "::1", "192.0.2.100"}) {
            var insecure = configuredTlsEnvironment().withProperty("server.address", bind);
            assertThrows(IllegalStateException.class,
                    () -> configuration.agentIngressAssertionPolicy(insecure));
        }
        for (String port : new String[]{"8080", "443", "8444"}) {
            var insecure = configuredTlsEnvironment().withProperty("server.port", port);
            assertThrows(IllegalStateException.class,
                    () -> configuration.agentIngressAssertionPolicy(insecure));
        }
        var absentBind = new MockEnvironment()
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.ssl.client-auth", "need")
                .withProperty("server.ssl.enabled-protocols", "TLSv1.3")
                .withProperty("server.forward-headers-strategy", "none")
                .withProperty("server.port", "8443")
                .withProperty("server.ssl.key-store", "/synthetic/backend.p12")
                .withProperty("server.ssl.trust-store", "/synthetic/caddy.p12");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(absentBind));
    }

    @Test
    void mixedOrLegacyTlsProtocolsCannotEnableAgentIdentity() {
        for (String protocols : new String[]{"TLSv1.2", "TLSv1.3,TLSv1.2", ""}) {
            var insecure = configuredTlsEnvironment()
                    .withProperty("server.ssl.enabled-protocols", protocols);
            assertThrows(IllegalStateException.class,
                    () -> configuration.agentIngressAssertionPolicy(insecure));
        }
    }

    @Test
    void trustedForwardedHeadersCannotOverrideAgentPeerEvidence() {
        for (String strategy : new String[]{"native", "framework", ""}) {
            var insecure = configuredTlsEnvironment()
                    .withProperty("server.forward-headers-strategy", strategy);
            assertThrows(IllegalStateException.class,
                    () -> configuration.agentIngressAssertionPolicy(insecure));
        }
    }

    @Test
    void missingTlsServerIdentityFailsClosed() {
        var noServerStore = configuredTlsEnvironment()
                .withProperty("server.ssl.key-store", "");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(noServerStore));
    }

    @Test
    void missingPinnedProxyFingerprintFailsClosedEvenWithMtlsSettings() {
        var missingPin = new MockEnvironment()
                .withProperty("server.ssl.enabled", "true")
                .withProperty("server.ssl.client-auth", "need")
                .withProperty("server.ssl.key-store", "synthetic")
                .withProperty("server.ssl.trust-store", "synthetic")
                .withProperty("lanveriq.agent.ingress.expected-host", "agent.lanveriq.internal");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(missingPin));
    }

    @Test
    void explicitSyntheticSettingsCreateOnlyAnInternalParser() {
        assertNotNull(configuration.agentIngressAssertionPolicy(configuredTlsEnvironment()));
    }
}
