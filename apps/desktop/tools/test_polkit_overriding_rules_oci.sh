#!/usr/bin/env bash
# L2B-26: malicious local Polkit YES from real polkitd MUST NOT trigger an
# unapproved Agent install in the disposable root-side D-Bus ReviewLab.
# This dangerous rules override lives ONLY inside a CI-only networkless OCI.
set -Eeuo pipefail
umask 077
die() { echo "NOT_APPROVED: $*" >&2; exit 90; }
test "$(id -u)" -eq 0 || die 'not OCI root'
test -f /.dockerenv || die 'Docker-only'
test "$(cat /proc/1/comm)" != systemd || die 'host systemd forbidden'
test "${LANVERIQ_POLKIT_RUNTIME_L2:-}" = disposable-oci-only || die 'CI scope missing'
test "${LANVERIQ_POLKIT_OVERRIDE_L2:-}" = ephemeral-only || die 'attack opt-in missing'
test ! -e /usr/bin/lanveriq-agent || die 'Agent installed in test target'
test ! -d /run/systemd/system || die 'running systemd'
test -r /mnt/lanveriq-policy/org.lanveriq.agent.package.policy || die 'missing readonly policy'
test -r /mnt/lanveriq-broker-source/polkit-oci-dbus-broker-lab.py || die 'missing readonly service'
test -r /mnt/lanveriq-broker-source/polkit-oci-dbus-review.conf || die 'missing readonly D-Bus config'
command -v gdbus >/dev/null
command -v pkcheck >/dev/null

# Everything below is written only to disposable OCI overlay, never host.
install -m 0644 /mnt/lanveriq-policy/org.lanveriq.agent.package.policy \
  /usr/share/polkit-1/actions/org.lanveriq.agent.package.policy
install -m 0644 /mnt/lanveriq-broker-source/polkit-oci-dbus-review.conf \
  /etc/dbus-1/system.d/org.lanveriq.Agent.ReviewLab.conf
install -d -m 0755 /etc/polkit-1/rules.d
cat > /etc/polkit-1/rules.d/00-lanveriq-malicious-ci-only.rules <<'RULE'
/* DO NOT DISTRIBUTE: disposable isolated CI attacker-injected Polkit rule */
polkit.addRule(function(action, subject) {
  if (action.id === "org.lanveriq.agent.package.install" &&
      subject.user === "nobody") {
    return polkit.Result.YES;
  }
});
RULE
chmod 0644 /etc/polkit-1/rules.d/00-lanveriq-malicious-ci-only.rules
mkdir -p /run/dbus
dbus-daemon --system --fork --nopidfile

# pkaction displays XML defaults, NOT effective JavaScript rule permission.
pkaction --action-id org.lanveriq.agent.package.install --verbose >/tmp/l2b26-action.txt
for property in any inactive active; do
  grep -E "implicit $property:[[:space:]]*no[[:space:]]*$" /tmp/l2b26-action.txt >/dev/null
done

# Direct genuine OS polkitd YES, not mocked subprocess.returncode=0.
runuser -u nobody -- /bin/bash -c '
  set -Eeuo pipefail
  start=$(awk "{print \$22}" "/proc/$$/stat")
  test "$start" -gt 0
  pkcheck --action-id org.lanveriq.agent.package.install \
      --process "$$,$start,$(id -u)" </dev/null >/dev/null
'
echo 'PASS: real polkitd returned YES despite static XML all-no'

for entry in 'nobody upgrade' 'nobody remove' 'daemon install'; do
  read -r user action <<< "$entry"
  set +e
  runuser -u "$user" -- /bin/bash -c '
    start=$(awk "{print \$22}" "/proc/$$/stat")
    pkcheck --action-id "org.lanveriq.agent.package.$1" \
      --process "$$,$start,$(id -u)" </dev/null >/dev/null 2>&1
  ' -- "$action"
  code=$?
  set -e
  test "$code" -eq 1 || die "incorrect native Polkit scope for $entry: exit $code"
done
echo 'PASS: OS override limited to nobody/install'

# Real D-Bus name ownership may only be acquired by the ReviewLab's root
# process; a non-root client cannot impersonate it even before startup.
set +e
takeover=$(runuser -u nobody -- dbus-send --system --print-reply \
    --dest=org.freedesktop.DBus /org/freedesktop/DBus \
    org.freedesktop.DBus.RequestName \
    string:org.lanveriq.Agent.ReviewLab uint32:0 2>&1)
status=$?
set -e
test "$status" -ne 0 || die 'non-root acquired root ReviewLab name'
grep -Eiq 'AccessDenied|not allowed to own|permission denied' <<< "$takeover" ||
  die 'system bus denied name without a permission error'
echo 'PASS: real D-Bus denied unprivileged service-name takeover'

/usr/bin/python3 /mnt/lanveriq-broker-source/polkit-oci-dbus-broker-lab.py \
    > /tmp/l2b26-service.log 2>&1 &
broker_pid=$!
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "FAIL: L2B26 real OS policy override test returned $status" >&2
    tail -n 100 /tmp/l2b26-service.log >&2 || :
  fi
  kill "$broker_pid" 2>/dev/null || :
  wait "$broker_pid" 2>/dev/null || :
}
trap cleanup EXIT
ready=0
for _ in $(seq 1 40); do
  if grep -Fq REVIEW_ONLY_DBUS_SERVICE_READY /tmp/l2b26-service.log; then
    ready=1
    break
  fi
  kill -0 "$broker_pid" 2>/dev/null || die 'root service exited before ready'
  sleep 0.1
done
test "$ready" -eq 1 || die 'root service did not acquire bus name'

name=org.lanveriq.Agent.ReviewLab
path=/org/lanveriq/Agent/ReviewLab
call() {
  local user=$1 action=$2 nonce=$3
  runuser -u "$user" -- gdbus call --system --dest "$name" \
    --object-path "$path" --method "$name.Review" \
    "'$action'" "'$nonce'"
}
yes_nonce=22222222222222222222222222222222
reply=$(call nobody install "$yes_nonce")
grep -Fq "'NOT_APPROVED'" <<< "$reply" ||
  die "real Polkit YES bypassed root-side missing-signer deny: $reply"
grep -Fq 'OS_PEER_POLICY_UNVERIFIED operation=install uid=65534' \
  /tmp/l2b26-service.log ||
  die 'root-side service did not observe non-denied OS Polkit check'
echo 'PASS: root-side real bus peer + real Polkit YES remains NOT_APPROVED'

replay=$(call nobody install "$yes_nonce")
grep -Fq "'REPLAY_DENIED'" <<< "$replay" || die 'YES nonce replay accepted'
for entry in 'nobody upgrade 33333333333333333333333333333333' \
             'nobody remove 44444444444444444444444444444444' \
             'daemon install 55555555555555555555555555555555'; do
  read -r user action nonce <<< "$entry"
  reply=$(call "$user" "$action" "$nonce")
  grep -Fq "'POLKIT_DENIED'" <<< "$reply" || die "$entry did not stay denied: $reply"
done
test "$(grep -Fc 'OS_PEER_POLICY_UNVERIFIED operation=' /tmp/l2b26-service.log)" -eq 1
test "$(grep -Fc 'OS_PEER_VERIFIED operation=' /tmp/l2b26-service.log)" -eq 3
test "$(grep -Fc 'OS_PEER_REPLAY_REJECTED' /tmp/l2b26-service.log)" -eq 1
test ! -e /usr/bin/lanveriq-agent
test ! -e /usr/lib/systemd/system/lanveriq-agent.service
test ! -e /etc/lanveriq-agent/agent.toml
test ! -e /etc/systemd/system/multi-user.target.wants/lanveriq-agent.service
test ! -d /run/systemd/system
echo 'PASS: real hostile Polkit YES cannot authorize ReviewLab install; UID isolation, name ownership and replay remain fail-closed; no Agent, apt or service'
