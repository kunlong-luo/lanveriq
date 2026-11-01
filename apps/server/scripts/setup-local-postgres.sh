#!/usr/bin/env bash
# Manual-only Lanveriq developer database provisioning for the existing local
# spark-postgres container. Never invoke from bootRun, Gradle, CI or deployment.
set -euo pipefail

CONTAINER='spark-postgres'
ADMIN='root'
SQL_FILE="$(cd "$(dirname "$0")" && pwd)/provision-lanveriq-database.sql"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
query() {
    docker exec -i "$CONTAINER" psql -X -w -A -t -v ON_ERROR_STOP=1 \
        -U "$ADMIN" -d postgres -c "$1"
}
confirm() {
    local choice
    printf '%s\n' "$1"
    read -r -p "Type '$2' to proceed: " choice
    [[ "$choice" == "$2" ]] || fail 'Cancelled; no further database changes.'
}

[[ -t 0 && -t 1 ]] || fail 'Run in a local interactive Linux terminal (not a SQL editor or CI).'
command -v docker >/dev/null || fail 'Docker CLI is not installed.'
[[ -f "$SQL_FILE" ]] || fail 'Cannot locate the reviewed SQL next to this script.'
state="$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" ||
    fail 'Expected existing spark-postgres container; never start or replace the shared database automatically.'
[[ "$state" == 'true' ]] || fail 'spark-postgres is not running; nothing was changed.'

printf 'Existing container: %s; maintenance database: postgres; admin role: %s\n' "$CONTAINER" "$ADMIN"
[[ "$(query "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")" == 't' ]] ||
    fail 'The connected administrator must have explicit cluster superuser privileges.'
version="$(query 'SHOW server_version_num')"
[[ "$version" =~ ^[0-9]+$ ]] && (( version >= 180000 && version < 190000 )) ||
    fail "Expected PostgreSQL 18; got version number: $version"
[[ "$(query 'SHOW password_encryption')" == 'scram-sha-256' ]] ||
    fail 'PostgreSQL password_encryption is not scram-sha-256; correct the server configuration first.'

role="$(query "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lanveriq')")"
db="$(query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'lanveriq')")"
printf 'Existing lanveriq role: %s; existing lanveriq database: %s\n' "$role" "$db"

if [[ "$role" == 'f' && "$db" == 'f' ]]; then
    confirm 'The reviewed SQL creates ONLY the lanveriq role and database on the existing server; iot_farm is untouched.' 'CREATE lanveriq'
    # psql reads individual statements from stdin with autocommit. In particular,
    # CREATE DATABASE is not wrapped in a transaction (unlike DBeaver script mode).
    docker exec -i "$CONTAINER" psql -X -w -v ON_ERROR_STOP=1 \
        -U "$ADMIN" -d postgres < "$SQL_FILE"
elif [[ "$role" == 'f' && "$db" == 't' ]]; then
    fail 'Database lanveriq exists without the role; stop and inspect manually. No changes made.'
else
    safe_role="$(query "SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls FROM pg_roles WHERE rolname = 'lanveriq'")"
    [[ "$safe_role" == 't' ]] || fail 'Existing lanveriq role has unexpected cluster privileges; manual review required.'
    if [[ "$db" == 'f' ]]; then
        login="$(query "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'lanveriq'")"
        [[ "$login" == 'f' ]] || fail 'Existing lanveriq role can already log in, but the database is absent; manual review required.'
        confirm 'A restricted NOLOGIN role exists, but the lanveriq database does not. Complete only the missing database creation?' 'RECOVER lanveriq'
        docker exec -i "$CONTAINER" psql -X -w -v ON_ERROR_STOP=1 \
            -U "$ADMIN" -d postgres -c 'CREATE DATABASE lanveriq OWNER lanveriq;'
        docker exec -i "$CONTAINER" psql -X -w -v ON_ERROR_STOP=1 \
            -U "$ADMIN" -d postgres -c 'REVOKE ALL ON DATABASE lanveriq FROM PUBLIC;'
    fi
fi

owner="$(query "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'lanveriq'")"
[[ "$owner" == 'lanveriq' ]] || fail 'The existing database is not owned by lanveriq; do not modify it.'
public_closed="$(query "SELECT datacl IS NOT NULL AND NOT EXISTS (SELECT 1 FROM aclexplode(datacl) WHERE grantee = 0) FROM pg_database WHERE datname = 'lanveriq'")"
[[ "$public_closed" == 't' ]] || fail 'The database still grants privileges to PUBLIC; an administrator must review and revoke these before LOGIN.'
login="$(query "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'lanveriq'")"
if [[ "$login" == 't' ]]; then
    printf 'lanveriq already exists and can log in. No password was changed.\n'
    printf 'Use the existing password with LANVERIQ_DB_PASSWORD in your Java backend configuration.\n'
    exit 0
fi

confirm 'The dedicated database is ready. The role is still NOLOGIN. Set its password interactively?' 'PASSWORD lanveriq'
printf 'Enter a NEW strong database password twice at the hidden psql prompts.\n'
# PostgreSQL psql -c accepts one backslash meta-command. Password input stays
# inside the interactive tty; it is never passed via CLI args, environment or Git.
docker exec -it "$CONTAINER" psql -X -v ON_ERROR_STOP=1 \
    -U "$ADMIN" -d postgres -c '\password lanveriq'

hash_ok="$(query "SELECT COALESCE(rolpassword LIKE 'SCRAM-SHA-256$%', FALSE) FROM pg_authid WHERE rolname = 'lanveriq'")"
[[ "$hash_ok" == 't' ]] || fail 'SCRAM password was not stored; role remains NOLOGIN. Check the password setup before retrying.'
docker exec -i "$CONTAINER" psql -X -w -v ON_ERROR_STOP=1 \
    -U "$ADMIN" -d postgres -c 'ALTER ROLE lanveriq LOGIN;'

printf '\nDatabase role and database created/configured. Verify it with a password-authenticated TCP connection:\n'
docker exec -it "$CONTAINER" psql -X -W -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 -U lanveriq -d lanveriq \
    -c 'SELECT current_user, current_database();'
printf '\nSuccess. Configure LANVERIQ_DB_PASSWORD in the IDE or trusted terminal; never commit it.\n'