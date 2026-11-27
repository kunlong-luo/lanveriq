package com.lanveriq.server;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Prevent accidental use of the public disposable test defaults against another target. */
@Configuration(proxyBeanMethods = false)
@Profile("local-test")
class LocalTestProfileConfiguration {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/lanveriq";
    private static final String USER = "lanveriq";

    @Bean
    static BeanFactoryPostProcessor localTestTargetGuard(Environment environment) {
        return ignored -> requireLocalTarget(environment);
    }

    static void requireLocalTarget(Environment env) {
        if (!Arrays.stream(env.getActiveProfiles()).allMatch(
                    Set.of("local-test", "agent-upstream-mtls-acceptance")::contains)
                || !"127.0.0.1".equals(env.getProperty("server.address"))
                || !URL.equals(env.getProperty("spring.datasource.url"))
                || !USER.equals(env.getProperty("spring.datasource.username"))
                || !URL.equals(env.getProperty("spring.datasource.hikari.jdbc-url", URL))
                || !USER.equals(env.getProperty("spring.datasource.hikari.username", USER))
                || !URL.equals(env.getProperty("spring.flyway.url", URL))
                || !USER.equals(env.getProperty("spring.flyway.user", USER))
                || !env.getProperty("spring.datasource.jndi-name", "").isBlank()) {
            throw new IllegalStateException("local-test requires the dedicated loopback development database and listener");
        }
    }
}
