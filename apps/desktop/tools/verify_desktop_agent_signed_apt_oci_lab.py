#!/usr/bin/env python3
"""L2B-23: native signed APT install of REAL audited Rust Agent in isolated OCI.

Disposable CI only: private signing keys stay outside OCI; no approved release
identity, production Polkit action, Tauri installer or Pop!_OS L3.
"""
import datetime
import hashlib
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
AUDIT = ROOT / "apps/agent/tools/linux/verify-offline-agent-deb.py"
INNER = ROOT / "apps/desktop/tools/agent-signed-apt-oci-inner.sh"
IMAGE = "ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b"
PACKAGE = "lanveriq-agent-offline-preview"
VERSION = "0.1.0~dev1"
NAME = f"{PACKAGE}_{VERSION}_amd64.deb"
CASES = ("signed-install-reinstall-remove", "wrong-key", "wrong-signer",
         "unsigned", "index-tamper", "expired", "future", "deb-tamper",
         "package-confusion", "signed-valid-old-release-acceptance")
MOUNT = "/mnt/lanveriq-lab"


def check(ok, message):
    if not ok:
        raise ValueError("NOT_APPROVED: " + message)


def run(args, *, stdout=None, timeout=60):
    result = subprocess.run(args, stdout=stdout or subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout,
                            check=False)
    if result.returncode:
        message = result.stderr.decode("utf-8", "replace")[-1500:]
        raise ValueError(f"native lab command {args[0]} failed: {message}")
    return result


def private_signer(home, public, label):
    home.mkdir(mode=0o700)
    run(("gpg", "--batch", "--homedir", str(home), "--passphrase", "",
         "--quick-generate-key",
         f"Lanveriq L2B23 EPHEMERAL {label} <{label}@example.invalid>",
         "ed25519", "sign", "0"))
    with public.open("wb") as file:
        run(("gpg", "--batch", "--homedir", str(home), "--export"), stdout=file)
    check(public.stat().st_size > 50, "test public key missing")


def rfc822_date(days=0):
    return (datetime.datetime.now(datetime.timezone.utc) +
            datetime.timedelta(days=days)).strftime("%a, %d %b %Y %H:%M:%S +0000")


def prepare_case(directory, deb, case, homes, public_keys):
    keys = directory / "keyrings"
    keys.mkdir()
    for role in ("publisher", "attacker"):
        shutil.copyfile(public_keys / (role + ".gpg"), keys / (role + ".gpg"))
    pool = directory / "repo/pool/main/l/lanveriq-agent-offline-preview"
    pool.mkdir(parents=True)
    target = pool / NAME
    shutil.copyfile(deb, target)
    artifact = target.read_bytes()
    package_name = PACKAGE + "-other" if case == "package-confusion" else PACKAGE
    index = directory / "repo/dists/lab/main/binary-amd64/Packages"
    index.parent.mkdir(parents=True)
    rel = "pool/main/l/lanveriq-agent-offline-preview/" + NAME
    index.write_text(
        f"Package: {package_name}\nVersion: {VERSION}\nArchitecture: amd64\n"
        "Maintainer: Lanveriq disposable test <ci@example.invalid>\n"
        f"Filename: {rel}\nSize: {len(artifact)}\n"
        f"SHA256: {hashlib.sha256(artifact).hexdigest()}\n"
        "Description: audited idle Agent, disposable signing test ONLY\n"
        " NOT APPROVED for release or real endpoint installation.\n\n",
        encoding="utf-8",
    )
    payload = index.read_bytes()
    delta_date = -1 / 1440
    delta_expiration = 2
    if case == "expired":
        delta_date, delta_expiration = -6, -1
    if case == "future":
        delta_date, delta_expiration = 2, 4
    release = directory / "repo/dists/lab/Release"
    release.write_text(
        "Origin: Lanveriq synthetic CI only, NOT approved\n"
        "Label: Temporary signed file repository\n"
        "Suite: lab\nCodename: lab\n"
        f"Date: {rfc822_date(delta_date)}\n"
        f"Valid-Until: {rfc822_date(delta_expiration)}\n"
        "Architectures: amd64\nComponents: main\n"
        "Description: Real Agent package, ephemeral testing-only signer\n"
        "SHA256:\n"
        f" {hashlib.sha256(payload).hexdigest()} {len(payload)} "
        "main/binary-amd64/Packages\n",
        encoding="utf-8",
    )
    signed = directory / "repo/dists/lab/InRelease"
    signer = "attacker" if case == "wrong-signer" else "publisher"
    run(("gpg", "--batch", "--yes", "--homedir", str(homes[signer]),
         "--clearsign", "--output", str(signed), str(release)))
    if case == "signed-valid-old-release-acceptance":
        # A second *validly signed* Release is one day OLDER than the first,
        # but still within Valid-Until. Sign it with the SAME disposable key.
        # This tests native apt-secure's rollback limitation; no APT install.
        stale = directory / "stale-signed"
        stale.mkdir()
        old_release = stale / "Release"
        old_release.write_text(
            "".join(
                f"Date: {rfc822_date(-1)}\n"
                if row.startswith("Date: ") else row
                for row in release.read_text(encoding="utf-8").splitlines(keepends=True)
            ), encoding="utf-8",
        )
        run(("gpg", "--batch", "--yes", "--homedir", str(homes["publisher"]),
             "--clearsign", "--output", str(stale / "InRelease"), str(old_release)))
    if case == "unsigned":
        signed.unlink()
    if case == "index-tamper":
        with index.open("ab") as file:
            file.write(b"Added-After-Signing: tamper\n")
    if case == "deb-tamper":
        with target.open("ab") as file:
            file.write(b"UNSIGNED-ATTACKER-CONTENT")
    key = "attacker" if case == "wrong-key" else "publisher"
    (directory / "lab.sources").write_text(
        f"Types: deb\nURIs: file:{MOUNT}/repo\n"
        f"Suites: lab\nComponents: main\n"
        f"Signed-By: {MOUNT}/keyrings/{key}.gpg\n"
        "Check-Valid-Until: yes\n\n", encoding="utf-8"
    )


def execute_case(case, directory):
    check(case in CASES, "invalid lab operation")
    # All APT/dpkg modifications are *inside* the container image overlay.
    # Only signed repo/known public key bytes are exposed via readonly bind.
    cmd = ("docker", "run", "--rm", "--interactive",
           "--network", "none", "--cap-drop", "ALL",
           "--security-opt", "no-new-privileges",
           "--pids-limit", "96", "--memory", "512m", "--cpus", "1",
           "--mount", f"type=bind,src={directory},dst={MOUNT},readonly",
           "--env", f"LANVERIQ_L2B23_CASE={case}",
           "--entrypoint", "/bin/bash", IMAGE, "-seuo", "pipefail")
    result = subprocess.run(cmd, input=INNER.read_bytes(),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=120, check=False)
    output = result.stdout.decode("utf-8", "replace")
    errors = result.stderr.decode("utf-8", "replace")
    check(result.returncode == 0,
          f"OCI {case} failed ({result.returncode})\n"
          f"stdout={output[-2000:]}\nstderr={errors[-2000:]}")
    check("PASS: " in output, f"missing genuine APT result marker: {case}")
    print(f"PASS: {case}: verified via actual apt-get in isolated OCI",
          flush=True)


def main():
    check(len(sys.argv) == 2, "usage: signed-apt-ci AUDITED_AGENT_DEB")
    check(os.getenv("GITHUB_ACTIONS") == "true"
          and os.getenv("LANVERIQ_SIGNED_AGENT_APT_L2") == "1"
          and os.getenv("RUNNER_OS") == "Linux"
          and os.geteuid() != 0 and sys.platform == "linux",
          "CI-only, unprivileged Linux runner with explicit lab consent")
    for binary in ("docker", "gpg", "dpkg-deb"):
        check(shutil.which(binary), f"missing native CI tool: {binary}")
    archive = pathlib.Path(sys.argv[1])
    check(archive.name == NAME and archive.is_file() and not archive.is_symlink(),
          "only regular exact offline Agent .deb is permitted")
    archive = archive.resolve(strict=True)
    check(archive.stat().st_size < 100 * 1024 * 1024,
          "unexpectedly large Agent archive")
    run((sys.executable, str(AUDIT), str(archive)))
    run(("docker", "pull", IMAGE), timeout=240)
    with tempfile.TemporaryDirectory(prefix="lanveriq-l2b23-secret-signers-") as home:
        with tempfile.TemporaryDirectory(prefix="lanveriq-l2b23-public-repos-") as root:
            homes = {}
            signing = pathlib.Path(home)
            base = pathlib.Path(root)
            pubs = base / "public-keyrings"
            pubs.mkdir()
            for role in ("publisher", "attacker"):
                homes[role] = signing / (role + "-gnupg")
                private_signer(homes[role], pubs / (role + ".gpg"), role)
            for case in CASES:
                directory = base / case
                directory.mkdir(mode=0o755)
                prepare_case(directory, archive, case, homes, pubs)
                execute_case(case, directory)
    print(f"PASS: {len(CASES)} real APT Agent package L2 OCI cases; "
          "all private keys destroyed, never host-installed or release-approved")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
