#!/usr/bin/env bash
# G05-LNX2: build offline .deb candidate with no activation hooks.
# Never install/enable/start/deploy to a real host.
set -Eeuo pipefail
umask 077

if (( $# != 2 )); then
  echo 'FAIL: usage: build-offline-agent-deb.sh COMPILED_ELF EXISTING_OUTPUT_DIR' >&2
  exit 2
fi
root=$(cd -- "$(dirname -- "$0")/../../../.." && pwd -P)
bin=$1
out=$2
if [[ -L "$bin" || -L "$out" || ! -f "$bin" || ! -x "$bin" || ! -d "$out" ]]; then
  echo 'FAIL: input must be non-symlink executable and output existing non-symlink directory' >&2
  exit 2
fi
expected="$root/apps/agent/target/release/lanveriq-agent"
if [[ "$(realpath -e -- "$bin")" != "$(realpath -m -- "$expected")" ]]; then
  echo 'FAIL: only the repository Cargo release build may be packaged' >&2
  exit 2
fi
command -v dpkg-deb >/dev/null
command -v dpkg >/dev/null
magic=$(od -An -N4 -tx1 -- "$bin" | tr -d ' \n')
if [[ "$magic" != 7f454c46 ]]; then
  echo 'FAIL: Agent input must be an ELF executable' >&2
  exit 2
fi
arch=$(dpkg --print-architecture)
if [[ "$arch" != amd64 ]]; then
  echo 'FAIL: this unapproved Pop!_OS 24.04 candidate is amd64-only' >&2
  exit 2
fi
unit="$root/apps/agent/deploy/systemd/lanveriq-agent.service"
if [[ -L "$unit" || ! -f "$unit" ]]; then
  echo 'FAIL: service unit must be a regular tracked source template' >&2
  exit 2
fi
python3 "$root/apps/agent/tools/linux/verify-systemd-popos24.py" "$unit"
out=$(cd -- "$out" && pwd -P)
dest="$out/lanveriq-agent-offline-preview_0.1.0~dev1_$arch.deb"
if [[ -e "$dest" || -L "$dest" ]]; then
  echo 'FAIL: archive exists; never overwrite a prior package artifact' >&2
  exit 2
fi
temp=$(mktemp -d -t lanveriq-offline-deb.XXXXXXXX)
cleanup() { rm -rf --one-file-system -- "$temp"; }
trap cleanup EXIT
mkdir -p -- "$temp/pkg/DEBIAN" "$temp/pkg/usr/bin" "$temp/pkg/usr/lib/systemd/system"
chmod 0755 "$temp/pkg" "$temp/pkg/DEBIAN" "$temp/pkg/usr" "$temp/pkg/usr/bin" "$temp/pkg/usr/lib" "$temp/pkg/usr/lib/systemd" "$temp/pkg/usr/lib/systemd/system"
install -m 0755 -- "$bin" "$temp/pkg/usr/bin/lanveriq-agent"
install -m 0644 -- "$unit" "$temp/pkg/usr/lib/systemd/system/lanveriq-agent.service"
cat > "$temp/pkg/DEBIAN/control" <<EOF
Package: lanveriq-agent-offline-preview
Version: 0.1.0~dev1
Section: admin
Priority: optional
Architecture: $arch
Maintainer: Lanveriq maintainers
Description: Idle-only Linux Agent package candidate (NOT RELEASE APPROVED)
 No runtime configuration, activation hooks, device collection or networking.
 This is a nonproduction packaging validation candidate.
EOF
chmod 0644 "$temp/pkg/DEBIAN/control"
dpkg-deb --build --root-owner-group "$temp/pkg" "$dest" >/dev/null
echo "PASS: offline candidate staged; no install, postinst, service enable or start: $dest"
