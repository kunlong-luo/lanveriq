#!/usr/bin/env bash
# L2B-25 real D-Bus peer UID/PID and polkitd, disposable OCI only.
# Review-only service: NEVER APT/dpkg/systemctl.
set -Eeuo pipefail
test "${EUID}" -eq 0
test "${LANVERIQ_POLKIT_RUNTIME_L2:-}" = disposable-oci-only
test "$(cat /proc/1/comm)" != systemd
test ! -e /usr/bin/lanveriq-agent
test ! -e /run/systemd/system
test -r /mnt/lanveriq-policy/org.lanveriq.agent.package.policy
test -r /mnt/lanveriq-broker-source/polkit-oci-dbus-broker-lab.py
test -r /mnt/lanveriq-broker-source/polkit-oci-dbus-review.conf

# These files are installed ONLY within disposable OCI overlay.
install -m 0644 /mnt/lanveriq-policy/org.lanveriq.agent.package.policy \
    /usr/share/polkit-1/actions/org.lanveriq.agent.package.policy
install -m 0644 /mnt/lanveriq-broker-source/polkit-oci-dbus-review.conf \
    /etc/dbus-1/system.d/org.lanveriq.Agent.ReviewLab.conf
mkdir -p /run/dbus
dbus-daemon --system --fork --nopidfile
pkaction --action-id org.lanveriq.agent.package.install --verbose |
    grep -E 'implicit active:[[:space:]]*no[[:space:]]*$'

/usr/bin/python3 /mnt/lanveriq-broker-source/polkit-oci-dbus-broker-lab.py \
    > /tmp/lanveriq-review-service.log 2>&1 &
broker_pid=$!
cleanup() {
    status=$?
    if [ "$status" -ne 0 ]; then
        echo "FAIL: review-only OCI D-Bus lab exit=$status" >&2
        tail -n 100 /tmp/lanveriq-review-service.log >&2 || :
    fi
    kill "$broker_pid" 2>/dev/null || :
    wait "$broker_pid" 2>/dev/null || :
}
trap cleanup EXIT
ready=0
for _ in $(seq 1 40); do
    if grep -Fq REVIEW_ONLY_DBUS_SERVICE_READY /tmp/lanveriq-review-service.log; then
        ready=1
        break
    fi
    if ! kill -0 "$broker_pid" 2>/dev/null; then
        cat /tmp/lanveriq-review-service.log >&2
        echo 'FAIL: D-Bus service exited before registration' >&2
        exit 80
    fi
    sleep 0.1
done
test "$ready" -eq 1

name=org.lanveriq.Agent.ReviewLab
path=/org/lanveriq/Agent/ReviewLab
method="$name.Review"
call_as() {
    local username=$1
    local operation=$2
    local nonce=$3
    shift 3
    # GVariant textual parser interprets all-digit 32hex as Int32, NOT a
    # D-Bus wire string. The explicit quoted literal proves genuine ss API.
    runuser -u "$username" -- gdbus call --system \
        --dest "$name" --object-path "$path" \
        --method "$method" "'$operation'" "'$nonce'" "$@"
}

nonce_i=1
for op in install upgrade remove; do
    for username in nobody daemon; do
        nonce=$(printf '%032x' "$nonce_i")
        reply=$(call_as "$username" "$op" "$nonce")
        grep -Fq "'POLKIT_DENIED'" <<<"$reply" || {
            printf 'FAIL: D-Bus service did not consult real Polkit for %s %s: %s\n' \
                "$username" "$op" "$reply" >&2
            cat /tmp/lanveriq-review-service.log >&2
            exit 81
        }
        printf 'PASS: root-side D-Bus peer identity -> Polkit DENIED %s as %s\n' \
            "$op" "$username"
        nonce_i=$((nonce_i + 1))
    done
done

# Reuse same request ID by a DIFFERENT gdbus process, same real Unix UID.
replayed=$(call_as nobody remove "$(printf '%032x' 1)")
grep -Fq "'REPLAY_DENIED'" <<<"$replayed"
# Cross-UID scoping: daemon may use a nonce previously used by nobody.
scoped=$(call_as daemon install "$(printf '%032x' 1)")
grep -Fq "'POLKIT_DENIED'" <<<"$scoped"
echo 'PASS: nonce replay rejected for same Unix UID, distinct Unix UID independently checked'
# Diagnose synthetic rejected argument decoding only; no user data in this OCI.
set -x

invalid=$(call_as nobody start "$(printf '%032x' 10)")
grep -Fq "'INVALID_OPERATION'" <<<"$invalid"
invalid=$(call_as nobody 'install; /bin/sh' "$(printf '%032x' 11)")
grep -Fq "'INVALID_OPERATION'" <<<"$invalid"
invalid=$(call_as nobody install '../../system/control')
grep -Fq "'INVALID_NONCE'" <<<"$invalid"
invalid=$(call_as nobody install 'AABBCCDD')
grep -Fq "'INVALID_NONCE'" <<<"$invalid"

# Deliberately do not quote all-numeric GVariant literal, to ensure
# int32/wire-type confusion is NOT silently accepted as a valid nonce.
numeric=$(runuser -u nobody -- gdbus call --system --dest "$name" \
    --object-path "$path" --method "$method" install "$(printf '%032x' 17)")
grep -Fq "'INVALID_NONCE'" <<<"$numeric"

# Method signature is exactly ss, so no caller-supplied UID/PID/URL/path.
set +e
extra=$(call_as nobody install "$(printf '%032x' 15)" '0' 2>&1)
extra_status=$?
set -e
test "$extra_status" -ne 0
grep -Eiq 'InvalidArgs|Invalid arguments|signature|too many arguments|expects' <<<"$extra"

set +x
# Transport-supplied identity cannot be forged by caller environment.
env_result=$(runuser -u nobody -- env PKEXEC_UID=0 \
    LANVERIQ_RELEASE_APPROVED=true \
    gdbus call --system --dest "$name" --object-path "$path" \
    --method "$method" upgrade "'$(printf '%032x' 16)'")
grep -Fq "'POLKIT_DENIED'" <<<"$env_result"

count=$(grep -Fc 'OS_PEER_VERIFIED operation=' /tmp/lanveriq-review-service.log)
test "$count" -eq 8  # 6 + scoped other UID + forged environment
test "$(grep -Fc 'OS_PEER_REPLAY_REJECTED' /tmp/lanveriq-review-service.log)" -eq 1
test ! -e /usr/bin/lanveriq-agent
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
test ! -e /run/systemd/system
echo 'PASS: real system DBus peer UID/PID/start + native Polkit 8 denials, replay/argv/env refused; no privileged transaction'
