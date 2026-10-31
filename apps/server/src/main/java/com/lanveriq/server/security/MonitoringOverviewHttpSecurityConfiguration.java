package com.lanveriq.server.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/** Only the overview GET is opt-in; all other business paths remain denied. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {
        "lanveriq.console.auth.enabled",
        "lanveriq.device-directory.enabled",
        "lanveriq.monitoring.overview-http-enabled"
}, havingValue = "true")
public class MonitoringOverviewHttpSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain consoleMonitoringOverviewReads(
            HttpSecurity http, Environment environment, AdminAccountRepository accounts)
            throws Exception {
        var policy = new ConsoleOriginPolicy(
                environment.getProperty("lanveriq.console.auth.origin"));
        return http
                .securityMatcher("/api/v1/monitoring/overview")
                .addFilterBefore(new ConsoleRequestGuardFilter(policy), CsrfFilter.class)
                .addFilterAfter(new ConsoleSessionRevalidationFilter(accounts),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/monitoring/overview")
                        .authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, error) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }
}
