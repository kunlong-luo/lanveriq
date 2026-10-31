package com.lanveriq.server.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Default OFF, internal-only, no HTTP route. Enabling this bean does not
 * authorize an Agent TLS deployment; actual isolation and mTLS are separate.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lanveriq.agent.ingress",
        name = "assertions-enabled", havingValue = "true")
class AgentIngressAssertionConfiguration {

    @Bean
    AgentIngressAssertionPolicy agentIngressAssertionPolicy(Environment environment) {
        // Fail startup if a caller enables parsing without backend TLS client
        // authentication. A TLS-only server cert is NOT sufficient: Tomcat
        // must demand the dedicated Caddy upstream client certificate.
        // Enabling the parser outside the dedicated acceptance connector must
        // fail at startup: even a correctly pinned proxy leaf is unsafe if
        // another interface, plaintext listener or forwarded headers can
        // influence the servlet request's claimed TLS/host identity.
        if (!environment.getProperty("server.ssl.enabled", Boolean.class, false)
                || !"need".equalsIgnoreCase(environment.getProperty("server.ssl.client-auth"))
                || environment.getProperty("server.ssl.trust-store", "").isBlank()
                || environment.getProperty("server.ssl.key-store", "").isBlank()
                || !"127.0.0.1".equals(environment.getProperty("server.address"))
                || !Integer.valueOf(8443).equals(
                        environment.getProperty("server.port", Integer.class))
                || !"TLSv1.3".equals(environment.getProperty("server.ssl.enabled-protocols"))
                || !"none".equalsIgnoreCase(
                        environment.getProperty("server.forward-headers-strategy", ""))) {
            throw new IllegalStateException(
                    "Agent assertion parsing requires isolated TLSv1.3 "
                    + "client-auth=need on 127.0.0.1:8443 without forwarded headers");
        }
        return new AgentIngressAssertionPolicy(
                environment.getRequiredProperty("lanveriq.agent.ingress.expected-host"),
                environment.getRequiredProperty(
                        "lanveriq.agent.ingress.trusted-proxy-cert-sha256"));
    }
}
