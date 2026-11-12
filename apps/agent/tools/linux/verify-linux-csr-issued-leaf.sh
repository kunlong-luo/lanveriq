#!/usr/bin/env bash
# Synthetic-only Rust/Linux CSR -> short-lived OpenSSL CA -> Java public fixtures.
# GitHub-hosted disposable Ubuntu 24.04 CI, never a real Agent or production CA.
set -Eeuo pipefail
umask 077
if [[ $EUID -ne 0 || $# -ne 2 || ! -x $1 || -z ${RUNNER_TEMP:-} || ${GITHUB_ACTIONS:-} != true ]]; then
  echo "requires isolated root CI and compiled synthetic Rust CSR example" >&2
  exit 2
fi
helper=$(realpath -e -- "$1")
case "$helper" in
  */apps/agent/target/debug/examples/linux_csr_synthetic) ;;
  *) echo "unapproved synthetic CSR example" >&2; exit 2 ;;
esac
public_dir=$2
[[ "$public_dir" == "$RUNNER_TEMP/lanveriq-linux-pki-public" && ! -e "$public_dir" ]] || {
  echo "public fixture location must be new and runner-local" >&2
  exit 2
}
command -v openssl >/dev/null
command -v useradd >/dev/null
command -v userdel >/dev/null
command -v runuser >/dev/null
nonce=$(openssl rand -hex 4)
[[ "$nonce" =~ ^[0-9a-f]{8}$ ]]
owner="lvcsr$nonce"
work=$(mktemp -d -p "$RUNNER_TEMP" lanveriq-linux-csr-ci.XXXXXXXX)
created=0
cleanup() {
  local ret=$?
  trap - EXIT
  if [[ "$work" == "$RUNNER_TEMP"/lanveriq-linux-csr-ci.* ]]; then
    rm -rf --one-file-system -- "$work"
  fi
  if (( created )); then userdel "$owner" >/dev/null 2>&1 || :; fi
  if (( ret != 0 )); then rm -rf --one-file-system -- "$public_dir"; fi
  exit "$ret"
}
trap cleanup EXIT
useradd --system --no-create-home --shell /usr/sbin/nologin "$owner"
created=1
# Staging ancestor is searchable only; CA key remains in a separate root-
# controlled 0700 directory, while the Agent's own parent is owner-only.
chmod 0711 "$work"
# GitHub workspace's ancestor permissions can deny an unrelated Linux UID
# executable traversal. Copy ONLY the public CI test binary into a scoped
# searchable staging directory; do NOT copy the private Agent key or CA key.
install -m 0755 "$helper" "$work/synthetic-csr"
parent="$work/lanveriq-linux-csr-ci.$nonce"
install -d -m 0700 -o "$owner" -g "$owner" "$parent"
install -d -m 0700 "$work/ca"
ca="$work/ca"
install -d -m 0755 "$public_dir"

runuser -u "$owner" -- env LANVERIQ_SYNTHETIC_CSR_ONLY=yes \
  LANVERIQ_SYNTHETIC_CSR_PARENT="$parent" "$work/synthetic-csr" "$parent/device-primary"
runuser -u "$owner" -- env LANVERIQ_SYNTHETIC_CSR_ONLY=yes \
  LANVERIQ_SYNTHETIC_CSR_PARENT="$parent" "$work/synthetic-csr" "$parent/device-alternate"
primary="$parent/device-primary/device-p256-request.pem"
other="$parent/device-alternate/device-p256-request.pem"
key="$parent/device-primary/device-p256-private.pem"
[[ -s "$primary" && -s "$other" && -s "$key" ]]
[[ "$(stat -c %a "$parent")" == 700 ]]
[[ "$(stat -c %a "$key")" == 600 ]]
[[ "$(stat -c %U "$key")" == "$owner" ]]
runuser -u "$owner" -- openssl req -verify -in "$primary" -noout >/dev/null 2>&1
runuser -u "$owner" -- openssl req -verify -in "$other" -noout >/dev/null 2>&1
if runuser -u nobody -- test -r "$key"; then
  echo "different unprivileged Unix identity could read Agent key" >&2
  exit 1
fi

cat > "$ca/root.cnf" <<'CONFIG'
[req]
distinguished_name=dn
[dn]
[v3_ca]
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
CONFIG
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
  -pkeyopt ec_param_enc:named_curve -out "$ca/root-key.pem" >/dev/null 2>&1
openssl req -new -x509 -sha256 -days 45 \
  -key "$ca/root-key.pem" -subj "/CN=Lanveriq Synthetic Linux Client CA" \
  -config "$ca/root.cnf" -extensions v3_ca -out "$ca/root.pem" >/dev/null 2>&1
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
  -pkeyopt ec_param_enc:named_curve -out "$ca/wrong-root-key.pem" >/dev/null 2>&1
openssl req -new -x509 -sha256 -days 45 \
  -key "$ca/wrong-root-key.pem" -subj "/CN=Lanveriq Synthetic WRONG CA" \
  -config "$ca/root.cnf" -extensions v3_ca -out "$ca/wrong-root.pem" >/dev/null 2>&1

device_id="71d3df4a-c892-47c7-9d44-83666a3cb3ab"
wrong_id="ce046b24-d3f1-407f-a6cd-913500d74bb9"
cat > "$ca/client.cnf" <<CONFIG
[leaf]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=clientAuth
subjectAltName=URI:urn:uuid:$device_id
CONFIG
cat > "$ca/wrong-san.cnf" <<CONFIG
[leaf]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=clientAuth
subjectAltName=URI:urn:uuid:$wrong_id
CONFIG
cat > "$ca/server-auth.cnf" <<CONFIG
[leaf]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=serverAuth
subjectAltName=URI:urn:uuid:$device_id
CONFIG

sign() {
  local csr=$1 output=$2 profile=$3
  openssl x509 -req -in "$csr" -CA "$ca/root.pem" -CAkey "$ca/root-key.pem" \
    -set_serial "0x$(openssl rand -hex 16)" -days 30 -sha256 \
    -extfile "$profile" -extensions leaf -out "$output" >/dev/null 2>&1
}
sign "$primary" "$ca/client-leaf.pem" "$ca/client.cnf"
sign "$primary" "$ca/wrong-san-leaf.pem" "$ca/wrong-san.cnf"
sign "$primary" "$ca/server-auth-leaf.pem" "$ca/server-auth.cnf"
sign "$other" "$ca/alternate-key-leaf.pem" "$ca/client.cnf"
openssl verify -purpose sslclient -CAfile "$ca/root.pem" "$ca/client-leaf.pem" >/dev/null
if openssl verify -purpose sslclient -CAfile "$ca/wrong-root.pem" "$ca/client-leaf.pem" >/dev/null 2>&1; then
  echo "wrong root unexpectedly accepted" >&2; exit 1
fi

# Only PUBLIC PEM files leave the root-controlled ephemeral signing stage.
install -m 0644 "$primary" "$public_dir/device-primary.csr.pem"
install -m 0644 "$ca/root.pem" "$public_dir/trusted-root.pem"
install -m 0644 "$ca/wrong-root.pem" "$public_dir/wrong-root.pem"
for name in client-leaf wrong-san-leaf server-auth-leaf alternate-key-leaf; do
  install -m 0644 "$ca/$name.pem" "$public_dir/$name.pem"
done
[[ "$(find "$public_dir" -maxdepth 1 -type f | wc -l)" == 7 ]]
if grep -R -E 'BEGIN (EC |RSA |ENCRYPTED )?PRIVATE KEY' "$public_dir" >/dev/null; then
  echo "private material leaked to public fixture" >&2; exit 1
fi
echo "PASS: real unprivileged Linux Rust P-256 CSR, ephemeral CA/clientAuth leaf and negative public fixtures; CA and Agent private keys never exported"
