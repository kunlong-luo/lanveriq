package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Manual-only actual Boot application component-scan + PG18 + Servlet/Spring
 * Security acceptance. Unlike the @WebMvcTest slice, this test NEVER imports
 * individual authentication configuration classes to make them appear.
 *
 * MockMvc exercises actual Spring filters and servlet routing; this remains
 * distinct from a real Tomcat TLS/ALPN, Caddy or browser acceptance.
 * The scope test creates only disposable synthetic PostgreSQL groups/users/
 * devices, using an authenticated test principal (no password, login UI or
 * Agent). Full TLS and browser sessions are separately verified.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=true",
        "lanveriq.console.auth.origin=https://console.lanveriq.internal",
        "lanveriq.device-directory.enabled=true",
        "lanveriq.device-directory.http-enabled=true",
        "lanveriq.monitoring.overview-http-enabled=true"
})
class PostgreSqlConsoleSecurityWiringIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_console_security_wiring_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void bootDiscoversConditionalConsoleChainsAndBlocksAnonymousReads()
            throws Exception {
        // Regression for the real 403: a @WebMvcTest can pass by importing a
        // security config that component scanning did not actually discover.
        for (String bean : new String[]{
                "consoleAuthenticationOnly",
                "scopedDeviceHealthReads",
                "consoleMonitoringOverviewReads",
                "denyBusinessRoutes"}) {
            assertTrue(context.containsBean(bean),
                    "Real Boot servlet context must construct security chain " + bean);
        }
        // Bean existence alone is insufficient: assert the actual servlet
        // security proxy selects the Console chain before the deny-all chain
        // for the exact route exercised by the real TLS harness.
        SecurityFilterChain console = context.getBean(
                "consoleAuthenticationOnly", SecurityFilterChain.class);
        SecurityFilterChain deny = context.getBean(
                "denyBusinessRoutes", SecurityFilterChain.class);
        List<SecurityFilterChain> installed = springSecurityFilterChain.getFilterChains();
        assertTrue(installed.indexOf(console) >= 0);
        assertTrue(installed.indexOf(console) < installed.indexOf(deny));
        var consoleRequest = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        consoleRequest.setServletPath("/api/v1/auth/session");
        consoleRequest.setScheme("https");
        consoleRequest.setSecure(true);
        consoleRequest.setServerName("console.lanveriq.internal");
        consoleRequest.setServerPort(443);
        consoleRequest.addHeader("Host", "console.lanveriq.internal:443");
        assertTrue(console.matches(consoleRequest));
        assertFalse(deny.matches(consoleRequest) && installed.indexOf(deny) < installed.indexOf(console));
        assertSame(console, installed.stream()
                .filter(chain -> chain.matches(consoleRequest))
                .findFirst().orElseThrow());
        assertTrue(console.getFilters().stream()
                .anyMatch(filter -> filter instanceof ConsoleRequestGuardFilter));
        mvc.perform(get("/api/v1/auth/session")
                        .secure(true)
                        .header("Host", "console.lanveriq.internal:443"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/auth/csrf")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
        // Both transport variants must converge to the same Spring
        // Security chain in the real, component-scanned application.
        mvc.perform(get("/api/v1/auth/session").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/auth/csrf").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(443);
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
        mvc.perform(get("/api/v1/auth/csrf").with(request -> {
                    request.setProtocol("HTTP/2.0");
                    request.setScheme("https");
                    request.setSecure(true);
                    request.setServerName("console.lanveriq.internal");
                    request.setServerPort(8443);
                    return request;
                }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/devices")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/monitoring/overview")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/devices/nonexistent/activate")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")
                        .header("Host", "agent.lanveriq.internal"))
                .andExpect(status().isForbidden());
    }
    // Kept in the existing manually dispatched PG18 servlet suite so the
    // Console + Next.js acceptance workflow executes this data-bearing gate
    // without ever starting a real user session or enabling device collection.
    private record ScopedFixture(UUID firstAdmin, UUID secondAdmin,
                                 UUID firstDevice, UUID secondDevice, UUID outsider) {}

    private UUID syntheticGroup() {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO device_groups(name) VALUES (:name) RETURNING id
                """).setParameter("name", "http-synthetic-" + UUID.randomUUID())
                .getSingleResult();
    }

    private UUID syntheticAdmin(UUID group, boolean duplicateReadRoles) {
        UUID id = (UUID) entityManager.createNativeQuery("""
                INSERT INTO users(normalized_login,password_hash)
                VALUES (:login,'synthetic-NO-LOGIN') RETURNING id
                """).setParameter("login", "http-synthetic-" + UUID.randomUUID())
                .getSingleResult();
        entityManager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :actor, r.id, :group FROM roles AS r
                WHERE r.code IN (:roles)
                """).setParameter("actor", id)
                .setParameter("group", group)
                .setParameter("roles", duplicateReadRoles
                        ? List.of("it_operator", "security_auditor")
                        : List.of("it_operator"))
                .executeUpdate();
        return id;
    }

    private UUID syntheticDevice(UUID group, String state) {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO devices(group_id,device_label,enrollment_state)
                VALUES(:group,'Synthetic scoped HTTP device',:state) RETURNING id
                """).setParameter("group", group)
                .setParameter("state", state)
                .getSingleResult();
    }

    private ScopedFixture seedScopedFixture() {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            UUID a = syntheticGroup();
            UUID b = syntheticGroup();
            UUID firstAdmin = syntheticAdmin(a, true);
            UUID secondAdmin = syntheticAdmin(b, false);
            UUID firstDevice = syntheticDevice(a, "active");
            UUID secondDevice = syntheticDevice(a, "pending_certificate");
            UUID outsider = syntheticDevice(b, "active");
            return new ScopedFixture(firstAdmin, secondAdmin, firstDevice,
                    secondDevice, outsider);
        });
    }

    private MockHttpServletRequestBuilder scopedGet(String url, UUID actor) {
        // The SecurityContext is supplied only by spring-security-test.
        // Account/session-version checks and group scope still hit actual PG18.
        var principal = new AdminPrincipal(actor, "synthetic-http-admin",
                "synthetic-NO-LOGIN", true, 0L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of());
        return get(url).secure(true)
                .header("Host", "console.lanveriq.internal:443")
                .header("Sec-Fetch-Site", "same-origin")
                .with(authentication(authentication));
    }

    @Test
    void realPostgresGroupsAreIsolatedAtActualServletEndpoints() throws Exception {
        ScopedFixture data = seedScopedFixture();

        // Two roles in the same group must not duplicate health rows.
        // The controller, real SecurityFilterChain and real JPA repository
        // all run through Boot component scan and a disposable PG18 database.
        mvc.perform(scopedGet("/api/v1/devices", data.firstAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].device_id",
                        containsInAnyOrder(data.firstDevice().toString(),
                                data.secondDevice().toString())))
                .andExpect(jsonPath("$.next_cursor").value(
                        org.hamcrest.Matchers.nullValue()));

        mvc.perform(scopedGet("/api/v1/devices", data.firstAdmin())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty());

        mvc.perform(scopedGet("/api/v1/devices", data.secondAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].device_id")
                        .value(data.outsider().toString()));

        mvc.perform(scopedGet("/api/v1/devices/" + data.firstDevice(),
                        data.firstAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(data.firstDevice().toString()));
        mvc.perform(scopedGet("/api/v1/devices/" + data.outsider(),
                        data.firstAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(scopedGet("/api/v1/devices/" + data.firstDevice(),
                        data.secondAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(scopedGet("/api/v1/devices/" + UUID.randomUUID(),
                        data.firstAdmin()))
                .andExpect(status().isNotFound());

        mvc.perform(scopedGet("/api/v1/monitoring/overview", data.firstAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.offline").value(1))
                .andExpect(jsonPath("$.pending").value(1));
        mvc.perform(scopedGet("/api/v1/monitoring/overview", data.secondAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.offline").value(1))
                .andExpect(jsonPath("$.pending").value(0));

        // Even an otherwise authenticated actor cannot use a hostile Origin.
        mvc.perform(scopedGet("/api/v1/devices", data.firstAdmin())
                        .header("Origin", "https://untrusted.example.test"))
                .andExpect(status().isForbidden());

        // A stale in-memory principal may not retain a deleted DB grant.
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        DELETE FROM user_role_scopes WHERE user_id=:actor
                        """).setParameter("actor", data.firstAdmin()).executeUpdate());
        mvc.perform(scopedGet("/api/v1/devices", data.firstAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(scopedGet("/api/v1/monitoring/overview", data.firstAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // Disabling the other account revokes its session before repository reads.
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                entityManager.createNativeQuery("""
                        UPDATE users SET disabled_at=clock_timestamp()
                        WHERE id=:actor
                        """).setParameter("actor", data.secondAdmin()).executeUpdate());
        mvc.perform(scopedGet("/api/v1/devices", data.secondAdmin()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void fullBootPostgresNeverAutoMountsPasskeyRoutesFromDependency() throws Exception {
        // A11-D1: proving real component-scanned Boot servlet security is
        // crucial; a WebMvcTest alone could silently omit an auto-configured
        // passkey registration handler. Supplying CSRF excludes false
        // positives caused solely by CSRF rejection.
        for (String route : new String[]{
                "/webauthn/register/options",
                "/webauthn/register",
                "/webauthn/authenticate/options",
                "/login/webauthn",
                "/api/v1/auth/webauthn/step-up"}) {
            mvc.perform(post(route).with(csrf()).secure(true)
                            .header("Host", "console.lanveriq.internal:443")
                            .header("Origin", "https://console.lanveriq.internal")
                            .header("Sec-Fetch-Site", "same-origin"))
                    .andExpect(status().isForbidden());
        }
        // A mock-authenticated user must not make these routes usable.
        ScopedFixture scoped = seedScopedFixture();
        mvc.perform(post("/api/v1/auth/webauthn/step-up")
                        .with(csrf()).secure(true)
                        .header("Host", "console.lanveriq.internal:443")
                        .header("Origin", "https://console.lanveriq.internal")
                        .header("Sec-Fetch-Site", "same-origin")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                new AdminPrincipal(scoped.firstAdmin(), "synthetic-admin",
                                        "synthetic-NO-LOGIN", true, 0L), null, List.of()))))
                .andExpect(status().isForbidden());
    }

}
