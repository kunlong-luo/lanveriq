package com.lanveriq.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Map;

/**
 * AUTH-01 foundation. Password authentication is wired but intentionally not
 * HTTP-exposed: Caddy ingress, administrator bootstrap and CSRF/Origin
 * acceptance remain separate prerequisites for console login.
 */
@Configuration(proxyBeanMethods = false)
public class DefaultDenySecurityConfiguration {

    // Offline first-run bootstrap must not construct servlet HttpSecurity.
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain denyBusinessRoutes(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // 64 MiB Argon2id, 3 iterations, single lane. Tune on actual pilot CPU.
        // Only approved scheme is accepted; no {noop}/legacy fallback.
        return new DelegatingPasswordEncoder("argon2id",
                Map.of("argon2id", new Argon2PasswordEncoder(16, 32, 1, 65536, 3)));
    }

    @Bean
    AuthenticationProvider adminAuthenticationProvider(UserDetailsService adminUserDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }
}
