#!/usr/bin/env bash
# Manual-only read-only preflight for the maintainer's local development device.
# This is NOT enrollment, approval, certificate issuance or activation.
set -euo pipefail
umask 077

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ $# == 0 ]] || fail 'No arguments accepted; fixed local development paths only.'
[[ -t 0 && -t 1 && -z "${CI:-}" && -z "${GITHUB_ACTIONS:-}" ]] ||
    fail 'Run manually in a local interactive terminal, never CI.'
for tool in docker openssl sudo sha256sum sed getent awk; do
    command -v "$tool" >/dev/null || fail "Missing prerequisite: $tool"
done

CERT=/etc/lanveriq/agent/tls/agent.crt
KEY=/etc/lanveriq/agent/tls/agent.key
CA=/etc/spark/caddy/tls/lanveriq/agent-ca.crt
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT

# Read public certificates into a private temporary directory. The private
# key remains in its protected location; only its public SPKI is exported.
sudo cat "$CERT" > "$TMP/agent.crt"
sudo cat "$CA" > "$TMP/agent-ca.crt"
openssl verify -purpose sslclient -CAfile "$TMP/agent-ca.crt" \
    "$TMP/agent.crt" >/dev/null || fail 'Client certificate chain or validity rejected.'
SAN="$(openssl x509 -in "$TMP/agent.crt" -noout -ext subjectAltName)"
DEVICE_ID="$(printf '%s\n' "$SAN" | sed -nE 's/^[[:space:]]*URI:urn:uuid:([0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12})[[:space:]]*$/\1/p')"
[[ "$DEVICE_ID" =~ ^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$ &&
   "$DEVICE_ID" != 00000000-0000-0000-0000-000000000000 ]] ||
    fail 'Expected exactly one canonical device URI SAN.'
# The OpenSSL-rendered extension must contain only its heading and one URI.
[[ "$(printf '%s\n' "$SAN" | awk 'NF {n++} END {print n+0}')" == 2 ]] ||
    fail 'Unexpected additional SAN entries.'

openssl x509 -in "$TMP/agent.crt" -pubkey -noout |
    openssl pkey -pubin -outform DER > "$TMP/leaf.spki"
sudo openssl pkey -in "$KEY" -pubout -outform DER > "$TMP/key.spki"
cmp -s "$TMP/leaf.spki" "$TMP/key.spki" || fail 'Client private key does not match leaf.'
FINGERPRINT="$(openssl x509 -in "$TMP/agent.crt" -outform DER | sha256sum | cut -d' ' -f1)"
[[ "$FINGERPRINT" =~ ^[0-9a-f]{64}$ ]] || fail 'Unexpected fingerprint format.'
printf 'PASS: local client certificate chain, canonical URI SAN and key match.\n'
printf 'NOTE: this is not the server PKIX/CSR review or registration authorization.\n'

if getent ahostsv4 agent.lanveriq.internal |
    awk 'NF {seen=1; if ($1 != "127.0.0.1") bad=1} END {exit !(seen && !bad)}'; then
    printf 'PASS: Agent hostname resolves to loopback over IPv4.\n'
else
    printf 'NOT READY: ordinary IPv4 DNS resolution is absent or not exclusively loopback.\n'
fi

[[ "$(docker inspect -f '{{.State.Running}}' spark-postgres 2>/dev/null)" == true ]] ||
    fail 'Existing spark-postgres is not running; no service will be started.'
printf 'Read-only identity query; enter the existing lanveriq database password.\n'
docker exec -it -e PGOPTIONS='-c default_transaction_read_only=on' spark-postgres psql \
    -X -W -h 127.0.0.1 -U lanveriq -d lanveriq -v ON_ERROR_STOP=1 \
    -c "
BEGIN READ ONLY;
SELECT current_database() = 'lanveriq' AND current_user = 'lanveriq' AS expected_database_role,
       current_setting('server_version_num')::integer BETWEEN 180000 AND 189999 AS postgres18;
SELECT EXISTS (SELECT 1 FROM public.devices WHERE id = '$DEVICE_ID') AS device_registered;
SELECT count(*) AS matching_credentials,
       count(*) FILTER (WHERE status = 'active' AND revoked_at IS NULL
         AND issued_at <= now() AND expires_at > now()) AS active_current_credentials
FROM public.device_credentials
WHERE device_id = '$DEVICE_ID' AND client_cert_sha256_fingerprint = '$FINGERPRINT';
COMMIT;"
printf 'Query completed. Zero matching/active credentials means registration is not ready.\n'
printf 'No database mutation, registration, activation or inventory upload was performed.\n'
