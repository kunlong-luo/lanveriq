package com.lanveriq.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class LocalTestProfileConfigurationTest {
    private static MockEnvironment valid() {
        var env = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/lanveriq")
                .withProperty("spring.datasource.username", "lanveriq");
        env.setActiveProfiles("test");
        return env;
    }

    @Test
    void bundledTestProfileContainsOnlyOptInFixtureDefaults() throws Exception {
        var resource = new ClassPathResource("application-test.yml");
        var env = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("bundled-test", resource)) {
            env.getPropertySources().addLast(source);
        }
        assertEquals("local-test-admin", env.getProperty("lanveriq.bootstrap.login"));
        assertEquals("local-test-devices", env.getProperty("lanveriq.bootstrap.group"));
        assertFalse(env.getProperty("lanveriq.bootstrap.enabled", Boolean.class, true));
        assertEquals("lanveriq", env.getProperty("spring.datasource.password"));
        assertEquals("Lvq!7mK9#qT4vR2x", env.getProperty("lanveriq.test-credentials.admin.password"));
        assertFalse(new ClassPathResource("application-local-test.yml").exists());
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
        tls.setActiveProfiles("test", "agent-upstream-mtls-acceptance");
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
        prod.setActiveProfiles("test", "prod");
        assertThrows(IllegalStateException.class,
                () -> LocalTestProfileConfiguration.requireLocalTarget(prod));
    }
}
