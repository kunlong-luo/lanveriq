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
        return new AgentIngressAssertionPolicy(environment.getRequiredProperty(
                "lanveriq.agent.ingress.expected-host"));
    }
}
