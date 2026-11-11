#!/usr/bin/env bash
# Isolated root-run CI only: two temporary *unprivileged* Unix identities.
# No real LanveriqAgent service is installed/enabled; no live enrollment,
# telemetry, production CA, device inventory or outside-network connection.
set -Eeuo pipefail
if [[ ${EUID} -ne 0 || ${#} -ne 1 || ! -x ${1} ]]; then
  echo "Requires root in isolated CI and one compiled synthetic example" >&2
  exit 2
fi
command -v useradd >/dev/null
command -v userdel >/dev/null
command -v runuser >/dev/null
command -v openssl >/dev/null
keytool=$(realpath -- "${1}")
case "${keytool}" in
  */apps/agent/target/debug/examples/linux_csr_synthetic) ;;
  *) echo "Unexpected synthetic test executable path" >&2; exit 2 ;;
esac

# Only ephemeral test user accounts. Avoid colliding with an existing UID.
nonce=$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')
owner="lvown${nonce}"
other="lvother${nonce}"
rootdir=$(mktemp -d -p /tmp lanveriq-linux-csr-ci.XXXXXXXX)
owner_created=0
other_created=0
cleanup() {
  local exit_status=$?
  trap - EXIT
  if [[ -d "$rootdir" && "$rootdir" == /tmp/lanveriq-linux-csr-ci.* ]]; then
    rm -rf --one-file-system -- "$rootdir"
  fi
  if (( other_created )); then userdel "$other" >/dev/null 2>&1 || :; fi
  if (( owner_created )); then userdel "$owner" >/dev/null 2>&1 || :; fi
  exit "$exit_status"
}
trap cleanup EXIT
useradd --system --no-create-home --shell /usr/sbin/nologin "$owner"
owner_created=1
useradd --system --no-create-home --shell /usr/sbin/nologin "$other"
other_created=1

# OpenSSL-generated key stays inside a 0700 test-parent. The probe is a
# short-lived executable only: no long-running service or scheduler.
install -m 0755 "$keytool" "$rootdir/synthetic-csr"
chown "$owner:$owner" "$rootdir"
chmod 0700 "$rootdir"
runuser -u "$owner" -- env \
  LANVERIQ_SYNTHETIC_CSR_ONLY=yes \
  LANVERIQ_SYNTHETIC_CSR_PARENT="$rootdir" \
  "$rootdir/synthetic-csr" "$rootdir/bundle-one"

private="$rootdir/bundle-one/device-p256-private.pem"
public="$rootdir/bundle-one/device-p256-request.pem"
[[ -s "$private" && -s "$public" ]]
[[ "$(stat -c %a "$rootdir")" == 700 ]]
[[ "$(stat -c %a "$rootdir/bundle-one")" == 700 ]]
[[ "$(stat -c %a "$private")" == 600 ]]
[[ "$(stat -c %U "$private")" == "$owner" ]]
[[ "$(stat -c %U "$rootdir/bundle-one")" == "$owner" ]]
runuser -u "$owner" -- openssl req -in "$public" -verify -noout >/dev/null 2>&1

# Separate process of the SAME approved Linux account can still open key.
runuser -u "$owner" -- test -r "$private"
runuser -u "$owner" -- openssl pkey -in "$private" -pubout -out /dev/null >/dev/null 2>&1

# A DIFFERENT unprivileged Unix account cannot traverse/read/write key,
# even if the path is known in advance.
if runuser -u "$other" -- test -r "$private"; then
  echo "FAIL: unrelated Linux account can read private key" >&2
  exit 1
fi
if runuser -u "$other" -- test -x "$rootdir"; then
  echo "FAIL: unrelated Linux account can traverse owner state directory" >&2
  exit 1
fi
if runuser -u "$other" -- head -c 1 "$private" >/dev/null 2>&1; then
  echo "FAIL: unrelated Linux account opened private key bytes" >&2
  exit 1
fi
if runuser -u "$other" -- sh -c 'printf x >> "$1"' sh "$private" 2>/dev/null; then
  echo "FAIL: unrelated Linux account modified private key" >&2
  exit 1
fi

# Unsafe parent-mode and existing-key protection must remain fail closed.
chmod 0755 "$rootdir"
if runuser -u "$owner" -- env \
  LANVERIQ_SYNTHETIC_CSR_ONLY=yes LANVERIQ_SYNTHETIC_CSR_PARENT="$rootdir" \
  "$rootdir/synthetic-csr" "$rootdir/unsafe-parent" >/dev/null 2>&1; then
  echo "FAIL: publicly traversable state parent was accepted" >&2
  exit 1
fi
chmod 0700 "$rootdir"
if runuser -u "$owner" -- env \
  LANVERIQ_SYNTHETIC_CSR_ONLY=yes LANVERIQ_SYNTHETIC_CSR_PARENT="$rootdir" \
  "$rootdir/synthetic-csr" "$rootdir/bundle-one" >/dev/null 2>&1; then
  echo "FAIL: generated CNG-equivalent Linux PEM key was overwritten" >&2
  exit 1
fi
[[ -s "$private" ]]
runuser -u "$owner" -- openssl pkey -in "$private" -pubout -out /dev/null >/dev/null 2>&1

# This is Linux DAC credential custody, NOT a TPM/non-exportable OS key
# or an actual systemd dynamic-UID restart across boots.
echo "PASS: Linux P-256 synthetic CSR signing, owner 0700/0600 DAC, second-process same-UID access, other-UID read/write/traverse denial and fail-closed no-overwrite policy"
