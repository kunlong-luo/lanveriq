package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import com.lanveriq.server.LanveriqServerApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DB-ROLE-01: isolated actual PG18 authenticated migrator/runtime sessions.
 * JDBC is used ONLY in this infrastructure integration test, never the
 * application's JPA/Hibernate persistence layer. NO production credentials,
 * roles, server, shared database, Agent endpoint or device are touched.
 */
@Testcontainers
class PostgreSqlRoleSeparationIT {
    private static final String DB="lanveriq_roles_synthetic";
    private static final String MIGRATOR="lanveriq_migrator_synthetic";
    private static final String RUNTIME="lanveriq_runtime_synthetic";
    private static final String MIGRATOR_PW="isolated-migrator-test-only";
    private static final String RUNTIME_PW="isolated-runtime-test-only";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7")
            .withDatabaseName(DB)
            .withUsername("lanveriq_bootstrap_synthetic")
            .withPassword("isolated-bootstrap-test-only");

    private static Connection bootstrap() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(),postgres.getPassword());
    }
    private static Connection migrator() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),MIGRATOR,MIGRATOR_PW);
    }
    private static Connection runtime() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(),RUNTIME,RUNTIME_PW);
    }
    private static Object one(Connection db,String sql) throws SQLException {
        try(Statement st=db.createStatement();ResultSet rs=st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getObject(1);
        }
    }
    private static void sql(Connection db,String sql) throws SQLException {
        try(Statement st=db.createStatement()){st.execute(sql);}
    }
    private static void blocked(Connection db,String sql) throws SQLException {
        try(Statement st=db.createStatement()) {
            SQLException ex=assertThrows(SQLException.class,()->st.execute(sql),sql);
            assertEquals("42501",ex.getSQLState(),"must be a privilege refusal");
        }
    }

    @BeforeAll
    static void migrateAsOwnerThenGrantOnlySelectedRuntimeRights()
            throws Exception {
        try(Connection admin=bootstrap()) {
            sql(admin,"REVOKE ALL ON DATABASE lanveriq_roles_synthetic FROM PUBLIC");
            sql(admin,"REVOKE ALL ON SCHEMA public FROM PUBLIC");
            sql(admin,"""
                    CREATE ROLE lanveriq_migrator_synthetic
                    LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
                    NOREPLICATION NOBYPASSRLS PASSWORD 'isolated-migrator-test-only'
                    """);
            sql(admin,"""
                    CREATE ROLE lanveriq_runtime_synthetic
                    LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
                    NOREPLICATION NOBYPASSRLS PASSWORD 'isolated-runtime-test-only'
                    """);
            sql(admin,"""
                    GRANT CONNECT ON DATABASE lanveriq_roles_synthetic TO
                    lanveriq_migrator_synthetic,lanveriq_runtime_synthetic
                    """);
            sql(admin,"GRANT USAGE,CREATE ON SCHEMA public TO lanveriq_migrator_synthetic");
            sql(admin,"GRANT USAGE ON SCHEMA public TO lanveriq_runtime_synthetic");
        }
        // Real Flyway V1...V30, owned by a NON-SUPERUSER distinct from runtime.
        var result=Flyway.configure()
                .dataSource(postgres.getJdbcUrl(),MIGRATOR,MIGRATOR_PW)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load().migrate();
        assertEquals(30,result.migrationsExecuted);

        // Test-only, explicit and incomplete grant manifest, NOT production:
        // narrow positive application write + security history read-only.
        try(Connection owner=migrator()) {
            sql(owner,"""
                    GRANT SELECT ON
                      admin_fido_mds_untrusted_streams_staging,
                      admin_fido_mds_untrusted_observations_staging,
                      admin_fido_mds_source_receipts_staging,
                      admin_fido_mds_source_fences_staging,
                      admin_fido_mds_source_fence_checkpoints_staging
                    TO lanveriq_runtime_synthetic
                    """);
            sql(owner,"GRANT SELECT,INSERT ON device_groups TO lanveriq_runtime_synthetic");
            // DB-ROLE-02: exact column dependencies of the existing
            // JpaAdminSessionRevocationRepository. This is TEST-ONLY.
            sql(owner,"GRANT SELECT (id,disabled_at,session_version) ON users TO lanveriq_runtime_synthetic");
            sql(owner,"GRANT UPDATE (session_version) ON users TO lanveriq_runtime_synthetic");
            sql(owner,"GRANT SELECT (id,code) ON roles TO lanveriq_runtime_synthetic");
            sql(owner,"GRANT SELECT (user_id,role_id,device_group_id) ON user_role_scopes TO lanveriq_runtime_synthetic");
            // Postgres SELECT ... FOR SHARE OF scope needs UPDATE privilege
            // on at least one column of its row-locked table. The metadata
            // column concession is not approved for production grants.
            sql(owner,"GRANT UPDATE (granted_at) ON user_role_scopes TO lanveriq_runtime_synthetic");
        }
    }

    @Test
    void migratorOwnsAllFlywayMigrationsAndRuntimeHasNarrowAppWrite()
            throws Exception {
        try(Connection owner=migrator();Connection app=runtime()) {
            assertEquals(MIGRATOR,one(owner,"SELECT current_user"));
            assertEquals(RUNTIME,one(app,"SELECT current_user"));
            assertNotEquals(one(owner,"SELECT current_user"),
                    one(app,"SELECT current_user"));
            assertEquals(MIGRATOR,one(owner,"""
                    SELECT pg_get_userbyid(relowner)
                    FROM pg_class WHERE oid='public.device_groups'::regclass
                    """));
            assertEquals(0L,((Number)one(app,
                    "SELECT count(*) FROM admin_fido_mds_source_receipts_staging"))
                    .longValue());
            try(var stmt=app.prepareStatement(
                    "INSERT INTO device_groups(name) VALUES (?) RETURNING id")) {
                stmt.setString(1,"db-role-only-"+UUID.randomUUID());
                try(ResultSet rs=stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getObject(1) instanceof UUID);
                }
            }
            assertEquals(false,one(app,"""
                    SELECT has_schema_privilege(current_user,'public','CREATE')
                    """));
            assertEquals(false,one(app,"""
                    SELECT has_database_privilege(current_user,current_database(),'CREATE')
                    """));
        }
    }

    @Test
    void runtimeCannotInheritOrSetMigratorAndCannotUseDdlOrTruncate()
            throws Exception {
        try(Connection app=runtime()) {
            assertEquals(false,one(app,"""
                    SELECT rolsuper OR rolcreatedb OR rolcreaterole
                           OR rolreplication OR rolbypassrls
                    FROM pg_roles WHERE rolname=current_user
                    """));
            for(String statement:new String[]{
                    "SET ROLE lanveriq_migrator_synthetic",
                    "CREATE TABLE a11_illegal_runtime_table(id int)",
                    "ALTER TABLE admin_fido_mds_source_receipts_staging DISABLE TRIGGER ALL",
                    "TRUNCATE TABLE admin_fido_mds_source_receipts_staging CASCADE",
                    "DROP TABLE admin_fido_mds_untrusted_streams_staging CASCADE",
                    "CREATE SCHEMA a11_illegal_runtime_schema"}) {
                blocked(app,statement);
            }
        }
    }

    @Test
    void runtimeCannotMutateUntrustedHistoryAuditOrFlywayDespiteLimitedDml()
            throws Exception {
        try(Connection app=runtime()) {
            for(String statement:new String[]{
                    "UPDATE admin_fido_mds_source_receipts_staging SET blob_no=1 WHERE false",
                    "DELETE FROM admin_fido_mds_source_fences_staging WHERE false",
                    "INSERT INTO admin_fido_mds_untrusted_streams_staging DEFAULT VALUES",
                    "DELETE FROM admin_access_log WHERE false",
                    "TRUNCATE TABLE device_groups",
                    "SELECT * FROM flyway_schema_history",
                    "SELECT password_hash FROM users",
                    "SELECT * FROM admin_access_log",
                    "DELETE FROM device_groups WHERE false"}) {
                blocked(app,statement);
            }
        }
    }

    @Test
    void futureMigratorObjectsNeverImplicitlyGrantRuntimeAccess()
            throws Exception {
        try(Connection owner=migrator()) {
            sql(owner,"""
                    CREATE TABLE a11_role_future_synthetic(id int PRIMARY KEY)
                    """);
            try {
                try(Connection app=runtime()) {
                    blocked(app,"SELECT * FROM a11_role_future_synthetic");
                    blocked(app,"INSERT INTO a11_role_future_synthetic(id) VALUES (1)");
                    blocked(app,"ALTER TABLE a11_role_future_synthetic ADD COLUMN p int");
                }
                assertEquals(MIGRATOR,one(owner,"""
                        SELECT pg_get_userbyid(relowner) FROM pg_class
                        WHERE oid='public.a11_role_future_synthetic'::regclass
                        """));
            } finally {
                sql(owner,"DROP TABLE a11_role_future_synthetic");
            }
        }
    }

    private record SyntheticRevocationScope(UUID owner, UUID target, UUID group) {}

    private static SyntheticRevocationScope syntheticRevocationScope()
            throws SQLException {
        try(Connection owner=migrator()) {
            owner.setAutoCommit(false);
            try {
                UUID group=(UUID)one(owner,"""
                        INSERT INTO device_groups(name)
                        VALUES ('role-scope-${random}')
                        RETURNING id
                        """.replace("${random}",UUID.randomUUID().toString()));
                UUID issuer=(UUID)one(owner,"""
                        INSERT INTO users(normalized_login,password_hash)
                        VALUES ('issuer-${random}','synthetic-do-not-authenticate')
                        RETURNING id
                        """.replace("${random}",UUID.randomUUID().toString()));
                UUID target=(UUID)one(owner,"""
                        INSERT INTO users(normalized_login,password_hash)
                        VALUES ('target-${random}','synthetic-do-not-authenticate')
                        RETURNING id
                        """.replace("${random}",UUID.randomUUID().toString()));
                sql(owner,"""
                        INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                        SELECT '${issuer}'::uuid,id,'${group}'::uuid
                        FROM roles WHERE code='owner_security_admin'
                        """.replace("${issuer}",issuer.toString())
                            .replace("${group}",group.toString()));
                sql(owner,"""
                        INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                        SELECT '${target}'::uuid,id,'${group}'::uuid
                        FROM roles WHERE code='it_operator'
                        """.replace("${target}",target.toString())
                            .replace("${group}",group.toString()));
                owner.commit();
                return new SyntheticRevocationScope(issuer,target,group);
            } catch(SQLException | RuntimeException failure) {
                owner.rollback();
                throw failure;
            }
        }
    }

    private static long sessionVersion(UUID target) throws SQLException {
        try(Connection owner=migrator()) {
            return ((Number)one(owner,"""
                    SELECT session_version FROM users WHERE id='${id}'::uuid
                    """.replace("${id}",target.toString()))).longValue();
        }
    }

    private static long sessionAudits(UUID target) throws SQLException {
        try(Connection owner=migrator()) {
            return ((Number)one(owner,"""
                    SELECT count(*) FROM admin_access_log
                    WHERE resource_id='${id}'::uuid AND action='admin.sessions.revoke'
                    """.replace("${id}",target.toString()))).longValue();
        }
    }

    private static void assertPermissionDeniedInCauseChain(Throwable thrown) {
        for(Throwable at=thrown;at!=null;at=at.getCause()) {
            if(at instanceof SQLException sql && "42501".equals(sql.getSQLState())) {
                return;
            }
        }
        fail("Expected PostgreSQL SQLSTATE 42501 privilege denial in actual JPA exception chain");
    }

    /**
     * Start the actual Spring Boot/JPA service with the opted-in runtime-only
     * profile. The JVM receives no migration password, no Flyway Bean and no
     * HTTP server. The server's real JPA transaction and authorization SQL
     * execute over an independently authenticated PostgreSQL runtime login.
     */
    private static org.springframework.context.ConfigurableApplicationContext
            runtimeSpring() {
        var context=new SpringApplicationBuilder(LanveriqServerApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.profiles.active=db-runtime-separation-acceptance",
                        "LANVERIQ_DB_RUNTIME_USER="+RUNTIME,
                        "LANVERIQ_DB_RUNTIME_PASSWORD="+RUNTIME_PW,
                        // application.yml resolves LANVERIQ_DB_URL at config-data
                        // precedence; SpringApplicationBuilder.properties() sets
                        // LOWEST precedence defaults. Using spring.datasource.url
                        // here would silently fall back to 127.0.0.1:5432.
                        "LANVERIQ_DB_URL="+postgres.getJdbcUrl(),
                        "lanveriq.bootstrap.enabled=false",
                        "lanveriq.console.auth.enabled=false",
                        "spring.main.banner-mode=off")
                .run();
        try {
            assertEquals(postgres.getJdbcUrl(),
                    context.getEnvironment().getProperty("spring.datasource.url"),
                    "Spring runtime must be bound to the disposable PG18 URL");
            assertEquals(RUNTIME,
                    context.getEnvironment().getProperty("spring.datasource.username"),
                    "Spring runtime must not inherit migrator or bootstrap login");
            return context;
        } catch(RuntimeException | AssertionError unexpected) {
            context.close();
            throw unexpected;
        }
    }

    @Test
    void realSpringJpaSessionRevocationRollsBackIfRuntimeCannotWriteAudit()
            throws Exception {
        var scope=syntheticRevocationScope();
        try(var ctx=runtimeSpring()) {
            assertTrue(ctx.getBeansOfType(Flyway.class).isEmpty(),
                    "long-running runtime must never start a migrator Flyway Bean");
            assertEquals(postgres.getJdbcUrl(),
                    ctx.getEnvironment().getProperty("spring.datasource.url"),
                    "real runtime Spring DataSource must use the disposable PG18 URL");
            var bean=ctx.getBean(AdminSessionRevocationService.class);
            var principal=new AdminPrincipal(scope.owner(),"synthetic.owner",
                    "synthetic-no-secret",true,0);
            var authentication=new UsernamePasswordAuthenticationToken(
                    principal,"synthetic-test",principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                // Real JPA must reach the audit INSERT with a live scoped owner.
                // The role lacks only audit INSERT: target UPDATE MUST rollback.
                var failAudit=assertThrows(RuntimeException.class,() ->
                        bean.revokeAll(scope.target(),scope.group()));
                assertPermissionDeniedInCauseChain(failAudit);
                assertEquals(0L,sessionVersion(scope.target()));
                assertEquals(0L,sessionAudits(scope.target()));

                try(Connection owner=migrator()) {
                    sql(owner,"GRANT INSERT ON admin_access_log TO lanveriq_runtime_synthetic");
                }
                try {
                    // Same Spring service, same restricted database login:
                    // a narrowly authorized complete transaction now commits.
                    assertEquals(1L,bean.revokeAll(scope.target(),scope.group()));
                    assertEquals(1L,sessionVersion(scope.target()));
                    assertEquals(1L,sessionAudits(scope.target()));
                    try(Connection app=runtime()) {
                        blocked(app,"DELETE FROM admin_access_log WHERE false");
                        blocked(app,"SELECT * FROM admin_access_log");
                        blocked(app,"UPDATE users SET password_hash='x' WHERE false");
                        blocked(app,"ALTER TABLE users DISABLE TRIGGER ALL");
                    }
                } finally {
                    try(Connection owner=migrator()) {
                        sql(owner,"REVOKE INSERT ON admin_access_log FROM lanveriq_runtime_synthetic");
                    }
                }
                var deniedAgain=assertThrows(RuntimeException.class,() ->
                        bean.revokeAll(scope.target(),scope.group()));
                assertPermissionDeniedInCauseChain(deniedAgain);
                assertEquals(1L,sessionVersion(scope.target()));
                assertEquals(1L,sessionAudits(scope.target()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void realSpringJpaRevocationCannotCommitWithoutSessionVersionUpdateGrant()
            throws Exception {
        var scope=syntheticRevocationScope();
        try(Connection owner=migrator()) {
            sql(owner,"REVOKE UPDATE (session_version) ON users FROM lanveriq_runtime_synthetic");
        }
        try {
            try(var ctx=runtimeSpring()) {
                var bean=ctx.getBean(AdminSessionRevocationService.class);
                var principal=new AdminPrincipal(scope.owner(),"synthetic.owner",
                        "synthetic-no-secret",true,0);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal,
                                "synthetic-test",principal.getAuthorities()));
                try {
                    var denied=assertThrows(RuntimeException.class,() ->
                            bean.revokeAll(scope.target(),scope.group()));
                    assertPermissionDeniedInCauseChain(denied);
                    assertEquals(0L,sessionVersion(scope.target()));
                    assertEquals(0L,sessionAudits(scope.target()));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        } finally {
            try(Connection owner=migrator()) {
                sql(owner,"GRANT UPDATE (session_version) ON users TO lanveriq_runtime_synthetic");
            }
        }
    }
}
