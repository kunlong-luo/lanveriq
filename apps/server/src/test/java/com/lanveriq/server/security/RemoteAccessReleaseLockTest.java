package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pure Spring Environment resolution negative tests; no DB, real endpoint,
 * remote control process, permission token or HTTP session.
 */
class RemoteAccessReleaseLockTest {

    private final RemoteAccessReleaseLock lock = new RemoteAccessReleaseLock();

    @Test
    void missingOrExplicitFalseConfigIsSafe() {
        assertDoesNotThrow(() -> lock.setEnvironment(new MockEnvironment()));
        assertDoesNotThrow(() -> lock.setEnvironment(
                new MockEnvironment().withProperty("lanveriq.remote.enabled", "false")));
        assertDoesNotThrow(() -> lock.setEnvironment(
                new MockEnvironment().withProperty("lanveriq.remote.enabled", "FALSE")));
    }

    @Test
    void attemptToEnableRemoteIsRejectedAtConfigurationInitialization() {
        var env = new MockEnvironment().withProperty("lanveriq.remote.enabled", "true");
        var error = assertThrows(IllegalStateException.class,
                () -> lock.setEnvironment(env));
        assertTrue(error.getMessage().contains("cannot be enabled"));
    }

    @Test
    void higherPriorityEnvironmentOverrideCannotDefeatLiteralYamlFalse() {
        var env = new MockEnvironment().withProperty("lanveriq.remote.enabled", "false");
        // Mimics SPRING_APPLICATION_JSON / OS env / system-property precedence,
        // where a higher-priority source wins over application.yml.
        env.getPropertySources().addFirst(
                new MapPropertySource("higher-priority-override",
                        Map.of("lanveriq.remote.enabled", "true")));
        assertThrows(IllegalStateException.class, () -> lock.setEnvironment(env));
    }

    @Test
    void realSpringContextRejectsEffectiveOverrideDuringRefresh() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(RemoteAccessReleaseLock.class);
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("external",
                            Map.of("lanveriq.remote.enabled", "true")));
            RuntimeException error = assertThrows(RuntimeException.class, context::refresh);
            Throwable current = error;
            boolean foundGate = false;
            while (current != null) {
                if (current.getMessage() != null
                        && current.getMessage().contains("Remote control cannot be enabled")) {
                    foundGate = true;
                    break;
                }
                current = current.getCause();
            }
            assertTrue(foundGate, "Startup must reject a higher-priority override");
        }
    }

    @Test
    void ambiguousOrMalformedFlagFailsClosedInsteadOfBooleanParseFalse() {
        for (var value : new String[]{"enabled", "1", "yes", "FALSEISH", "not-validated"}) {
            var env = new MockEnvironment().withProperty("lanveriq.remote.enabled", value);
            assertThrows(IllegalStateException.class,
                    () -> lock.setEnvironment(env), "Invalid flag: " + value);
        }
    }
}
