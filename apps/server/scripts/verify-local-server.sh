#!/usr/bin/env bash
# Manual, read-only localhost Spring Boot smoke probe.
# Does NOT query PostgreSQL directly or modify any data/services.
# Default port matches application.yml; pass 18081 when configured explicitly.
set -euo pipefail

die() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

port="${1:-8080}"
[[ "$#" -le 1 && "$port" =~ ^[0-9]+$ ]] ||
  die 'Usage: bash apps/server/scripts/verify-local-server.sh [8080|18081|other-local-port]'
(( 10#$port >= 1024 && 10#$port <= 65535 )) ||
  die 'Port must be an unprivileged TCP port in range 1024..65535.'
command -v curl >/dev/null || die 'curl is required.'
command -v python3 >/dev/null || die 'python3 is required.'

tmp="$(mktemp -d)" || die 'Failed to create a private temporary directory.'
chmod 700 "$tmp"
trap 'rm -rf "$tmp"' EXIT

base="http://127.0.0.1:${port}"
echo "Checking local-only Spring Boot at ${base} (no writes)..."
health_code="$(curl --noproxy '*' --silent --show-error --max-time 5 \
  --output "$tmp/health.json" --write-out '%{http_code}' \
  "${base}/actuator/health")" ||
  die "Cannot connect to ${base}; keep the IDEA Java process running and verify its actual port."
[[ "$health_code" == '200' ]] ||
  die "Health endpoint returned HTTP ${health_code}, expected 200."

python3 - "$tmp/health.json" <<'PY' || die 'Health response was not a JSON object with status UP.'
import json
from pathlib import Path
import sys
try:
    payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    assert isinstance(payload, dict) and payload.get("status") == "UP"
except (AssertionError, ValueError, OSError, UnicodeError):
    sys.exit(1)
PY

# No redirects are followed. The default-deny business route must not leak
# device records even if a future controller or a default security rule changes.
device_code="$(curl --noproxy '*' --silent --show-error --max-time 5 \
  --output "$tmp/device-response" --write-out '%{http_code}' \
  "${base}/api/v1/devices")" ||
  die 'Could not query the default-denied device endpoint.'
[[ "$device_code" == '403' ]] ||
  die "Default-deny device endpoint returned HTTP ${device_code}, expected 403."

echo 'PASS: health HTTP 200/UP; unauthenticated device listing HTTP 403.'
echo 'Flyway/PostgreSQL acceptance still requires the independent manual PG18.6 Testcontainers suite.'
