#!/usr/bin/env bash
# G05-LNX4: REAL dpkg installation/removal of the actual compiled idle Agent,
# ONLY in a disposable networkless pinned Ubuntu OCI container.
#
# NEVER run dpkg on the host. NOT a distributable/signed Agent or Pop!_OS L3.
set -Eeuo pipefail
umask 077

die() { printf 'NOT_APPROVED: %s\n' "$*" >&2; exit 2; }

if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${LANVERIQ_DISPOSABLE_DPKG_L2:-}" != "1" ||
      "${RUNNER_OS:-}" != "Linux" ]]; then
  die 'disposable dpkg exercise requires explicit GitHub CI-only authorization'
fi
if (( EUID == 0 )); then
  die 'host-side invocation must use an unprivileged GitHub runner identity'
fi
if (( $# != 1 )); then
  die 'usage: verify-disposable-agent-dpkg-lifecycle.sh STRICTLY_AUDITED_AGENT_DEB'
fi

root=$(cd -- "$(dirname -- "$0")/../../../.." && pwd -P)
archive=$1
if [[ -L "$archive" || ! -f "$archive" ]]; then
  die 'a regular, non-symlink .deb is required'
fi
archive=$(realpath -e -- "$archive")
[[ "$archive" == *.deb ]] || die 'candidate path must end in .deb'
command -v docker >/dev/null || die 'Docker CI isolation is unavailable'

# The preceding SOURCE archive inspection rejects hooks, symlinks, unexpected
# paths, packages with config/state and service changes. No docker invocation
# is made if those safety preconditions fail.
python3 "$root/apps/agent/tools/linux/verify-offline-agent-deb.py" "$archive" ||
  die 'native .deb archive failed the existing strict allowlist audit'

# Existing, previously reviewed Ubuntu 24.04 OCI manifest digest.
# Pin by digest, never tag-only latest. The pull uses the runner's daemon;
# the ephemeral container itself has absolutely no network or host mounts
# except the single read-only package file below.
image='ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b'
docker pull "$image" >/dev/null

docker run --rm --interactive \
  --network none \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --pids-limit 96 \
  --memory 512m \
  --cpus 1 \
  --mount "type=bind,src=$archive,dst=/tmp/lanveriq-ci-only.deb,readonly" \
  --entrypoint /bin/bash \
  "$image" -seuo pipefail <<'CONTAINER'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive

# MUST NOT have a systemd manager: no service activation can be performed.
[[ "$(cat /proc/1/comm)" != 'systemd' ]]
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /var/lib/private/lanveriq-agent
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service

# Actual dpkg database transaction in the networkless disposable container.
dpkg --install /tmp/lanveriq-ci-only.deb
test "$(dpkg-query -W -f='${Status}' lanveriq-agent-offline-preview)" = 'install ok installed'
test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev1'
dpkg-query -S /usr/bin/lanveriq-agent | grep -Fx \
  'lanveriq-agent-offline-preview: /usr/bin/lanveriq-agent'
dpkg-query -S /usr/lib/systemd/system/lanveriq-agent.service | grep -Fx \
  'lanveriq-agent-offline-preview: /usr/lib/systemd/system/lanveriq-agent.service'
test -x /usr/bin/lanveriq-agent
test -f /usr/lib/systemd/system/lanveriq-agent.service
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /var/lib/private/lanveriq-agent
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
test ! -d /run/systemd/system

# Execute only --help; never run the service lifecycle with --config.
help_output=$(/usr/bin/lanveriq-agent --help)
grep -F 'Usage:' <<<"$help_output" >/dev/null
printf '%s\n' 'PASS: installed actual compiled Rust binary answers --help without Agent activation'

# Verify reinstall/removal leaves independently operator-owned data untouched.
install -d -m 0700 /etc/lanveriq-agent /var/lib/private/lanveriq-agent
printf '%s\n' 'synthetic-owned-operator-config-no-secrets' > /etc/lanveriq-agent/agent.toml
printf '%s\n' 'synthetic-owned-operator-state-no-secrets' > /var/lib/private/lanveriq-agent/synthetic-state
chmod 0600 /etc/lanveriq-agent/agent.toml /var/lib/private/lanveriq-agent/synthetic-state

# Same-version reinstall proves dpkg transaction stability, NOT an actual
# higher-version migration/upgrade or rollback.
dpkg --install /tmp/lanveriq-ci-only.deb
test "$(cat /etc/lanveriq-agent/agent.toml)" = 'synthetic-owned-operator-config-no-secrets'
test "$(cat /var/lib/private/lanveriq-agent/synthetic-state)" = 'synthetic-owned-operator-state-no-secrets'
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service

dpkg --remove lanveriq-agent-offline-preview
test ! -e /usr/bin/lanveriq-agent
test ! -e /usr/lib/systemd/system/lanveriq-agent.service
test "$(cat /etc/lanveriq-agent/agent.toml)" = 'synthetic-owned-operator-config-no-secrets'
test "$(cat /var/lib/private/lanveriq-agent/synthetic-state)" = 'synthetic-owned-operator-state-no-secrets'
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
test ! -d /run/systemd/system

printf '%s\n' 'PASS: real disposable dpkg install/reinstall/remove; idle binary help and operator config/state retention; NO systemd, network, approved signer or actual Pop!_OS device'
CONTAINER
