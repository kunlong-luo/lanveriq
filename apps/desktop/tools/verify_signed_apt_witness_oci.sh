#!/usr/bin/env bash
# L2B-32: CI-only, read-only independent-signer witness *experiment*.
# Reuse native gpgv, sha256sum and bash, not a product verifier/installer.
# This fixture is deliberately NOT an independently custodied high-water.
set -Eeuo pipefail
if [ "${LANVERIQ_SYNTHETIC_WITNESS_L2:-}" != 1 ] ||
   [[ "${LANVERIQ_L2B23_CASE:-}" != signed-witness-* ]] ||
   [ "$(cat /proc/1/comm)" = systemd ]; then
  echo 'NOT_APPROVED: witness preflight is disposable OCI only' >&2
  exit 30
fi
if [ "$#" -ne 3 ]; then
  echo 'NOT_APPROVED: use exact witness manifest, detached signature and signed APT Release' >&2
  exit 31
fi
manifest="$1"
signature="$2"
apt_release="$3"
witness_key=/mnt/lanveriq-lab/keyrings/witness.gpg
case "$manifest" in
  /mnt/lanveriq-lab/witness/approved.manifest|/mnt/lanveriq-lab/witness/old.manifest|/mnt/lanveriq-lab/witness/tampered.manifest|/mnt/lanveriq-lab/witness/replayed-sequence2.manifest) ;;
  *) echo 'NOT_APPROVED: unapproved witness manifest path' >&2; exit 32 ;;
esac
case "$signature" in
  /mnt/lanveriq-lab/witness/approved.sig|/mnt/lanveriq-lab/witness/old.sig|/mnt/lanveriq-lab/witness/forged-publisher.sig|/mnt/lanveriq-lab/witness/replayed-sequence2.sig) ;;
  *) echo 'NOT_APPROVED: unapproved witness signature path' >&2; exit 33 ;;
esac
case "$apt_release" in
  /tmp/lanveriq-witness-l2/repo/dists/lab/InRelease|/tmp/lanveriq-witness-cold-anchor/repo/dists/lab/InRelease|/mnt/lanveriq-lab/stale-signed/InRelease) ;;
  *) echo 'NOT_APPROVED: unapproved OCI APT Release path' >&2; exit 34 ;;
esac
for f in "$witness_key" "$manifest" "$signature" "$apt_release"; do
  if [ ! -f "$f" ] || [ -L "$f" ]; then
    echo 'NOT_APPROVED: missing or linked witness input' >&2
    exit 35
  fi
done
if ! gpgv --keyring "$witness_key" "$signature" "$manifest" >/dev/null 2>&1; then
  echo 'NOT_APPROVED: independent witness GPG signature invalid' >&2
  exit 36
fi
mapfile -t fields < "$manifest"
if [ "${#fields[@]}" -ne 3 ] ||
   [ "${fields[0]}" != 'LANVERIQ-CI-ONLY-APT-WITNESS-V1' ]; then
  echo 'NOT_APPROVED: witness manifest schema invalid' >&2
  exit 37
fi
# The expected value is pinned in this OCI-only preflight, NOT by APT.
# In a real product only an independently custodied monotonic authority could
# prevent a privileged operator from rolling back this policy as well.
if [ "${fields[1]}" != 'sequence=2' ]; then
  echo 'NOT_APPROVED: signed witness sequence below pinned synthetic high-water' >&2
  exit 38
fi
if [[ ! "${fields[2]}" =~ ^release_sha256=([0-9a-f]{64})$ ]]; then
  echo 'NOT_APPROVED: signed Release digest field malformed' >&2
  exit 39
fi
expected="${BASH_REMATCH[1]}"
actual="$(sha256sum "$apt_release" | cut -d' ' -f1)"
if [ "$actual" != "$expected" ]; then
  echo 'NOT_APPROVED: valid APT signature, but independent witness Release digest mismatch' >&2
  exit 40
fi
# L2B-33: the approved witness HASH must be pinned separately from both
# the publisher's APT source and the replayable witness/key files.
# The CI-only anchor is generated outside the APT repository and mounted RO.
# It is NOT a remote/independently administered monotonic authority.
case "${LANVERIQ_L2B23_CASE:-}" in
  signed-witness-cold-cache-*)
    anchor=/mnt/lanveriq-anchor/approved-witness.sha256
    if [ ! -f "$anchor" ] || [ -L "$anchor" ]; then
      echo 'NOT_APPROVED: independent read-only witness anchor missing' >&2
      exit 41
    fi
    mapfile -t anchor_rows < "$anchor"
    if [ "${#anchor_rows[@]}" -ne 1 ] ||
       [[ ! "${anchor_rows[0]}" =~ ^[0-9a-f]{64}$ ]]; then
      echo 'NOT_APPROVED: independently mounted witness anchor malformed' >&2
      exit 42
    fi
    actual_witness="$(sha256sum "$manifest" | cut -d' ' -f1)"
    if [ "$actual_witness" != "${anchor_rows[0]}" ]; then
      echo 'NOT_APPROVED: separately mounted approved witness digest mismatch' >&2
      exit 43
    fi
    ;;
esac
echo 'PASS: disposable separate-signer witness matches signed APT Release and optional separate RO CI anchor; NOT production high-water'
