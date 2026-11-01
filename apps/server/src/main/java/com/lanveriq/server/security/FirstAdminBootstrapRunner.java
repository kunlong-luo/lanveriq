package com.lanveriq.server.security;

import java.io.Console;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Opt-in interactive offline CLI. Requires all three explicit flags:
 * --spring.main.web-application-type=none
 * --lanveriq.bootstrap.enabled=true
 * --lanveriq.bootstrap.login=<login> --lanveriq.bootstrap.group=<group>
 *
 * No password in flags/environment/files. Without a real attached console,
 * bootstrap fails closed. It is never an HTTP or remotely callable operation.
 */
@Component
@ConditionalOnProperty(prefix = "lanveriq.bootstrap", name = "enabled", havingValue = "true")
public final class FirstAdminBootstrapRunner implements ApplicationRunner {

    private final FirstAdminBootstrapService service;
    private final Environment environment;
    private final PasswordPrompt prompt;

    // Two constructors exist (production injection and a test-only prompt
    // seam). Without an explicit injection marker, Spring cannot choose
    // this one and attempts construction without a default constructor.
    @Autowired
    FirstAdminBootstrapRunner(FirstAdminBootstrapService service, Environment environment) {
        this(service, environment, new LocalConsolePasswordPrompt());
    }

    FirstAdminBootstrapRunner(FirstAdminBootstrapService service, Environment environment,
                              PasswordPrompt prompt) {
        this.service = Objects.requireNonNull(service, "service");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
    }

    @Override
    public void run(ApplicationArguments args) {
        String mode = environment.getProperty("spring.main.web-application-type", "");
        if (!"none".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Offline bootstrap requires web-application-type=none");
        }
        String login = FirstAdminBootstrapService.normalizeLogin(
                environment.getProperty("lanveriq.bootstrap.login"));
        String group = FirstAdminBootstrapService.validateGroupName(
                environment.getProperty("lanveriq.bootstrap.group"));

        char[] password = null;
        char[] confirmation = null;
        try {
            password = prompt.read("New administrator password: ");
            confirmation = prompt.read("Confirm administrator password: ");
            FirstAdminBootstrapService.validatePassword(password);
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Administrator password confirmation mismatch");
            }
            service.bootstrap(login, group, password);
            // No credential, hash or DB identifiers are printed to stdout.
            System.out.println("Initial administrator created. Stop offline bootstrap and configure console TLS.");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    interface PasswordPrompt {
        char[] read(String message);
    }

    private static final class LocalConsolePasswordPrompt implements PasswordPrompt {
        @Override
        public char[] read(String message) {
            Console console = System.console();
            if (console == null) {
                throw new IllegalStateException("Attached interactive terminal required for bootstrap");
            }
            char[] secret = console.readPassword("%s", message);
            if (secret == null) {
                throw new IllegalStateException("Password input aborted");
            }
            return secret;
        }
    }
}
