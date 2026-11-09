package com.lanveriq.server.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * A single Agent activation POST is isolated from browser authentication and
 * the heartbeat chain. permitAll is only filter routing: actual upstream mTLS
 * certificate, strict Host/route, verified client fingerprint and currently
 * reviewed pending credential are all required by controller and JPA.
 *
 * Both flags are OFF in the normal application configuration. Never enable
 * in a LAN or production deployment without two-hop TLS/PG18 negative tests.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.activation.http-enabled"},
        havingValue = "true")
class AgentActivationHttpSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    SecurityFilterChain scopedActivationIngress(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/devices/*/activate")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/devices/*/activate").permitAll()
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }
}
