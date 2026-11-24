#!/usr/bin/env python3
"""L2B-10: clean Ubuntu 24.04 OCI APT *simulation*, not an installation.

The audited .deb is mounted read-only. Docker runs a fresh Ubuntu rootfs with
capabilities dropped and a read-only root, with only APT indexes on tmpfs.
This does not exercise Pop!_OS/COSMIC, the Agent or an authorized installer.
"""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "abi", HERE / "verify_desktop_deb_shlibdeps_audit.py"
)
abi = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(abi)
require = abi.require
IMAGE_TAG = "ubuntu:24.04"
DIGEST = re.compile(r"ubuntu@sha256:([0-9a-f]{64})\Z")
HEX = re.compile(r"[0-9a-f]{64}\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
MAX_LOG = 300_000
# No echo of any user-controlled package text into the container shell.
CONTAINER_SCRIPT = r"""
set -eu
. /etc/os-release
printf 'LNV_RELEASE %s:%s\n' "$ID" "$VERSION_ID"
printf 'LNV_ARCH %s\n' "$(dpkg --print-architecture)"
printf 'LNV_BASE_COUNT %s\n' "$(dpkg-query -W | wc -l)"
for package in lanveriq-desktop libwebkit2gtk-4.1-0 libgtk-3-0t64; do
  if dpkg-query -s "$package" >/dev/null 2>&1; then
    echo "NOT_CLEAN_BASE_IMAGE: $package already registered" >&2
    exit 41
  fi
done
echo 'LNV_BASE_GUI_LIBS absent'
printf 'LNV_PACKAGE_SHA %s\n' "$(sha256sum /candidate.deb | cut -d ' ' -f 1)"
# With ALL Linux capabilities dropped, no-new-privileges and read-only rootfs,
# APT cannot setuid to _apt. Its index retrieval runs only inside this
# disposable restricted OCI root with tmpfs package indexes; no install occurs.
apt-get -o APT::Sandbox::User=root -o Acquire::Retries=2 update -qq
apt-get -s -o Debug::NoLocking=1 -o APT::Install-Recommends=false install /candidate.deb
"""

def hash_file(path):
    path = pathlib.Path(path)
    require(path.is_file() and not path.is_symlink()
            and 1024 <= path.stat().st_size < 120_000_000, "missing/unreviewed candidate .deb")
    return abi.chain.owner.elfguard.payload.base.digest(path)

def check_previous(report_path, package_sha, source, run_id, attempt):
    require(COMMIT.fullmatch(source) and type(run_id) is int and run_id > 0
            and type(attempt) is int and attempt > 0, "invalid source/run identity")
    rec = abi.chain.owner.elfguard.payload.base.json_read(report_path, 131072)
    require(rec.get("schema") == "lanveriq.desktop.untrusted-dpkg-shlibdeps-v1"
            and rec.get("source_sha") == source
            and rec.get("run_id") == run_id and rec.get("attempt") == attempt
            and rec.get("deb_sha256") == package_sha
            and rec.get("direct_package_metadata") == "NO_GAP_DETECTED_UNAPPROVED"
            and isinstance(rec.get("comparison"), dict)
            and rec["comparison"].get("direct_declaration_gap_count") == 0
            and rec.get("license_review") == "OPEN_NOT_APPROVED"
            and rec.get("release_approved") is False
            and rec.get("install_authorized") is False,
            "prior exact-SHA official ABI audit is absent, changed or not approved")
    return abi.chain.owner.elfguard.payload.base.digest(report_path)

def parse_image_digests(raw):
    try:
        values = json.loads(raw)
    except (ValueError, TypeError) as exc:
        raise ValueError("Docker image digest metadata invalid") from exc
    require(isinstance(values, list) and values, "missing OCI image manifest digest")
    digests = [item for item in values
               if isinstance(item, str) and DIGEST.fullmatch(item)]
    require(len(set(digests)) == 1, "untrusted/ambiguous Ubuntu OCI image digest")
    return digests[0]

def docker_args(image, package):
    require(DIGEST.fullmatch(image), "OCI image must use a resolved sha256 digest")
    path = pathlib.Path(package).resolve(strict=True)
    require(path.is_file() and not pathlib.Path(package).is_symlink(),
            "invalid OCI package bind source")
    return [
        "docker", "run", "--rm", "--pull=never", "--platform=linux/amd64",
        "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
        "--pids-limit=256", "--memory=1536m", "--cpus=2",
        "--network=bridge", "--tmpfs=/tmp:rw,mode=1777,size=64m",
        "--tmpfs=/var/cache/apt:rw,mode=0755,size=64m",
        "--tmpfs=/var/lib/apt/lists:rw,mode=0755,size=256m",
        "--mount=type=bind,source=" + str(path) + ",destination=/candidate.deb,readonly",
        image, "/bin/sh", "-ceu", CONTAINER_SCRIPT
    ]

def docker_cmd(argv, timeout):
    try:
        res = subprocess.run(argv, shell=False, capture_output=True, timeout=timeout,
                             check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError) as exc:
        raise ValueError("clean Ubuntu OCI simulation unavailable") from exc
    require(res.returncode == 0, "clean Ubuntu OCI command failed (exit " +
            str(res.returncode) + "): " + res.stderr.decode("utf-8", "replace")[-1000:])
    require(len(res.stdout) < MAX_LOG, "unbounded clean OCI output")
    return res.stdout.decode("utf-8", "strict")

def run_clean_simulation(package):
    # The tag resolves through the registry, then the actual container runs
    # only the concrete digest of the image retrieved for THIS invocation.
    docker_cmd(["docker", "pull", "--platform=linux/amd64", IMAGE_TAG], 240)
    repo_digests = docker_cmd(["docker", "image", "inspect", "--format",
                               "{{json .RepoDigests}}", IMAGE_TAG], 20)
    image = parse_image_digests(repo_digests)
    output = docker_cmd(docker_args(image, package), 360)
    return image, output

def parse_simulation(log, package_sha):
    require(isinstance(log, str) and 100 < len(log) < MAX_LOG, "empty/oversized APT simulation")
    lines = log.splitlines()
    def field(prefix):
        found = [s[len(prefix):] for s in lines if s.startswith(prefix)]
        require(len(found) == 1, "missing/duplicate clean APT proof: " + prefix)
        return found[0]
    require(field("LNV_RELEASE ") == "ubuntu:24.04"
            and field("LNV_ARCH ") == "amd64"
            and field("LNV_BASE_GUI_LIBS ") == "absent",
            "not a fresh Ubuntu24 amd64 rootfs without GUI dependencies")
    count = field("LNV_BASE_COUNT ")
    require(count.isdigit() and 1 <= int(count) <= 250,
            "base OCI image contains unexpected installed package population")
    require(field("LNV_PACKAGE_SHA ") == package_sha and HEX.fullmatch(package_sha),
            "OCI simulated .deb bytes mismatch")
    candidate = [s for s in lines if
                 re.match(r"^Inst lanveriq-desktop \(0\.1\.0(?: |\))", s)]
    configured = [s for s in lines if s.startswith("Conf lanveriq-desktop (0.1.0 ")]
    install = [s for s in lines if s.startswith("Inst ")]
    remove = [s for s in lines if s.startswith(("Remv ", "Purg "))]
    require(len(candidate) == 1 and len(configured) == 1,
            "APT did not plan to install and configure exactly one GUI-only candidate")
    require(not remove and 8 <= len(install) <= 500,
            "unexpected removals or incomplete/overlarge APT install plan")
    require(all("lanveriq-agent" not in s and "lanveriq-service" not in s
                for s in lines), "Agent or service appeared in OCI plan")
    return {"os": "Ubuntu 24.04", "arch": "amd64",
            "initial_installed_package_count": int(count),
            "planned_install_count": len(install),
            "candidate_plan": candidate[0],
            "planned_removal_count": 0, "no_preinstalled_gtk_webkit": True}

def build_receipt(log, image, package, prior, source, run_id, attempt):
    require(DIGEST.fullmatch(image), "missing pinned OCI image digest")
    pkg_sha = hash_file(package)
    prior_sha = check_previous(prior, pkg_sha, source, run_id, attempt)
    rec = parse_simulation(log, pkg_sha)
    return {
        "schema": "lanveriq.desktop.untrusted-clean-ubuntu-apt-simulation.v1",
        "source_sha": source, "run_id": run_id, "attempt": attempt,
        "deb_sha256": pkg_sha, "shlibdeps_report_sha256": prior_sha,
        "oci_image_pulled_by_tag": IMAGE_TAG,
        "oci_resolved_manifest_digest": image,
        "oci_architecture": "linux/amd64",
        "simulation_log_sha256": hashlib.sha256(log.encode("utf-8")).hexdigest(),
        "simulation": rec,
        "apt_action": "SIMULATE_ONLY_NO_PACKAGES_INSTALLED",
        "ubuntu_clean_container": "SIMULATED_DEPENDENCY_PLAN_ONLY",
        "pop_os_24_cosmic": "NOT_TESTED",
        "license_approval": "OPEN",
        "provenance_trust": "UNTRUSTED",
        "release_approved": False, "install_authorized": False,
    }

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("collect", "verify"))
    for name in ("package", "previous", "log", "output"):
        p.add_argument("--" + name, type=pathlib.Path, required=True)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--run-id", type=int, required=True)
    p.add_argument("--attempt", type=int, required=True)
    x = p.parse_args()
    require(not x.output.exists() if x.mode == "collect" else x.output.is_file(),
            "report must not overwrite existing evidence")
    if x.mode == "collect":
        require(not x.log.exists(), "refuse overwrite of clean rootfs log")
        # Validate full source+prior chain BEFORE allowing networked APT metadata
        # retrieval in a disposable OCI environment.
        check_previous(x.previous, hash_file(x.package), x.source_sha, x.run_id, x.attempt)
        image, log = run_clean_simulation(x.package)
        receipt = build_receipt(log, image, x.package, x.previous,
                                x.source_sha, x.run_id, x.attempt)
        x.log.write_text(log)
        x.output.write_text(json.dumps(receipt, sort_keys=True, indent=2) + "\n")
        print("PASS: clean Ubuntu OCI APT simulated an uninstalled GUI-only .deb")
    else:
        rec = abi.chain.owner.elfguard.payload.base.json_read(x.output, 16384)
        log = x.log.read_text(encoding="utf-8")
        expected = build_receipt(log, rec.get("oci_resolved_manifest_digest"),
                                 x.package, x.previous, x.source_sha, x.run_id, x.attempt)
        require(rec == expected, "untrusted OCI plan/report drift or forged authority")
        print("PASS: verified digest-bound Ubuntu24 OCI APT simulation; Pop!_OS NOT TESTED")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError,
            subprocess.SubprocessError, KeyError) as exc:
        print("NOT_APPROVED: " + str(exc), file=sys.stderr)
        sys.exit(2)
