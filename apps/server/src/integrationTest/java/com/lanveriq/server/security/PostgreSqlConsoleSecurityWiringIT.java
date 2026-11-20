package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.security.SecureRandom;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
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

    @Autowired
    private AdminPasskeyUserHandleRepository passkeyHandles;

    @Autowired
    private AdminPasskeyCredentialRepository passkeyCredentials;

    @Autowired
    private AdminAccountRepository adminAccounts;

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

    // V16 durable storage tests use random bytes and synthetic COSE-like
    // payloads solely to exercise DB constraints. No WebAuthn credential,
    // cryptographic assertion, verified user or production route is created.
    private byte[] syntheticPasskeyBytes(int length) {
        byte[] data = new byte[length];
        new SecureRandom().nextBytes(data);
        return data;
    }

    private void tx(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> action.run());
    }

    private UUID passkeyFixture(byte[] handle) {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            UUID user = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO users(normalized_login,password_hash)
                    VALUES (:login,'synthetic-passkey-NO-LOGIN') RETURNING id
                    """).setParameter("login", "synthetic-passkey-" + UUID.randomUUID())
                    .getSingleResult();
            entityManager.createNativeQuery("""
                    INSERT INTO admin_passkey_user_handles (user_id,user_handle)
                    VALUES (:user,:handle)
                    """).setParameter("user", user).setParameter("handle", handle)
                    .executeUpdate();
            return user;
        });
    }

    private void insertPasskeyCredential(byte[] id, UUID user, byte[] cose) {
        entityManager.createNativeQuery("""
                INSERT INTO admin_passkey_credentials
                    (credential_id,user_id,rp_id,credential_public_key_cose)
                VALUES (:id,:user,'console.lanveriq.internal',:cose)
                """).setParameter("id", id).setParameter("user", user)
                .setParameter("cose", cose).executeUpdate();
    }

    @Test
    void passkeyUserHandleIsStableGloballyUniqueAndReadThroughJpa() {
        byte[] handle = syntheticPasskeyBytes(32);
        UUID owner = passkeyFixture(handle);
        var stored = passkeyHandles.findById(owner).orElseThrow();
        assertArrayEquals(handle, stored.userHandle());
        assertEquals(owner, passkeyHandles.findByUserHandle(handle)
                .orElseThrow().userId());

        assertThrows(RuntimeException.class, () -> tx(() -> {
            UUID other = (UUID) entityManager.createNativeQuery("""
                    INSERT INTO users(normalized_login,password_hash)
                    VALUES (:login,'synthetic-passkey-NO-LOGIN') RETURNING id
                    """).setParameter("login", "synthetic-passkey-" + UUID.randomUUID())
                    .getSingleResult();
            entityManager.createNativeQuery("""
                    INSERT INTO admin_passkey_user_handles(user_id,user_handle)
                    VALUES (:user,:handle)
                    """).setParameter("user", other)
                    .setParameter("handle", handle).executeUpdate();
        }));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_user_handles
                           SET user_handle=:replacement WHERE user_id=:owner
                        """).setParameter("replacement", syntheticPasskeyBytes(32))
                        .setParameter("owner", owner).executeUpdate()));
        assertArrayEquals(handle, passkeyHandles.findById(owner)
                .orElseThrow().userHandle());
    }

    @Test
    void passkeyIdIsGloballyUniqueAndOwnerPublicKeyAndRpAreImmutable() {
        UUID first = passkeyFixture(syntheticPasskeyBytes(32));
        UUID second = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id, first, syntheticPasskeyBytes(128)));
        assertEquals(first, passkeyCredentials.findByUserId(first)
                .getFirst().userId());
        assertEquals(0, passkeyCredentials.findByUserId(second).size());

        assertThrows(RuntimeException.class, () -> tx(() ->
                insertPasskeyCredential(id, second, syntheticPasskeyBytes(128))));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials SET user_id=:other
                        WHERE credential_id=:id
                        """).setParameter("other", second)
                        .setParameter("id", id).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials SET rp_id='other.example'
                         WHERE credential_id=:id
                        """).setParameter("id", id).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials
                           SET credential_public_key_cose=:forged
                         WHERE credential_id=:id
                        """).setParameter("forged", syntheticPasskeyBytes(128))
                        .setParameter("id", id).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_credentials
                        WHERE credential_id=:id
                        """).setParameter("id", id).executeUpdate()));
    }

    @Test
    void passkeyCountersAndRevocationCannotRollBackOrResurrect() {
        UUID owner = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id, owner, syntheticPasskeyBytes(128)));
        tx(() -> entityManager.createNativeQuery("""
                UPDATE admin_passkey_credentials
                   SET signature_count=5,last_verified_at=clock_timestamp()
                 WHERE credential_id=:id
                """).setParameter("id", id).executeUpdate());
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials SET signature_count=4
                         WHERE credential_id=:id
                        """).setParameter("id", id).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials SET backup_state=true
                         WHERE credential_id=:id
                        """).setParameter("id", id).executeUpdate()));
        tx(() -> entityManager.createNativeQuery("""
                UPDATE admin_passkey_credentials
                   SET revoked_at=clock_timestamp(),revoked_by=:owner
                 WHERE credential_id=:id
                """).setParameter("id", id)
                .setParameter("owner", owner).executeUpdate());
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials
                           SET revoked_at=NULL,revoked_by=NULL
                         WHERE credential_id=:id
                        """).setParameter("id", id).executeUpdate()));
        assertTrue(passkeyCredentials.findByUserId(owner)
                .getFirst().revoked());
    }

    @Test
    void officialWebAuthnUserAdapterReadsOnlyActivePg18UserHandles() {
        byte[] firstHandle = syntheticPasskeyBytes(32);
        byte[] otherHandle = syntheticPasskeyBytes(32);
        UUID first = passkeyFixture(firstHandle);
        UUID other = passkeyFixture(otherHandle);
        var bridge = new StagedJpaWebAuthnUserEntityRepository(
                passkeyHandles, adminAccounts);
        String firstLogin = adminAccounts.findById(first)
                .orElseThrow().getNormalizedLogin();
        String otherLogin = adminAccounts.findById(other)
                .orElseThrow().getNormalizedLogin();
        var firstById = bridge.findById(new Bytes(firstHandle));
        assertNotNull(firstById);
        assertEquals(firstLogin, firstById.getName());
        assertArrayEquals(firstHandle, firstById.getId().getBytes());
        assertArrayEquals(otherHandle, bridge.findByUsername(otherLogin)
                .getId().getBytes());
        assertNull(bridge.findByUsername("synthetic-cross-user-invalid"));
        assertNull(bridge.findById(new Bytes(syntheticPasskeyBytes(32))));

        // Account disable MUST revoke eligibility immediately on DB reread,
        // independent of a stale Spring Security session.
        tx(() -> entityManager.createNativeQuery("""
                UPDATE users SET disabled_at=clock_timestamp(),
                                  session_version=session_version+1
                 WHERE id=:user
                """).setParameter("user", first).executeUpdate());
        assertNull(bridge.findById(new Bytes(firstHandle)));
        assertNull(bridge.findByUsername(firstLogin));
        assertEquals(otherLogin, bridge.findByUsername(otherLogin).getName());
        assertThrows(AccessDeniedException.class,
                () -> bridge.save(firstById));
        assertThrows(AccessDeniedException.class,
                () -> bridge.delete(new Bytes(firstHandle)));
    }

    @Test
    void completeSpringWebAuthnCredentialAdapterIsStillFailClosedWithPg18Rows() {
        UUID user = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] credential = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(credential, user, syntheticPasskeyBytes(128)));
        assertEquals(1, passkeyCredentials.findByUserId(user).size());

        // V16 test bytes cannot be parsed/accepted as authenticator proofs:
        // even an inserted row and an active account never yield a Spring
        // Security CredentialRecord or a login/step-up authority.
        var bridge = new StagedDenyAllWebAuthnCredentialRepository();
        assertNull(bridge.findByCredentialId(new Bytes(credential)));
        byte[] handle = passkeyHandles.findById(user).orElseThrow().userHandle();
        assertTrue(bridge.findByUserId(new Bytes(handle)).isEmpty());
        assertThrows(AccessDeniedException.class, () -> bridge.save(null));
        assertThrows(AccessDeniedException.class,
                () -> bridge.delete(new Bytes(credential)));

        // No adapter bean is registered with SecurityFilterChain; the
        // official default-in-memory credential pathway is not activated.
        assertTrue(context.getBeansOfType(
                PublicKeyCredentialUserEntityRepository.class).isEmpty());
        assertTrue(context.getBeansOfType(UserCredentialRepository.class).isEmpty());
    }

    @Test
    void stagedCredentialRecordEnforcesOwnerHandleAndRpWithoutEnablingAuthentication() {
        byte[] handle = syntheticPasskeyBytes(32);
        UUID owner = passkeyFixture(handle);
        UUID outsider = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] credential = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(credential, owner, syntheticPasskeyBytes(128)));
        var insertRecord = (java.util.function.BiConsumer<UUID, byte[]>) (actor, userHandle) ->
                entityManager.createNativeQuery("""
                        INSERT INTO admin_passkey_credential_records_staging
                         (credential_id,user_id,rp_id,user_handle,
                          registration_attestation_object,registration_client_data_json,
                          uv_initialized,display_label,registration_tracking_id,
                          registered_session_version)
                        VALUES(:credential,:actor,'console.lanveriq.internal',:handle,
                               :attestation,:client,true,'synthetic-only',:tracking,0)
                        """).setParameter("credential", credential)
                        .setParameter("actor", actor)
                        .setParameter("handle", userHandle)
                        .setParameter("attestation", syntheticPasskeyBytes(256))
                        .setParameter("client", syntheticPasskeyBytes(128))
                        .setParameter("tracking", UUID.randomUUID()).executeUpdate();
        assertThrows(RuntimeException.class, () -> tx(() ->
                insertRecord.accept(outsider,
                        passkeyHandles.findById(outsider).orElseThrow().userHandle())));
        assertThrows(RuntimeException.class, () -> tx(() ->
                insertRecord.accept(owner,
                        passkeyHandles.findById(outsider).orElseThrow().userHandle())));
        tx(() -> insertRecord.accept(owner, handle));
        assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_passkey_credential_records_staging
                WHERE credential_id=:id AND user_id=:owner
                """).setParameter("id", credential).setParameter("owner", owner)
                .getSingleResult()).longValue());

        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credential_records_staging
                           SET rp_id='hostile.example' WHERE credential_id=:id
                        """).setParameter("id", credential).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_credential_records_staging
                        WHERE credential_id=:id
                        """).setParameter("id", credential).executeUpdate()));
        assertNull(new StagedDenyAllWebAuthnCredentialRepository()
                .findByCredentialId(new Bytes(credential)));
    }

    @Test
    void latestStoredPasskeyCounterCasRejectsStaleReplaysAndWrongOwners() {
        UUID owner = passkeyFixture(syntheticPasskeyBytes(32));
        UUID outsider = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id, owner, syntheticPasskeyBytes(128)));
        var gate = new StagedWebAuthnCounterGate(entityManager);
        var expectedRp = "console.lanveriq.internal";
        tx(() -> assertEquals(StagedWebAuthnCounterGate.Outcome.ADVANCED,
                gate.compareAndAdvance(owner,id,expectedRp,0,5,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,expectedRp,0,3,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,expectedRp,0,5,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(outsider,id,expectedRp,5,6,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,"hostile.example",5,6,false)));
        tx(() -> assertEquals(StagedWebAuthnCounterGate.Outcome.ADVANCED,
                gate.compareAndAdvance(owner,id,expectedRp,5,9,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,expectedRp,5,8,false)));
        assertEquals(9L, ((Number) entityManager.createNativeQuery("""
                SELECT signature_count FROM admin_passkey_credentials
                WHERE credential_id=:id
                """).setParameter("id", id).getSingleResult()).longValue());
        assertEquals(2L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE principal_id=:owner AND action='admin.passkey.counter.advance.staged'
                """).setParameter("owner", owner).getSingleResult()).longValue());
    }

    @Test
    void counterlessCredentialProvidesNoAuthorizationAndDisabledOrRevokedOwnerDenies() {
        UUID owner = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id, owner, syntheticPasskeyBytes(128)));
        var gate = new StagedWebAuthnCounterGate(entityManager);
        assertEquals(StagedWebAuthnCounterGate.Outcome.NO_COUNTER_SIGNAL,
                new TransactionTemplate(transactionManager).execute(ignored ->
                        gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,0,false)));
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,1,true)));
        tx(() -> entityManager.createNativeQuery("""
                UPDATE users SET disabled_at=clock_timestamp(),
                                 session_version=session_version+1 WHERE id=:owner
                """).setParameter("owner",owner).executeUpdate());
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,1,false)));
        tx(() -> entityManager.createNativeQuery("""
                UPDATE admin_passkey_credentials
                SET revoked_at=clock_timestamp(),revoked_by=:owner
                WHERE credential_id=:id
                """).setParameter("owner",owner).setParameter("id",id).executeUpdate());
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,2,false)));
        assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE principal_id=:owner AND action='admin.passkey.counter.advance.staged'
                """).setParameter("owner", owner).getSingleResult()).longValue());
    }

    @Test
    void silentlySuppressedCounterAuditRollsBackSignatureUpdate() {
        UUID owner = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id, owner, syntheticPasskeyBytes(128)));
        var gate = new StagedWebAuthnCounterGate(entityManager);
        tx(() -> {
            entityManager.createNativeQuery("""
                    CREATE FUNCTION a11_counter_suppress_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.counter.advance.staged' THEN
                            RETURN NULL;
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE TRIGGER a11_counter_suppress_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_counter_suppress_audit()
                    """).executeUpdate();
        });
        try {
            assertThrows(IllegalStateException.class, () -> tx(() ->
                    gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,4,false)));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT signature_count FROM admin_passkey_credentials
                    WHERE credential_id=:id
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            tx(() -> {
                entityManager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS a11_counter_suppress_audit
                        ON admin_access_log
                        """).executeUpdate();
                entityManager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS a11_counter_suppress_audit()
                        """).executeUpdate();
            });
        }
        tx(() -> gate.compareAndAdvance(owner,id,"console.lanveriq.internal",0,4,false));
        assertEquals(4L, ((Number) entityManager.createNativeQuery("""
                SELECT signature_count FROM admin_passkey_credentials
                WHERE credential_id=:id
                """).setParameter("id",id).getSingleResult()).longValue());
    }

    @Test
    void realPostgresBootCannotAutoInstallStagedWebAuthn4JVerifierOrTrustSource()
            throws Exception {
        // The new bridge is intentionally package-private, NOT a Spring
        // @Bean/@Component, even when the full PostgreSQL + Servlet stack
        // discovers and creates every other application security bean.
        assertTrue(context.getBeansOfType(Class.forName(
                "com.lanveriq.server.enrollment.StagedWebAuthn4JAssertionVerifier"))
                .isEmpty());
        assertTrue(context.getBeansOfType(Class.forName(
                "com.lanveriq.server.enrollment.StagedWebAuthn4JAssertionVerifier$TrustedCredentialSource"))
                .isEmpty());
    }

    // V19: stage-only registration challenge; NEVER issues any trusted
    // CredentialRecord, first-pin MFA proof or production certificate.
    private void registrationActor(UUID actor, long version) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        new AdminPrincipal(actor, "synthetic-registration-NO-LOGIN",
                                "synthetic-NO-LOGIN", true, version),
                        null, List.of()));
    }

    private UUID registrationOwner() {
        UUID id = passkeyFixture(syntheticPasskeyBytes(32));
        tx(() -> {
            UUID group = syntheticGroup();
            entityManager.createNativeQuery("""
                    INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                    SELECT :user,r.id,:group FROM roles r
                    WHERE r.code='owner_security_admin'
                    """).setParameter("user", id).setParameter("group", group)
                    .executeUpdate();
        });
        return id;
    }

    private StagedPasskeyRegistrationChallengeLedger registrationLedger(
            StagedPasskeyRegistrationChallengeLedger.RegistrationProofVerifier verifier) {
        return new StagedPasskeyRegistrationChallengeLedger(entityManager, verifier);
    }

    private StagedPasskeyRegistrationChallengeLedger.IssuedChallenge issueRegistration(
            StagedPasskeyRegistrationChallengeLedger service) {
        return new TransactionTemplate(transactionManager)
                .execute(ignored -> service.issue());
    }

    private void consumeRegistration(StagedPasskeyRegistrationChallengeLedger service,
            StagedPasskeyRegistrationChallengeLedger.IssuedChallenge issued, byte[] response) {
        tx(() -> service.consume(issued.id(), issued.nonceBase64Url(), response));
    }

    @Test
    void registrationNonceIsCSPRNGHashOnlyAndProductionHasNoTrustedPromotion() throws Exception {
        UUID owner = registrationOwner();
        registrationActor(owner, 0);
        try {
            var service = registrationLedger(new StagedWebAuthn4JRegistrationVerifier(
                    com.webauthn4j.WebAuthnManager.createNonStrictWebAuthnManager()));
            var issued = issueRegistration(service);
            byte[] raw = java.util.Base64.getUrlDecoder().decode(issued.nonceBase64Url());
            assertEquals(32, raw.length);
            assertEquals(43, issued.nonceBase64Url().length());
            byte[] digest = (byte[]) entityManager.createNativeQuery("""
                    SELECT challenge_sha256 FROM admin_passkey_registration_challenges
                    WHERE id=:id
                    """).setParameter("id", issued.id()).getSingleResult();
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw), digest);
            assertThrows(AccessDeniedException.class, () ->
                    consumeRegistration(service, issued, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_challenges
                    WHERE id=:id AND consumed_at IS NULL
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_credentials WHERE user_id=:owner
                    """).setParameter("owner",owner).getSingleResult()).longValue());
            assertTrue(context.getBeansOfType(StagedPasskeyRegistrationChallengeLedger.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(StagedWebAuthn4JRegistrationVerifier.class)
                    .isEmpty());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void registrationNonceCannotCrossActorsOrReplayEvenWithSyntheticTestVerifier() {
        UUID owner = registrationOwner();
        UUID other = registrationOwner();
        var ledger = registrationLedger(input -> {
            assertEquals(owner, input.actorId());
            assertEquals(0L, input.sessionVersion());
        });
        try {
            registrationActor(owner, 0);
            var issued = issueRegistration(ledger);
            registrationActor(other, 0);
            assertThrows(AccessDeniedException.class, () ->
                    consumeRegistration(ledger, issued, new byte[]{1}));
            registrationActor(owner, 0);
            assertThrows(AccessDeniedException.class, () -> tx(() ->
                    ledger.consume(issued.id(), "A".repeat(43), new byte[]{1})));
            consumeRegistration(ledger, issued, new byte[]{1});
            assertThrows(AccessDeniedException.class, () ->
                    consumeRegistration(ledger, issued, new byte[]{1}));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_challenges
                    WHERE id=:id AND consumed_at IS NOT NULL
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_credentials
                    WHERE user_id IN (:first,:second)
                    """).setParameter("first",owner).setParameter("second",other)
                    .getSingleResult()).longValue());
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE principal_id=:owner
                      AND action='admin.passkey.registration.challenge.consume.staged'
                    """).setParameter("owner",owner).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void registrationRotatedSessionAndImmutableNonceHistoryFailClosed() {
        UUID owner = registrationOwner();
        registrationActor(owner, 0);
        try {
            var ledger = registrationLedger(input -> {});
            var issued = issueRegistration(ledger);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                            UPDATE admin_passkey_registration_challenges
                            SET expires_at=clock_timestamp()+INTERVAL '3 minutes'
                            WHERE id=:id
                            """).setParameter("id",issued.id()).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                            DELETE FROM admin_passkey_registration_challenges
                            WHERE id=:id
                            """).setParameter("id",issued.id()).executeUpdate()));
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1
                    WHERE id=:actor
                    """).setParameter("actor",owner).executeUpdate());
            assertThrows(AccessDeniedException.class, () -> issueRegistration(ledger));
            assertThrows(AccessDeniedException.class, () ->
                    consumeRegistration(ledger, issued, new byte[]{2}));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_challenges
                    WHERE id=:id AND consumed_at IS NOT NULL
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void revocationFreezesAllSubsequentPasskeyCredentialStateInV19() {
        UUID owner = passkeyFixture(syntheticPasskeyBytes(32));
        byte[] id = syntheticPasskeyBytes(64);
        tx(() -> insertPasskeyCredential(id,owner,syntheticPasskeyBytes(128)));
        tx(() -> entityManager.createNativeQuery("""
                UPDATE admin_passkey_credentials
                SET revoked_at=clock_timestamp(),revoked_by=:owner
                WHERE credential_id=:id
                """).setParameter("owner",owner).setParameter("id",id).executeUpdate());
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials
                           SET signature_count=signature_count+1
                         WHERE credential_id=:id
                        """).setParameter("id",id).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                entityManager.createNativeQuery("""
                        UPDATE admin_passkey_credentials
                           SET backup_state=backup_state
                         WHERE credential_id=:id
                        """).setParameter("id",id).executeUpdate()));
        assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                SELECT signature_count FROM admin_passkey_credentials
                WHERE credential_id=:id AND revoked_at IS NOT NULL
                """).setParameter("id",id).getSingleResult()).longValue());
    }

    // V20 uses ONLY synthetic bytes for V16/V18; receipt state is always
    // untrusted_candidate and never becomes an authorized login credential.
    private void syntheticRegistrationRows(UUID owner, byte[] id) {
        byte[] handle = (byte[]) entityManager.createNativeQuery("""
                SELECT user_handle FROM admin_passkey_user_handles
                WHERE user_id=:actor
                """).setParameter("actor",owner).getSingleResult();
        tx(() -> {
            insertPasskeyCredential(id,owner,syntheticPasskeyBytes(128));
            entityManager.createNativeQuery("""
                    INSERT INTO admin_passkey_credential_records_staging
                      (credential_id,user_id,rp_id,user_handle,
                       registration_attestation_object,registration_client_data_json,
                       uv_initialized,display_label,registration_tracking_id,
                       registered_session_version)
                    VALUES (:id,:owner,'console.lanveriq.internal',:handle,
                            :attestation,:client,true,'synthetic-untrusted',:tracking,0)
                    """).setParameter("id",id).setParameter("owner",owner)
                    .setParameter("handle",handle)
                    .setParameter("attestation",syntheticPasskeyBytes(256))
                    .setParameter("client",syntheticPasskeyBytes(128))
                    .setParameter("tracking",UUID.randomUUID()).executeUpdate();
        });
    }

    @Test
    void consumedRegistrationStagesOnlyImmutableUntrustedCandidateAndAudit() throws Exception {
        UUID owner = registrationOwner();
        byte[] credential = syntheticPasskeyBytes(64);
        syntheticRegistrationRows(owner,credential);
        registrationActor(owner,0);
        try {
            var nonceLedger = registrationLedger(input -> {});
            var intake = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(nonceLedger);
            byte[] response = syntheticPasskeyBytes(512);
            tx(() -> {
                nonceLedger.consume(issued.id(),issued.nonceBase64Url(),response);
                intake.stageUntrusted(issued.id(),credential,response);
            });
            Object[] record = (Object[]) entityManager.createNativeQuery("""
                    SELECT evidence_grade, registration_response_sha256
                    FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id",issued.id()).getSingleResult();
            assertEquals("untrusted_candidate",record[0]);
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(response),(byte[])record[1]);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE resource_id=:id
                      AND action='admin.passkey.registration.candidate.untrusted'
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_registration_candidates_staging
                        SET evidence_grade='trusted' WHERE challenge_id=:id
                        """).setParameter("id",issued.id()).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_registration_candidates_staging
                        WHERE challenge_id=:id
                        """).setParameter("id",issued.id()).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    intake.stageUntrusted(issued.id(),credential,response)));
            assertTrue(context.getBeansOfType(
                    StagedPasskeyRegistrationCandidateLedger.class).isEmpty());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unconsumedRegistrationCannotBeStagedEvenWithSyntheticV16V18Rows() {
        UUID owner = registrationOwner();
        byte[] credential = syntheticPasskeyBytes(64);
        syntheticRegistrationRows(owner,credential);
        registrationActor(owner,0);
        try {
            var issued = issueRegistration(registrationLedger(input -> {}));
            var intake = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    intake.stageUntrusted(issued.id(),credential,new byte[]{1})));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void invalidRegistrationCandidateRollsBackConsumedChallengeAndAuditAtomically() {
        UUID owner = registrationOwner();
        byte[] credential = syntheticPasskeyBytes(64);
        syntheticRegistrationRows(owner,credential);
        registrationActor(owner,0);
        try {
            var nonce = registrationLedger(input -> {});
            var intake = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(nonce);
            assertThrows(RuntimeException.class, () -> tx(() -> {
                nonce.consume(issued.id(),issued.nonceBase64Url(),new byte[]{1});
                intake.stageUntrusted(issued.id(),syntheticPasskeyBytes(64),
                        new byte[]{1});
            }));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_challenges
                    WHERE id=:id AND consumed_at IS NULL
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE resource_id=:id AND action IN
                    ('admin.passkey.registration.candidate.untrusted',
                     'admin.passkey.registration.challenge.consume.staged')
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            // Same nonce remains retryable only because the ENTIRE failed
            // transaction rolled back; then valid candidate can be staged.
            tx(() -> {
                nonce.consume(issued.id(),issued.nonceBase64Url(),new byte[]{1});
                intake.stageUntrusted(issued.id(),credential,new byte[]{1});
            });
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id AND evidence_grade='untrusted_candidate'
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void registrationCandidateDeniesWrongActorAndRotatedSessionOrRevokedKey() {
        UUID owner = registrationOwner();
        UUID outsider = registrationOwner();
        byte[] credential = syntheticPasskeyBytes(64);
        syntheticRegistrationRows(owner,credential);
        registrationActor(owner,0);
        try {
            var nonce = registrationLedger(input -> {});
            var intake = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(nonce);
            consumeRegistration(nonce,issued,new byte[]{1});
            registrationActor(outsider,0);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    intake.stageUntrusted(issued.id(),credential,new byte[]{1})));
            registrationActor(owner,0);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1
                    WHERE id=:owner
                    """).setParameter("owner",owner).executeUpdate());
            assertThrows(RuntimeException.class, () -> tx(() ->
                    intake.stageUntrusted(issued.id(),credential,new byte[]{1})));
            // Rotation is irreversible and historical V19 nonce is useless.
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE admin_passkey_credentials SET
                      revoked_at=clock_timestamp(),revoked_by=:actor
                    WHERE credential_id=:credential
                    """).setParameter("actor",owner)
                    .setParameter("credential",credential).executeUpdate());
            registrationActor(owner,1);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    intake.stageUntrusted(issued.id(),credential,new byte[]{1})));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private byte[] makeUntrustedCandidateCredential(UUID actor) {
        byte[] id = syntheticPasskeyBytes(64);
        byte[] handle = passkeyHandles.findById(actor).orElseThrow().userHandle();
        tx(() -> {
            insertPasskeyCredential(id, actor, syntheticPasskeyBytes(128));
            entityManager.createNativeQuery("""
                    INSERT INTO admin_passkey_credential_records_staging
                        (credential_id,user_id,rp_id,user_handle,
                         registration_attestation_object,registration_client_data_json,
                         uv_initialized,display_label,registration_tracking_id,
                         registered_session_version)
                    VALUES (:credential,:actor,'console.lanveriq.internal',:handle,
                            :attestation,:client,true,'untrusted-only',:tracking,0)
                    """).setParameter("credential", id).setParameter("actor", actor)
                    .setParameter("handle", handle)
                    .setParameter("attestation", syntheticPasskeyBytes(256))
                    .setParameter("client", syntheticPasskeyBytes(128))
                    .setParameter("tracking", UUID.randomUUID()).executeUpdate();
        });
        return id;
    }

    @Test
    void untrustedCandidateRequiresConsumedChallengeAndNeverGrantsAuthentication()
            throws Exception {
        UUID owner = registrationOwner();
        byte[] credential = makeUntrustedCandidateCredential(owner);
        byte[] payload = syntheticPasskeyBytes(96);
        registrationActor(owner, 0);
        try {
            var ledger = registrationLedger(input -> {});
            var writer = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(ledger);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    writer.stageUntrusted(issued.id(), credential, payload)));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id", issued.id()).getSingleResult()).longValue());

            // The synthetic verifier checks DB serialization only, and
            // NEVER makes these bytes a trusted authenticator key.
            tx(() -> {
                ledger.consume(issued.id(), issued.nonceBase64Url(), new byte[]{1});
                writer.stageUntrusted(issued.id(), credential, payload);
            });
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    SELECT evidence_grade,registration_response_sha256
                    FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id", issued.id()).getSingleResult();
            assertEquals("untrusted_candidate", row[0]);
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payload), (byte[]) row[1]);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE principal_id=:owner AND resource_id=:id
                      AND action='admin.passkey.registration.candidate.untrusted'
                    """).setParameter("owner", owner).setParameter("id", issued.id())
                    .getSingleResult()).longValue());
            assertTrue(context.getBeansOfType(StagedPasskeyRegistrationCandidateLedger.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(UserCredentialRepository.class)
                    .values().stream()
                    .noneMatch(bean -> !(bean instanceof StagedDenyAllWebAuthnCredentialRepository)));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void candidateCannotBeReboundToOtherOwnerCredentialOrRotatedSession() {
        UUID owner = registrationOwner();
        UUID outsider = registrationOwner();
        byte[] credential = makeUntrustedCandidateCredential(owner);
        byte[] foreignCredential = makeUntrustedCandidateCredential(outsider);
        registrationActor(owner, 0);
        try {
            var ledger = registrationLedger(input -> {});
            var issued = issueRegistration(ledger);
            consumeRegistration(ledger, issued, new byte[]{1});
            var writer = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    writer.stageUntrusted(issued.id(),foreignCredential,new byte[]{1})));
            registrationActor(outsider, 0);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    writer.stageUntrusted(issued.id(),credential,new byte[]{1})));
            registrationActor(owner, 0);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1
                    WHERE id=:user
                    """).setParameter("user",owner).executeUpdate());
            assertThrows(RuntimeException.class, () -> tx(() ->
                    writer.stageUntrusted(issued.id(),credential,new byte[]{1})));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id", issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void candidateReceiptCannotReplayDeleteOrUpgradeUntrustedAssurance() {
        UUID owner = registrationOwner();
        byte[] credential = makeUntrustedCandidateCredential(owner);
        registrationActor(owner, 0);
        try {
            var ledger = registrationLedger(input -> {});
            var writer = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(ledger);
            tx(() -> {
                ledger.consume(issued.id(), issued.nonceBase64Url(), new byte[]{1});
                writer.stageUntrusted(issued.id(), credential, new byte[]{4,5});
            });
            assertThrows(RuntimeException.class, () -> tx(() ->
                    writer.stageUntrusted(issued.id(),credential,new byte[]{6})));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_registration_candidates_staging
                        SET evidence_grade='verified' WHERE challenge_id=:id
                        """).setParameter("id", issued.id()).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_registration_candidates_staging
                        WHERE challenge_id=:id
                        """).setParameter("id", issued.id()).executeUpdate()));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id AND evidence_grade='untrusted_candidate'
                    """).setParameter("id", issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void failedUntrustedCandidateInsertRollsBackChallengeAndBothAudits() {
        UUID owner = registrationOwner();
        byte[] credential = makeUntrustedCandidateCredential(owner);
        registrationActor(owner, 0);
        try {
            var ledger = registrationLedger(input -> {});
            var writer = new StagedPasskeyRegistrationCandidateLedger(entityManager);
            var issued = issueRegistration(ledger);
            assertThrows(RuntimeException.class, () -> tx(() -> {
                ledger.consume(issued.id(), issued.nonceBase64Url(), new byte[]{1});
                // A nonexistent credential must cause V20 FK/guard failure,
                // rolling back the *same transaction's* nonce consumption.
                writer.stageUntrusted(issued.id(), syntheticPasskeyBytes(64),
                        new byte[]{1});
            }));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_challenges
                    WHERE id=:id AND consumed_at IS NULL
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE resource_id=:id AND action IN (
                        'admin.passkey.registration.challenge.consume.staged',
                        'admin.passkey.registration.candidate.untrusted')
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
            // This is a fresh retry ONLY because failed consumption rolled
            // back. A committed valid consumption is irreversible.
            tx(() -> {
                ledger.consume(issued.id(), issued.nonceBase64Url(), new byte[]{1});
                writer.stageUntrusted(issued.id(), credential, new byte[]{1});
            });
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_registration_candidates_staging
                    WHERE challenge_id=:id AND evidence_grade='untrusted_candidate'
                    """).setParameter("id",issued.id()).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }


    // V21 is deliberately a rejection-only ledger. It does not make any
    // synthetic V16/V18/V20 material trusted or authorize credential login.
    private UUID syntheticV21Candidate(UUID actor) {
        byte[] credential = makeUntrustedCandidateCredential(actor);
        var nonce = registrationLedger(input -> {}); // synthetic, test-only
        var issued = issueRegistration(nonce);
        tx(() -> {
            nonce.consume(issued.id(), issued.nonceBase64Url(), new byte[]{1});
            new StagedPasskeyRegistrationCandidateLedger(entityManager)
                    .stageUntrusted(issued.id(), credential, new byte[]{1});
        });
        return issued.id();
    }

    @Test
    void attestationRejectionRequiresV20CandidateAndRecordsOnlyNotTrusted() {
        UUID actor = registrationOwner();
        registrationActor(actor, 0);
        try {
            var ledger = new StagedAttestationRejectionLedger(entityManager);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    ledger.recordUnconfiguredTrustRoots(UUID.randomUUID())));
            UUID nonce = syntheticV21Candidate(actor);
            tx(() -> ledger.recordUnconfiguredTrustRoots(nonce));
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    SELECT rejection.evidence_grade,rejection.rejection_code,
                           rejection.registration_response_sha256,
                           candidate.registration_response_sha256
                    FROM admin_passkey_attestation_rejections_staging rejection
                    JOIN admin_passkey_registration_candidates_staging candidate
                      ON candidate.challenge_id=rejection.challenge_id
                    WHERE rejection.challenge_id=:nonce
                    """).setParameter("nonce", nonce).getSingleResult();
            assertEquals("not_trusted", row[0]);
            assertEquals("trust_roots_unconfigured", row[1]);
            assertArrayEquals((byte[]) row[3], (byte[]) row[2]);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE principal_id=:actor AND resource_id=:nonce
                      AND action='admin.passkey.attestation.untrusted_no_roots'
                      AND outcome='denied'
                    """).setParameter("actor", actor).setParameter("nonce", nonce)
                    .getSingleResult()).longValue());
            assertTrue(context.getBeansOfType(StagedAttestationRejectionLedger.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(UserCredentialRepository.class)
                    .values().stream()
                    .noneMatch(bean -> !(bean instanceof StagedDenyAllWebAuthnCredentialRepository)));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void attestationRejectionCannotReplayUpgradeOrEraseHistory() {
        UUID actor = registrationOwner();
        registrationActor(actor, 0);
        try {
            UUID nonce = syntheticV21Candidate(actor);
            var ledger = new StagedAttestationRejectionLedger(entityManager);
            tx(() -> ledger.recordUnconfiguredTrustRoots(nonce));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    ledger.recordUnconfiguredTrustRoots(nonce)));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_rejections_staging
                        SET evidence_grade='trusted' WHERE challenge_id=:nonce
                        """).setParameter("nonce", nonce).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_rejections_staging
                        SET rejection_code='verified' WHERE challenge_id=:nonce
                        """).setParameter("nonce", nonce).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_attestation_rejections_staging
                        WHERE challenge_id=:nonce
                        """).setParameter("nonce", nonce).executeUpdate()));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_rejections_staging
                    WHERE challenge_id=:nonce AND evidence_grade='not_trusted'
                    """).setParameter("nonce", nonce).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void attestationRejectionDeniesForeignActorAndRotatedSession() {
        UUID actor = registrationOwner();
        UUID other = registrationOwner();
        registrationActor(actor, 0);
        try {
            UUID nonce = syntheticV21Candidate(actor);
            var ledger = new StagedAttestationRejectionLedger(entityManager);
            registrationActor(other, 0);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    ledger.recordUnconfiguredTrustRoots(nonce)));
            registrationActor(actor, 0);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1
                    WHERE id=:actor
                    """).setParameter("actor", actor).executeUpdate());
            assertThrows(RuntimeException.class, () -> tx(() ->
                    ledger.recordUnconfiguredTrustRoots(nonce)));
            registrationActor(actor, 1);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    ledger.recordUnconfiguredTrustRoots(nonce)));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_rejections_staging
                    WHERE challenge_id=:nonce
                    """).setParameter("nonce", nonce).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void suppressedAttestationRejectionAuditRollsBackReceiptThenAllowsRetry() {
        UUID actor = registrationOwner();
        registrationActor(actor, 0);
        try {
            UUID nonce = syntheticV21Candidate(actor);
            var ledger = new StagedAttestationRejectionLedger(entityManager);
            tx(() -> {
                entityManager.createNativeQuery("""
                    CREATE FUNCTION a11_attestation_suppress_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.attestation.untrusted_no_roots' THEN
                            RETURN NULL;
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
                entityManager.createNativeQuery("""
                    CREATE TRIGGER a11_attestation_suppress_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_attestation_suppress_audit()
                    """).executeUpdate();
            });
            try {
                assertThrows(IllegalStateException.class, () -> tx(() ->
                        ledger.recordUnconfiguredTrustRoots(nonce)));
                assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM admin_passkey_attestation_rejections_staging
                        WHERE challenge_id=:nonce
                        """).setParameter("nonce", nonce).getSingleResult()).longValue());
            } finally {
                tx(() -> {
                    entityManager.createNativeQuery("""
                            DROP TRIGGER IF EXISTS a11_attestation_suppress_audit
                            ON admin_access_log
                            """).executeUpdate();
                    entityManager.createNativeQuery("""
                            DROP FUNCTION IF EXISTS a11_attestation_suppress_audit()
                            """).executeUpdate();
                });
            }
            tx(() -> ledger.recordUnconfiguredTrustRoots(nonce));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_rejections_staging
                    WHERE challenge_id=:nonce AND evidence_grade='not_trusted'
                    """).setParameter("nonce", nonce).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }


    private byte[] syntheticOfflineRootDer() {
        try {
            var generator = java.security.KeyPairGenerator.getInstance("EC");
            generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            var keys = generator.generateKeyPair();
            var subject = new org.bouncycastle.asn1.x500.X500Name(
                    "CN=synthetic-untrusted-root-" + UUID.randomUUID());
            var now = java.time.Instant.now();
            var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                    subject, new java.math.BigInteger(80, new SecureRandom()).add(java.math.BigInteger.ONE),
                    java.util.Date.from(now.minusSeconds(86400)),
                    java.util.Date.from(now.plusSeconds(86400 * 30L)),
                    subject, keys.getPublic());
            builder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints,
                    true, new org.bouncycastle.asn1.x509.BasicConstraints(true));
            builder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage,
                    true, new org.bouncycastle.asn1.x509.KeyUsage(
                            org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                            | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));
            return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                    .getCertificate(builder.build(
                            new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
                                    "SHA256withECDSA").build(keys.getPrivate())))
                    .getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build synthetic CA fixture", e);
        }
    }

    private UUID stageSyntheticRoot(byte[] der) {
        return new TransactionTemplate(transactionManager).execute(ignored ->
                new StagedAttestationRootProposalLedger(entityManager,
                        java.time.Clock.systemUTC()).stageOfflineUntrustedRoot(der));
    }

    @Test
    void attestationRootProposalPersistsOnlyUntrustedCanonicalDerAndSha256() throws Exception {
        UUID owner = registrationOwner();
        byte[] der = syntheticOfflineRootDer();
        registrationActor(owner, 0);
        try {
            UUID id = stageSyntheticRoot(der);
            Object[] result = (Object[]) entityManager.createNativeQuery("""
                    SELECT submitted_by,submitted_session_version,certificate_der,
                           certificate_sha256,trust_state,source
                    FROM admin_passkey_attestation_root_proposals_staging
                    WHERE proposal_id=:id
                    """).setParameter("id", id).getSingleResult();
            assertEquals(owner, result[0]);
            assertEquals(0L, ((Number) result[1]).longValue());
            assertArrayEquals(der, (byte[]) result[2]);
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(der),
                    (byte[]) result[3]);
            assertEquals("untrusted_proposal", result[4]);
            assertEquals("offline_candidate", result[5]);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE principal_id=:actor AND resource_id=:id
                      AND action='admin.passkey.attestation.root.untrusted_proposal'
                      AND outcome='allowed'
                    """).setParameter("actor", owner).setParameter("id", id)
                    .getSingleResult()).longValue());
            assertTrue(context.getBeansOfType(StagedAttestationRootProposalLedger.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(StagedStrictAttestationWebAuthn4JFactory.class)
                    .isEmpty());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void attestationRootProposalCannotReplayUpgradeOrDelete() {
        UUID owner = registrationOwner();
        byte[] der = syntheticOfflineRootDer();
        registrationActor(owner, 0);
        try {
            UUID id = stageSyntheticRoot(der);
            assertThrows(RuntimeException.class, () -> stageSyntheticRoot(der));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_root_proposals_staging
                        SET trust_state='approved' WHERE proposal_id=:id
                        """).setParameter("id", id).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_root_proposals_staging
                        SET certificate_der=:der WHERE proposal_id=:id
                        """).setParameter("id", id)
                    .setParameter("der", syntheticOfflineRootDer()).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_attestation_root_proposals_staging
                        WHERE proposal_id=:id
                        """).setParameter("id", id).executeUpdate()));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_proposals_staging
                    WHERE proposal_id=:id AND trust_state='untrusted_proposal'
                    """).setParameter("id", id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void attestationRootProposalRejectsRotatedDisabledAndUnscopedActor() {
        UUID owner = registrationOwner();
        UUID another = passkeyFixture(syntheticPasskeyBytes(32));
        registrationActor(another, 0);
        try {
            assertThrows(RuntimeException.class,
                    () -> stageSyntheticRoot(syntheticOfflineRootDer()));
            registrationActor(owner, 0);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1 WHERE id=:actor
                    """).setParameter("actor", owner).executeUpdate());
            assertThrows(RuntimeException.class,
                    () -> stageSyntheticRoot(syntheticOfflineRootDer()));
            registrationActor(owner, 1);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET disabled_at=clock_timestamp() WHERE id=:actor
                    """).setParameter("actor", owner).executeUpdate());
            assertThrows(RuntimeException.class,
                    () -> stageSyntheticRoot(syntheticOfflineRootDer()));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void suppressedAttestationRootProposalAuditRollsBackInsert() {
        UUID owner = registrationOwner();
        registrationActor(owner, 0);
        byte[] der = syntheticOfflineRootDer();
        try {
            tx(() -> {
                entityManager.createNativeQuery("""
                    CREATE FUNCTION a11_root_proposal_suppress_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.attestation.root.untrusted_proposal'
                        THEN RETURN NULL; END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
                entityManager.createNativeQuery("""
                    CREATE TRIGGER a11_root_proposal_suppress_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_root_proposal_suppress_audit()
                    """).executeUpdate();
            });
            try {
                assertThrows(IllegalStateException.class, () -> stageSyntheticRoot(der));
                assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM admin_passkey_attestation_root_proposals_staging
                        WHERE certificate_sha256=sha256(:der)
                        """).setParameter("der", der).getSingleResult()).longValue());
            } finally {
                tx(() -> {
                    entityManager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS a11_root_proposal_suppress_audit
                        ON admin_access_log
                        """).executeUpdate();
                    entityManager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS a11_root_proposal_suppress_audit()
                        """).executeUpdate();
                });
            }
            UUID id = stageSyntheticRoot(der);
            assertNotNull(id);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }


    private UUID scopedRootReviewer(UUID proposer) {
        UUID reviewer = passkeyFixture(syntheticPasskeyBytes(32));
        tx(() -> entityManager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :reviewer,scope.role_id,scope.device_group_id
                FROM user_role_scopes scope JOIN roles r ON r.id=scope.role_id
                WHERE scope.user_id=:proposer AND r.code='owner_security_admin'
                """).setParameter("reviewer", reviewer)
                .setParameter("proposer", proposer).executeUpdate());
        return reviewer;
    }

    private void screenSyntheticRoot(UUID id, int step) {
        tx(() -> new StagedAttestationRootScreeningLedger(entityManager)
                .screenUntrustedCandidate(id, step));
    }

    @Test
    void independentRootScreeningsStayUntrustedAndBoundToCertificate() {
        UUID proposer = registrationOwner();
        UUID first = scopedRootReviewer(proposer);
        UUID second = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            registrationActor(first, 0);
            screenSyntheticRoot(id, 1);
            registrationActor(second, 0);
            screenSyntheticRoot(id, 2);
            var entries = entityManager.createNativeQuery("""
                    SELECT reviewer_id,screening_state,certificate_sha256
                    FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id ORDER BY step
                    """).setParameter("id", id).getResultList();
            assertEquals(2, entries.size());
            Object[] one = (Object[]) entries.get(0);
            Object[] two = (Object[]) entries.get(1);
            assertEquals(first, one[0]);
            assertEquals(second, two[0]);
            assertEquals("screened_untrusted", one[1]);
            assertEquals("screened_untrusted", two[1]);
            assertArrayEquals((byte[]) one[2], (byte[]) two[2]);
            assertEquals("untrusted_proposal", entityManager.createNativeQuery("""
                    SELECT trust_state FROM admin_passkey_attestation_root_proposals_staging
                    WHERE proposal_id=:id
                    """).setParameter("id", id).getSingleResult());
            assertEquals(2L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_access_log
                    WHERE resource_id=:id
                      AND action IN ('admin.passkey.root.screening.untrusted.step1',
                                     'admin.passkey.root.screening.untrusted.step2')
                    """).setParameter("id", id).getSingleResult()).longValue());
            assertTrue(context.getBeansOfType(StagedAttestationRootScreeningLedger.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(
                    StagedStrictAttestationWebAuthn4JFactory.class).isEmpty());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rootScreeningRejectsSelfReviewForeignScopeSkippedAndRepeatedSteps() {
        UUID proposer = registrationOwner();
        UUID first = scopedRootReviewer(proposer);
        UUID second = scopedRootReviewer(proposer);
        UUID outsider = registrationOwner();
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 1));
            registrationActor(outsider, 0);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 1));
            registrationActor(second, 0);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            registrationActor(first, 0);
            screenSyntheticRoot(id, 1);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 1));
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            registrationActor(second, 0);
            screenSyntheticRoot(id, 2);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            assertEquals(2L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rootScreeningDeniesRotatedProposerAndRevokedFirstReviewer() {
        UUID proposer = registrationOwner();
        UUID first = scopedRootReviewer(proposer);
        UUID second = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            registrationActor(first, 0);
            screenSyntheticRoot(id, 1);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1 WHERE id=:actor
                    """).setParameter("actor", first).executeUpdate());
            registrationActor(second, 0);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            tx(() -> entityManager.createNativeQuery("""
                    DELETE FROM user_role_scopes WHERE user_id=:actor
                    """).setParameter("actor", first).executeUpdate());
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1 WHERE id=:actor
                    """).setParameter("actor", proposer).executeUpdate());
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(id, 2));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rootScreeningSqlCannotForgeDigestUpgradeOrErase() {
        UUID proposer = registrationOwner();
        UUID first = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            registrationActor(first, 0);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        INSERT INTO admin_passkey_attestation_root_screenings_staging
                          (proposal_id,step,reviewer_id,reviewer_session_version,
                           certificate_sha256)
                        VALUES (:id,1,:actor,0,:wrong)
                        """).setParameter("id",id).setParameter("actor",first)
                    .setParameter("wrong",syntheticPasskeyBytes(32)).executeUpdate()));
            screenSyntheticRoot(id, 1);
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_root_screenings_staging
                        SET screening_state='approved' WHERE proposal_id=:id
                        """).setParameter("id",id).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_attestation_root_screenings_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).executeUpdate()));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id AND screening_state='screened_untrusted'
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void suppressedRootScreeningAuditRollsBackStepAndAllowsRetry() {
        UUID proposer = registrationOwner();
        UUID first = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            registrationActor(first, 0);
            tx(() -> {
                entityManager.createNativeQuery("""
                    CREATE FUNCTION a11_root_screening_suppress_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.root.screening.untrusted.step1'
                        THEN RETURN NULL; END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
                entityManager.createNativeQuery("""
                    CREATE TRIGGER a11_root_screening_suppress_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_root_screening_suppress_audit()
                    """).executeUpdate();
            });
            try {
                assertThrows(IllegalStateException.class, () -> screenSyntheticRoot(id, 1));
                assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).getSingleResult()).longValue());
            } finally {
                tx(() -> {
                    entityManager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS a11_root_screening_suppress_audit
                        ON admin_access_log
                        """).executeUpdate();
                    entityManager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS a11_root_screening_suppress_audit()
                        """).executeUpdate();
                });
            }
            screenSyntheticRoot(id, 1);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id AND screening_state='screened_untrusted'
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }


    private void disqualifySyntheticRoot(UUID proposalId,
            StagedAttestationRootDisqualificationLedger.Reason reason) {
        tx(() -> new StagedAttestationRootDisqualificationLedger(entityManager)
                .disqualifyUntrustedCandidate(proposalId, reason));
    }

    @Test
    void rootDisqualificationStopsNewOrSecondScreeningWithoutRewritingHistory() {
        UUID proposer = registrationOwner();
        UUID reviewer1 = scopedRootReviewer(proposer);
        UUID reviewer2 = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        try {
            UUID early = stageSyntheticRoot(syntheticOfflineRootDer());
            UUID late = stageSyntheticRoot(syntheticOfflineRootDer());
            disqualifySyntheticRoot(early,
                    StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL);
            registrationActor(reviewer1, 0);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(early, 1));
            screenSyntheticRoot(late, 1);
            registrationActor(proposer, 0);
            disqualifySyntheticRoot(late,
                    StagedAttestationRootDisqualificationLedger.Reason.SUSPECTED_COMPROMISE);
            registrationActor(reviewer2, 0);
            assertThrows(RuntimeException.class, () -> screenSyntheticRoot(late, 2));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                    WHERE proposal_id=:id
                    """).setParameter("id",late).getSingleResult()).longValue());
            for (UUID id : java.util.List.of(early, late)) {
                assertEquals("untrusted_proposal", entityManager.createNativeQuery("""
                        SELECT trust_state
                        FROM admin_passkey_attestation_root_proposals_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).getSingleResult());
                assertEquals("disqualified_untrusted", entityManager.createNativeQuery("""
                        SELECT decision_state
                        FROM admin_passkey_attestation_root_disqualifications_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).getSingleResult());
            }
            assertTrue(context.getBeansOfType(StagedAttestationRootDisqualificationLedger.class)
                    .isEmpty());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rootDisqualificationRejectsUnscopedRotatedAndDisabledActors() {
        UUID proposer = registrationOwner();
        UUID unscoped = passkeyFixture(syntheticPasskeyBytes(32));
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            registrationActor(unscoped, 0);
            assertThrows(RuntimeException.class, () -> disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL));
            registrationActor(proposer, 0);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET session_version=session_version+1 WHERE id=:id
                    """).setParameter("id",proposer).executeUpdate());
            assertThrows(RuntimeException.class, () -> disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL));
            registrationActor(proposer, 1);
            tx(() -> entityManager.createNativeQuery("""
                    UPDATE users SET disabled_at=clock_timestamp() WHERE id=:id
                    """).setParameter("id",proposer).executeUpdate());
            assertThrows(RuntimeException.class, () -> disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.SUSPECTED_COMPROMISE));
            assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_disqualifications_staging
                    WHERE proposal_id=:id
                    """).setParameter("id",id).getSingleResult()).longValue());
            // Emergency disqualification remains possible by another active
            // owner even if the original proposer has been removed.
            UUID recovery = registrationOwner();
            registrationActor(recovery, 0);
            disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.SUSPECTED_COMPROMISE);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rootDisqualificationCannotForgeFingerprintReplayOrErase() {
        UUID proposer = registrationOwner();
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        INSERT INTO admin_passkey_attestation_root_disqualifications_staging
                          (proposal_id,disqualified_by,disqualifier_session_version,
                           certificate_sha256,reason_code)
                        VALUES (:id,:actor,0,:wrong,'operator_withdrawal')
                        """).setParameter("id",id).setParameter("actor",proposer)
                    .setParameter("wrong",syntheticPasskeyBytes(32)).executeUpdate()));
            disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL);
            assertThrows(RuntimeException.class, () -> disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.METADATA_STATUS_CHANGED));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        UPDATE admin_passkey_attestation_root_disqualifications_staging
                        SET decision_state='trusted' WHERE proposal_id=:id
                        """).setParameter("id",id).executeUpdate()));
            assertThrows(RuntimeException.class, () -> tx(() ->
                    entityManager.createNativeQuery("""
                        DELETE FROM admin_passkey_attestation_root_disqualifications_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).executeUpdate()));
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_disqualifications_staging
                    WHERE proposal_id=:id AND decision_state='disqualified_untrusted'
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void suppressedRootDisqualificationAuditRollsBackAndAllowsRetry() {
        UUID proposer = registrationOwner();
        registrationActor(proposer, 0);
        try {
            UUID id = stageSyntheticRoot(syntheticOfflineRootDer());
            tx(() -> {
                entityManager.createNativeQuery("""
                    CREATE FUNCTION a11_root_disqualification_suppress_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.root.candidate.disqualified_untrusted'
                        THEN RETURN NULL; END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
                entityManager.createNativeQuery("""
                    CREATE TRIGGER a11_root_disqualification_suppress_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_root_disqualification_suppress_audit()
                    """).executeUpdate();
            });
            try {
                assertThrows(IllegalStateException.class, () -> disqualifySyntheticRoot(id,
                        StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL));
                assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM admin_passkey_attestation_root_disqualifications_staging
                        WHERE proposal_id=:id
                        """).setParameter("id",id).getSingleResult()).longValue());
            } finally {
                tx(() -> {
                    entityManager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS a11_root_disqualification_suppress_audit
                        ON admin_access_log
                        """).executeUpdate();
                    entityManager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS a11_root_disqualification_suppress_audit()
                        """).executeUpdate();
                });
            }
            disqualifySyntheticRoot(id,
                    StagedAttestationRootDisqualificationLedger.Reason.OPERATOR_WITHDRAWAL);
            assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM admin_passkey_attestation_root_disqualifications_staging
                    WHERE proposal_id=:id
                    """).setParameter("id",id).getSingleResult()).longValue());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentRootScreeningWaitsForDisqualificationAndRejects() throws Exception {
        UUID proposer = registrationOwner();
        UUID reviewer = scopedRootReviewer(proposer);
        registrationActor(proposer, 0);
        UUID id;
        try {
            id = stageSyntheticRoot(syntheticOfflineRootDer());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        var locked = new java.util.concurrent.CountDownLatch(1);
        var finish = new java.util.concurrent.CountDownLatch(1);
        var attempted = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var disqualifier = executor.submit(() -> {
                registrationActor(proposer, 0);
                try {
                    tx(() -> {
                        entityManager.createNativeQuery("""
                                SELECT proposal_id
                                FROM admin_passkey_attestation_root_proposals_staging
                                WHERE proposal_id=:id FOR UPDATE
                                """).setParameter("id",id).getSingleResult();
                        locked.countDown();
                        try {
                            if (!finish.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
                                throw new IllegalStateException("synthetic lock release absent");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                        new StagedAttestationRootDisqualificationLedger(entityManager)
                                .disqualifyUntrustedCandidate(id,
                                    StagedAttestationRootDisqualificationLedger.Reason
                                            .OPERATOR_WITHDRAWAL);
                    });
                    return true;
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
            assertTrue(locked.await(15, java.util.concurrent.TimeUnit.SECONDS));
            var screening = executor.submit(() -> {
                registrationActor(reviewer, 0);
                attempted.countDown();
                try {
                    screenSyntheticRoot(id, 1);
                    return false;
                } catch (RuntimeException expected) {
                    return true;
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
            assertTrue(attempted.await(15, java.util.concurrent.TimeUnit.SECONDS));
            finish.countDown();
            assertTrue(disqualifier.get(25, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(screening.get(25, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            finish.countDown();
        }
        assertEquals(0L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_passkey_attestation_root_screenings_staging
                WHERE proposal_id=:id
                """).setParameter("id",id).getSingleResult()).longValue());
        assertEquals(1L, ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_passkey_attestation_root_disqualifications_staging
                WHERE proposal_id=:id
                """).setParameter("id",id).getSingleResult()).longValue());
    }

    @Test
    void offlineSignedMdsInspectorIsNotInstalledOrTrustedByRealPgBoot() {
        assertTrue(context.getBeansOfType(
                StagedOfflineSignedFidoMdsEnvelopeInspector.class).isEmpty());
        assertTrue(context.getBeansOfType(
                StagedStrictAttestationWebAuthn4JFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(
                StagedAttestationRootDisqualificationLedger.class).isEmpty());
        assertTrue(context.getBeansOfType(UserCredentialRepository.class)
                .values().stream().noneMatch(bean ->
                    !(bean instanceof StagedDenyAllWebAuthnCredentialRepository)));
    }

    @Test
    void offlinePkixMdsSigningInspectorNeverBecomesApprovedSpringTrustSource() {
        assertTrue(context.getBeansOfType(
                StagedOfflineFidoMdsSigningChainInspector.class).isEmpty());
        assertTrue(context.getBeansOfType(
                StagedOfflineSignedFidoMdsEnvelopeInspector.class).isEmpty());
        assertTrue(context.getBeansOfType(
                StagedStrictAttestationWebAuthn4JFactory.class).isEmpty());
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
                    .andExpect(status().is4xxClientError());
        }
        // Anonymous deny-all may surface as 401 rather than 403. The
        // authenticated test below confirms even a real PG18-backed
        // administrator cannot make the reviewer route usable.
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
