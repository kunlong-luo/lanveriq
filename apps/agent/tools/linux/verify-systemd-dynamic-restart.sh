#!/usr/bin/env bash
# Disposable Ubuntu 24.04 GitHub-hosted CI only. This does NOT install/start
# LanveriqAgent, enroll a device, enable network or touch user hardware.
set -Eeuo pipefail
if [[ ${EUID} -ne 0 || ${#} -ne 2 || ! -x "${1}" || ! -f "${2}" ]]; then
  echo "requires isolated root CI, compiled synthetic CSR example and stage script" >&2
  exit 2
fi
if [[ ! -d /run/systemd/system ]]; then
  echo "FAIL: a running systemd manager is required, no static substitute" >&2
  exit 1
fi
command -v systemd-run >/dev/null
command -v systemctl >/dev/null
command -v runuser >/dev/null
command -v openssl >/dev/null

helper=$(realpath -e -- "$1")
stage=$(realpath -e -- "$2")
case "$helper" in
  */apps/agent/target/debug/examples/linux_csr_synthetic) ;;
  *) echo "unexpected probe binary path" >&2; exit 2 ;;
esac
case "$stage" in
  */apps/agent/tools/linux/systemd-csr-dynamic-stage.sh) ;;
  *) echo "unexpected systemd stage source path" >&2; exit 2 ;;
esac
nonce=$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')
[[ "$nonce" =~ ^[0-9a-f]{8}$ ]]
unit="lvcsrd${nonce}"
state="lanveriq-linux-csr-ci.${nonce}"
runtime="/run/${unit}"
private_dir="/var/lib/private/${state}"
public_link="/var/lib/${state}"
cleanup() {
  local status=$?
  trap - EXIT
  systemctl stop "${unit}.service" >/dev/null 2>&1 || :
  systemctl reset-failed "${unit}.service" >/dev/null 2>&1 || :
  if [[ -L "$public_link" ]]; then rm -f -- "$public_link"; fi
  if [[ -d "$private_dir" ]]; then rm -rf --one-file-system -- "$private_dir"; fi
  if [[ -d "$runtime" ]]; then rm -rf --one-file-system -- "$runtime"; fi
  exit "$status"
}
trap cleanup EXIT

install -d -m 0755 "$runtime"
install -m 0755 "$helper" "$runtime/csr-helper"
install -m 0755 "$stage" "$runtime/stage"

start_phase() {
  local phase=$1
  # The SAME transient systemd unit name and SAME persistent StateDirectory
  # are requested for two separate invocations. No permanent unit or
  # systemctl enable command; no connection or real Agent process.
  systemd-run --wait --collect --quiet --unit="$unit" \
    -p DynamicUser=yes \
    -p StateDirectory="$state" \
    -p StateDirectoryMode=0700 \
    -p RuntimeDirectory="${unit}-runtime" \
    -p RuntimeDirectoryMode=0700 \
    -p UMask=0077 \
    -p NoNewPrivileges=yes \
    -p CapabilityBoundingSet= \
    -p AmbientCapabilities= \
    -p PrivateTmp=yes \
    -p PrivateDevices=yes \
    -p PrivateNetwork=yes \
    -p IPAddressDeny=any \
    -p SocketBindDeny=any \
    -p RestrictAddressFamilies=AF_UNIX \
    -p ProtectSystem=strict \
    -p ProtectHome=yes \
    -p ProtectKernelTunables=yes \
    -p ProtectKernelModules=yes \
    -p ProtectKernelLogs=yes \
    -p ProtectControlGroups=yes \
    -p ProtectClock=yes \
    -p ProtectHostname=yes \
    -p LockPersonality=yes \
    -p RestrictSUIDSGID=yes \
    -p RestrictRealtime=yes \
    -p RestrictNamespaces=yes \
    -p SystemCallArchitectures=native \
    -p MemoryDenyWriteExecute=yes \
    -p Environment=LANVERIQ_SYNTHETIC_SYSTEMD_ONLY=yes \
    "$runtime/stage" "$phase" "$runtime/csr-helper"
}

start_phase create

# Nobody should be able to read/traverse a managed private DynamicUser
# directory, whether it is reached by the public symlink or real target.
[[ -d "$private_dir" ]]
[[ -s "$private_dir/bundle-one/device-p256-private.pem" ]]
if runuser -u nobody -- test -r "$private_dir/bundle-one/device-p256-private.pem"; then
  echo "FAIL: unrelated unprivileged user could read retained DynamicUser key" >&2
  exit 1
fi
if runuser -u nobody -- test -x "$private_dir"; then
  echo "FAIL: unrelated unprivileged user could traverse DynamicUser state" >&2
  exit 1
fi
# A completed one-shot is never an actively running Agent. Do not
# accidentally re-enter an installed LanveriqAgent unit.
if systemctl is-active --quiet lanveriq-agent.service; then
  echo "FAIL: production LanveriqAgent was unexpectedly running" >&2
  exit 1
fi

# --collect unloads transient units. Confirm it can be re-instantiated with
# the same name and the directory is retained without changing UID manually.
for _ in 1 2 3 4 5; do
  if [[ "$(systemctl show -p LoadState --value "${unit}.service" 2>/dev/null || :)" == "not-found" ]]; then
    break
  fi
  sleep 1
done
start_phase reopen

# Negative permissions after second independent service invocation too.
if runuser -u nobody -- test -r "$private_dir/bundle-one/device-p256-private.pem"; then
  echo "FAIL: unrelated user read key after systemd restart" >&2
  exit 1
fi
[[ -f "$private_dir/first-invocation-id" ]]
[[ -s "$private_dir/bundle-one/device-p256-private.pem" ]]
echo "PASS: same isolated transient systemd unit restarted, DynamicUser key persisted across distinct invocation IDs, other UID denied, offline IP socket blocked"
