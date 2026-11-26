#!/usr/bin/env bash
# CI-only cross-runner PG18 recovery. GitHub artifact transport is SAME-ADMIN.
# This never authorizes production restore, independent WORM or RPO/RTO.
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
if [[ $# -ne 1 || "$GITHUB_ACTIONS" != true || "$RUNNER_OS" != Linux ||
      "$LANVERIQ_SYNTHETIC_CROSS_RUNNER_RECOVERY" != 1 ||
      "$1" != "$RUNNER_TEMP/lanveriq-g08-received" ]]; then
    echo "FAIL: separate-runner restore requires disposable CI scope" >&2
    exit 1
fi
dir="$1"
[[ -d "$dir" && ! -L "$dir" ]] || {
    echo "FAIL: disposable handoff directory missing or linked" >&2
    exit 1
}
[[ "$(find "$dir" -mindepth 1 -maxdepth 1 -print | wc -l)" -eq 6 ]] || {
    echo "FAIL: CI handoff contains extra or missing files" >&2
    exit 1
}
for name in synthetic-pg18-recovery.dump synthetic-pg18-recovery.evidence.json \
            source-fingerprint.sha256 source-commit.txt source-run.txt source-host.txt; do
    file="$dir/$name"
    [[ -f "$file" && ! -L "$file" && "$(stat -c %h "$file")" == 1 ]] || {
        echo "FAIL: missing, linked or multiply-linked CI handoff file" >&2
        exit 1
    }
    chmod 600 "$file"
done
source_sha="$(cat "$dir/source-commit.txt")"
source_run="$(cat "$dir/source-run.txt")"
source_host="$(cat "$dir/source-host.txt")"
expected="$(cat "$dir/source-fingerprint.sha256")"
[[ "$source_sha" =~ ^[a-f0-9]{40}$ && "$source_sha" == "$GITHUB_SHA" &&
   "$source_run" =~ ^[0-9]+$ && "$source_run" == "$GITHUB_RUN_ID" &&
   -n "$source_host" && "$source_host" != "$(hostname)" &&
   "$expected" =~ ^[a-f0-9]{64}$ ]] || {
    echo "FAIL: source SHA/run/runner-host/fingerprint mismatch" >&2
    exit 1
}
python3 "$ROOT/apps/server/scripts/pg_backup_evidence.py" verify \
    "$dir/synthetic-pg18-recovery.dump" \
    "$dir/synthetic-pg18-recovery.evidence.json"

tmp="$(mktemp -d)"
cid=""
cleanup() {
    [[ -z "$cid" ]] || docker rm -f "$cid" >/dev/null 2>&1 || true
    rm -rf "$tmp"
}
trap cleanup EXIT
openssl rand -hex 32 > "$tmp/postgres-password"
chmod 600 "$tmp/postgres-password"
cid="$(docker run --detach --network none \
    --env POSTGRES_DB=lanveriq \
    --env POSTGRES_USER=lanveriq \
    --env POSTGRES_PASSWORD_FILE=/run/secrets/synthetic-password \
    --volume "$tmp/postgres-password:/run/secrets/synthetic-password:ro" \
    postgres:18.7)"
[[ -n "$cid" && "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$cid")" == none &&
   -z "$(docker port "$cid")" ]] || {
    echo "FAIL: receiver PostgreSQL network or published-port boundary violated" >&2
    exit 1
}
ready=0
for _ in $(seq 1 60); do
    if docker exec "$cid" pg_isready -U lanveriq -d lanveriq >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done
[[ "$ready" == 1 ]] || { echo "FAIL: receiver PG18 not ready" >&2; exit 1; }
docker exec -i "$cid" pg_restore -U lanveriq -d lanveriq \
    --exit-on-error --single-transaction --no-owner --no-acl \
    < "$dir/synthetic-pg18-recovery.dump" > "$tmp/restore.log" 2>&1 || {
    echo "FAIL: native PG18 receiver rejected backup archive" >&2
    exit 1
}
fingerprint() {
    docker exec -i "$cid" psql -U lanveriq -d lanveriq -XAt -v ON_ERROR_STOP=1 \
        < "$ROOT/apps/server/scripts/pg18-inventory-canonical-fingerprint.sql" \
        | sha256sum | awk '{print $1}'
}
[[ "$(fingerprint)" == "$expected" ]] || {
    echo "FAIL: cross-runner full-row inventory/Flyway content changed" >&2
    exit 1
}
[[ "$(docker exec "$cid" psql -U lanveriq -d lanveriq -XAtqc \
    'SELECT (SELECT count(*) FROM device_inventory_current) > 0 AND (SELECT count(*) FROM flyway_schema_history) > 0')" == t ]] || {
    echo "FAIL: receiver lacks actual inventory or Flyway history" >&2
    exit 1
}
docker restart --time 3 "$cid" >/dev/null
ready=0
for _ in $(seq 1 60); do
    if docker exec "$cid" pg_isready -U lanveriq -d lanveriq >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 1
done
[[ "$ready" == 1 && "$(fingerprint)" == "$expected" ]] || {
    echo "FAIL: receiver PG18 restart or content comparison failed" >&2
    exit 1
}
echo "PASS: PG18.7 restored and restarted on a DIFFERENT disposable GitHub runner; full inventory/Flyway content matches; no host ports."
echo "NOT PROVEN: physical host custody, separate admin/KMS/WORM, SRE signoff, production RPO/RTO or failover. G-02/G-08 OPEN."
