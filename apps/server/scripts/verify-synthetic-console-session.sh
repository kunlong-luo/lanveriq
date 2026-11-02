#!/usr/bin/env bash
# MANUAL synthetic-only acceptance, invoked solely by the guarded temporary
# Caddy/Tomcat/PG18 harness. Never run against a real console or account.
# Requires localhost Caddy with a disposable test-only console proxy.
set -Eeuo pipefail
umask 077
[[ "$#" == 4 ]] || { echo "Expected private synthetic fixture parameters" >&2; exit 1; }
ROOT_CA="$1"
TMP="$2"
DB_CID="$3"
LOGIN="$4"
# The parent supplies a one-shot password via inherited FD 3. Never read a
# password from argv, an exported environment variable, a URL or a log.
IFS= read -r PASSWORD <&3 || {
    echo "FAIL: synthetic password input not available" >&2
    exit 1
}
[[ "$LOGIN" == synthetic-owner && -s "$ROOT_CA" && -d "$TMP" && -n "$DB_CID" ]] || {
    echo "Refusing non-synthetic console acceptance" >&2
    exit 1
}
CONSOLE="https://console.lanveriq.internal"
COOKIE_JAR="$TMP/synthetic-console-cookies"
HEADERS="$TMP/synthetic-console-response-headers"
BODY="$TMP/synthetic-console-response-body"
: > "$COOKIE_JAR"
chmod 600 "$COOKIE_JAR"
# Fixed DNS and cert verification, never -k. The browser must independently
# establish its own trusted server CA before this transport becomes runtime.
console_curl() {
    curl --silent --show-error --noproxy '*' --http2 --max-time 12 \
        --cacert "$ROOT_CA" \
        --resolve console.lanveriq.internal:443:127.0.0.1 \
        --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
        --dump-header "$HEADERS" --output "$BODY" \
        -w '%{http_code}' "$@"
}
expect_status() {
    local label="$1" expected="$2"
    shift 2
    local status
    status="$(console_curl "$@")" || {
        echo "FAIL: $label did not complete strict HTTPS request" >&2
        exit 1
    }
    [[ "$status" == "$expected" ]] || {
        echo "FAIL: $label returned HTTP $status, not $expected" >&2
        exit 1
    }
}
assert_no_store_json() {
    python3 - "$HEADERS" "$BODY" <<'PY'
from pathlib import Path
import json
import sys
headers = Path(sys.argv[1]).read_text(encoding="iso-8859-1").lower()
assert "cache-control:" in headers and "no-store" in headers, "Cache-Control no-store required"
assert "content-type:" in headers and "application/json" in headers, "JSON required"
json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
PY
}
read_csrf() {
    expect_status "synthetic CSRF bootstrap" 200 "$CONSOLE/api/v1/auth/csrf"
    assert_no_store_json
    python3 - "$BODY" <<'PY'
from pathlib import Path
import json
import sys
token = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")).get("csrfToken")
if not isinstance(token, str) or not (8 <= len(token) <= 4096):
    raise SystemExit("FAIL: missing or invalid synthetic CSRF token")
print(token, end="")
PY
}
csrf_config() {
    local token="$1"
    # Emit the temporary curl config from standard input. Never place the
    # CSRF token on python3's command line; argv can be read by other
    # processes sharing the runner. The file lives only in the mode-0700
    # synthetic fixture and is deleted at the end of this test.
    printf '%s' "$token" | python3 -c '
from pathlib import Path
import sys
token = sys.stdin.read()
if (not token or len(token) > 4096 or
        any(c in token for c in "\\r\\n\\\"\\\\")):
    raise SystemExit("FAIL: invalid synthetic CSRF token")
Path(sys.argv[1]).write_text(
    "header = \\"X-CSRF-TOKEN: " + token + "\\"\\n",
    encoding="utf-8",
)
' "$TMP/console-csrf.curlrc"
    chmod 600 "$TMP/console-csrf.curlrc"
}
cookie_id() {
    python3 - "$COOKIE_JAR" <<'PY'
from pathlib import Path
import sys
for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if line.startswith("#HttpOnly_"):
        line = line[len("#HttpOnly_"):]
    if line.startswith("#"):
        continue
    parts = line.split("\t")
    if len(parts) == 7 and parts[5] == "__Host-lanveriq-session":
        if parts[0] != "console.lanveriq.internal" or parts[1] != "FALSE" or parts[3] != "TRUE" or parts[2] != "/":
            raise SystemExit("FAIL: session cookie violates host-only/Secure/Path")
        print(parts[6], end="")
        break
PY
}
assert_cookie_flags() {
    python3 - "$HEADERS" <<'PY'
from pathlib import Path
import sys
headers = Path(sys.argv[1]).read_text(encoding="iso-8859-1")
matching = [line for line in headers.splitlines()
            if line.lower().startswith("set-cookie: __host-lanveriq-session=")]
if len(matching) != 1:
    raise SystemExit("FAIL: expected exactly one synthetic host-only session cookie")
line = matching[0].lower()
for flag in ("; secure", "; httponly", "; samesite=strict", "; path=/"):
    if flag not in line:
        raise SystemExit("FAIL: synthetic cookie flag missing: " + flag)
if "; domain=" in line:
    raise SystemExit("FAIL: __Host- session cookie must have no Domain")
PY
}
assert_session() {
    expect_status "authenticated synthetic administrator session" 200 "$CONSOLE/api/v1/auth/session"
    assert_no_store_json
    python3 - "$BODY" <<'PY'
from pathlib import Path
import json, sys
assert json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")) == {"login": "synthetic-owner"}
PY
}
synthetic_login() {
    local before
    read_csrf >/dev/null
    before="$(cookie_id)"
    [[ -n "$before" ]] || { echo "FAIL: no pre-login session cookie" >&2; exit 1; }
    local csrf
    csrf="$(read_csrf)"
    csrf_config "$csrf"
    # Hex-only random password is passed to curl through stdin, NOT argv,
    # an environment variable, a log, query string, or a repository file.
    local status
    status="$(printf 'username=synthetic-owner&password=%s' "$PASSWORD" |
        console_curl --config "$TMP/console-csrf.curlrc" \
            -H "Origin: $CONSOLE" -H 'Sec-Fetch-Site: same-origin' \
            -H 'Content-Type: application/x-www-form-urlencoded' \
            --data-binary @- "$CONSOLE/api/v1/auth/login")" || {
        echo "FAIL: synthetic administrator login HTTPS failed" >&2
        exit 1
    }
    [[ "$status" == 204 ]] || {
        echo "FAIL: synthetic administrator login returned HTTP $status, not 204" >&2
        exit 1
    }
    local after
    after="$(cookie_id)"
    [[ -n "$after" && "$after" != "$before" ]] || {
        echo "FAIL: successful login did not rotate synthetic session identifier" >&2
        exit 1
    }
    local fresh
    fresh="$(read_csrf)"
    [[ "$fresh" != "$csrf" ]] || {
        echo "FAIL: post-fixation CSRF was not refreshed" >&2
        exit 1
    }
    assert_session
}
echo "Synthetic-only console HTTPS session acceptance: starting."
expect_status "pre-login session denied" 401 "$CONSOLE/api/v1/auth/session"
CSRF="$(read_csrf)"
assert_cookie_flags
[[ -n "$(cookie_id)" ]] || { echo "FAIL: no secure session cookie" >&2; exit 1; }
# First prove a form login missing CSRF or with a hostile Origin never reaches
# the success handler. No real credential is sent in these negative cases.
expect_status "missing CSRF login rejected" 403 \
    -H "Origin: $CONSOLE" -H 'Sec-Fetch-Site: same-origin' \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data 'username=synthetic-owner&password=bad' "$CONSOLE/api/v1/auth/login"
csrf_config "$CSRF"
expect_status "cross-origin login rejected" 403 \
    --config "$TMP/console-csrf.curlrc" \
    -H 'Origin: https://untrusted.example.test' -H 'Sec-Fetch-Site: cross-site' \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data 'username=synthetic-owner&password=bad' "$CONSOLE/api/v1/auth/login"
expect_status "incorrect password denied" 401 \
    --config "$TMP/console-csrf.curlrc" \
    -H "Origin: $CONSOLE" -H 'Sec-Fetch-Site: same-origin' \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data 'username=synthetic-owner&password=wrong' "$CONSOLE/api/v1/auth/login"
synthetic_login
echo "PASS: real Caddy HTTPS -> Tomcat form login, session fixation, fresh CSRF and strict cookie."
expect_status "scoped empty device page" 200 "$CONSOLE/api/v1/devices?limit=50"
assert_no_store_json
python3 - "$BODY" <<'PY'
from pathlib import Path
import json, sys
data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert isinstance(data, dict) and data.get("items") == [], "fresh PG18 must have no devices"
PY
expect_status "authorized monitoring overview" 200 "$CONSOLE/api/v1/monitoring/overview"
assert_no_store_json
expect_status "cross-origin scoped read forbidden" 403 \
    -H 'Origin: https://untrusted.example.test' "$CONSOLE/api/v1/devices"
echo "PASS: actual PG18 role-scoped empty device/overview reads; cross-Origin denied."
CSRF="$(read_csrf)"
csrf_config "$CSRF"
expect_status "valid CSRF logout" 204 \
    --config "$TMP/console-csrf.curlrc" -H "Origin: $CONSOLE" \
    -H 'Sec-Fetch-Site: same-origin' -X POST "$CONSOLE/api/v1/auth/logout"
expect_status "logged-out session denied" 401 "$CONSOLE/api/v1/auth/session"
expect_status "logged-out device read denied" 401 "$CONSOLE/api/v1/devices"
echo "PASS: authenticated logout removes access to scoped HTTP reads."
# Re-login, then revoke via a direct SQL mutation of synthetic admin ONLY.
# Never expose a revocation HTTP backdoor or modify any real account.
synthetic_login
[[ "$(docker exec "$DB_CID" psql -U lanveriq -d lanveriq -XAtqc \
    "UPDATE users SET session_version = session_version + 1 WHERE normalized_login = 'synthetic-owner' RETURNING session_version")" == 1 ]] || {
    echo "FAIL: synthetic session epoch could not be rotated" >&2
    exit 1
}
expect_status "revoked synthetic session denied" 401 "$CONSOLE/api/v1/auth/session"
expect_status "revoked synthetic device read denied" 401 "$CONSOLE/api/v1/devices"
echo "MANUAL SYNTHETIC CONSOLE PASS: real Caddy/Tomcat/PG18 form login, cookie, CSRF, session revocation, scoped empty reads."
echo "NOT PROVEN: actual browser network stack, multi-group access, Next.js runtime hooks, real users/devices or deployment."
