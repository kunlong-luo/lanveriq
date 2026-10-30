package com.lanveriq.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SRV-01 bootstrap: all business routes remain closed until AUTH-01/SRV-02
 * implement real administrator sessions and certificate-bound Agent identity.
 */
@Configuration(proxyBeanMethods = false)
public class DefaultDenySecurityConfiguration {

    @Bean
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

    /** Prevent Spring Security from logging a generated bootstrap password. */
    @Bean
    UserDetailsService noDefaultAdministrator() {
        return username -> {
            throw new UsernameNotFoundException("Administrator bootstrap is not implemented");
        };
    }
}
