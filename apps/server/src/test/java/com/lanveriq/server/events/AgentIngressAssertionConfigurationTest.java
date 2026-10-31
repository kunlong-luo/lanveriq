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
                .withProperty("server.ssl.key-store", "/synthetic-only/backend-server.p12")
                .withProperty("server.ssl.trust-store", "/synthetic-only/caddy-client-ca.p12")
                .withProperty("lanveriq.agent.ingress.expected-host",
                        "agent.lanveriq.internal")
                .withProperty("lanveriq.agent.ingress.trusted-proxy-cert-sha256",
                        "a".repeat(64));
    }

    @Test
    void requiresExplicitBackendMutualTlsConfiguration() {
        var missing = configuredTlsEnvironment()
                .withProperty("server.ssl.enabled", "false");
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(missing));

        missing = configuredTlsEnvironment()
                .withProperty("server.ssl.client-auth", "want");
        MockEnvironment optionalCert = missing;
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(optionalCert));

        missing = configuredTlsEnvironment()
                .withProperty("server.ssl.trust-store", "");
        MockEnvironment noTrust = missing;
        assertThrows(IllegalStateException.class,
                () -> configuration.agentIngressAssertionPolicy(noTrust));
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
