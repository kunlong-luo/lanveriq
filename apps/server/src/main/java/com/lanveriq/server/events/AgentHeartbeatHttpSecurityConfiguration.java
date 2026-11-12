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
 * Agent's opt-in heartbeat POST and read-only exact-request receipt GET are
 * separate from browser sessions, console CSRF/login and other operations.
 *
 * permitAll here is ONLY an HTTP filter-chain routing decision: the endpoint
 * itself refuses any request without pinned *actual* upstream Caddy mTLS
 * certificate + canonical Host + verified fingerprint, and Spring JPA again
 * verifies the active device credential inside the write transaction.
 * This route cannot be mounted without the fail-closed TLS parser bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.heartbeat.http-enabled"},
        havingValue = "true")
class AgentHeartbeatHttpSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain scopedHeartbeatIngress(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/devices/*/heartbeats",
                        "/api/v1/devices/*/heartbeats/requests/*")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/devices/*/heartbeats").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/devices/*/heartbeats/requests/*").permitAll()
                        .anyRequest().denyAll())
                // Agent uses TLS client certificates; it never receives a
                // browser session or CSRF cookie. Disable CSRF ONLY in this
                // isolated exact-route stateless filter chain.
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
