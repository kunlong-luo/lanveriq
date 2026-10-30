#!/usr/bin/env bash
# MANUAL LOCAL acceptance only: synthetic PostgreSQL 18.6 + real Flyway startup.
# Not invoked from CI; requires local Docker, curl, openssl and JDK 25.
set -euo pipefail

for executable in docker curl openssl; do
    command -v "$executable" >/dev/null || {
        echo "Missing required tool: $executable" >&2
        exit 1
    }
done

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
chmod 700 "$TMP"
DB_CONTAINER="lanveriq-postgres18-check-$$"
RUN_PID=""
cleanup() {
    if [[ -n "$RUN_PID" ]]; then
        kill "$RUN_PID" >/dev/null 2>&1 || true
        wait "$RUN_PID" >/dev/null 2>&1 || true
    fi
    docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
    rm -rf "$TMP"
}
trap cleanup EXIT

openssl rand -hex 32 > "$TMP/postgres_password"
chmod 600 "$TMP/postgres_password"

echo "Starting isolated synthetic PostgreSQL 18.6 (127.0.0.1:55432 only)..."
docker run --detach --rm \
    --name "$DB_CONTAINER" \
    --publish 127.0.0.1:55432:5432 \
    --env POSTGRES_USER=lanveriq \
    --env POSTGRES_DB=lanveriq \
    --env POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
    --volume "$TMP/postgres_password:/run/secrets/db-password:ro" \
    postgres:18.6 >/dev/null

READY=0
for _ in $(seq 1 60); do
    if docker exec "$DB_CONTAINER" pg_isready -U lanveriq -d lanveriq >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 2
done
if [[ "$READY" -ne 1 ]]; then
    echo "PostgreSQL 18.6 did not become ready" >&2
    exit 1
fi

PG_VERSION="$(docker exec "$DB_CONTAINER" psql -U lanveriq -d lanveriq -XAtqc "SHOW server_version")"
echo "Connected to PostgreSQL $PG_VERSION"
[[ "$PG_VERSION" == 18.6* ]] || { echo "Wrong PostgreSQL minor version" >&2; exit 1; }

echo "Building runnable archive with Java 25..."
(cd "$SERVER_DIR" && ./gradlew bootJar)

echo "Starting Spring Boot / Flyway against isolated PostgreSQL..."
(
    cd "$SERVER_DIR"
    export LANVERIQ_DB_URL="jdbc:postgresql://127.0.0.1:55432/lanveriq"
    export LANVERIQ_DB_USER="lanveriq"
    export LANVERIQ_DB_PASSWORD
    LANVERIQ_DB_PASSWORD="$(cat "$TMP/postgres_password")"
    export LANVERIQ_HTTP_BIND="127.0.0.1"
    export LANVERIQ_HTTP_PORT="18080"
    exec ./gradlew bootRun > "$TMP/boot.log" 2>&1
) &
RUN_PID=$!

HEALTH_READY=0
for _ in $(seq 1 90); do
    if curl --fail --silent --max-time 2 "http://127.0.0.1:18080/actuator/health" \
        | grep -q '"status":"UP"'; then
        HEALTH_READY=1
        break
    fi
    if ! kill -0 "$RUN_PID" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
if [[ "$HEALTH_READY" -ne 1 ]]; then
    echo "Spring Boot did not reach healthy startup; Flyway/JPA errors follow:" >&2
    tail -n 100 "$TMP/boot.log" >&2
    exit 1
fi

MIGRATIONS="$(docker exec "$DB_CONTAINER" psql -U lanveriq -d lanveriq -XAtqc \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version IN ('1', '2')")"
[[ "$MIGRATIONS" == "2" ]] || { echo "Flyway V1/V2 migration check failed" >&2; exit 1; }

UUID_CHECK="$(docker exec "$DB_CONTAINER" psql -U lanveriq -d lanveriq -XAtqc \
    "BEGIN; INSERT INTO device_groups (name) VALUES ('synthetic_pg18_check') RETURNING uuid_extract_version(id); ROLLBACK;")"
grep -qx '7' <<< "$UUID_CHECK" || {
    echo "PG18 DB-default uuidv7() check failed" >&2
    exit 1
}

USER_COUNT="$(docker exec "$DB_CONTAINER" psql -U lanveriq -d lanveriq -XAtqc \
    "SELECT COUNT(*) FROM users")"
[[ "$USER_COUNT" == "0" ]] || { echo "Unexpected default administrator exists" >&2; exit 1; }

DENIED_HTTP="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    "http://127.0.0.1:18080/api/v1/devices")"
[[ "$DENIED_HTTP" == "403" ]] || {
    echo "Protected business endpoint returned HTTP $DENIED_HTTP, expected 403" >&2
    exit 1
}

echo "MANUAL PASS: PG18.6 startup, Flyway V1/V2, native UUIDv7, no default admin, denied business route."
echo "Important: this does NOT validate mTLS, an enrollment transaction, CSRF or a real endpoint."
