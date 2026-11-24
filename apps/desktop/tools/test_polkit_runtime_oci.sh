#!/usr/bin/env bash
# L2B-24: actual polkitd + system DBus + pkcheck; no host policy changes.
# Executed only in a disposable OCI with --network none.
set -Eeuo pipefail
test "${EUID}" -eq 0
test "$(cat /proc/1/comm)" != systemd
test ! -e /usr/bin/lanveriq-agent
test ! -d /run/systemd/system
test "${LANVERIQ_POLKIT_RUNTIME_L2:-}" = disposable-oci-only

source_policy=/mnt/lanveriq-policy/org.lanveriq.agent.package.policy
test -f "$source_policy"
test ! -L "$source_policy"
test -r "$source_policy"

# Only inside container overlay: Polkit requires action XML in its system path.
# Not a release install and NOT executed on runner/Pop!_OS host.
install -m 0644 "$source_policy" /usr/share/polkit-1/actions/org.lanveriq.agent.package.policy
mkdir -p /run/dbus
dbus-daemon --system --fork --nopidfile
# Native polkitd activates over private CONTAINER system bus.
pkaction --action-id org.lanveriq.agent.package.install --verbose >/tmp/polkit-inspected.txt
grep -F 'implicit any:' /tmp/polkit-inspected.txt | grep -E ':[[:space:]]*no[[:space:]]*$'
grep -F 'implicit inactive:' /tmp/polkit-inspected.txt | grep -E ':[[:space:]]*no[[:space:]]*$'
grep -F 'implicit active:' /tmp/polkit-inspected.txt | grep -E ':[[:space:]]*no[[:space:]]*$'

for op in install upgrade remove; do
    id="org.lanveriq.agent.package.$op"
    pkaction --action-id "$id" --verbose >"/tmp/polkit-$op.info"
    grep -F 'implicit any:' "/tmp/polkit-$op.info" | grep -Eq ':[[:space:]]*no[[:space:]]*$'
    grep -F 'implicit inactive:' "/tmp/polkit-$op.info" | grep -Eq ':[[:space:]]*no[[:space:]]*$'
    grep -F 'implicit active:' "/tmp/polkit-$op.info" | grep -Eq ':[[:space:]]*no[[:space:]]*$'
    for user in nobody daemon; do
        # Real OS caller UID+PID+kernel process start time; no spoofed JS fields.
        # pkcheck exits 1 for explicit denial. Exit 127 (daemon error)
        # is NOT an accepted denial and fails the job.
        runuser -u "$user" -- /bin/bash -c '
            set -Eeuo pipefail
            id="$1"
            start=$(awk "{print \$22}" "/proc/$$/stat")
            subject="$$,$start,$(id -u)"
            set +e
            pkcheck --action-id "$id" --process "$subject" > /tmp/ignore-output-unsafe 2>/dev/null
            status=$?
            set -e
            if [ "$status" -ne 1 ]; then
                echo "FAIL: polkitd did not explicitly deny $id for UID $(id -u); exit=$status" >&2
                exit 86
            fi
        ' -- "$id"
        printf 'PASS: native polkitd denied %s to %s\n' "$id" "$user"
    done
done

# Polkit never sees an action granting a generic install process, service or shell.
for suffix in start enable network collection remote-control exec; do
    if pkaction --action-id "org.lanveriq.agent.package.$suffix" >/dev/null 2>&1; then
        echo "FAIL: unexpected native polkit action $suffix" >&2
        exit 88
    fi
done
test ! -e /usr/bin/lanveriq-agent
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
echo 'PASS: real container polkitd/DBus policy loaded 3 actions; 6 explicit UID-bound denials, no install or network'
