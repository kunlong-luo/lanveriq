#!/usr/bin/env bash
# G-08: explicit-operator, non-production physical-host restore drill.
# NOT a production installer, failover procedure, SRE signoff or independent custody proof.
# Never automatically invoked by Agent/Spring, never by GitHub CI.
set -Eeuo pipefail
umask 077

usage() {
    echo "Usage: LANVERIQ_G08_SACRIFICIAL_LAB_CONSENT=YES bash $0 --lab-only ARCHIVE EXPECTED_ARCHIVE_SHA256 EXPECTED_DB_ROWS_SHA256 SOURCE_HOST_LABEL" >&2
}

# Refuse before touching Docker, archive or host when authorization is missing.
if [[ $# -ne 5 || "$1" != "--lab-only" ||
      "${LANVERIQ_G08_SACRIFICIAL_LAB_CONSENT:-}" != YES ||
      "${GITHUB_ACTIONS:-}" == true || "${CI:-}" == true ||
      "$EUID" -eq 0 ]]; then
    usage
    echo "BLOCKED: explicit non-root operator consent and standalone lab required; G-08 OPEN." >&2
    exit 2
fi

archive="$2"
expected_archive="$3"
expected_rows="$4"
source_host="$5"
root="$(cd "$(dirname "$0")/../../.." && pwd)"
target_host="$(hostname)"
if [[ ! "$expected_archive" =~ ^[0-9a-f]{64}$ ||
      ! "$expected_rows" =~ ^[0-9a-f]{64}$ ||
      ! "$source_host" =~ ^[a-zA-Z0-9._-]{3,128}$ ||
      "$source_host" == "$target_host" ||
      ! -f "$archive" || -L "$archive" ||
      "$(stat -c %u "$archive" 2>/dev/null || echo -1)" != "$EUID" ||
      "$(stat -c %h "$archive" 2>/dev/null || echo 0)" != 1 ||
      -n "$(find "$archive" -maxdepth 0 -perm /077 -print)" ||
      "$(head -c 5 "$archive")" != PGDMP ]]; then
    echo "BLOCKED: private custom archive, distinct claimed source and pinned hashes required; G-08 OPEN." >&2
    exit 2
fi

# The archive and source-row checksums MUST come from separately approved
# outside channels. This script checks equality, not who authored the values.
actual_archive="$(sha256sum -- "$archive" | awk '{print $1}')"
if [[ "$actual_archive" != "$expected_archive" ]]; then
    echo "BLOCKED: archive differs from externally supplied digest; G-08 OPEN." >&2
    exit 2
fi
if ! command -v docker >/dev/null 2>&1 ||
   ! docker info >/dev/null 2>&1 ||
   ! docker image inspect postgres:18.7 >/dev/null 2>&1; then
    echo "BLOCKED: preapproved local PostgreSQL 18.7 image and Docker are required (no implicit pull); G-08 OPEN." >&2
    exit 2
fi

tmp="$(mktemp -d)"
cid=""
cleanup() {
    if [[ -n "$cid" ]]; then
        docker rm -f "$cid" >/dev/null 2>&1 || true
    fi
    rm -rf -- "$tmp"
}
trap cleanup EXIT
openssl rand -hex 32 > "$tmp/postgres-password"
chmod 600 "$tmp/postgres-password"
cid="$(docker run --detach --network none \
    --label lanveriq.g08=authorized-synthetic-lab \
    --env POSTGRES_USER=lanveriq \
    --env POSTGRES_DB=lanveriq \
    --env POSTGRES_PASSWORD_FILE=/run/secrets/pg-password \
    --volume "$tmp/postgres-password:/run/secrets/pg-password:ro" \
    postgres:18.7)"
if [[ -z "$cid" ||
      "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$cid")" != none ||
      -n "$(docker port "$cid")" ]]; then
    echo "BLOCKED: recovered database must have no network and no published port." >&2
    exit 2
fi

await_pg18() {
    local ready=0
    for _ in $(seq 1 60); do
        if docker exec "$cid" pg_isready -U lanveriq -d lanveriq >/dev/null 2>&1; then
            ready=1
            break
        fi
        sleep 2
    done
    [[ "$ready" == 1 ]]
}
if ! await_pg18; then
    echo "FAIL: restored PG18 did not become ready; G-08 OPEN." >&2
    exit 2
fi
started="$(date +%s)"
if ! docker exec -i "$cid" pg_restore -U lanveriq -d lanveriq \
    --exit-on-error --single-transaction --no-owner --no-acl \
    < "$archive" > "$tmp/restore.log" 2>&1; then
    echo "FAIL: native isolated PG18 restore rejected archive; G-08 OPEN." >&2
    exit 2
fi
fingerprint() {
    docker exec -i "$cid" psql -U lanveriq -d lanveriq -XAt -v ON_ERROR_STOP=1 \
        < "$root/apps/server/scripts/pg18-inventory-canonical-fingerprint.sql" \
        | sha256sum | awk '{print $1}'
}
if [[ "$(fingerprint)" != "$expected_rows" ]]; then
    echo "FAIL: source-pinned full inventory/Flyway fingerprint differs; G-08 OPEN." >&2
    exit 2
fi
if [[ "$(docker exec "$cid" psql -U lanveriq -d lanveriq -XAtqc \
    'SELECT (SELECT count(*) FROM device_inventory_current) > 0 AND (SELECT count(*) FROM flyway_schema_history) > 0')" != t ]]; then
    echo "FAIL: missing synthetic inventory or Flyway content; G-08 OPEN." >&2
    exit 2
fi
docker restart --time 3 "$cid" >/dev/null
if ! await_pg18 || [[ "$(fingerprint)" != "$expected_rows" ]]; then
    echo "FAIL: PostgreSQL restart/readback differs from external expected data; G-08 OPEN." >&2
    exit 2
fi
finished="$(date +%s)"
echo "PASS: operator-started LAB-ONLY PG18.7 native restore, full-row readback and restart with no published ports."
echo "LAB_RESTORE_ELAPSED_SECONDS=$((finished - started)) (NOT production RTO)"
echo "REVIEW_REQUIRED: source label=$source_host target label=$target_host; labels alone DO NOT prove distinct physical hosts."
echo "NOT RUN/NOT PROVEN: independent WORM/key/time/high-water, ACL adversarial tests, physical serial/site custody, production RPO/RTO, independent SRE/security/legal signoff."
echo "G-01–G-10 OPEN; v0.1 NOT RELEASE READY; no production restore or failover authorized."
