#!/usr/bin/env bash
# Runs INSIDE an isolated transient systemd DynamicUser service on Ubuntu24.
# The executable and this script are copied to /run by the CI harness.
# Never use on a real endpoint; no upload, network or Agent runtime.
set -Eeuo pipefail
if [[ ${#} -ne 2 || ${EUID} -eq 0 || ${LANVERIQ_SYNTHETIC_SYSTEMD_ONLY:-} != yes ]]; then
  echo "synthetic unprivileged transient systemd context required" >&2
  exit 2
fi
stage=$1
helper=$2
if [[ ! -x "$helper" || ! -n "${INVOCATION_ID:-}" || -z "${STATE_DIRECTORY:-}" ]]; then
  echo "missing synthetic systemd invocation and test helper" >&2
  exit 2
fi
# Reject any other state or any unexpected parent. systemd allocates the
# state directory, not this script. Resolve the managed /var/lib symlink
# to the private canonical path, satisfying the CSR API's no-symlink rule.
state=$(realpath -e -- "$STATE_DIRECTORY")
case "$state" in
  /var/lib/private/lanveriq-linux-csr-ci.*) ;;
  *) echo "state path is not a dedicated private synthetic directory" >&2; exit 2 ;;
esac
[[ "$(stat -c %a "$state")" == 700 ]]
[[ "$(stat -c %u "$state")" == "$EUID" ]]
# The idle-only service must be unable to create an IP socket. This is a
# real kernel/systemd enforcement test, not a grep of the unit file.
if /usr/bin/python3 -c 'import socket; socket.socket(socket.AF_INET, socket.SOCK_STREAM)' >/dev/null 2>&1; then
  echo "FAIL: offline systemd service can create IPv4 sockets" >&2
  exit 1
fi

bundle="$state/bundle-one"
private="$bundle/device-p256-private.pem"
public="$bundle/device-p256-request.pem"
invocation="$state/first-invocation-id"
digest="$state/first-public-csr.sha256"
case "$stage" in
  create)
    [[ ! -e "$bundle" && ! -e "$invocation" && ! -e "$digest" ]]
    env LANVERIQ_SYNTHETIC_CSR_ONLY=yes LANVERIQ_SYNTHETIC_CSR_PARENT="$state" \
      "$helper" "$bundle"
    [[ -s "$private" && -s "$public" ]]
    [[ "$(stat -c %a "$bundle")" == 700 ]]
    [[ "$(stat -c %a "$private")" == 600 ]]
    openssl req -in "$public" -verify -noout >/dev/null 2>&1
    # Only the PUBLIC CSR's digest is persisted, not the key or its bytes.
    printf '%s\n' "$INVOCATION_ID" > "$invocation"
    sha256sum "$public" | cut -d' ' -f1 > "$digest"
    [[ "$(stat -c %a "$invocation")" == 600 ]]
    [[ "$(stat -c %a "$digest")" == 600 ]]
    echo "PASS: first systemd DynamicUser invocation created bounded owner-private CSR"
    ;;
  reopen)
    [[ -s "$private" && -s "$public" && -s "$invocation" && -s "$digest" ]]
    [[ "$INVOCATION_ID" != "$(cat "$invocation")" ]]
    [[ "$(stat -c %a "$bundle")" == 700 ]]
    [[ "$(stat -c %a "$private")" == 600 ]]
    [[ "$(stat -c %u "$private")" == "$EUID" ]]
    [[ "$(sha256sum "$public" | cut -d' ' -f1)" == "$(cat "$digest")" ]]
    openssl pkey -in "$private" -pubout -out /dev/null >/dev/null 2>&1
    openssl req -in "$public" -verify -noout >/dev/null 2>&1
    # Prove the persisted key and CSR belong together without exporting
    # the private key: compare only DER-encoded public keys.
    key_hash=$(openssl pkey -in "$private" -pubout -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1)
    csr_hash=$(openssl req -in "$public" -pubkey -noout 2>/dev/null | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1)
    [[ -n "$key_hash" && "$key_hash" == "$csr_hash" ]]
    echo "PASS: a distinct systemd invocation reopened the exact retained key/CSR without weakening private state"
    ;;
  *) echo "unexpected synthetic systemd lifecycle stage" >&2; exit 2 ;;
esac
