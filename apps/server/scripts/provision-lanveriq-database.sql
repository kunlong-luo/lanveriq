-- Lanveriq ONLY: one-time PostgreSQL cluster bootstrap (NOT a Flyway migration).
-- This script is deliberately NOT called from bootRun, CI, Docker startup,
-- or any application installation process.
--
-- PURE POSTGRESQL SQL: may be opened in IntelliJ IDEA / DataGrip / pgAdmin
-- as well as run through psql. No psql backslash commands are executable here.
-- For graphical SQL clients: connect as an authorized PostgreSQL administrator
-- to database postgres, enable AUTO-COMMIT and STOP ON ERROR, then execute the
-- statements in their listed order. Never choose Run in a single transaction:
-- PostgreSQL forbids CREATE DATABASE inside a transaction block.
-- DBeaver: SQL Editor preferences > SQL Processing > Scripts:
--   Commit type = After each query (autocommit), NOT At script end.
-- Set Error handling to STOP on first error. This script must execute in order
-- (Alt+X), NEVER in parallel, and without any BEGIN / single-transaction wrapper.
-- DBeaver's toolbar Auto-commit alone may not override SCRIPT commit type.
-- Before any retry, check pg_roles and pg_database for partial initialization.
-- This creates NOLOGIN only; after inspection, set a strong SCRAM password
-- using your client's protected role/password dialog, or psql's interactive
-- backslash-password command, and only THEN enable LOGIN.
--
-- Explicit manual invocation, after inspecting the target container:
--   docker exec -i spark-postgres psql -X -v ON_ERROR_STOP=1 \
--     -U root -d postgres < apps/server/scripts/provision-lanveriq-database.sql
--
-- The role starts NOLOGIN so failed/interrupted provisioning cannot accidentally
-- expose a usable credentialless application login. Set its password separately
-- using the interactive psql \password command, then enable LOGIN.
-- Do NOT pass -1 / --single-transaction: CREATE DATABASE must run outside a transaction.
-- Never point this script at iot_farm, or run it on a cluster without approval.
--
-- Intentional fail-closed behavior: an existing role OR database causes an error.
-- Investigate an interrupted/previous installation; never auto-reown/alter/drop.
-- Do not store actual credentials in this file or Git.
DO $lanveriq_preflight$
BEGIN
    IF current_database() <> 'postgres' THEN
        RAISE EXCEPTION
            'Refusing provisioning outside maintenance database postgres';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = current_user AND rolsuper
    ) THEN
        RAISE EXCEPTION 'Provisioning requires an explicitly authorized cluster administrator';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lanveriq') THEN
        RAISE EXCEPTION
            'Role lanveriq already exists; inspect ownership and privileges manually';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'lanveriq') THEN
        RAISE EXCEPTION
            'Database lanveriq already exists; inspect it before any changes';
    END IF;
END
$lanveriq_preflight$;

CREATE ROLE lanveriq
    NOLOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;

CREATE DATABASE lanveriq OWNER lanveriq;

-- By default PostgreSQL grants some database privileges to PUBLIC.
-- Scope connection, temporary-object and create privileges to the owner.
REVOKE ALL ON DATABASE lanveriq FROM PUBLIC;

-- After successful provisioning, set the password interactively using
-- a trusted PostgreSQL GUI credential dialog or interactive psql.
-- Do not paste a real password into this SQL file.
-- For psql only (typed in its prompt, NOT in a JDBC SQL editor):
--   \password lanveriq
--   ALTER ROLE lanveriq LOGIN;
-- Then verify a password-authenticated TCP connection as the dedicated role.
-- Flyway creates/updates ONLY the schema in jdbc:postgresql://127.0.0.1:5432/lanveriq.
