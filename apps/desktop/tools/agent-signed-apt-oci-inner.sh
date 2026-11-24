#!/usr/bin/env bash
# L2B-23 only: run as root INSIDE networkless disposable pinned Ubuntu OCI.
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
test "$(cat /proc/1/comm)" != systemd
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /var/lib/private/lanveriq-agent
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
opts=(
  -oDir::Etc::sourcelist=/mnt/lanveriq-lab/lab.sources
  -oDir::Etc::sourceparts=-
  -oDir::Etc::trusted=/dev/null
  -oDir::Etc::trustedparts=-
  -oAcquire::Languages=none
  -oAcquire::AllowInsecureRepositories=false
  -oAPT::Get::AllowUnauthenticated=false
  -oAcquire::Check-Valid-Until=true
  -oAcquire::Check-Date=true
  -oAPT::Update::Error-Mode=any
  -oAPT::Sandbox::User=root
)
case "$LANVERIQ_L2B23_CASE" in
  signed-install-reinstall-remove)
    apt-get "${opts[@]}" update
    apt-get "${opts[@]}" --no-install-recommends -y install lanveriq-agent-offline-preview=0.1.0~dev1
    test "$(dpkg-query -W -f='${Status}' lanveriq-agent-offline-preview)" = 'install ok installed'
    test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev1'
    dpkg-query -S /usr/bin/lanveriq-agent | grep -Fx 'lanveriq-agent-offline-preview: /usr/bin/lanveriq-agent'
    dpkg-query -S /usr/lib/systemd/system/lanveriq-agent.service | grep -Fx 'lanveriq-agent-offline-preview: /usr/lib/systemd/system/lanveriq-agent.service'
    test -x /usr/bin/lanveriq-agent
    /usr/bin/lanveriq-agent --help | grep -F 'Usage:'
    test ! -e /etc/lanveriq-agent/agent.toml
    test ! -e /var/lib/private/lanveriq-agent
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    install -d -m 0700 /etc/lanveriq-agent /var/lib/private/lanveriq-agent
    printf '%s\n' 'synthetic-operator-config-no-secrets' > /etc/lanveriq-agent/agent.toml
    printf '%s\n' 'synthetic-operator-state-no-secrets' > /var/lib/private/lanveriq-agent/local-state
    chmod 0600 /etc/lanveriq-agent/agent.toml /var/lib/private/lanveriq-agent/local-state
    apt-get "${opts[@]}" --no-install-recommends --reinstall -y install lanveriq-agent-offline-preview=0.1.0~dev1
    test "$(cat /etc/lanveriq-agent/agent.toml)" = 'synthetic-operator-config-no-secrets'
    test "$(cat /var/lib/private/lanveriq-agent/local-state)" = 'synthetic-operator-state-no-secrets'
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    apt-get "${opts[@]}" -y remove lanveriq-agent-offline-preview
    test ! -e /usr/bin/lanveriq-agent
    test ! -e /usr/lib/systemd/system/lanveriq-agent.service
    test "$(cat /etc/lanveriq-agent/agent.toml)" = 'synthetic-operator-config-no-secrets'
    test "$(cat /var/lib/private/lanveriq-agent/local-state)" = 'synthetic-operator-state-no-secrets'
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    echo 'PASS: real signed-index APT installed/reinstalled/removed audited Rust Agent only in disposable OCI; no start/network'
    ;;
  signed-valid-old-release-acceptance)
    # L2B-27: prove actual apt-secure will accept a correctly signed Release
    # with an EARLIER Date while Valid-Until remains future. No installation.
    # Only the disposable container overlay is writable; signer key is RO.
    mkdir -p /tmp/lanveriq-apt-rollback
    cp -R /mnt/lanveriq-lab/repo /tmp/lanveriq-apt-rollback/repo
    sed 's#file:/mnt/lanveriq-lab/repo#file:/tmp/lanveriq-apt-rollback/repo#' \
      /mnt/lanveriq-lab/lab.sources > /tmp/lanveriq-apt-rollback/sources
    opts[0]=-oDir::Etc::sourcelist=/tmp/lanveriq-apt-rollback/sources
    apt-get "${opts[@]}" update >/tmp/lanveriq-apt-fresh-update.log 2>&1
    grep -Fq 'InRelease' /tmp/lanveriq-apt-fresh-update.log
    original=/tmp/lanveriq-apt-rollback/repo/dists/lab/InRelease
    fresh_epoch=$(date -ud "$(sed -n 's/^Date: //p' /mnt/lanveriq-lab/repo/dists/lab/Release)" +%s)
    stale_epoch=$(date -ud "$(sed -n 's/^Date: //p' /mnt/lanveriq-lab/stale-signed/Release)" +%s)
    test "$stale_epoch" -lt "$fresh_epoch"
    fresh_sha=$(sha256sum "$original" | cut -d' ' -f1)
    cp /mnt/lanveriq-lab/stale-signed/InRelease "$original"
    stale_sha=$(sha256sum "$original" | cut -d' ' -f1)
    test "$fresh_sha" != "$stale_sha"
    if ! apt-get "${opts[@]}" update >/tmp/lanveriq-apt-stale-update.log 2>&1; then
      # If a later APT release gains a monotonic rollback check, that is a
      # security improvement. This test must be reviewed rather than silently
      # claiming APT still accepts old metadata.
      cat /tmp/lanveriq-apt-stale-update.log >&2
      echo 'NOT_APPROVED: expected APT native signed stale Release behavior changed' >&2
      exit 96
    fi
    grep -Fq 'InRelease' /tmp/lanveriq-apt-stale-update.log
    test ! -e /usr/bin/lanveriq-agent
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    if dpkg-query -W lanveriq-agent-offline-preview >/dev/null 2>&1; then
      echo 'FAIL: Agent installed during read-only stale signature probe' >&2
      exit 97
    fi
    echo 'PASS: real apt accepted valid SIGNED older Release after newer; no high-water or authorized install; production ROLLBACK RISK OPEN'
    ;;
  wrong-key|wrong-signer|unsigned|index-tamper|expired|future)
    if apt-get "${opts[@]}" update > /tmp/apt-error.log 2>&1; then
      echo 'FAIL: APT accepted forbidden signed repository' >&2
      exit 91
    fi
    case "$LANVERIQ_L2B23_CASE" in
      wrong-key|wrong-signer) grep -Eiq 'NO_PUBKEY|signatures couldn.t be verified|not signed' /tmp/apt-error.log ;;
      unsigned) grep -Eiq 'not signed|does not have a Release file' /tmp/apt-error.log ;;
      index-tamper) grep -Eiq 'Hash Sum mismatch|unexpected size|SHA256' /tmp/apt-error.log ;;
      expired) grep -Eiq 'Release file.*expired|is expired' /tmp/apt-error.log ;;
      future) grep -Eiq 'not valid yet|invalid for another' /tmp/apt-error.log ;;
    esac
    if dpkg-query -W lanveriq-agent-offline-preview >/dev/null 2>&1; then
      echo 'FAIL: package installed during rejected index' >&2
      exit 92
    fi
    test ! -e /usr/bin/lanveriq-agent
    echo "PASS: actual APT refused hostile source: $LANVERIQ_L2B23_CASE"
    ;;
  deb-tamper|package-confusion)
    apt-get "${opts[@]}" update
    if apt-get "${opts[@]}" --no-install-recommends -y install lanveriq-agent-offline-preview=0.1.0~dev1 > /tmp/apt-error.log 2>&1; then
      echo 'FAIL: APT installed modified or substituted Agent' >&2
      exit 93
    fi
    if [ "$LANVERIQ_L2B23_CASE" = deb-tamper ]; then
      grep -Eiq 'Hash Sum mismatch|unexpected size|Size mismatch|SHA256' /tmp/apt-error.log
    else
      grep -Eiq 'Unable to locate package|Version .* was not found|has no installation candidate' /tmp/apt-error.log
    fi
    if dpkg-query -W lanveriq-agent-offline-preview >/dev/null 2>&1; then
      echo 'FAIL: package installed after APT rejection' >&2
      exit 94
    fi
    test ! -e /usr/bin/lanveriq-agent
    echo "PASS: actual APT refused hostile package: $LANVERIQ_L2B23_CASE"
    ;;
  *) echo 'FAIL: unknown APT OCI lab case' >&2; exit 95 ;;
esac
