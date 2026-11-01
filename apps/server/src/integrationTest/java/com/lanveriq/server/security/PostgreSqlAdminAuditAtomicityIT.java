package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Manual-only PostgreSQL 18.6 acceptance for default-OFF administrator
 * mutations. No real administrator password, CLI, browser session, LAN
 * endpoint or production database is used. Explicit Spring transactions
 * must roll back *all* writes when a PostgreSQL trigger suppresses an insert.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false"
})
class PostgreSqlAdminAuditAtomicityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_admin_atomicity_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private JpaFirstAdminBootstrapRepository bootstrap;

    @Autowired
    private JpaAdminSessionRevocationRepository sessions;

    @Autowired
    private EntityManager manager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void inTransaction(Runnable operation) {
        transaction().executeWithoutResult(ignored -> operation.run());
    }

    @BeforeEach
    void resetDisposableSyntheticDatabase() {
        removeSuppression();
        inTransaction(() -> {
            manager.createNativeQuery("DELETE FROM admin_access_log").executeUpdate();
            manager.createNativeQuery("DELETE FROM user_role_scopes").executeUpdate();
            manager.createNativeQuery("DELETE FROM users").executeUpdate();
            manager.createNativeQuery("DELETE FROM device_groups").executeUpdate();
        });
        assertEquals(0L, count("users"));
        assertEquals(0L, count("device_groups"));
    }

    @AfterEach
    void removeOnlySyntheticTriggers() {
        removeSuppression();
    }

    private void removeSuppression() {
        inTransaction(() -> {
            manager.createNativeQuery("""
                    DROP TRIGGER IF EXISTS synthetic_admin_scope_suppress
                    ON user_role_scopes
                    """).executeUpdate();
            manager.createNativeQuery("""
                    DROP TRIGGER IF EXISTS synthetic_admin_audit_suppress
                    ON admin_access_log
                    """).executeUpdate();
            manager.createNativeQuery("""
                    DROP FUNCTION IF EXISTS synthetic_admin_scope_suppress()
                    """).executeUpdate();
            manager.createNativeQuery("""
                    DROP FUNCTION IF EXISTS synthetic_admin_audit_suppress()
                    """).executeUpdate();
        });
    }

    private void suppressScopeInsert() {
        inTransaction(() -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION synthetic_admin_scope_suppress()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                      RETURN NULL;
                    END
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER synthetic_admin_scope_suppress
                    BEFORE INSERT ON user_role_scopes
                    FOR EACH ROW EXECUTE FUNCTION synthetic_admin_scope_suppress()
                    """).executeUpdate();
        });
    }

    private void suppressAdminAudit(String action) {
        if (!"administrator.bootstrap".equals(action)
                && !"admin.sessions.revoke".equals(action)) {
            throw new IllegalArgumentException("Unexpected synthetic audit action");
        }
        // Constant test-only actions; no production SQL identifier/input interpolation.
        inTransaction(() -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION synthetic_admin_audit_suppress()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                      IF NEW.action = 'administrator.bootstrap'
                        OR NEW.action = 'admin.sessions.revoke' THEN
                        RETURN NULL;
                      END IF;
                      RETURN NEW;
                    END
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER synthetic_admin_audit_suppress
                    BEFORE INSERT ON admin_access_log
                    FOR EACH ROW EXECUTE FUNCTION synthetic_admin_audit_suppress()
                    """).executeUpdate();
        });
    }

    private long count(String table) {
        String sql = switch (table) {
            case "users" -> "SELECT COUNT(*) FROM users";
            case "device_groups" -> "SELECT COUNT(*) FROM device_groups";
            case "user_role_scopes" -> "SELECT COUNT(*) FROM user_role_scopes";
            case "admin_access_log" -> "SELECT COUNT(*) FROM admin_access_log";
            case "user_data_grants" -> "SELECT COUNT(*) FROM user_data_grants";
            default -> throw new IllegalArgumentException("Unsupported synthetic table");
        };
        return ((Number) manager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    /**
     * Repository beans are advised with Spring persistence exception translation.
     * IllegalStateException from the repository is wrapped before it crosses
     * the Spring proxy. Verify the precise original invariant failure as well
     * as the rollback, never accept an unrelated SQL or authentication error.
     */
    private void assertRepositoryInvariantRejected(String reason, Executable attempted) {
        var failure = assertThrows(InvalidDataAccessApiUsageException.class, attempted);
        assertTrue(failure.getCause() instanceof IllegalStateException,
                "The translated repository failure must preserve its original cause");
        assertEquals(reason, failure.getCause().getMessage());
    }

    private FirstAdminBootstrapRepository.BootstrappedAdmin firstOwner() {
        return transaction().execute(ignored -> bootstrap.createFirstOwner(
                "synthetic.owner", "synthetic-not-a-login-hash", "Synthetic Engineering"));
    }

    private UUID insertSyntheticUser() {
        return transaction().execute(ignored -> (UUID) manager.createNativeQuery("""
                INSERT INTO users (normalized_login,password_hash)
                VALUES (:login,'synthetic-not-a-login-hash') RETURNING id
                """).setParameter("login", "synthetic." + UUID.randomUUID())
                .getSingleResult());
    }

    private UUID insertGroup() {
        return transaction().execute(ignored -> (UUID) manager.createNativeQuery("""
                INSERT INTO device_groups(name) VALUES (:name) RETURNING id
                """).setParameter("name", "Synthetic-" + UUID.randomUUID())
                .getSingleResult());
    }

    private void assign(UUID user, UUID group, String role) {
        inTransaction(() -> assertEquals(1, manager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :user,r.id,:group FROM roles AS r WHERE r.code=:role
                """).setParameter("user", user).setParameter("group", group)
                .setParameter("role", role).executeUpdate()));
    }

    private long sessionVersion(UUID user) {
        return ((Number) manager.createNativeQuery("""
                SELECT session_version FROM users WHERE id=:id
                """).setParameter("id", user).getSingleResult()).longValue();
    }

    private long auditCount(String action) {
        return ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log WHERE action=:action
                """).setParameter("action", action).getSingleResult()).longValue();
    }

    @Test
    void suppressedBootstrapGrantRollsBackUserAndGroupWithoutAudit() {
        suppressScopeInsert();
        assertRepositoryInvariantRejected("Initial administrator grant was not persisted", this::firstOwner);
        assertEquals(0L, count("users"));
        assertEquals(0L, count("device_groups"));
        assertEquals(0L, count("user_role_scopes"));
        assertEquals(0L, count("admin_access_log"));
        removeSuppression();
        firstOwner();
        assertEquals(1L, count("users"));
        assertEquals(1L, count("device_groups"));
        assertEquals(1L, count("user_role_scopes"));
        assertEquals(1L, auditCount("administrator.bootstrap"));
    }

    @Test
    void suppressedBootstrapAuditRollsBackUserGroupAndGrant() {
        suppressAdminAudit("administrator.bootstrap");
        assertRepositoryInvariantRejected("Initial administrator audit was not persisted", this::firstOwner);
        assertEquals(0L, count("users"));
        assertEquals(0L, count("device_groups"));
        assertEquals(0L, count("user_role_scopes"));
        assertEquals(0L, count("admin_access_log"));
        removeSuppression();
        firstOwner();
        assertEquals(1L, count("users"));
        assertEquals(1L, count("user_role_scopes"));
        assertEquals(1L, auditCount("administrator.bootstrap"));
    }

    @Test
    void successfulBootstrapHasOneScopedOwnerAndNoImplicitDataGrant() {
        var created = firstOwner();
        assertEquals(1L, count("users"));
        assertEquals(1L, count("device_groups"));
        assertEquals(1L, count("user_role_scopes"));
        assertEquals(1L, auditCount("administrator.bootstrap"));
        assertEquals(0L, count("user_data_grants"));
        assertEquals(1L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM user_role_scopes s
                JOIN roles r ON r.id=s.role_id
                WHERE s.user_id=:user AND s.device_group_id=:group
                  AND r.code='owner_security_admin'
                """).setParameter("user", created.userId())
                .setParameter("group", created.groupId()).getSingleResult()).longValue());
        assertRepositoryInvariantRejected("Initial administrator already exists", this::firstOwner);
        assertEquals(1L, count("users"));
        assertEquals(1L, auditCount("administrator.bootstrap"));
    }

    @Test
    void suppressedSessionAuditRollsBackVersionAndAllowsRetry() {
        var owner = firstOwner();
        UUID target = insertSyntheticUser();
        assign(target, owner.groupId(), "it_operator");
        suppressAdminAudit("admin.sessions.revoke");
        assertRepositoryInvariantRejected("Session revocation audit was not persisted",
                () -> transaction().executeWithoutResult(
                ignored -> sessions.rotateAllSessions(
                        owner.userId(), target, owner.groupId())));
        assertEquals(0L, sessionVersion(target));
        assertEquals(0L, auditCount("admin.sessions.revoke"));
        removeSuppression();
        long next = transaction().execute(ignored -> sessions.rotateAllSessions(
                owner.userId(), target, owner.groupId()));
        assertEquals(1L, next);
        assertEquals(1L, sessionVersion(target));
        assertEquals(1L, auditCount("admin.sessions.revoke"));
    }

    @Test
    void wrongGroupAndDisabledIssuerCannotChangeSessionVersionOrAudit() {
        var owner = firstOwner();
        UUID target = insertSyntheticUser();
        UUID unrelated = insertGroup();
        assign(target, unrelated, "it_operator");
        assertThrows(AccessDeniedException.class, () -> transaction().executeWithoutResult(
                ignored -> sessions.rotateAllSessions(
                        owner.userId(), target, owner.groupId())));
        assertEquals(0L, sessionVersion(target));
        assign(target, owner.groupId(), "it_operator");
        inTransaction(() -> manager.createNativeQuery("""
                UPDATE users SET disabled_at=clock_timestamp() WHERE id=:owner
                """).setParameter("owner", owner.userId()).executeUpdate());
        assertThrows(AccessDeniedException.class, () -> transaction().executeWithoutResult(
                ignored -> sessions.rotateAllSessions(
                        owner.userId(), target, owner.groupId())));
        assertEquals(0L, sessionVersion(target));
        assertEquals(0L, auditCount("admin.sessions.revoke"));
    }

    @Test
    void successfulSessionRevocationCommitsExactlyOneAuditPerIncrement() {
        var owner = firstOwner();
        UUID target = insertSyntheticUser();
        assign(target, owner.groupId(), "it_operator");
        long first = transaction().execute(ignored ->
                sessions.rotateAllSessions(owner.userId(), target, owner.groupId()));
        long second = transaction().execute(ignored ->
                sessions.rotateAllSessions(owner.userId(), target, owner.groupId()));
        assertEquals(1L, first);
        assertEquals(2L, second);
        assertEquals(2L, sessionVersion(target));
        assertEquals(2L, auditCount("admin.sessions.revoke"));
        assertEquals(1L, auditCount("administrator.bootstrap"));
    }
    @Test
    void revokedOwnerGrantDuringTargetLockWaitDeniesWithoutSessionMutation() throws Exception {
        var owner = firstOwner();
        UUID target = insertSyntheticUser();
        assign(target, owner.groupId(), "it_operator");

        var holderReady = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var workerStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() ->
                    inTransaction(() -> {
                        manager.createNativeQuery("""
                                SELECT id FROM users WHERE id=:target FOR UPDATE
                                """).setParameter("target", target).getSingleResult();
                        holderReady.countDown();
                        try {
                            if (!releaseHolder.await(25, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Synthetic user row lock expired");
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(error);
                        }
                    }));
            try {
                assertTrue(holderReady.await(15, TimeUnit.SECONDS));
                var attempt = pool.submit(() -> {
                    workerStarted.countDown();
                    return assertThrows(AccessDeniedException.class, () ->
                            inTransaction(() -> sessions.rotateAllSessions(
                                    owner.userId(), target, owner.groupId())));
                });
                assertTrue(workerStarted.await(15, TimeUnit.SECONDS));

                // Observe a genuine PostgreSQL lock wait, not sequential
                // grant removal before the revocation attempt even begins.
                boolean blocked = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < deadline) {
                    long waits = ((Number) manager.createNativeQuery("""
                            SELECT COUNT(*) FROM pg_stat_activity
                            WHERE datname=current_database()
                              AND wait_event_type='Lock'
                              AND query LIKE '%ORDER BY u.id FOR UPDATE OF u%'
                            """).getSingleResult()).longValue();
                    if (waits > 0) {
                        blocked = true;
                        break;
                    }
                    Thread.sleep(50);
                }
                assertTrue(blocked, "Must observe actual PostgreSQL user-row lock wait");
                inTransaction(() -> assertEquals(1, manager.createNativeQuery("""
                        DELETE FROM user_role_scopes
                        WHERE user_id=:owner AND device_group_id=:group
                          AND role_id=(SELECT id FROM roles
                                       WHERE code='owner_security_admin')
                        """).setParameter("owner", owner.userId())
                        .setParameter("group", owner.groupId()).executeUpdate()));
                releaseHolder.countDown();
                holder.get(25, TimeUnit.SECONDS);
                attempt.get(25, TimeUnit.SECONDS);
            } finally {
                releaseHolder.countDown();
            }
        }
        assertEquals(0L, sessionVersion(target));
        assertEquals(0L, auditCount("admin.sessions.revoke"));
    }

}
