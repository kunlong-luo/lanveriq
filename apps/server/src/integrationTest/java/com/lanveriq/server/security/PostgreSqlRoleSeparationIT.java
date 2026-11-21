package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
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
        // Real Flyway V1...V29, owned by a NON-SUPERUSER distinct from runtime.
        var result=Flyway.configure()
                .dataSource(postgres.getJdbcUrl(),MIGRATOR,MIGRATOR_PW)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load().migrate();
        assertEquals(29,result.migrationsExecuted);

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
}
