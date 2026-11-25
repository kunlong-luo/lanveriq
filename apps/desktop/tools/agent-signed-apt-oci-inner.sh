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
  signed-two-versions-unattended-downgrade-refused|signed-two-versions-explicit-oci-downgrade|signed-distinct-elf-unattended-downgrade-refused|signed-distinct-elf-explicit-oci-downgrade)
    # L2B-30 and L2B-31: two actual native dpkg versions signed by the
    # same throwaway signer. The L2B-31 pair contains truly different ELF
    # bytes from pinned distinct Git source revisions. All package changes
    # occur strictly inside the disposable networkless OCI overlay.
    source_distinct=false
    case "$LANVERIQ_L2B23_CASE" in
      signed-distinct-elf-*) source_distinct=true ;;
    esac
    apt-get "${opts[@]}" update
    apt-cache "${opts[@]}" policy lanveriq-agent-offline-preview |
      grep -F '0.1.0~dev0' > /dev/null
    apt-cache "${opts[@]}" policy lanveriq-agent-offline-preview |
      grep -F '0.1.0~dev1' > /dev/null
    apt-get "${opts[@]}" --no-install-recommends -y install lanveriq-agent-offline-preview=0.1.0~dev1
    test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev1'
    test -x /usr/bin/lanveriq-agent
    /usr/bin/lanveriq-agent --help | grep -F 'Usage:' > /dev/null
    if [ "$source_distinct" = true ]; then
      latest_hash="$(cat /mnt/lanveriq-lab/latest-elf.sha256)"
      historical_hash="$(cat /mnt/lanveriq-lab/historical-elf.sha256)"
      test "${#latest_hash}" = 64
      test "${#historical_hash}" = 64
      test "$latest_hash" != "$historical_hash"
      test "$(sha256sum /usr/bin/lanveriq-agent | cut -d' ' -f1)" = "$latest_hash"
      echo 'STAGE: L2B-31 new Cargo ELF verified by installed-file SHA256'
    fi
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    install -d -m 0700 /etc/lanveriq-agent /var/lib/private/lanveriq-agent
    printf '%s\n' 'synthetic-l2b30-operator-config' > /etc/lanveriq-agent/agent.toml
    printf '%s\n' 'synthetic-l2b30-private-state' > /var/lib/private/lanveriq-agent/state
    chmod 0600 /etc/lanveriq-agent/agent.toml /var/lib/private/lanveriq-agent/state
    if [ "$LANVERIQ_L2B23_CASE" = signed-two-versions-unattended-downgrade-refused ] ||
       [ "$LANVERIQ_L2B23_CASE" = signed-distinct-elf-unattended-downgrade-refused ]; then
      if apt-get "${opts[@]}" --no-install-recommends -y install \
          lanveriq-agent-offline-preview=0.1.0~dev0 > /tmp/l2b30-refusal.log 2>&1; then
        echo 'FAIL: unattended APT downgraded without explicit override' >&2
        exit 121
      fi
      grep -Eiq 'allow-downgrades|downgrad' /tmp/l2b30-refusal.log || {
        cat /tmp/l2b30-refusal.log >&2
        echo 'NOT_APPROVED: APT refusal not caused by downgrade protection' >&2
        exit 122
      }
      test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev1'
      if [ "$source_distinct" = true ]; then
        test "$(sha256sum /usr/bin/lanveriq-agent | cut -d' ' -f1)" = "$latest_hash"
      fi
      echo 'STAGE: native unattended APT refused validly signed older dpkg version'
    else
      # Deliberate OCI attack/exception demonstration, never product workflow.
      # Allow-downgrades proves system APT is NOT independent trusted highwater.
      apt-get "${opts[@]}" --no-install-recommends -y --allow-downgrades install \
        lanveriq-agent-offline-preview=0.1.0~dev0
      test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev0'
      if [ "$source_distinct" = true ]; then
        test "$(sha256sum /usr/bin/lanveriq-agent | cut -d' ' -f1)" = "$historical_hash"
        /usr/bin/lanveriq-agent --help | grep -F 'Usage:' > /dev/null
        echo 'STAGE: L2B-31 actually installed DIFFERENT old Cargo ELF inside OCI'
      fi
      echo 'STAGE: actual dpkg installed-version DOWNGRADE inside OCI only'
      apt-get "${opts[@]}" --no-install-recommends -y install lanveriq-agent-offline-preview=0.1.0~dev1
      test "$(dpkg-query -W -f='${Version}' lanveriq-agent-offline-preview)" = '0.1.0~dev1'
      if [ "$source_distinct" = true ]; then
        test "$(sha256sum /usr/bin/lanveriq-agent | cut -d' ' -f1)" = "$latest_hash"
        echo 'STAGE: L2B-31 restored new Cargo ELF after actual APT upgrade'
      fi
    fi
    test "$(cat /etc/lanveriq-agent/agent.toml)" = synthetic-l2b30-operator-config
    test "$(cat /var/lib/private/lanveriq-agent/state)" = synthetic-l2b30-private-state
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    apt-get "${opts[@]}" -y remove lanveriq-agent-offline-preview
    test ! -e /usr/bin/lanveriq-agent
    test ! -e /usr/lib/systemd/system/lanveriq-agent.service
    test "$(cat /etc/lanveriq-agent/agent.toml)" = synthetic-l2b30-operator-config
    test "$(cat /var/lib/private/lanveriq-agent/state)" = synthetic-l2b30-private-state
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    echo "PASS: native two-version APT $LANVERIQ_L2B23_CASE; idle, state preserved, no boot enable"
    ;;
  signed-old-different-packages-retained)
    # L2B-29: both indexes are signed by the SAME throwaway publisher,
    # but their Packages bytes/description and signed Release hashes differ.
    # Check the real APT cache retains the newer Packages, not simply that it
    # accepts old InRelease signatures; this is NOT external highwater.
    work=/tmp/lanveriq-apt-index-rollback
    mkdir -p "$work"
    cp -R /mnt/lanveriq-lab/repo "$work/repo"
    sed "s#file:/mnt/lanveriq-lab/repo#file:$work/repo#" \
      /mnt/lanveriq-lab/lab.sources > "$work/lab.sources"
    opts[0]=-oDir::Etc::sourcelist="$work/lab.sources"
    if ! apt-get "${opts[@]}" update > "$work/fresh-apt.log" 2>&1; then
      cat "$work/fresh-apt.log" >&2
      echo 'NOT_APPROVED: original genuine APT index update failed' >&2
      exit 101
    fi
    grep -Fq 'InRelease' "$work/fresh-apt.log"
    apt-cache "${opts[@]}" show lanveriq-agent-offline-preview \
      | grep -Fx 'Description: audited idle Agent, disposable signing test ONLY'
    echo 'STAGE: first native APT update and fresh Packages description observed'
    release_new=/mnt/lanveriq-lab/repo/dists/lab/Release
    release_old=/mnt/lanveriq-lab/stale-signed/Release
    test "$(date -ud "$(sed -n 's/^Date: //p' "$release_old")" +%s)" \
      -lt "$(date -ud "$(sed -n 's/^Date: //p' "$release_new")" +%s)" || {
      echo 'NOT_APPROVED: stale index Date was not earlier than fresh Date' >&2
      exit 104
    }
    echo 'STAGE: older Release Date confirmed'
    fresh_index="$work/repo/dists/lab/main/binary-amd64/Packages"
    stale_index=/mnt/lanveriq-lab/stale-signed/Packages
    test -s "$stale_index" || {
      echo 'NOT_APPROVED: old Packages bytes missing' >&2
      exit 105
    }
    fresh_digest="$(sha256sum "$fresh_index" | cut -d' ' -f1)"
    stale_digest="$(sha256sum "$stale_index" | cut -d' ' -f1)"
    test "$fresh_digest" != "$stale_digest" || {
      echo 'NOT_APPROVED: older Packages was not different' >&2
      exit 106
    }
    echo 'STAGE: two different signed package-index hashes confirmed'
    # Native Signed-By verifies old InRelease; its SHA256 authenticates the
    # old Packages. No --allow-downgrades, no unchecked repositories.
    cp "$stale_index" "$fresh_index"
    cp /mnt/lanveriq-lab/stale-signed/InRelease "$work/repo/dists/lab/InRelease"
    echo 'STAGE: old signed Release and different Packages swapped in disposable OCI'
    if ! apt-get "${opts[@]}" update > "$work/old-apt.log" 2>&1; then
      cat "$work/old-apt.log" >&2
      echo 'NOT_APPROVED: genuine APT did not accept older signed Packages' >&2
      exit 102
    fi
    grep -Fq 'InRelease' "$work/old-apt.log" || {
      echo 'NOT_APPROVED: native apt update had no old signed InRelease fetch' >&2
      exit 107
    }
    echo 'STAGE: second native apt update accepted old signed InRelease'
    # APT can validate InRelease without refetching a changed Packages list.
    # Diagnose that distinction rather than treating update exit 0 as proof
    # of the old index being adopted by APT's native package cache.
    if grep -Eq '^Get:[0-9]+ .*Packages' "$work/old-apt.log"; then
      echo 'NOT_APPROVED: real APT unexpectedly fetched older different Packages' >&2
      exit 111
    fi
    echo 'STAGE: second native APT update did not download older Packages'
    apt_metadata="$(apt-cache "${opts[@]}" show lanveriq-agent-offline-preview)" || {
      echo 'NOT_APPROVED: native apt-cache could not read any package metadata' >&2
      exit 109
    }
    if printf '%s\n' "$apt_metadata" |
         grep -Fxq 'Description: audited idle Agent, historical signed index ONLY'; then
      echo 'NOT_APPROVED: older different Packages was adopted; rollback-risk review required' >&2
      exit 112
    elif printf '%s\n' "$apt_metadata" |
         grep -Fxq 'Description: audited idle Agent, disposable signing test ONLY'; then
      echo 'STAGE: native APT retained fresh Packages after old signed InRelease'
    else
      echo 'NOT_APPROVED: native apt-cache has unexpected/missing package description' >&2
      exit 110
    fi
    test ! -e /usr/bin/lanveriq-agent
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    if dpkg-query -W lanveriq-agent-offline-preview >/dev/null 2>&1; then
      echo 'FAIL: replay index test accidentally installed an Agent package' >&2
      exit 103
    fi
    echo 'PASS: native APT accepted older valid signed InRelease but retained newer DIFFERENT Packages; no independent persistent high-water'
    ;;
  signed-witness-valid-old-release-refused|signed-witness-old-seal-and-forged-signatures-refused)
    # L2B-32 CI-only: gpgv verifies a different signer from APT. This fixture
    # pins sequence=2 and the exact InRelease hash but is NOT external custody.
    work=/tmp/lanveriq-witness-l2
    mkdir -p "$work"
    cp -R /mnt/lanveriq-lab/repo "$work/repo"
    sed "s#file:/mnt/lanveriq-lab/repo#file:$work/repo#" \
      /mnt/lanveriq-lab/lab.sources > "$work/lab.sources"
    opts[0]=-oDir::Etc::sourcelist="$work/lab.sources"
    verifier=/mnt/lanveriq-lab/verify-witness.sh
    accepted=/mnt/lanveriq-lab/witness/approved.manifest
    approved_sig=/mnt/lanveriq-lab/witness/approved.sig
    target="$work/repo/dists/lab/InRelease"
    apt-get "${opts[@]}" update > "$work/fresh.log" 2>&1 || {
      cat "$work/fresh.log" >&2
      echo 'NOT_APPROVED: native APT did not accept original signed source' >&2
      exit 131
    }
    grep -F 'InRelease' "$work/fresh.log" > /dev/null
    bash "$verifier" "$accepted" "$approved_sig" "$target" > /dev/null || {
      echo 'NOT_APPROVED: independent signer rejected original signed APT source' >&2
      exit 132
    }
    echo 'STAGE: native apt-secure and different witness signer accepted fresh signed Release'
    if [ "$LANVERIQ_L2B23_CASE" = signed-witness-valid-old-release-refused ]; then
      old_release=/mnt/lanveriq-lab/stale-signed/InRelease
      test "$(sha256sum "$target" | cut -d' ' -f1)" != "$(sha256sum "$old_release" | cut -d' ' -f1)"
      new_epoch="$(date -ud "$(sed -n 's/^Date: //p' /mnt/lanveriq-lab/repo/dists/lab/Release)" +%s)"
      old_epoch="$(date -ud "$(sed -n 's/^Date: //p' /mnt/lanveriq-lab/stale-signed/Release)" +%s)"
      test "$old_epoch" -lt "$new_epoch"
      cp "$old_release" "$target"
      apt-get "${opts[@]}" update > "$work/stale.log" 2>&1 || {
        cat "$work/stale.log" >&2
        echo 'NOT_APPROVED: native APT valid signed old Release behavior changed' >&2
        exit 133
      }
      grep -F 'InRelease' "$work/stale.log" > /dev/null
      if refusal="$(bash "$verifier" "$accepted" "$approved_sig" "$target" 2>&1)"; then
        echo 'FAIL: witness accepted an older valid signed APT Release' >&2
        exit 134
      fi
      if [[ "$refusal" != *'NOT_APPROVED: valid APT signature, but independent witness Release digest mismatch'* ]]; then
        printf '%s\n' "$refusal" >&2
        echo 'NOT_APPROVED: signed old Release rejected for wrong reason' >&2
        exit 135
      fi
      echo 'STAGE: native APT accepted older valid signed InRelease, witness SHA256 separately refused'
    else
      old_manifest=/mnt/lanveriq-lab/witness/old.manifest
      old_sig=/mnt/lanveriq-lab/witness/old.sig
      old_release=/mnt/lanveriq-lab/stale-signed/InRelease
      gpgv --keyring /mnt/lanveriq-lab/keyrings/witness.gpg \
        "$old_sig" "$old_manifest" > /dev/null 2>&1 || {
        echo 'NOT_APPROVED: valid historical witness signature failed native gpgv' >&2
        exit 136
      }
      if refusal="$(bash "$verifier" "$old_manifest" "$old_sig" "$old_release" 2>&1)"; then
        echo 'FAIL: correctly signed older witness sequence was accepted' >&2
        exit 137
      fi
      if [[ "$refusal" != *'NOT_APPROVED: signed witness sequence below pinned synthetic high-water'* ]]; then
        echo 'NOT_APPROVED: signed older witness rejected for wrong reason' >&2
        exit 138
      fi
      if refusal="$(bash "$verifier" "$accepted" /mnt/lanveriq-lab/witness/forged-publisher.sig "$target" 2>&1)"; then
        echo 'FAIL: APT publisher key impersonated independent witness' >&2
        exit 139
      fi
      if [[ "$refusal" != *'NOT_APPROVED: independent witness GPG signature invalid'* ]]; then
        echo 'NOT_APPROVED: publisher-forged witness rejected for wrong reason' >&2
        exit 140
      fi
      if refusal="$(bash "$verifier" /mnt/lanveriq-lab/witness/tampered.manifest "$approved_sig" "$target" 2>&1)"; then
        echo 'FAIL: tampered witness verified' >&2
        exit 141
      fi
      if [[ "$refusal" != *'NOT_APPROVED: independent witness GPG signature invalid'* ]]; then
        echo 'NOT_APPROVED: tampered witness rejected for wrong reason' >&2
        exit 142
      fi
      echo 'STAGE: correctly signed older sequence, publisher forgery and modified witness all DENIED'
    fi
    test ! -e /usr/bin/lanveriq-agent
    test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
    test ! -d /run/systemd/system
    if dpkg-query -W lanveriq-agent-offline-preview > /dev/null 2>&1; then
      echo 'FAIL: witness test installed the Agent' >&2
      exit 143
    fi
    echo 'PASS: separate CI gpgv witness rejects APT valid replay and bad seals; no independent custody or release approval'
    ;;
  signed-valid-old-release-acceptance)
    # L2B-27: prove actual apt-secure will accept a correctly signed Release
    # with an EARLIER Date while Valid-Until remains future. No installation.
    # Only the disposable container overlay is writable; signer key is RO.
    mkdir -p /tmp/lanveriq-apt-rollback
    cp -R /mnt/lanveriq-lab/repo /tmp/lanveriq-apt-rollback/repo
    sed 's#file:/mnt/lanveriq-lab/repo#file:/tmp/lanveriq-apt-rollback/repo#' \
      /mnt/lanveriq-lab/lab.sources > /tmp/lanveriq-apt-rollback/lab.sources
    opts[0]=-oDir::Etc::sourcelist=/tmp/lanveriq-apt-rollback/lab.sources
    if ! apt-get "${opts[@]}" update >/tmp/lanveriq-apt-fresh-update.log 2>&1; then
      cat /tmp/lanveriq-apt-fresh-update.log >&2
      echo 'NOT_APPROVED: first native APT snapshot validation failed, no replay verdict' >&2
      exit 98
    fi
    if ! grep -Fq 'InRelease' /tmp/lanveriq-apt-fresh-update.log; then
      cat /tmp/lanveriq-apt-fresh-update.log >&2
      echo 'NOT_APPROVED: APT did not fetch the signed initial Release' >&2
      exit 99
    fi
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
