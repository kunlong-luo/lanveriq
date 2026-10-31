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

/**
 * Explicitly opt-in console READ-ONLY HTTP perimeter. Defaults remain
 * deny-all for every other business route. Never broaden to /api/v1/**.
 *
 * The matching Spring controller performs current DB group/role checks;
 * authenticated() alone is not device authorization.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = {"lanveriq.console.auth.enabled",
                "lanveriq.device-directory.enabled",
                "lanveriq.device-directory.http-enabled"},
        havingValue = "true")
public class DeviceDirectoryHttpSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain scopedDeviceHealthReads(
            HttpSecurity http, Environment environment, AdminAccountRepository accounts)
            throws Exception {
        var origin = new ConsoleOriginPolicy(
                environment.getProperty("lanveriq.console.auth.origin"));
        return http
                .securityMatcher("/api/v1/devices", "/api/v1/devices/*")
                .addFilterBefore(new ConsoleRequestGuardFilter(origin), CsrfFilter.class)
                .addFilterAfter(new ConsoleSessionRevalidationFilter(accounts),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/devices", "/api/v1/devices/*").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // No browser redirect/Basic challenge: missing or expired
                // cookie must give the future React client a stable 401.
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
