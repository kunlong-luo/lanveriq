#!/usr/bin/env bash
# MANUAL ONLY. Synthetic PKI and Caddy mTLS tests; no real Agent or user data.
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
CID=""
BACKEND_PID=""
cleanup() {
    if [[ -n "$CID" ]]; then docker rm -f "$CID" >/dev/null 2>&1 || true; fi
    if [[ -n "$BACKEND_PID" ]]; then
        kill "$BACKEND_PID" 2>/dev/null || true
        wait "$BACKEND_PID" 2>/dev/null || true
    fi
    rm -rf "$TMP"
}
trap cleanup EXIT
mkdir -p "$TMP/pki" "$TMP/data" "$TMP/config"
for cmd in openssl curl jq docker python3; do
    command -v "$cmd" >/dev/null || { echo "Missing tool: $cmd" >&2; exit 1; }
done
docker info >/dev/null
[[ "$(curl --version)" == *HTTP2* ]] || { echo "curl must support HTTP/2" >&2; exit 1; }
DEVICE_ID="00000000-0000-7000-8000-000000000001"
make_ca() {
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 \
        -nodes -keyout "$TMP/pki/$1.key" -out "$TMP/pki/$1.crt" \
        -days 2 -sha256 -subj "/CN=$2" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" >/dev/null 2>&1
}
make_leaf() {
    openssl req -new -newkey ec -pkeyopt ec_paramgen_curve:P-256 \
        -nodes -keyout "$TMP/pki/$1.key" -out "$TMP/pki/$1.csr" \
        -subj "/CN=synthetic-$1" >/dev/null 2>&1
    cat >"$TMP/pki/$1.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=$3
subjectAltName=$4
EOF
    openssl x509 -req -in "$TMP/pki/$1.csr" \
        -CA "$TMP/pki/$2.crt" -CAkey "$TMP/pki/$2.key" -CAcreateserial \
        -out "$TMP/pki/$1.crt" -days 1 -sha256 \
        -extfile "$TMP/pki/$1.ext" >/dev/null 2>&1
}
sha256_der() {
    openssl x509 -in "$TMP/pki/$1.crt" -outform DER | sha256sum | cut -d' ' -f1
}
make_ca approved-client-ca "Synthetic Agent Client CA"
make_ca backend-server-ca "Synthetic Backend Server CA"
make_ca upstream-proxy-ca "Synthetic Caddy Proxy CA"
make_ca untrusted-ca "Untrusted Synthetic CA"
# The Caddy reference intentionally uses .pem trust paths. Both aliases
# refer to generated PUBLIC CA certificates only; no private-key alias.
cp "$TMP/pki/approved-client-ca.crt" "$TMP/pki/approved-client-ca.pem"
cp "$TMP/pki/backend-server-ca.crt" "$TMP/pki/backend-server-ca.pem"
make_leaf synthetic-agent approved-client-ca clientAuth "URI:urn:uuid:$DEVICE_ID"
make_leaf untrusted-agent untrusted-ca clientAuth "URI:urn:uuid:$DEVICE_ID"
make_leaf caddy-upstream-client upstream-proxy-ca clientAuth \
    "DNS:caddy-proxy.lanveriq.internal"
make_leaf untrusted-proxy untrusted-ca clientAuth \
    "DNS:untrusted-proxy.lanveriq.internal"
make_leaf backend-server backend-server-ca serverAuth \
    "DNS:spring-backend.lanveriq.internal"

export SYNTHETIC_AGENT_SHA256="$(sha256_der synthetic-agent)"
export SYNTHETIC_PROXY_SHA256="$(sha256_der caddy-upstream-client)"
export SYNTHETIC_DEVICE_ID="$DEVICE_ID"
export SYNTHETIC_BACKEND_CERT="$TMP/pki/backend-server.crt"
export SYNTHETIC_BACKEND_KEY="$TMP/pki/backend-server.key"
export SYNTHETIC_PROXY_CA="$TMP/pki/upstream-proxy-ca.crt"
python3 "$ROOT/deploy/caddy/tests/synthetic-mtls-backend.py" >"$TMP/backend.log" 2>&1 &
BACKEND_PID=$!
for i in $(seq 1 30); do
    if (echo > /dev/tcp/127.0.0.1/8443) 2>/dev/null; then break; fi
    kill -0 "$BACKEND_PID" || { echo "Backend failed to start" >&2; exit 1; }
    sleep 1
done
IMAGE="caddy:2.11.7"
docker pull "$IMAGE" >/dev/null
docker image inspect "$IMAGE" --format \
    'Synthetic Caddy image ID {{.Id}}; digests {{json .RepoDigests}}'
# Docker runner is isolated; test CA files/keys are never written into repo.
CADDYFILE="$ROOT/deploy/caddy/Caddyfile.reference"
docker run --rm -v "$CADDYFILE:/etc/caddy/Caddyfile:ro" \
    -v "$TMP/pki:/etc/lanveriq/pki:ro" -v "$TMP/data:/data" \
    -v "$TMP/config:/config" "$IMAGE" \
    adapt --config /etc/caddy/Caddyfile --pretty >"$TMP/adapted.json"
jq -e '.apps.http.servers | type == "object"' "$TMP/adapted.json" >/dev/null
jq -e '[.apps.http.servers[] | select(.strict_sni_host == true and
    ((.protocols // []) | index("h1") != null) and
    ((.protocols // []) | index("h2") != null) and
    ((.protocols // []) | index("h3") == null))] | length > 0' \
    "$TMP/adapted.json" >/dev/null
jq -e '[.. | objects | select(.client_authentication?.mode? == "require_and_verify")]
    | length > 0' "$TMP/adapted.json" >/dev/null
docker run --rm -v "$CADDYFILE:/etc/caddy/Caddyfile:ro" \
    -v "$TMP/pki:/etc/lanveriq/pki:ro" -v "$TMP/data:/data" \
    -v "$TMP/config:/config" "$IMAGE" validate --config /etc/caddy/Caddyfile
echo "PASS: real Caddy 2.11.7 adapt and validate (synthetic trust files)."

CID="$(docker run -d --network host \
    -v "$CADDYFILE:/etc/caddy/Caddyfile:ro" \
    -v "$TMP/pki:/etc/lanveriq/pki:ro" -v "$TMP/data:/data" \
    -v "$TMP/config:/config" "$IMAGE" run --config /etc/caddy/Caddyfile)"
CA_ROOT="$TMP/data/caddy/pki/authorities/local/root.crt"
for i in $(seq 1 45); do
    if [[ -s "$CA_ROOT" ]]; then break; fi
    docker inspect -f '{{.State.Running}}' "$CID" | grep -q true || {
        docker logs "$CID" >&2; exit 1;
    }
    sleep 1
done
[[ -s "$CA_ROOT" ]] || { echo "No Caddy internal server CA" >&2; exit 1; }
URL="https://agent.lanveriq.internal/api/v1/devices/$DEVICE_ID/activate"
# All curl invocations verify real synthetic server certificates; NEVER -k.
AGENT_CRT="$TMP/pki/synthetic-agent.crt"
AGENT_KEY="$TMP/pki/synthetic-agent.key"
FP_FORGED="$(printf '%064d' 0)"
status="$(curl -sS --noproxy '*' --max-time 15 --retry 5 --retry-connrefused --retry-delay 1 --http2 \
    --cacert "$CA_ROOT" --resolve agent.lanveriq.internal:443:127.0.0.1 \
    --cert "$AGENT_CRT" --key "$AGENT_KEY" \
    -H "X-Lanveriq-Verified-Client-Fingerprint: $FP_FORGED" \
    -H "X-Forwarded-Client-Cert: forged" \
    -H "SSL-Client-Cert: forged" \
    -X POST -o "$TMP/response.json" -w '%{http_code}' "$URL")"
[[ "$status" == 200 ]] || { echo "Valid synthetic Agent route failed: $status" >&2; exit 1; }
python3 - "$TMP/response.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as src:
    assert json.load(src) == {"synthetic_upstream_verified": True}
PY
echo "PASS: Agent TLS, Caddy->backend TLS and verified fingerprint overwrite."

deny_agent() {
    local label="$1"
    shift
    local code
    code="$(curl -sS --noproxy '*' --max-time 10 --http2 \
        --cacert "$CA_ROOT" --resolve agent.lanveriq.internal:443:127.0.0.1 \
        "$@" -X POST -o "$TMP/denied.json" -w '%{http_code}' "$URL" 2>/dev/null || true)"
    [[ "$code" != 200 ]] || { echo "FAIL: $label reached backend" >&2; exit 1; }
    echo "PASS: $label rejected ($code)."
}
deny_agent "Agent missing client certificate"
deny_agent "Agent signed by wrong CA" \
    --cert "$TMP/pki/untrusted-agent.crt" --key "$TMP/pki/untrusted-agent.key"
mismatch="$(curl -sS --noproxy '*' --max-time 10 --http2 \
    --cacert "$CA_ROOT" --resolve agent.lanveriq.internal:443:127.0.0.1 \
    --cert "$AGENT_CRT" --key "$AGENT_KEY" \
    -H "Host: console.lanveriq.internal" -X POST \
    -o "$TMP/mismatch.json" -w '%{http_code}' "$URL")"
[[ "$mismatch" == 421 ]] || { echo "FAIL: expected Host/SNI 421, got $mismatch" >&2; exit 1; }
echo "PASS: cross-host HTTP/2 SNI/Host rejected."

# The two unprotected vhosts must NOT proxy Agent operations; neither asks
# for an Agent client certificate, and both intentionally return 503.
for host in console.lanveriq.internal enroll.lanveriq.internal; do
    status="$(curl -sS --noproxy '*' --max-time 10 --http2         --cacert "$CA_ROOT" --resolve "$host:443:127.0.0.1"         -X POST -o "$TMP/denied-site.json" -w '%{http_code}'         "https://$host/api/v1/devices/$DEVICE_ID/activate")"
    [[ "$status" == 503 ]] || {
        echo "FAIL: $host should not proxy an Agent operation: $status" >&2; exit 1;
    }
    echo "PASS: $host does not proxy Agent operations."
done
# Unknown Agent paths must fail at the Caddy edge, even with valid mTLS.
status="$(curl -sS --noproxy '*' --max-time 10 --http2     --cacert "$CA_ROOT" --resolve agent.lanveriq.internal:443:127.0.0.1     --cert "$AGENT_CRT" --key "$AGENT_KEY" -X POST     -o "$TMP/unknown.json" -w '%{http_code}'     "https://agent.lanveriq.internal/api/v1/unknown")"
[[ "$status" == 404 ]] || {
    echo "FAIL: unknown Agent route should be rejected at edge: $status" >&2; exit 1;
}
echo "PASS: unregistered Agent routes are denied at edge."

BACKEND_URL="https://spring-backend.lanveriq.internal:8443/api/v1/devices/$DEVICE_ID/activate"
deny_direct() {
    local label="$1"
    shift
    if curl -sS --noproxy '*' --max-time 10 \
       --cacert "$TMP/pki/backend-server-ca.crt" \
       --resolve spring-backend.lanveriq.internal:8443:127.0.0.1 \
       -H "Host: agent.lanveriq.internal" \
       -H "X-Lanveriq-Verified-Client-Fingerprint: $SYNTHETIC_AGENT_SHA256" \
       "$@" -X POST -o "$TMP/direct.json" "$BACKEND_URL" >/dev/null 2>&1; then
        echo "FAIL: direct backend accepted $label" >&2
        exit 1
    fi
    echo "PASS: direct backend rejects $label at TLS layer."
}
deny_direct "no proxy client certificate"
deny_direct "wrong-CA proxy client certificate" \
    --cert "$TMP/pki/untrusted-proxy.crt" \
    --key "$TMP/pki/untrusted-proxy.key"
echo "PASS: synthetic double-mTLS gate only."
echo "NOT PROVEN: real Spring Tomcat, PostgreSQL 18, revocation, production OS."
