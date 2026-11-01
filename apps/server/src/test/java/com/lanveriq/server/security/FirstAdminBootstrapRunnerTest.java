package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FirstAdminBootstrapRunnerTest {

    private final FirstAdminBootstrapService service =
            Mockito.mock(FirstAdminBootstrapService.class);

    /**
     * Constructor injection is deliberately tested through a REAL Spring
     * context, not by calling new FirstAdminBootstrapRunner(...). The
     * production bean has two constructors; Spring must select the marked
     * (service, environment) constructor instead of expecting a no-arg one.
     * No CLI runner executes, no database is created, and no password read.
     */
    @Test
    void optInBootstrapRunnerIsConstructibleByActualSpringContext() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("synthetic-test-bootstrap",
                            Map.of("lanveriq.bootstrap.enabled", "true")));
            context.registerBean(FirstAdminBootstrapService.class, () -> service);
            context.register(FirstAdminBootstrapRunner.class);
            context.refresh();
            assertNotNull(context.getBean(FirstAdminBootstrapRunner.class));
            verify(service, never()).bootstrap(anyString(), anyString(), any(char[].class));
        }
    }

    @Test
    void bootstrapRunnerAbsentWhenDefaultOff() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FirstAdminBootstrapService.class, () -> service);
            context.register(FirstAdminBootstrapRunner.class);
            context.refresh();
            assertEquals(0, context.getBeanNamesForType(FirstAdminBootstrapRunner.class).length);
        }
    }

    @Test
    void refusesBootstrapWhenWebServerWouldRunWithoutReadingSecret() {
        var env = new MockEnvironment()
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("lanveriq.bootstrap.login", "admin")
                .withProperty("lanveriq.bootstrap.group", "Engineering");
        var prompt = Mockito.mock(FirstAdminBootstrapRunner.PasswordPrompt.class);
        var runner = new FirstAdminBootstrapRunner(service, env, prompt);
        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(prompt, never()).read(anyString());
        verify(service, never()).bootstrap(anyString(), anyString(), any(char[].class));
    }

    @Test
    void rejectsUnconfirmedPasswordAndWipesBothArrays() {
        var env = offlineEnvironment();
        char[] secret = "very-long-synthetic-secret-123".toCharArray();
        char[] mismatch = "very-long-synthetic-secret-456".toCharArray();
        var prompt = Mockito.mock(FirstAdminBootstrapRunner.PasswordPrompt.class);
        when(prompt.read(anyString())).thenReturn(secret, mismatch);

        assertThrows(IllegalArgumentException.class,
                () -> new FirstAdminBootstrapRunner(service, env, prompt).run(null));

        assertArrayEquals(new char[secret.length], secret);
        assertArrayEquals(new char[mismatch.length], mismatch);
        verify(service, never()).bootstrap(anyString(), anyString(), any(char[].class));
    }

    @Test
    void callsServiceOnlyOnceAndAlwaysWipesTerminalPassword() {
        var env = offlineEnvironment();
        char[] secret = "very-long-synthetic-secret-123".toCharArray();
        char[] confirmation = secret.clone();
        var prompt = Mockito.mock(FirstAdminBootstrapRunner.PasswordPrompt.class);
        when(prompt.read(anyString())).thenReturn(secret, confirmation);

        new FirstAdminBootstrapRunner(service, env, prompt).run(null);

        verify(service).bootstrap("admin", "Engineering", secret);
        assertArrayEquals(new char[secret.length], secret);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }

    private static MockEnvironment offlineEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("lanveriq.bootstrap.login", "admin")
                .withProperty("lanveriq.bootstrap.group", "Engineering");
    }
}
