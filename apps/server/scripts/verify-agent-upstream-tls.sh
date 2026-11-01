#!/usr/bin/env bash
# MANUAL ONLY: actual Spring Boot/Tomcat upstream TLS with synthetic PKI + PG18.
# It does not start Caddy, expose Agent APIs or authorize any endpoint data.
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)"
DB_CID=""
APP_PID=""
CADDY_CID=""
CADDY_DATA_VOLUME=""
CADDY_CONFIG_VOLUME=""
BRIDGE_MODE="${LANVERIQ_SYNTHETIC_CADDY_BRIDGE:-0}"
CONSOLE_MODE="${LANVERIQ_SYNTHETIC_CONSOLE_SESSION:-0}"
[[ "$CONSOLE_MODE" == 0 || "$CONSOLE_MODE" == 1 ]] || {
    echo "Synthetic console mode must be 0 or 1" >&2
    exit 1
}
[[ "$CONSOLE_MODE" == 0 || "$BRIDGE_MODE" == 1 ]] || {
    echo "Synthetic console mode requires explicitly enabled isolated Caddy bridge" >&2
    exit 1
}
[[ "$BRIDGE_MODE" == 0 || "$BRIDGE_MODE" == 1 ]] || {
    echo "Synthetic bridge mode must be 0 or 1" >&2
    exit 1
}
stop_caddy() {
    if [[ -n "$CADDY_CID" ]]; then
        docker rm -f "$CADDY_CID" >/dev/null 2>&1 || true
        CADDY_CID=""
    fi
}
cleanup() {
    stop_caddy
    if [[ -n "$APP_PID" ]]; then
        kill "$APP_PID" >/dev/null 2>&1 || true
        wait "$APP_PID" >/dev/null 2>&1 || true
    fi
    if [[ -n "$DB_CID" ]]; then
        docker rm -f "$DB_CID" >/dev/null 2>&1 || true
    fi
    # Do not make the container-owned private Caddy CA world-readable.
    [[ -z "$CADDY_DATA_VOLUME" ]] || docker volume rm "$CADDY_DATA_VOLUME" >/dev/null 2>&1 || true
    [[ -z "$CADDY_CONFIG_VOLUME" ]] || docker volume rm "$CADDY_CONFIG_VOLUME" >/dev/null 2>&1 || true
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
if [[ "$BRIDGE_MODE" == 1 ]]; then
    for tool in jq sed awk grep; do
        command -v "$tool" >/dev/null || {
            echo "Missing synthetic Caddy test tool: $tool" >&2
            exit 1
        }
    done
    [[ "$(curl --version)" == *HTTP2* ]] || {
        echo "HTTP/2 capable curl is required" >&2
        exit 1
    }
    if (echo > /dev/tcp/127.0.0.1/443) >/dev/null 2>&1; then
        echo "Port 443 occupied; refuse unrelated service test" >&2
        exit 1
    fi
fi
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
if [[ "$CONSOLE_MODE" == 1 ]]; then
    for tool in script python3; do
        command -v "$tool" >/dev/null || {
            echo "Missing synthetic browser-auth acceptance tool: $tool" >&2
            exit 1
        }
    done
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
if [[ "$BRIDGE_MODE" == 1 ]]; then
    DEVICE_ID="00000000-0000-7000-8000-000000000001"
    make_ca approved-client-ca "Synthetic Agent Client CA"
    make_ca untrusted-agent-ca "Synthetic Wrong Agent Client CA"
    make_leaf agent approved-client-ca clientAuth "URI:urn:uuid:$DEVICE_ID"
    make_leaf wrong-agent untrusted-agent-ca clientAuth "URI:urn:uuid:$DEVICE_ID"
    cp "$TMP/pki/approved-client-ca.crt" "$TMP/pki/approved-client-ca.pem"
    cp "$TMP/pki/backend-ca.crt" "$TMP/pki/backend-server-ca.pem"
    cp "$TMP/pki/untrusted-ca.crt" "$TMP/pki/wrong-backend-ca.pem"
    cp "$TMP/pki/proxy.crt" "$TMP/pki/caddy-upstream-client.crt"
    cp "$TMP/pki/proxy.key" "$TMP/pki/caddy-upstream-client.key"
    # Named volumes are allocated only in the opt-in bridge mode.
fi
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
# No seeded administrator or stored credential is permitted in the default
# application. Only the disposable manual console acceptance may run the
# REAL local, interactive, one-time bootstrap runner against an empty PG18.
# util-linux 'script' creates the attached PTY required by System.console().
# The generated password travels only via a pipe, never CLI/env/CI logs.
if [[ "$CONSOLE_MODE" == 1 ]]; then
    SYNTHETIC_LOGIN="synthetic-owner"
    SYNTHETIC_PASSWORD="$(openssl rand -hex 24)"
    # util-linux script defaults to echoing *piped* PTY input before
    # Console.readPassword has disabled echo. Never store the generated
    # password in the private bootstrap transcript either.
    script --help | grep -Fq -- '--echo' || {
        echo "FAIL: util-linux script without private-input echo controls" >&2
        exit 1
    }
    # The child must use the REAL interactive Java Console, with both
    # passwords sent over stdin and neither password in argv/env/CI logs.
    # --echo never ensures PTY input is not reproduced in the captured log.
    if ! (printf '%s\n%s\n' "$SYNTHETIC_PASSWORD" "$SYNTHETIC_PASSWORD" |
        script --quiet --return --echo never --command \
        "java -jar $ROOT/apps/server/build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --lanveriq.bootstrap.enabled=true --lanveriq.bootstrap.login=$SYNTHETIC_LOGIN --lanveriq.bootstrap.group=SyntheticQA" \
        /dev/null >"$TMP/console-bootstrap.log" 2>&1); then
        echo "FAIL: isolated offline first-admin bootstrap failed" >&2
        # Classify from private runner-only output using a fixed set of
        # public labels; do not print exception text, SQL, logs or secrets.
        python3 "$ROOT/apps/server/scripts/diagnose-synthetic-bootstrap.py" \
            "$TMP/console-bootstrap.log" || true
        exit 1
    fi
    # A zero process exit is not enough: verify the real bootstrap runner
    # reached its post-commit success event without exposing the transcript.
    if ! grep -Fq "Initial administrator created." "$TMP/console-bootstrap.log"; then
        echo "FAIL: Java bootstrap exited without its commit confirmation" >&2
        python3 "$ROOT/apps/server/scripts/diagnose-synthetic-bootstrap.py" \
            "$TMP/console-bootstrap.log" || true
        exit 1
    fi
    [[ "$(docker exec "$DB_CID" psql -U lanveriq -d lanveriq -XAtqc \
        "SELECT count(*) FROM users WHERE normalized_login = 'synthetic-owner'")" == 1 ]] || {
        echo "FAIL: synthetic bootstrap did not commit exactly one administrator" >&2
        exit 1
    }
fi
# Use the real Tomcat connector. No proxy, fake servlet attributes, HTTP endpoint
# for identity assertions or extra public listener is created.
java -jar "$ROOT/apps/server/build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar" \
    --spring.profiles.active=agent-upstream-mtls-acceptance \
    --lanveriq.console.auth.enabled="$CONSOLE_MODE" \
    --lanveriq.console.auth.origin=https://console.lanveriq.internal \
    --lanveriq.device-directory.enabled="$CONSOLE_MODE" \
    --lanveriq.device-directory.http-enabled="$CONSOLE_MODE" \
    --lanveriq.monitoring.overview-http-enabled="$CONSOLE_MODE" \
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

# A TLS failure is meaningful only if the SAME real Tomcat listener still
# accepts the trusted proxy leaf immediately afterwards. Otherwise a crash,
# unreachable service or unrelated local listener could falsely pass a
# negative-certificate test. Do not use --insecure or synthetic HTTP stubs.
assert_trusted_upstream_healthy() {
    if ! kill -0 "$APP_PID" >/dev/null 2>&1; then
        echo "FAIL: real Spring/Tomcat process stopped during TLS tests" >&2
        exit 1
    fi
    local body
    body="$(curl --silent --fail --noproxy '*' --max-time 8 \
        --tlsv1.3 --tls-max 1.3 \
        --cacert "$TMP/pki/backend-ca.crt" --resolve "$HOST_RESOLVE" \
        --cert "$TMP/pki/proxy.crt" --key "$TMP/pki/proxy.key" \
        "$URL/actuator/health")" || {
        echo "FAIL: trusted mTLS health probe failed after negative check" >&2
        exit 1
    }
    if [[ "$body" != *'"status":"UP"'* ]]; then
        echo "FAIL: trusted mTLS health probe did not return UP" >&2
        exit 1
    fi
}

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
    assert_trusted_upstream_healthy
    echo "PASS: $label failed TLS handshake; valid TLSv1.3 peer still healthy."
}
tls_denied "missing upstream client certificate"
tls_denied "untrusted upstream client certificate" \
    --cert "$TMP/pki/untrusted.crt" --key "$TMP/pki/untrusted.key"
tls_denied "forged identity header without upstream certificate" \
    -H "X-Lanveriq-Verified-Client-Fingerprint: $LANVERIQ_CADDY_UPSTREAM_CLIENT_DER_SHA256"
# Even with an otherwise trusted client certificate, TLSv1.2 must be
# negotiated at neither the connector nor a second fallback listener.
tls_denied "forced TLSv1.2 despite valid proxy certificate" \
    --tlsv1.2 --tls-max 1.2 \
    --cert "$TMP/pki/proxy.crt" --key "$TMP/pki/proxy.key"

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
assert_trusted_upstream_healthy
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

if [[ "$BRIDGE_MODE" == 1 ]]; then
    IMAGE="caddy:2.11.7"
    docker pull "$IMAGE" >/dev/null
    docker image inspect "$IMAGE" --format \
        'Synthetic Caddy image ID {{.Id}}; digests {{json .RepoDigests}}'
    CADDY_DATA_VOLUME="$(docker volume create)"
    CADDY_CONFIG_VOLUME="$(docker volume create)"
    [[ -n "$CADDY_DATA_VOLUME" && -n "$CADDY_CONFIG_VOLUME" ]] || {
        echo "Could not allocate isolated Caddy bridge volumes" >&2
        exit 1
    }
    # Make the Caddy reference loopback-only during this synthetic test.
    # Extend ONLY this throwaway Caddyfile with a read-only Tomcat health
    # path routed through the exact same double-mTLS reverse_proxy block.
    # No Agent business API or real reference config is changed. A 200/UP
    # response now positively proves a request traversed Caddy->Tomcat,
    # whereas a 403 business denial on its own could not prove transit.
    sed -e 's/servers :443 {/servers 127.0.0.1:443 {/' \
        -e '/^console\.lanveriq\.internal {/a\    bind 127.0.0.1' \
        -e '/^enroll\.lanveriq\.internal {/a\    bind 127.0.0.1' \
        -e '/^agent\.lanveriq\.internal {/a\    bind 127.0.0.1' \
        -e 's|@agentRoutes path |@agentRoutes path /actuator/health |' \
        "$ROOT/deploy/caddy/Caddyfile.reference" > "$TMP/Caddyfile.synthetic"
    CADDY_REFERENCE="$TMP/Caddyfile.synthetic"
    if [[ "$CONSOLE_MODE" == 1 ]]; then
        # Replace exactly one 503 response in the disposable config.
        # Caddy's checked-in deploy reference is NEVER touched; enrollment
        # and unknown Agent endpoints remain denied.
        python3 - "$CADDY_REFERENCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
marker = '    respond "Console not enabled in this reference configuration" 503'
proxy = """    # TEST-ONLY canonical console host; never copy to deployed Caddyfile.
    reverse_proxy https://127.0.0.1:8443 {
        transport http {
            tls
            tls_client_auth /etc/lanveriq/pki/caddy-upstream-client.crt /etc/lanveriq/pki/caddy-upstream-client.key
            tls_trust_pool file /etc/lanveriq/pki/backend-server-ca.pem
            tls_server_name spring-backend.lanveriq.internal
            versions 1.1 2
        }
        header_up -X-Forwarded-Client-Cert
        header_up -SSL-Client-Cert
        header_up -X-Lanveriq-Verified-Client-*
        header_up Host console.lanveriq.internal
    }"""
if text.count(marker) != 1 or "TEST-ONLY canonical console host" in text:
    raise SystemExit("Refusing unexpected console Caddyfile template")
path.write_text(text.replace(marker, proxy), encoding="utf-8")
PY
    fi
    grep -Fq '@agentRoutes path /actuator/health ' "$CADDY_REFERENCE" || {
        echo "Synthetic bridge health route was not inserted; abort before Caddy" >&2
        exit 1
    }
    run_caddy_command() {
        local config="$1"
        shift
        docker run --rm \
            -v "$config:/etc/caddy/Caddyfile:ro" \
            -v "$TMP/pki:/etc/lanveriq/pki:ro" \
            -v "$CADDY_DATA_VOLUME:/data" \
            -v "$CADDY_CONFIG_VOLUME:/config" \
            "$IMAGE" caddy "$@"
    }
    run_caddy_command "$CADDY_REFERENCE" adapt \
        --config /etc/caddy/Caddyfile --pretty > "$TMP/caddy-adapted.json"
    # Fail before Docker can bind a public interface if Caddy adaptation
    # unexpectedly introduces any listener other than loopback TCP 443.
    jq -e '[.apps.http.servers[] | .listen[]] |
        (length > 0) and (all(.[]; . == "127.0.0.1:443"))' \
        "$TMP/caddy-adapted.json" >/dev/null
    jq -e '[.apps.http.servers[] | select(.strict_sni_host == true and
        ((.protocols // []) | index("h1") != null) and
        ((.protocols // []) | index("h2") != null) and
        ((.protocols // []) | index("h3") == null))] | length > 0' \
        "$TMP/caddy-adapted.json" >/dev/null
    jq -e '[.. | objects | select(.client_authentication?.mode? == "require_and_verify")]
        | length > 0' "$TMP/caddy-adapted.json" >/dev/null
    run_caddy_command "$CADDY_REFERENCE" validate \
        --config /etc/caddy/Caddyfile >/dev/null
    start_caddy() {
        local config="$1"
        stop_caddy
        local port_released=0
        for _ in $(seq 1 20); do
            if ! (echo > /dev/tcp/127.0.0.1/443) >/dev/null 2>&1; then
                port_released=1
                break
            fi
            sleep 1
        done
        [[ "$port_released" == 1 ]] || {
            echo "Prior synthetic Caddy still occupies TCP 443" >&2
            exit 1
        }
        CADDY_CID="$(docker run --detach --rm --network host \
            -v "$config:/etc/caddy/Caddyfile:ro" \
            -v "$TMP/pki:/etc/lanveriq/pki:ro" \
            -v "$CADDY_DATA_VOLUME:/data" \
            -v "$CADDY_CONFIG_VOLUME:/config" \
            "$IMAGE" caddy run --config /etc/caddy/Caddyfile)"
        local ready=0
        for _ in $(seq 1 30); do
            if (echo > /dev/tcp/127.0.0.1/443) >/dev/null 2>&1; then
                ready=1
                break
            fi
            docker inspect --format '{{.State.Running}}' "$CADDY_CID" | grep -qx true || {
                echo "Synthetic Caddy startup failed" >&2
                exit 1
            }
            sleep 1
        done
        [[ "$ready" == 1 ]] || {
            echo "Synthetic Caddy failed to listen on loopback TCP 443" >&2
            exit 1
        }
    }
    start_caddy "$CADDY_REFERENCE"
    ROOT_CA="$TMP/public-caddy-root.crt"
    for _ in $(seq 1 30); do
        docker inspect -f '{{.State.Running}}' "$CADDY_CID" | grep -qx true || {
            echo "Synthetic Caddy bridge stopped before CA readiness" >&2
            docker logs "$CADDY_CID" >&2 || true
            exit 1
        }
        if docker cp "$CADDY_CID:/data/caddy/pki/authorities/local/root.crt" \
            "$ROOT_CA" >/dev/null 2>"$TMP/ca-copy-error" && [[ -s "$ROOT_CA" ]]; then
            break
        fi
        # Bootstrap ONLY: the next real curl requests verify this public root.
        openssl s_client -connect 127.0.0.1:443 \
            -servername console.lanveriq.internal </dev/null \
            >"$TMP/ca-bootstrap" 2>&1 || true
        sleep 1
    done
    [[ -s "$ROOT_CA" ]] || {
        echo "Caddy bridge public CA missing; cannot verify HTTPS" >&2
        docker logs "$CADDY_CID" >&2 || true
        exit 1
    }
    openssl x509 -in "$ROOT_CA" -noout >/dev/null || {
        echo "Bridge root is not an X.509 certificate" >&2
        exit 1
    }
    EDGE_URL="https://agent.lanveriq.internal/api/v1/devices/$DEVICE_ID/activate"
    # Test-only: this GET path is appended to @agentRoutes exclusively in
    # $TMP/Caddyfile.synthetic. A CA-verified UP is mandatory independent
    # proof of successful two-way TLS on BOTH proxy and real Tomcat.
    bridge_health_request() {
        curl --silent --show-error --noproxy '*' --http2 --max-time 12 \
            --cacert "$ROOT_CA" \
            --resolve agent.lanveriq.internal:443:127.0.0.1 \
            --cert "$TMP/pki/agent.crt" --key "$TMP/pki/agent.key" \
            -o "$TMP/bridge-health-response" -w '%{http_code}' \
            "https://agent.lanveriq.internal/actuator/health"
    }
    assert_bridge_up() {
        local code
        code="$(bridge_health_request)" || {
            echo "FAIL: double-mTLS bridge health TLS request failed" >&2
            exit 1
        }
        [[ "$code" == 200 ]] && grep -Fq '"status":"UP"' "$TMP/bridge-health-response" || {
            echo "FAIL: Caddy->real Spring/Tomcat did not return strict 200/UP (HTTP $code)" >&2
            exit 1
        }
        assert_trusted_upstream_healthy
    }
    assert_bridge_up
    echo "PASS: Agent mTLS -> Caddy -> real Spring/Tomcat mTLS -> PG18 health 200/UP."
    edge_request() {
        curl --silent --show-error --noproxy '*' --http2 --max-time 12 \
            --cacert "$ROOT_CA" \
            --resolve agent.lanveriq.internal:443:127.0.0.1 \
            --cert "$TMP/pki/agent.crt" --key "$TMP/pki/agent.key" \
            -X POST -o "$TMP/edge-response" -w '%{http_code}' "$EDGE_URL"
    }
    status="$(edge_request)"
    [[ "$status" == 403 ]] || {
        echo "FAIL: legitimate Agent and proxy chain must reach default-deny Tomcat (got $status)" >&2
        exit 1
    }
    echo "PASS: Agent->Caddy mTLS and Caddy->real Spring/Tomcat mTLS, HTTP business deny 403."

    deny_agent_handshake() {
        local label="$1"
        shift
        local code=000
        local rc=0
        code="$(curl --silent --noproxy '*' --http2 --max-time 10 \
            --cacert "$ROOT_CA" \
            --resolve agent.lanveriq.internal:443:127.0.0.1 \
            "$@" -X POST -o "$TMP/agent-denied" -w '%{http_code}' \
            "$EDGE_URL" 2>"$TMP/agent-error")" || rc=$?
        if [[ "$rc" == 0 || "$code" != 000 ]]; then
            echo "FAIL: $label was not denied at TLS boundary (HTTP $code)" >&2
            exit 1
        fi
        assert_bridge_up
        echo "PASS: $label denied at Caddy TLS boundary; trusted double-mTLS still UP."
    }
    deny_agent_handshake "no Agent client certificate"
    deny_agent_handshake "Agent client signed by untrusted CA" \
        --cert "$TMP/pki/wrong-agent.crt" --key "$TMP/pki/wrong-agent.key"
    host_mismatch="$(curl --silent --show-error --noproxy '*' --http2 --max-time 10 \
        --cacert "$ROOT_CA" \
        --resolve agent.lanveriq.internal:443:127.0.0.1 \
        --cert "$TMP/pki/agent.crt" --key "$TMP/pki/agent.key" \
        -H 'Host: console.lanveriq.internal' -X POST \
        -o "$TMP/mismatch" -w '%{http_code}' "$EDGE_URL")"
    [[ "$host_mismatch" == 421 ]] || {
        echo "FAIL: cross-SNI HTTP/2 Host request not rejected (HTTP $host_mismatch)" >&2
        exit 1
    }
    assert_bridge_up
    echo "PASS: cross-host HTTP/2 coalescing rejected with 421."

    cp "$CADDY_REFERENCE" "$TMP/Caddyfile.wrong-proxy"
    sed -i \
        -e 's@caddy-upstream-client.crt@untrusted.crt@g' \
        -e 's@caddy-upstream-client.key@untrusted.key@g' \
        "$TMP/Caddyfile.wrong-proxy"
    start_caddy "$TMP/Caddyfile.wrong-proxy"
    status="$(edge_request)"
    [[ "$status" == 502 ]] || {
        echo "FAIL: wrong Caddy upstream client certificate did not fail closed (HTTP $status)" >&2
        exit 1
    }
    status="$(bridge_health_request)"
    [[ "$status" == 502 ]] || {
        echo "FAIL: wrong proxy certificate unexpectedly reached real Tomcat health (HTTP $status)" >&2
        exit 1
    }
    assert_trusted_upstream_healthy
    echo "PASS: real Tomcat rejects wrong-CA Caddy upstream leaf (edge 502), healthy Tomcat remains UP."

    cp "$CADDY_REFERENCE" "$TMP/Caddyfile.wrong-backend-ca"
    sed -i 's@backend-server-ca.pem@wrong-backend-ca.pem@g' \
        "$TMP/Caddyfile.wrong-backend-ca"
    start_caddy "$TMP/Caddyfile.wrong-backend-ca"
    status="$(edge_request)"
    [[ "$status" == 502 ]] || {
        echo "FAIL: Caddy accepted wrong Spring backend CA (HTTP $status)" >&2
        exit 1
    }
    status="$(bridge_health_request)"
    [[ "$status" == 502 ]] || {
        echo "FAIL: wrong Spring CA unexpectedly returned health through Caddy (HTTP $status)" >&2
        exit 1
    }
    assert_trusted_upstream_healthy
    echo "PASS: Caddy rejects wrong Spring backend CA (edge 502), healthy Tomcat remains UP."

    start_caddy "$CADDY_REFERENCE"
    assert_bridge_up
    status="$(edge_request)"
    [[ "$status" == 403 ]] || {
        echo "FAIL: restoring authentic upstream chain did not restore default-deny (HTTP $status)" >&2
        exit 1
    }
    echo "MANUAL SYNTHETIC BRIDGE PASS: Caddy->real Tomcat health 200/UP, business default-deny 403, PostgreSQL 18.6 Flyway."
    echo "NOT PROVEN: Agent controller/revocation, actual servlet assertion, key ACL, live OS."
    if [[ "$CONSOLE_MODE" == 1 ]]; then
        bash "$ROOT/apps/server/scripts/verify-synthetic-console-session.sh" \
            "$ROOT_CA" "$TMP" "$DB_CID" "$SYNTHETIC_LOGIN" "$SYNTHETIC_PASSWORD"
        unset SYNTHETIC_PASSWORD
    fi
fi

echo "MANUAL SYNTHETIC PASS: real Spring/Tomcat mTLS, PG18 Flyway startup, default deny."
if [[ "$BRIDGE_MODE" == 0 ]]; then
    echo "NOT PROVEN: real Caddy-to-Tomcat handshake, path-specific policy resolution,"
else
    echo "NOT PROVEN: enabled Agent controller or per-request servlet-assertion handling,"
fi
echo "per-request PostgreSQL revocation, OS key custody, or any actual Agent."
