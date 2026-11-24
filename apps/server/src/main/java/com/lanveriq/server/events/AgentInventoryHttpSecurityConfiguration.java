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
 * Exact-path stateless routing, not independent authorization. Only the
 * pinned Caddy/Tomcat mTLS assertion plus JPA active credential can accept
 * inventory; every other URL and method is rejected.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {
        "lanveriq.agent.ingress.assertions-enabled",
        "lanveriq.agent.inventory.http-enabled"}, havingValue = "true")
class AgentInventoryHttpSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    SecurityFilterChain scopedInventoryIngress(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/devices/*/inventory")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/devices/*/inventory").permitAll()
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
