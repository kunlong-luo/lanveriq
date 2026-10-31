#!/usr/bin/env bash
# MANUAL ONLY: actual Spring Boot/Tomcat upstream TLS with synthetic PKI + PG18.
# It does not start Caddy, expose Agent APIs or authorize any endpoint data.
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
DB_CID=""
APP_PID=""
cleanup() {
    if [[ -n "$APP_PID" ]]; then
        kill "$APP_PID" >/dev/null 2>&1 || true
        wait "$APP_PID" >/dev/null 2>&1 || true
    fi
    if [[ -n "$DB_CID" ]]; then
        docker rm -f "$DB_CID" >/dev/null 2>&1 || true
    fi
    rm -rf "$TMP"
}
trap cleanup EXIT
for cmd in java keytool openssl curl docker sha256sum; do
    command -v "$cmd" >/dev/null || {
        echo "Missing required tool: $cmd" >&2
        exit 1
    }
done
docker info >/dev/null
[[ "$(java -version 2>&1)" == *'version "25'* ]] || {
    echo "Java 25 is required" >&2
    exit 1
}
if (echo > /dev/tcp/127.0.0.1/8443) >/dev/null 2>&1; then
    echo "Port 8443 is occupied; refuse to probe an unrelated service" >&2
    exit 1
fi
if (echo > /dev/tcp/127.0.0.1/8080) >/dev/null 2>&1; then
    echo "Port 8080 is occupied; cannot prove absence of an alternate listener" >&2
    exit 1
fi
mkdir -p "$TMP/pki"
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
    cat > "$TMP/pki/$1.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=$3
subjectAltName=$4
EOF
    openssl x509 -req -in "$TMP/pki/$1.csr" \
        -CA "$TMP/pki/$2.crt" -CAkey "$TMP/pki/$2.key" \
        -CAcreateserial -out "$TMP/pki/$1.crt" -days 1 -sha256 \
        -extfile "$TMP/pki/$1.ext" >/dev/null 2>&1
}
make_ca backend-ca "Synthetic Spring Backend Server CA"
make_ca proxy-ca "Synthetic Caddy Upstream Client CA"
make_ca untrusted-ca "Synthetic Wrong Client CA"
make_leaf backend backend-ca serverAuth "DNS:spring-backend.lanveriq.internal"
make_leaf proxy proxy-ca clientAuth "DNS:caddy-proxy.lanveriq.internal"
make_leaf untrusted untrusted-ca clientAuth "DNS:wrong-proxy.lanveriq.internal"
openssl verify -purpose sslserver -CAfile "$TMP/pki/backend-ca.crt" \
    "$TMP/pki/backend.crt" >/dev/null
openssl verify -purpose sslclient -CAfile "$TMP/pki/proxy-ca.crt" \
    "$TMP/pki/proxy.crt" >/dev/null
TLS_PASS="$(openssl rand -hex 24)"
DB_PASS="$(openssl rand -hex 24)"
export LANVERIQ_CADDY_UPSTREAM_CLIENT_DER_SHA256
LANVERIQ_CADDY_UPSTREAM_CLIENT_DER_SHA256="$(
    openssl x509 -in "$TMP/pki/proxy.crt" -outform DER | sha256sum | cut -d ' ' -f1
)"
openssl pkcs12 -export -inkey "$TMP/pki/backend.key" \
    -in "$TMP/pki/backend.crt" -certfile "$TMP/pki/backend-ca.crt" \
    -name synthetic-backend -out "$TMP/backend.p12" \
    -passout "pass:$TLS_PASS" >/dev/null 2>&1
keytool -importcert -noprompt -alias synthetic-caddy-client-ca \
    -file "$TMP/pki/proxy-ca.crt" -keystore "$TMP/proxy-trust.p12" \
    -storetype PKCS12 -storepass "$TLS_PASS" >/dev/null 2>&1
export LANVERIQ_BACKEND_TLS_KEYSTORE="file:$TMP/backend.p12"
export LANVERIQ_BACKEND_TLS_KEYSTORE_PASSWORD="$TLS_PASS"
export LANVERIQ_BACKEND_TLS_CADDY_CLIENT_TRUSTSTORE="file:$TMP/proxy-trust.p12"
export LANVERIQ_BACKEND_TLS_CADDY_CLIENT_TRUSTSTORE_PASSWORD="$TLS_PASS"
printf '%s' "$DB_PASS" > "$TMP/postgres-password"
chmod 600 "$TMP/postgres-password"

# A random Docker-chosen host port prevents accidental interference with LAN DBs.
DB_CID="$(docker run --detach --rm \
    --publish 127.0.0.1::5432 \
    --env POSTGRES_DB=lanveriq \
    --env POSTGRES_USER=lanveriq \
    --env POSTGRES_PASSWORD_FILE=/run/secrets/synthetic-password \
    --volume "$TMP/postgres-password:/run/secrets/synthetic-password:ro" \
    postgres:18.6)"
PG_PORT="$(docker port "$DB_CID" 5432/tcp | awk -F: 'NR==1 {print $NF}')"
[[ "$PG_PORT" =~ ^[0-9]+$ ]] || {
    echo "Cannot determine isolated PostgreSQL port" >&2
    exit 1
}
ready=0
for _ in $(seq 1 60); do
    if docker exec "$DB_CID" pg_isready -U lanveriq -d lanveriq >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 2
done
[[ "$ready" == 1 ]] || {
    echo "Synthetic PostgreSQL 18 did not start" >&2
    exit 1
}
PG_VERSION="$(docker exec "$DB_CID" psql -U lanveriq -d lanveriq -XAtqc 'SHOW server_version')"
[[ "$PG_VERSION" == 18.6* ]] || {
    echo "Unexpected PostgreSQL version" >&2
    exit 1
}
docker image inspect postgres:18.6 --format \
    'Synthetic PostgreSQL image: {{.Id}}; digests {{json .RepoDigests}}'
(
    cd "$ROOT/apps/server"
    echo '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98  gradle/wrapper/gradle-wrapper.jar' \
        | sha256sum --check
    ./gradlew bootJar
)
export LANVERIQ_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/lanveriq"
export LANVERIQ_DB_USER="lanveriq"
export LANVERIQ_DB_PASSWORD="$DB_PASS"
# Use the real Tomcat connector. No proxy, fake servlet attributes, HTTP endpoint
# for identity assertions or extra public listener is created.
java -jar "$ROOT/apps/server/build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar" \
    --spring.profiles.active=agent-upstream-mtls-acceptance \
    --lanveriq.console.auth.enabled=false \
    >"$TMP/spring.log" 2>&1 &
APP_PID=$!

URL="https://spring-backend.lanveriq.internal:8443"
HOST_RESOLVE="spring-backend.lanveriq.internal:8443:127.0.0.1"
HEALTH_READY=0
for _ in $(seq 1 90); do
    if curl --silent --fail --noproxy '*' --max-time 3 \
        --cacert "$TMP/pki/backend-ca.crt" --resolve "$HOST_RESOLVE" \
        --cert "$TMP/pki/proxy.crt" --key "$TMP/pki/proxy.key" \
        "$URL/actuator/health" | grep -q '"status":"UP"'; then
        HEALTH_READY=1
        break
    fi
    if ! kill -0 "$APP_PID" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
if [[ "$HEALTH_READY" != 1 ]]; then
    echo "Actual Spring/Tomcat mTLS health check failed" >&2
    tail -n 80 "$TMP/spring.log" >&2
    exit 1
fi
echo "PASS: actual Spring/Tomcat TLSv1.3 accepts dedicated synthetic proxy leaf."

# A correct response requires the real client cert; never use --insecure.
tls_denied() {
    local label="$1"
    shift
    local code="none"
    local exit_code=0
    code="$(curl --silent --show-error --noproxy '*' --max-time 8 \
        --cacert "$TMP/pki/backend-ca.crt" --resolve "$HOST_RESOLVE" \
        "$@" -o "$TMP/denied-body" -w '%{http_code}' \
        "$URL/actuator/health" 2>"$TMP/denied-stderr")" || exit_code=$?
    if [[ "$exit_code" == 0 || "$code" != 000 ]]; then
        echo "FAIL: $label was not rejected during TLS handshake (HTTP $code)" >&2
        exit 1
    fi
    echo "PASS: $label failed TLS handshake."
}
tls_denied "missing upstream client certificate"
tls_denied "untrusted upstream client certificate" \
    --cert "$TMP/pki/untrusted.crt" --key "$TMP/pki/untrusted.key"
tls_denied "forged identity header without upstream certificate" \
    -H "X-Lanveriq-Verified-Client-Fingerprint: $LANVERIQ_CADDY_UPSTREAM_CLIENT_DER_SHA256"

# Wrong server DNS must fail verification even with a valid client credential.
wrong_code=0
curl --silent --show-error --noproxy '*' --max-time 8 \
    --cacert "$TMP/pki/backend-ca.crt" \
    --resolve "wrong-backend.lanveriq.internal:8443:127.0.0.1" \
    --cert "$TMP/pki/proxy.crt" --key "$TMP/pki/proxy.key" \
    "https://wrong-backend.lanveriq.internal:8443/actuator/health" \
    -o "$TMP/wrong-host" 2>"$TMP/wrong-host-error" || wrong_code=$?
[[ "$wrong_code" == 60 ]] || {
    echo "FAIL: wrong backend DNS name did not fail peer verification (curl $wrong_code)" >&2
    exit 1
}
echo "PASS: backend certificate DNS/SNI verification rejects wrong hostname."

# A fully trusted upstream connection must still deny *all* Agent business
# routes. The policy parser is internal and never an HTTP authorization bypass.
status="$(curl --silent --noproxy '*' --max-time 8 \
    --cacert "$TMP/pki/backend-ca.crt" --resolve "$HOST_RESOLVE" \
    --cert "$TMP/pki/proxy.crt" --key "$TMP/pki/proxy.key" \
    -H "Host: agent.lanveriq.internal" \
    -H "X-Lanveriq-Verified-Client-Fingerprint: $LANVERIQ_CADDY_UPSTREAM_CLIENT_DER_SHA256" \
    -X POST -o "$TMP/denied-route" -w '%{http_code}' \
    "$URL/api/v1/devices/00000000-0000-7000-8000-000000000001/activate")"
[[ "$status" == 403 ]] || {
    echo "FAIL: Agent business route with trusted upstream returned $status, not 403" >&2
    exit 1
}
echo "PASS: even the authentic proxy TLS peer cannot open disabled Agent APIs."
plaintext_code="$(curl --silent --noproxy '*' --max-time 4 \
    -o "$TMP/plaintext" -w '%{http_code}' \
    "http://127.0.0.1:8443/actuator/health" 2>/dev/null || true)"
[[ "$plaintext_code" != 200 ]] || {
    echo "FAIL: plaintext HTTP health was exposed on 8443" >&2
    exit 1
}
if (echo > /dev/tcp/127.0.0.1/8080) >/dev/null 2>&1; then
    echo "FAIL: default plaintext 8080 listener is available" >&2
    exit 1
fi
echo "PASS: no plaintext service is available on the tested connector/port 8080."
echo "MANUAL SYNTHETIC PASS: real Spring/Tomcat mTLS, PG18 Flyway startup, default deny."
echo "NOT PROVEN: real Caddy-to-Tomcat handshake, path-specific policy resolution,"
echo "per-request PostgreSQL revocation, OS key custody, or any actual Agent."
