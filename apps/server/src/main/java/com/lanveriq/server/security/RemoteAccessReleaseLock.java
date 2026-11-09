package com.lanveriq.server.security;

import java.util.Locale;
import java.util.Objects;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * RD-2 release lock. The current RustDesk OSS data plane does not enforce
 * Lanveriq administrator/group/session permissions. It must NOT be activated.
 *
 * A literal false in application.yml alone is insufficient: Spring resolves
 * environment variables, system properties, SPRING_APPLICATION_JSON and other
 * higher-priority configuration sources. Reject any effective value other
 * than false BEFORE the application context finishes starting.
 *
 * This is a fail-closed startup guard, NOT a session authorization adapter.
 * Removing it requires an approved and independently tested data-plane
 * enforcement mechanism, including bypass and revocation acceptance.
 */
@Configuration(proxyBeanMethods = false)
public class RemoteAccessReleaseLock implements EnvironmentAware {

    static final String PROPERTY = "lanveriq.remote.enabled";

    @Override
    public void setEnvironment(Environment environment) {
        Objects.requireNonNull(environment, "Spring environment required");
        String effective = environment.getProperty(PROPERTY);
        if (effective == null || "false".equals(effective.strip().toLowerCase(Locale.ROOT))) {
            return;
        }
        throw new IllegalStateException(
                "Remote control cannot be enabled: RD-2 server-enforced session "
                        + "authorization and bypass/revocation acceptance are not approved");
    }
}
