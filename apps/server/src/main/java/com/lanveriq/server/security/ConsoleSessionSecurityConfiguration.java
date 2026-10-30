package com.lanveriq.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Staged browser session design. Disabled by default. No business REST/WSS
 * permissions are granted, even when the operator explicitly enables this
 * narrow console authentication slice in a synthetic TLS acceptance sandbox.
 *
 * DO NOT enable before trusted console TLS, login throttling and browser
 * negative tests. This feature flag is not a production approval.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "lanveriq.console.auth", name = "enabled", havingValue = "true")
public class ConsoleSessionSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain consoleAuthenticationOnly(
            HttpSecurity http, AuthenticationProvider adminAuthenticationProvider,
            Environment environment) throws Exception {
        var policy = new ConsoleOriginPolicy(
                environment.getProperty("lanveriq.console.auth.origin"));
        var csrfTokens = new HttpSessionCsrfTokenRepository();

        return http
                .securityMatcher("/api/v1/auth/**")
                .addFilterBefore(new ConsoleRequestGuardFilter(policy),
                        CsrfFilter.class)
                .authenticationProvider(adminAuthenticationProvider)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .formLogin(login -> login
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setStatus(204);
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.sendError(401);
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setStatus(204);
                        }))
                .build();
    }
}
