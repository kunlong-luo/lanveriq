package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

class FirstAdminBootstrapRunnerTest {

    private final FirstAdminBootstrapService service =
            Mockito.mock(FirstAdminBootstrapService.class);

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
