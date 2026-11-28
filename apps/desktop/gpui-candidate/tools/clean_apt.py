#!/usr/bin/env python3
"""Clean Ubuntu direct ABI APT simulation for the exact temporary GUI-only deb."""
import hashlib
import json
import os
import re

import package_audit as audit

# Reuse the existing restricted, digest-pinned Ubuntu OCI runner. The
# Tauri-specific parser and package identities are not applicable to GPUI.
oci = audit.load("desktop_clean_apt_common", "verify_desktop_clean_apt_simulation.py")
SCRIPT = r"""
set -eu
export LC_ALL=C
. /etc/os-release
printf 'LNV_RELEASE %s:%s\n' "$ID" "$VERSION_ID"
printf 'LNV_ARCH %s\n' "$(dpkg --print-architecture)"
printf 'LNV_BASE_COUNT %s\n' "$(dpkg-query -W | wc -l)"
for p in lanveriq-desktop lanveriq-desktop-gpui-candidate lanveriq-agent libxcb1 libxkbcommon0 libxkbcommon-x11-0; do
  if dpkg-query -s "$p" >/dev/null 2>&1; then
    echo 'unexpected preinstalled GUI package' >&2
    exit 41
  fi
done
echo 'LNV_BASE_GUI_LIBS absent'
for p in libc6 libgcc-s1 libxcb1 libxkbcommon0 libxkbcommon-x11-0; do
  v="$(dpkg-query -W -f='${Version}' "$p" 2>/dev/null)" || v=MISSING
  printf 'LNV_BASE %s %s\n' "$p" "$v"
done
printf 'LNV_PACKAGE_SHA %s\n' "$(sha256sum /candidate.deb | cut -d ' ' -f 1)"
before="$(sha256sum /var/lib/dpkg/status | cut -d ' ' -f 1)"
apt-get -o APT::Sandbox::User=root -o APT::Get::AllowUnauthenticated=false -o Acquire::AllowInsecureRepositories=false -o APT::Update::Error-Mode=any -o Acquire::Retries=2 update -qq
apt-get -s -o APT::Get::AllowUnauthenticated=false -o Debug::NoLocking=1 -o APT::Install-Recommends=false install /candidate.deb
after="$(sha256sum /var/lib/dpkg/status | cut -d ' ' -f 1)"
test "$before" = "$after"
echo 'LNV_DPKG unchanged'
"""
VERSION = re.compile(r"[A-Za-z0-9.+:~_-]{1,180}\Z")
PLAN = re.compile(r"^(Inst|Conf) ([a-z0-9][a-z0-9+.-]*)(?::amd64)?(?: \[[^\]\n]+\])? \(([^\s()]+)(?: [^\n]*)?\)$")

def parse(log, package_sha, groups, compare):
    audit.check(100 < len(log) < oci.MAX_LOG, "invalid simulation output size")
    lines = log.splitlines()
    def field(prefix):
        found = [line[len(prefix):] for line in lines if line.startswith(prefix)]
        audit.check(len(found) == 1, "missing or duplicate OCI field: " + prefix)
        return found[0]
    audit.check(field("LNV_RELEASE ") == "ubuntu:24.04" and field("LNV_ARCH ") == "amd64"
                and field("LNV_BASE_GUI_LIBS ") == "absent" and field("LNV_DPKG ") == "unchanged",
                "unexpected clean rootfs or changed dpkg state")
    audit.check(field("LNV_PACKAGE_SHA ") == package_sha and oci.HEX.fullmatch(package_sha),
                "package digest mismatch")
    count = field("LNV_BASE_COUNT ")
    audit.check(count.isdigit() and 1 <= int(count) <= 250, "unexpected installed base count")
    base = {}
    for line in lines:
        if line.startswith("LNV_BASE "):
            parts = line.split()
            audit.check(len(parts) == 3 and parts[1] in audit.DIRECT and parts[1] not in base
                        and VERSION.fullmatch(parts[2]), "unexpected base dependency version")
            base[parts[1]] = parts[2]
    audit.check(set(base) == audit.DIRECT and all(base[n] == "MISSING" for n in
                ("libxcb1", "libxkbcommon0", "libxkbcommon-x11-0")), "base dependency scope mismatch")
    plans = {"Inst": {}, "Conf": {}}
    for line in lines:
        audit.check(not line.startswith(("Remv ", "Purg ")), "unexpected package removal")
        if line.startswith(("Inst ", "Conf ")):
            m = PLAN.fullmatch(line)
            audit.check(m is not None, "unexpected APT plan row")
            kind, name, version = m.groups()
            audit.check(name not in plans[kind] and VERSION.fullmatch(version)
                        and (not name.startswith("lanveriq-") or name == audit.NAME),
                        "duplicate or unexpected package plan")
            plans[kind][name] = version
    inst, conf = plans["Inst"], plans["Conf"]
    audit.check(4 <= len(inst) <= 500 and inst == conf and inst.get(audit.NAME) == audit.VERSION,
                "incomplete or unexpected install/configure plan")
    audit.validate_relations(groups)
    audit.check({g[0]["name"] for g in groups} == audit.DIRECT, "incomplete ABI floors")
    direct = {}
    for (item,) in groups:
        n = item["name"]
        v = inst.get(n, base[n])
        audit.check(v != "MISSING" and (not item["op"] or compare(v, item["version"]) >= 0),
                    "direct dependency missing or below floor")
        direct[n] = {"version": v, "floor": item["version"], "planned": n in inst}
    return {"base_count": int(count), "planned_install_count": len(inst),
            "planned_removal_count": 0, "direct_dependencies": direct, "dpkg_unchanged": True}

def collect(deb, groups, original_sha, stripped_sha, output, compare):
    package_sha = audit.digest(deb)
    oci.docker_cmd(["docker", "pull", "--platform=linux/amd64", oci.IMAGE_TAG], 240)
    image = oci.parse_image_digests(oci.docker_cmd(
        ["docker", "image", "inspect", "--format", "{{json .RepoDigests}}", oci.IMAGE_TAG], 20))
    args = oci.docker_args(image, deb)
    audit.check(args[-1] == oci.CONTAINER_SCRIPT, "OCI helper contract drift")
    args[-1] = SCRIPT
    log = oci.docker_cmd(args, 360)
    simulation = parse(log, package_sha, groups, compare)
    audit.check(audit.digest(deb) == package_sha, "temporary package drift")
    report = {"schema": "lanveriq.gpui.clean-ubuntu-apt-simulation-v1",
              "source_sha": os.environ["GITHUB_SHA"], "lock_sha256": audit.digest(audit.ROOT / "Cargo.lock"),
              "original_elf_sha256": original_sha, "stripped_elf_sha256": stripped_sha,
              "deb_sha256": package_sha, "oci_resolved_manifest_digest": image,
              "simulation_log_sha256": hashlib.sha256(log.encode()).hexdigest(), "simulation": simulation,
              "scope": "DIRECT_ABI_SIMULATION_ONLY_NO_INSTALL", "pop_os_cosmic": "NOT_RUN",
              "release_approved": False, "install_authorized": False}
    with output.open("x") as stream:
        json.dump(report, stream, indent=2, sort_keys=True)
        stream.write("\n")
    print("PASS: clean Ubuntu24 OCI direct ABI APT simulation (no installation):", simulation)
