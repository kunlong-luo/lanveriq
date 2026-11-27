package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminAuthenticationAudit;
import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.io.Console;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit local development enrollment, not a production enrollment route.
 * No private key input, issuer, fabricated administrator or active-state write.
 * Uses a separately provisioned local test DB, existing real admin credentials
 * and the same scoped token, CSR and pending-certificate transactions.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lanveriq.local-enrollment", name = "enabled",
        havingValue = "true")
class LocalDeviceEnrollmentConfiguration {

    static final String TEST_DB = "lanveriq_local_test";
    static final String TEST_URL = "jdbc:postgresql://127.0.0.1:5432/" + TEST_DB;
    static final String TEST_GROUP = "local-test-devices";

    // Runs before singleton initialization, including Flyway and DataSource.
    @Bean
    static BeanFactoryPostProcessor localEnrollmentEarlyGuard(Environment environment) {
        return ignored -> requireLocalConfiguration(environment);
    }

    static void requireLocalConfiguration(Environment env) {
        String action = env.getProperty("lanveriq.local-enrollment.action", "");
        if (!List.of("prepare", "register").contains(action)
                || !"none".equals(env.getProperty("spring.main.web-application-type"))
                || !TEST_URL.equals(env.getProperty("spring.datasource.url"))
                || !TEST_DB.equals(env.getProperty("spring.datasource.username"))
                || !Boolean.FALSE.equals(env.getProperty("spring.flyway.enabled", Boolean.class))
                || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                || env.getProperty("lanveriq.client-cert-registration.enabled", Boolean.class, false)
                    != "register".equals(action)) {
            throw new IllegalStateException("Local enrollment requires dedicated offline test configuration");
        }
        for (String flag : List.of("lanveriq.bootstrap.enabled",
                "lanveriq.console.auth.enabled", "lanveriq.agent.ingress.assertions-enabled",
                "lanveriq.agent.activation.http-enabled", "lanveriq.agent.heartbeat.http-enabled",
                "lanveriq.agent.inventory.http-enabled", "lanveriq.agent.credential-epoch.http-enabled",
                "lanveriq.device-directory.enabled", "lanveriq.device-directory.http-enabled",
                "lanveriq.monitoring.overview-http-enabled", "lanveriq.device-revocation.enabled",
                "lanveriq.redis.enabled", "lanveriq.remote.enabled")) {
            if (env.getProperty(flag, Boolean.class, false)) {
                throw new IllegalStateException("Other capabilities must stay disabled during local enrollment");
            }
        }
        if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
            throw new IllegalStateException("Interactive local enrollment cannot run in CI");
        }
    }

    @Bean
    ApplicationRunner localDeviceEnrollmentRunner(Environment env, EntityManager manager,
            PlatformTransactionManager txManager, AuthenticationProvider authenticationProvider,
            AdminAuthenticationAudit audit, AuthenticatedEnrollmentTokenIssuer tokens,
            PendingEnrollmentService enrollment,
            ObjectProvider<ReviewedDeviceCertificateRegistrationService> registrar) {
        return ignored -> {
            requireLocalConfiguration(env);
            Console console = System.console();
            if (console == null) {
                throw new IllegalStateException("Attached local terminal required");
            }
            requireTestDatabase(manager);
            String action = env.getRequiredProperty("lanveriq.local-enrollment.action");
            String login = readLine(console, "Local test administrator login: ");
            Authentication authenticated = authenticate(console, login, authenticationProvider, audit);
            AdminPrincipal principal = (AdminPrincipal) authenticated.getPrincipal();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            try {
                var group = groupId(manager);
                if ("prepare".equals(action)) {
                    String csr = readPublicPem(console, "Absolute public CSR PEM path: ",
                            CertificateSigningRequestValidator.MAX_PEM_BYTES);
                    var validated = new CertificateSigningRequestValidator().validate(csr);
                    console.printf("Public CSR SHA256: %s%n", validated.csrSha256Digest());
                    console.printf("Notice v1: only your authorized local test device; no automatic collection or upload.%n");
                    confirm(console, "PREPARE LOCAL TEST DEVICE");
                    var result = new TransactionTemplate(txManager).execute(status -> {
                        var token = tokens.issue(group, 1, Duration.ofMinutes(5));
                        return enrollment.register(token.plaintext(), 1, csr);
                    });
                    if (result == null) {
                        throw new IllegalStateException("Pending enrollment did not return a device");
                    }
                    console.printf("Pending device UUID: %s%n", result.deviceId());
                    console.printf("Sign the reviewed CSR offline with this exact URI SAN; activation remains separate.%n");
                } else {
                    UUID device = UUID.fromString(readLine(console, "Server-assigned pending device UUID: "));
                    if (device.version() != 7 || !isTestGroupDevice(manager, device, group)) {
                        throw new IllegalArgumentException("Expected a pending device in the local test group");
                    }
                    String leaf = readPublicPem(console, "Absolute signed public leaf/chain PEM path: ",
                            32 * 1024);
                    console.printf("Register signed leaf for pending device %s only.%n", device);
                    confirm(console, "REGISTER LOCAL TEST DEVICE");
                    registrar.getObject().register(device, leaf);
                    console.printf("Certificate registered as pending. No activation or inventory upload performed.%n");
                }
            } finally {
                SecurityContextHolder.clearContext();
                audit.logoutAllowed(principal.userId());
            }
        };
    }

    private static void requireTestDatabase(EntityManager manager) {
        Object safe = manager.createNativeQuery("""
                SELECT current_database()='lanveriq_local_test'
                  AND current_user='lanveriq_local_test'
                  AND session_user=current_user AND current_schema()='public'
                  AND current_setting('server_version_num')::integer BETWEEN 180000 AND 189999
                  AND EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=current_user
                    AND NOT r.rolsuper AND NOT r.rolcreatedb AND NOT r.rolcreaterole
                    AND NOT r.rolreplication AND NOT r.rolbypassrls)
                  AND EXISTS (SELECT 1 FROM pg_database d WHERE d.datname=current_database()
                    AND pg_get_userbyid(d.datdba)=current_user)
                """).getSingleResult();
        if (!Boolean.TRUE.equals(safe)) {
            throw new IllegalStateException("Actual database or role is not the dedicated local test database");
        }
    }

    private static UUID groupId(EntityManager manager) {
        return (UUID) manager.createNativeQuery("SELECT id FROM device_groups WHERE name=:name")
                .setParameter("name", TEST_GROUP).getSingleResult();
    }

    private static boolean isTestGroupDevice(EntityManager manager, UUID device, UUID group) {
        return Boolean.TRUE.equals(manager.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM devices WHERE id=:id AND group_id=:group
                    AND enrollment_state='pending_certificate' AND revoked_at IS NULL)
                """).setParameter("id", device).setParameter("group", group).getSingleResult());
    }

    private static Authentication authenticate(Console console, String login,
            AuthenticationProvider provider, AdminAuthenticationAudit audit) {
        char[] password = console.readPassword("Local test administrator password: ");
        if (password == null) {
            throw new IllegalStateException("Authentication cancelled");
        }
        var request = UsernamePasswordAuthenticationToken.unauthenticated(login, CharBuffer.wrap(password));
        try {
            if (password.length < 16 || password.length > 128) {
                throw new IllegalArgumentException("Invalid administrator password length");
            }
            Authentication result;
            try {
                result = provider.authenticate(request);
            } catch (AuthenticationException denied) {
                audit.loginDenied();
                throw denied;
            }
            if (result == null || !result.isAuthenticated()
                    || !(result.getPrincipal() instanceof AdminPrincipal principal)
                    || !principal.isEnabled()) {
                audit.loginDenied();
                throw new IllegalStateException("Administrator authentication denied");
            }
            if (result instanceof CredentialsContainer credentials) {
                credentials.eraseCredentials();
            }
            audit.loginAllowed(principal.userId());
            return result;
        } finally {
            request.eraseCredentials();
            Arrays.fill(password, '\0');
        }
    }

    private static String readLine(Console console, String prompt) {
        String value = console.readLine("%s", prompt);
        if (value == null || value.isBlank() || value.length() > 4096) {
            throw new IllegalArgumentException("Missing or invalid local input");
        }
        return value;
    }

    private static void confirm(Console console, String expected) {
        if (!expected.equals(readLine(console, "Type " + expected + " to continue: "))) {
            throw new IllegalStateException("Local enrollment cancelled");
        }
    }

    private static String readPublicPem(Console console, String prompt, int limit) throws Exception {
        Path path = Path.of(readLine(console, prompt));
        if (!path.isAbsolute() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.equals(path.toRealPath())) {
            throw new IllegalArgumentException("Expected an absolute canonical public PEM file");
        }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = input.readNBytes(limit + 1);
            if (data.length == 0 || data.length > limit) {
                throw new IllegalArgumentException("Public PEM file size out of bounds");
            }
            String pem = new String(data, StandardCharsets.US_ASCII);
            if (pem.contains("PRIVATE KEY")) {
                throw new IllegalArgumentException("Private keys are never accepted by local enrollment");
            }
            return pem;
        }
    }
}
