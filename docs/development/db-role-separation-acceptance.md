# DB-ROLE-01 · PostgreSQL 18 migrator/runtime isolation (synthetic acceptance only)

**Status: isolated privilege rehearsal. NOT production role provisioning, not a release gate approval.**  
Functional scope: `apps/server/src/integrationTest/java/com/lanveriq/server/security/PostgreSqlRoleSeparationIT.java` and `application-db-runtime-separation-acceptance.yml`. No production password, production role or connected DB is created/modified by this work. Issue #5 stays OPEN; v0.1 is NOT RELEASE READY.

## Actual tested PostgreSQL 18 design

In a disposable `postgres:18.6` Testcontainers database, a one-use bootstrap principal creates two separate **LOGIN, NOINHERIT, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION, NOBYPASSRLS** SQL identities with separate synthetic passwords. The bootstrap principal revokes PUBLIC database CONNECT and schema permissions, grants the non-superuser migrator `CONNECT` plus schema `USAGE,CREATE` and gives runtime `CONNECT` plus `USAGE` only. The migrator executes **all real V1–V29 Flyway migrations** and owns their tables. The runtime is *never* a member of the migrator role, has no object ownership and cannot `SET ROLE` to the migrator.

After migration, the owner explicitly grants runtime **SELECT** on a small set of security/metadata tables, plus **SELECT,INSERT** on the real `device_groups` application table solely to prove a narrowly authorized DML write can work. The test checks authenticated `current_user` on separate TCP connections and owner identity via `pg_class.relowner`. It asserts SQLSTATE **42501** for runtime `CREATE TABLE`, `CREATE SCHEMA`, `ALTER TABLE ... DISABLE TRIGGER`, `DROP TABLE`, `TRUNCATE`, audit or V25–V29 history mutation, unauthorized Flyway history read and `SET ROLE`. It also creates a future migration-owned table and proves the runtime receives no implicit grants.

**The example grants are deliberately INCOMPLETE and not suitable for a production application.** Most real API writes, logging and authorization flows are not yet privilege-mapped under a low-privilege account. The test does not claim a full application can run against these grants. The migration role is not a superuser; only the disposable bootstrap account can create SQL roles.

## Separate Spring acceptance profile

`spring.profiles.active=db-runtime-separation-acceptance` loads a deliberately non-HTTP, **Flyway-disabled** Spring Boot configuration:

- `LANVERIQ_DB_URL` points to an approved local/disposable database; the default URL is loopback only
- `LANVERIQ_DB_RUNTIME_USER` and `LANVERIQ_DB_RUNTIME_PASSWORD` **must** be supplied explicitly; no password defaults or secrets are committed
- `spring.flyway.enabled=false`, `spring.main.web-application-type=none`, `spring.jpa.hibernate.ddl-auto=validate` and `spring.sql.init.mode=never` remain fixed in the acceptance profile

The profile never receives a `LANVERIQ_DB_MIGRATOR_PASSWORD` property. It makes no network endpoint or Agent/remote feature available. This configuration is **opt-in and is not part of ordinary boot or a production deployment**. Real role authentication and least privileges are tested separately by the Testcontainers suite; this profile is separately protected by offline static checks. An environment administrator must independently ensure the configured runtime role has only the approved grants.

## Evidence and unclosed production blockers

The GitHub real PostgreSQL integration workflows require all four `PostgreSqlRoleSeparationIT` cases through the fail-closed actual JUnit XML gate (10 integration classes). The Java L1 workflow verifies the profile's strict disabled Flyway/HTTP and explicit runtime-only environment variables. No user-managed local testing is required for these isolated checks.

Before any real deployment, complete: audited external role provisioning with distinct DB owner and *separate one-shot migration job*, password/key rotation and secret-store custody, a reviewed **per-table + per-operation** grant manifest for every live Spring/JPA flow (including logging), validated Flyway upgrade compatibility under migrator-only DDL, explicit absence of role membership/SET ROLE, non-bypassable runtime access to security functions, backup/restore permissions, trusted external append-only audit and operational monitoring. Keep migration credentials out of the long-running Spring JVM entirely. Never grant `ALL ON ALL TABLES`, schema ownership or superuser privileges to runtime as a shortcut.

This does **not** improve the still-unapproved official FIDO R46 MDS source, authentic CRL/time custody, global anti-rollback namespace, admin WebAuthn MFA or Pop!_OS24.04 physical hardware L3.

References: [PostgreSQL 18 GRANT](https://www.postgresql.org/docs/18/sql-grant.html), [PostgreSQL 18 schemas and CREATE privilege](https://www.postgresql.org/docs/18/ddl-schemas.html), [PostgreSQL 18 role attributes](https://www.postgresql.org/docs/18/role-attributes.html), [Spring Boot Flyway external DataSource](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
