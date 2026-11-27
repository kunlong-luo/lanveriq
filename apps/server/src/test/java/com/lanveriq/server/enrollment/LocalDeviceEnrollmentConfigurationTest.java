package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class LocalDeviceEnrollmentConfigurationTest {

    private static MockEnvironment valid(String action) {
        return new MockEnvironment()
                .withProperty("lanveriq.local-enrollment.action", action)
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.datasource.url", LocalDeviceEnrollmentConfiguration.TEST_URL)
                .withProperty("spring.datasource.username", LocalDeviceEnrollmentConfiguration.TEST_DB)
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("lanveriq.client-cert-registration.enabled",
                        Boolean.toString("register".equals(action)));
    }

    @Test
    void ordinaryApplicationDoesNotMountLocalEnrollmentConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalDeviceEnrollmentConfiguration.class)
                .run(context -> org.assertj.core.api.Assertions.assertThat(context)
                        .doesNotHaveBean("localDeviceEnrollmentRunner"));
    }

    @Test
    void onlyOfflineDedicatedDatabaseModesAreAllowed() {
        for (String action : new String[]{"prepare", "register"}) {
            assertDoesNotThrow(() -> LocalDeviceEnrollmentConfiguration
                    .requireLocalConfiguration(valid(action)));
        }
        for (var entry : Map.ofEntries(
                Map.entry("lanveriq.local-enrollment.action", "activate"),
                Map.entry("spring.main.web-application-type", "servlet"),
                Map.entry("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/lanveriq"),
                Map.entry("spring.datasource.username", "lanveriq"),
                Map.entry("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://127.0.0.1:5432/iot_farm"),
                Map.entry("spring.datasource.hikari.username", "root"),
                Map.entry("spring.datasource.jndi-name", "external"),
                Map.entry("spring.flyway.enabled", "true"),
                Map.entry("spring.sql.init.mode", "always"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "create"),
                Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "update")
        ).entrySet()) {
            var env = valid("prepare").withProperty(entry.getKey(), entry.getValue());
            assertThrows(IllegalStateException.class, () ->
                    LocalDeviceEnrollmentConfiguration.requireLocalConfiguration(env), entry.getKey());
        }
    }

    @Test
    void incompatibleCapabilityFlagsAreRefused() {
        for (String flag : new String[]{"lanveriq.bootstrap.enabled", "lanveriq.console.auth.enabled",
                "lanveriq.agent.ingress.assertions-enabled", "lanveriq.agent.activation.http-enabled",
                "lanveriq.agent.heartbeat.http-enabled", "lanveriq.agent.inventory.http-enabled",
                "lanveriq.agent.credential-epoch.http-enabled", "lanveriq.device-directory.enabled",
                "lanveriq.device-directory.http-enabled", "lanveriq.monitoring.overview-http-enabled",
                "lanveriq.device-revocation.enabled", "lanveriq.redis.enabled", "lanveriq.remote.enabled",
                "lanveriq.client-cert-registration.enabled"}) {
            var env = valid("prepare").withProperty(flag, "true");
            assertThrows(IllegalStateException.class, () ->
                    LocalDeviceEnrollmentConfiguration.requireLocalConfiguration(env), flag);
        }
        assertThrows(IllegalStateException.class, () ->
                LocalDeviceEnrollmentConfiguration.requireLocalConfiguration(
                        valid("register").withProperty("lanveriq.client-cert-registration.enabled", "false")));
    }

    @Test
    void manualModeRejectsCiEvenIfVariableIsEmpty() {
        assertDoesNotThrow(() -> LocalDeviceEnrollmentConfiguration.requireManualExecution(Map.of()));
        for (String variable : new String[]{"CI", "GITHUB_ACTIONS"}) {
            assertThrows(IllegalStateException.class, () ->
                    LocalDeviceEnrollmentConfiguration.requireManualExecution(Map.of(variable, "")));
        }
    }
}
