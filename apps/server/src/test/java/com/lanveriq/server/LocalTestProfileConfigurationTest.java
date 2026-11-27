package com.lanveriq.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class LocalTestProfileConfigurationTest {
    private static MockEnvironment valid() {
        var env = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/lanveriq")
                .withProperty("spring.datasource.username", "lanveriq");
        env.setActiveProfiles("local-test");
        return env;
    }

    @Test
    void defaultApplicationDoesNotMountTestGuard() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalTestProfileConfiguration.class)
                .run(context -> org.assertj.core.api.Assertions.assertThat(context)
                        .doesNotHaveBean("localTestTargetGuard"));
    }

    @Test
    void onlyDedicatedLocalTargetsAllowed() {
        assertDoesNotThrow(() -> LocalTestProfileConfiguration.requireLocalTarget(valid()));
        var tls = valid();
        tls.setActiveProfiles("local-test", "agent-upstream-mtls-acceptance");
        assertDoesNotThrow(() -> LocalTestProfileConfiguration.requireLocalTarget(tls));
        for (var entry : Map.of(
                "server.address", "0.0.0.0",
                "spring.datasource.url", "jdbc:postgresql://example.invalid/lanveriq",
                "spring.datasource.username", "root",
                "spring.datasource.hikari.jdbc-url", "jdbc:postgresql://127.0.0.1:5432/iot_farm",
                "spring.datasource.hikari.username", "root",
                "spring.flyway.url", "jdbc:postgresql://127.0.0.1:5432/iot_farm",
                "spring.flyway.user", "root",
                "spring.datasource.jndi-name", "other").entrySet()) {
            var env = valid().withProperty(entry.getKey(), entry.getValue());
            assertThrows(IllegalStateException.class,
                    () -> LocalTestProfileConfiguration.requireLocalTarget(env), entry.getKey());
        }
        var prod = valid();
        prod.setActiveProfiles("local-test", "prod");
        assertThrows(IllegalStateException.class,
                () -> LocalTestProfileConfiguration.requireLocalTarget(prod));
    }
}
