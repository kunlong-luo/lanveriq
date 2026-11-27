#!/usr/bin/env bash
# Manual-only provisioning of the separate local enrollment test database.
# Never run from application startup, CI, or deployment.
set -euo pipefail
umask 077
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ $# == 0 && -t 0 && -t 1 && ! -v CI && ! -v GITHUB_ACTIONS ]] ||
    fail 'Run without arguments from an interactive local terminal, never CI.'
command -v docker >/dev/null || fail 'Docker is required.'
[[ "$(docker inspect -f '{{.State.Running}}' spark-postgres 2>/dev/null)" == true ]] ||
    fail 'Expected existing running spark-postgres. No service will be started.'
[[ "$(docker port spark-postgres 5432/tcp)" == '127.0.0.1:5432' ]] ||
    fail 'Expected PostgreSQL published only on IPv4 loopback port 5432.'
query() {
    docker exec -i spark-postgres psql -X -w -At -v ON_ERROR_STOP=1 \
        -U root -d postgres -c "$1"
}
[[ "$(query "SELECT current_database()='postgres' AND EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolsuper)
    AND current_setting('server_version_num')::integer BETWEEN 180000 AND 189999
    AND current_setting('password_encryption')='scram-sha-256'")" == t ]] ||
    fail 'Expected authorized PostgreSQL 18 administrator with SCRAM.'
[[ "$(query "SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='lanveriq_local_test')
    AND NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='lanveriq_local_test')")" == t ]] ||
    fail 'Test role or database already exists. Inspect manually; no reset, drop or recovery performed.'
printf 'Creates ONLY lanveriq_local_test role and database. Existing lanveriq and IoT databases stay unchanged.\n'
read -rp 'Type CREATE lanveriq_local_test to continue: ' choice
[[ "$choice" == 'CREATE lanveriq_local_test' ]] || fail 'Cancelled.'

# Separate autocommit statements: CREATE DATABASE cannot run in a transaction.
# Use fixed identifiers, no caller-supplied SQL. An interrupted run leaves
# NOLOGIN and must be reviewed before any retry.
query 'CREATE ROLE lanveriq_local_test NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;'
query 'CREATE DATABASE lanveriq_local_test OWNER lanveriq_local_test;'
query 'REVOKE ALL ON DATABASE lanveriq_local_test FROM PUBLIC;'
[[ "$(query "SELECT pg_get_userbyid(datdba)='lanveriq_local_test'
    AND datacl IS NOT NULL AND NOT EXISTS (SELECT 1 FROM aclexplode(datacl) WHERE grantee=0)
    FROM pg_database WHERE datname='lanveriq_local_test'")" == t ]] ||
    fail 'Database ownership or PUBLIC ACL mismatch; role remains NOLOGIN.'
printf 'Set a NEW dedicated test database password at the hidden psql prompts.\n'
docker exec -it spark-postgres psql -X -w -v ON_ERROR_STOP=1 \
    -U root -d postgres -c '\password lanveriq_local_test'
[[ "$(query "SELECT COALESCE(rolpassword LIKE 'SCRAM-SHA-256$%',false)
    AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
    AND NOT rolreplication AND NOT rolbypassrls
    FROM pg_authid WHERE rolname='lanveriq_local_test'")" == t ]] ||
    fail 'Password or role policy invalid; role remains NOLOGIN.'
query 'ALTER ROLE lanveriq_local_test LOGIN;'
docker exec -it spark-postgres psql -X -W -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 -U lanveriq_local_test -d lanveriq_local_test \
    -c "SELECT current_database()='lanveriq_local_test' AND current_user='lanveriq_local_test' AS dedicated_test_database;"
printf 'Dedicated test database provisioning completed. Flyway and administrator bootstrap are separate.\n'
