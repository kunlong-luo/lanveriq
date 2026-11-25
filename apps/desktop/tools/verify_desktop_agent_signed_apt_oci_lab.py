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
WITNESS_SCRIPT = ROOT / "apps/desktop/tools/verify_signed_apt_witness_oci.sh"
IMAGE = "ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b"
PACKAGE = "lanveriq-agent-offline-preview"
VERSION = "0.1.0~dev1"
NAME = f"{PACKAGE}_{VERSION}_amd64.deb"
PRIOR_VERSION = "0.1.0~dev0"
PRIOR_NAME = f"{PACKAGE}_{PRIOR_VERSION}_amd64.deb"
PINNED_HISTORICAL_AGENT_SHA = "cd031894048dfcdc479aeaddd72e6254abde7303"
ANCHOR_CASES = (
    "signed-witness-cold-cache-approved-anchor-accepted",
    "signed-witness-cold-cache-double-replay-refused",
    "signed-witness-cold-cache-missing-anchor-refused",
)
WITNESS_CASES = (
    "signed-witness-valid-old-release-refused",
    "signed-witness-old-seal-and-forged-signatures-refused",
)
SOURCE_DISTINCT_CASES = (
    "signed-distinct-elf-unattended-downgrade-refused",
    "signed-distinct-elf-explicit-oci-downgrade",
)
VERSION_CASES = ("signed-two-versions-unattended-downgrade-refused",
                 "signed-two-versions-explicit-oci-downgrade")
CASES = ("signed-install-reinstall-remove", "wrong-key", "wrong-signer",
         "unsigned", "index-tamper", "expired", "future", "deb-tamper",
         "package-confusion", "signed-valid-old-release-acceptance",
         "signed-old-different-packages-retained", *VERSION_CASES,
         *SOURCE_DISTINCT_CASES, *WITNESS_CASES, *ANCHOR_CASES)
MOUNT = "/mnt/lanveriq-lab"
CURRENT_DESCRIPTION = b"Description: audited idle Agent, disposable signing test ONLY"
PRIOR_DESCRIPTION = b"Description: audited idle Agent, historical signed index ONLY"


def previous_packages_index(current: bytes) -> bytes:
    """Alter signed package metadata, not the real audited Agent .deb binary.

    The historical signed index describes the exact same package/version and
    SHA256 artifact, so observing APT use it is NOT an Agent downgrade proof.
    """
    if current.count(CURRENT_DESCRIPTION) != 1 or PRIOR_DESCRIPTION in current:
        raise ValueError("NOT_APPROVED: invalid base package-index fixture")
    older = current.replace(CURRENT_DESCRIPTION, PRIOR_DESCRIPTION)
    if older == current or len(older) == len(current) and older == current:
        raise ValueError("NOT_APPROVED: old index did not differ")
    return older


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



def require_distinct_elf_bytes(current: bytes, historical: bytes):
    """Refuse a synthetic 'different source' claim with the same Rust ELF."""
    check(current.startswith(bytes.fromhex("7f454c46")) and
          historical.startswith(bytes.fromhex("7f454c46")),
          "both binary artifacts must be actual ELF objects")
    current_hash = hashlib.sha256(current).hexdigest()
    historical_hash = hashlib.sha256(historical).hexdigest()
    check(current_hash != historical_hash,
          "identical ELF cannot prove different compiled Agent source")
    return current_hash, historical_hash


def verify_historical_agent_checkout(path):
    """Pin exact prior Git source and Cargo-built ELF, not a local arbitrary file."""
    workspace = os.getenv("GITHUB_WORKSPACE")
    check(workspace is not None and pathlib.Path(workspace).is_absolute(),
          "historical source must run under GitHub CI workspace")
    historical_root = pathlib.Path(workspace) / "l2b31-historical"
    expected = historical_root / "apps/agent/target/release/lanveriq-agent"
    check(path == expected and not path.is_symlink() and path.is_file()
          and path.stat().st_size < 100 * 1024 * 1024,
          "historical ELF must be the exact pinned Cargo build output")
    check((historical_root / ".git").is_dir(),
          "historical checkout must be a Git repository")
    revision = run(("git", "-C", str(historical_root), "rev-parse", "HEAD")
                  ).stdout.decode("utf-8").strip()
    check(revision == PINNED_HISTORICAL_AGENT_SHA,
          "historical Git source revision mismatch")
    older_source = historical_root / "apps/agent/src/inventory_platform.rs"
    newer_source = ROOT / "apps/agent/src/inventory_platform.rs"
    check(older_source.is_file() and newer_source.is_file() and
          older_source.read_bytes() != newer_source.read_bytes(),
          "different historical Agent source required")
    check(path.read_bytes()[:4] == bytes.fromhex("7f454c46"),
          "historical Cargo binary is not ELF")
    return path


def create_prior_version_archive(audited_current, prior_dest, replacement_elf=None):
    """CI-only .deb dev0 repack; optional pinned historic ELF, never signed release.

    The preexisting dev0 version remains a same-ELF control-version baseline.
    A separate dev0 build uses a pinned older source's ACTUAL DIFFERENT ELF.
    All installations remain solely inside networkless disposable OCI.
    """
    check(prior_dest.name == PRIOR_NAME and not prior_dest.exists(),
          "only one exact historical CI archive may be constructed")
    with tempfile.TemporaryDirectory(prefix="lanveriq-l2b30-repack-") as temp:
        stage = pathlib.Path(temp) / "unpacked"
        run(("dpkg-deb", "--raw-extract", str(audited_current), str(stage)))
        control = stage / "DEBIAN/control"
        original = control.read_text(encoding="utf-8")
        check(original.count(f"Version: {VERSION}\n") == 1 and
              sum(row.startswith("Version: ") for row in original.splitlines()) == 1,
              "audited baseline control version must be unambiguous")
        path_elf = "usr/bin/lanveriq-agent"
        path_unit = "usr/lib/systemd/system/lanveriq-agent.service"
        check((stage / path_elf).is_file() and (stage / path_unit).is_file(),
              "expected audited payload missing")
        expected_elf = hashlib.sha256((stage / path_elf).read_bytes()).hexdigest()
        if replacement_elf is not None:
            check(replacement_elf.is_file() and not replacement_elf.is_symlink(),
                  "historical replacement must be a regular Cargo ELF")
            require_distinct_elf_bytes((stage / path_elf).read_bytes(),
                                       replacement_elf.read_bytes())
            shutil.copyfile(replacement_elf, stage / path_elf)
            os.chmod(stage / path_elf, 0o755)
            expected_elf = hashlib.sha256((stage / path_elf).read_bytes()).hexdigest()
        expected_unit = hashlib.sha256((stage / path_unit).read_bytes()).hexdigest()
        control.write_text(original.replace(f"Version: {VERSION}\n",
                                            f"Version: {PRIOR_VERSION}\n"),
                           encoding="utf-8")
        run(("dpkg-deb", "--build", "--root-owner-group",
             str(stage), str(prior_dest)))
        check(prior_dest.is_file() and prior_dest.stat().st_size < 100 * 1024 * 1024,
              "historical CI package too large or missing")
        check(run(("dpkg-deb", "--field", str(prior_dest), "Version")
                  ).stdout.decode("utf-8").strip() == PRIOR_VERSION,
              "dpkg historical package Version mismatch")
        run(("dpkg", "--compare-versions", PRIOR_VERSION, "lt", VERSION))
        old_stage = pathlib.Path(temp) / "old-unpacked"
        run(("dpkg-deb", "--raw-extract", str(prior_dest), str(old_stage)))
        check(hashlib.sha256((old_stage / path_elf).read_bytes()).hexdigest()
              == expected_elf, "historical repack changed Rust ELF")
        check(hashlib.sha256((old_stage / path_unit).read_bytes()).hexdigest()
              == expected_unit, "historical repack changed idle systemd unit")
        check({item.name for item in (old_stage / "DEBIAN").iterdir()}
              <= {"control", "md5sums"}, "historical repack gained install hooks")
    return prior_dest


def private_signer(home, public, label):
    home.mkdir(mode=0o700)
    run(("gpg", "--batch", "--homedir", str(home), "--passphrase", "",
         "--quick-generate-key",
         f"Lanveriq L2B23 EPHEMERAL {label} <{label}@example.invalid>",
         "ed25519", "sign", "0"))
    with public.open("wb") as file:
        run(("gpg", "--batch", "--homedir", str(home), "--export"), stdout=file)
    check(public.stat().st_size > 50, "test public key missing")


def witness_manifest(sequence, inrelease: bytes) -> bytes:
    """Build a strict signed *fixture* for gpgv, NOT a release trust schema."""
    check(sequence in (1, 2), "unapproved CI witness sequence")
    check(inrelease.startswith(b"-----BEGIN PGP SIGNED MESSAGE-----"),
          "signed APT InRelease required")
    digest = hashlib.sha256(inrelease).hexdigest()
    # Assemble real LF bytes, not literal backslash-n escape text.
    return bytes([10]).join((
        b"LANVERIQ-CI-ONLY-APT-WITNESS-V1",
        f"sequence={sequence}".encode("ascii"),
        f"release_sha256={digest}".encode("ascii"),
        b"",
    ))



def detached_witness_signature(home, manifest, output):
    run(("gpg", "--batch", "--yes", "--homedir", str(home),
         "--detach-sign", "--output", str(output), str(manifest)))


def rfc822_date(days=0):
    return (datetime.datetime.now(datetime.timezone.utc) +
            datetime.timedelta(days=days)).strftime("%a, %d %b %Y %H:%M:%S +0000")


def prepare_case(directory, deb, case, homes, public_keys, historical=None,
                 distinct_historical=None, elf_digests=None, anchor_dir=None):
    keys = directory / "keyrings"
    keys.mkdir()
    for role in ("publisher", "attacker"):
        shutil.copyfile(public_keys / (role + ".gpg"), keys / (role + ".gpg"))
    if case in (*WITNESS_CASES, *ANCHOR_CASES):
        check((public_keys / "publisher.gpg").read_bytes() !=
              (public_keys / "witness.gpg").read_bytes(),
              "witness signer must differ from APT publisher")
        shutil.copyfile(public_keys / "witness.gpg", keys / "witness.gpg")
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
    if case in (*VERSION_CASES, *SOURCE_DISTINCT_CASES):
        prior = (distinct_historical if case in SOURCE_DISTINCT_CASES
                 else historical)
        check(prior is not None and prior.name == PRIOR_NAME and
              prior.is_file() and not prior.is_symlink(),
              "real historic-version .deb required for version downgrade lab")
        old_target = pool / PRIOR_NAME
        shutil.copyfile(prior, old_target)
        old_bytes = old_target.read_bytes()
        if case in SOURCE_DISTINCT_CASES:
            check(elf_digests is not None and len(elf_digests) == 2
                  and elf_digests[0] != elf_digests[1],
                  "two distinct ELF hashes required for source history proof")
            (directory / "latest-elf.sha256").write_text(
                elf_digests[0] + "\n", encoding="ascii")
            (directory / "historical-elf.sha256").write_text(
                elf_digests[1] + "\n", encoding="ascii")
        old_rel = "pool/main/l/lanveriq-agent-offline-preview/" + PRIOR_NAME
        with index.open("a", encoding="utf-8") as stream:
            stream.write(
                f"Package: {PACKAGE}\nVersion: {PRIOR_VERSION}\n"
                "Architecture: amd64\n"
                "Maintainer: Lanveriq disposable test <ci@example.invalid>\n"
                f"Filename: {old_rel}\nSize: {len(old_bytes)}\n"
                f"SHA256: {hashlib.sha256(old_bytes).hexdigest()}\n"
                "Description: historic control-version repack of audited idle "
                "Agent, disposable CI ONLY\n"
                " NOT APPROVED for release or real endpoint installation.\n\n"
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
    if case in ("signed-valid-old-release-acceptance",
                "signed-old-different-packages-retained", *WITNESS_CASES,
                *ANCHOR_CASES):
        # A second *validly signed* Release is one day OLDER than the first,
        # but still within Valid-Until. Sign it with the SAME disposable key.
        # This tests native apt-secure's rollback limitation; no APT install.
        stale = directory / "stale-signed"
        stale.mkdir()
        old_release = stale / "Release"
        older_payload = (previous_packages_index(payload)
                         if case in ("signed-old-different-packages-retained",
                                     "signed-witness-cold-cache-double-replay-refused")
                         else payload)
        if case in ("signed-old-different-packages-retained",
                    "signed-witness-cold-cache-double-replay-refused"):
            (stale / "Packages").write_bytes(older_payload)
        old_release.write_text(
            "".join(
                f"Date: {rfc822_date(-1)}\n" if row.startswith("Date: ") else
                (f" {hashlib.sha256(older_payload).hexdigest()} "
                 f"{len(older_payload)} main/binary-amd64/Packages\n")
                if case in ("signed-old-different-packages-retained",
                            "signed-witness-cold-cache-double-replay-refused")
                and row.endswith(" main/binary-amd64/Packages\n")
                else row
                for row in release.read_text(encoding="utf-8").splitlines(keepends=True)
            ), encoding="utf-8",
        )
        run(("gpg", "--batch", "--yes", "--homedir", str(homes["publisher"]),
             "--clearsign", "--output", str(stale / "InRelease"), str(old_release)))
    if case in (*WITNESS_CASES, *ANCHOR_CASES):
        check((stale / "InRelease").is_file(),
              "genuine old signed APT Release witness input missing")
        shutil.copyfile(WITNESS_SCRIPT, directory / "verify-witness.sh")
        witness = directory / "witness"
        witness.mkdir()
        approved = witness / "approved.manifest"
        approved.write_bytes(witness_manifest(2, signed.read_bytes()))
        detached_witness_signature(homes["witness"], approved,
                                   witness / "approved.sig")
        old = witness / "old.manifest"
        old.write_bytes(witness_manifest(1,
                                         (stale / "InRelease").read_bytes()))
        detached_witness_signature(homes["witness"], old, witness / "old.sig")
        # Negative controls: unauthenticated publisher-forged witness signature
        # and mutated manifest, both must fail actual native gpgv.
        detached_witness_signature(homes["publisher"], approved,
                                   witness / "forged-publisher.sig")
        (witness / "tampered.manifest").write_bytes(
            approved.read_bytes().replace(b"sequence=2", b"sequence=8"))
        if case in ANCHOR_CASES:
            check(anchor_dir is not None and not anchor_dir.exists(),
                  "anchor must be in an independent fresh host directory")
            # OCI root has all capabilities dropped: the public digest directory
            # needs execute/read permission while the Docker bind stays read-only.
            anchor_dir.mkdir(mode=0o755)
            (anchor_dir / "approved-witness.sha256").write_text(
                hashlib.sha256(approved.read_bytes()).hexdigest() + "\n",
                encoding="ascii")
            # This is a *second* fully valid witness at the SAME sequence 2,
            # for the old separately publisher-signed Release. Sequence alone
            # therefore cannot distinguish the whole jointly replayed pair.
            replay = witness / "replayed-sequence2.manifest"
            replay.write_bytes(
                witness_manifest(2, (stale / "InRelease").read_bytes()))
            detached_witness_signature(
                homes["witness"], replay,
                witness / "replayed-sequence2.sig")
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


def execute_case(case, directory, anchor_dir=None):
    check(case in CASES, "invalid lab operation")
    # All APT/dpkg modifications are *inside* the container image overlay.
    # Only signed repo/known public key bytes are exposed via readonly bind.
    cmd = ["docker", "run", "--rm", "--interactive",
           "--network", "none", "--cap-drop", "ALL",
           "--security-opt", "no-new-privileges",
           "--pids-limit", "96", "--memory", "512m", "--cpus", "1",
           "--mount", f"type=bind,src={directory},dst={MOUNT},readonly"]
    if case in ANCHOR_CASES and case != "signed-witness-cold-cache-missing-anchor-refused":
        check(anchor_dir is not None and anchor_dir.is_dir(),
              "separate approved witness anchor directory missing")
        check(anchor_dir.parent != directory.parent and
              not anchor_dir.is_relative_to(directory),
              "witness anchor must not live inside signed APT repository")
        cmd += ["--mount", f"type=bind,src={anchor_dir},dst=/mnt/lanveriq-anchor,readonly"]
    cmd += ["--env", f"LANVERIQ_L2B23_CASE={case}",
            "--env", f"LANVERIQ_SYNTHETIC_WITNESS_L2={int(case in (*WITNESS_CASES, *ANCHOR_CASES))}",
            "--entrypoint", "/bin/bash", IMAGE, "-seuo", "pipefail"]
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
    # Permission guard MUST execute before argument shape validation:
    # otherwise a CI negative test could pass only due to a usage error.
    check(os.getenv("GITHUB_ACTIONS") == "true"
          and os.getenv("LANVERIQ_SIGNED_AGENT_APT_L2") == "1"
          and os.getenv("RUNNER_OS") == "Linux"
          and os.geteuid() != 0 and sys.platform == "linux",
          "CI-only, unprivileged Linux runner with explicit lab consent")
    check(len(sys.argv) == 3,
          "usage: signed-apt-ci AUDITED_AGENT_DEB PINNED_HISTORICAL_ELF")
    for binary in ("docker", "gpg", "dpkg-deb"):
        check(shutil.which(binary), f"missing native CI tool: {binary}")
    archive = pathlib.Path(sys.argv[1])
    check(archive.name == NAME and archive.is_file() and not archive.is_symlink(),
          "only regular exact offline Agent .deb is permitted")
    archive = archive.resolve(strict=True)
    check(archive.stat().st_size < 100 * 1024 * 1024,
          "unexpectedly large Agent archive")
    run((sys.executable, str(AUDIT), str(archive)))
    historical_elf = verify_historical_agent_checkout(
        pathlib.Path(sys.argv[2]))
    latest_elf = ROOT / "apps/agent/target/release/lanveriq-agent"
    check(latest_elf.is_file() and not latest_elf.is_symlink(),
          "current audited Cargo ELF missing")
    elf_digests = require_distinct_elf_bytes(latest_elf.read_bytes(),
                                             historical_elf.read_bytes())
    print("PASS: exact historical Git source and distinct ELF SHA256, "
          "not independent approved publisher provenance", flush=True)
    run(("docker", "pull", IMAGE), timeout=240)
    with tempfile.TemporaryDirectory(prefix="lanveriq-l2b23-secret-signers-") as home:
        with tempfile.TemporaryDirectory(prefix="lanveriq-l2b23-public-repos-") as root:
            with tempfile.TemporaryDirectory(prefix="lanveriq-l2b33-separate-anchor-") as anchor_base:
                return run_cases(pathlib.Path(home), pathlib.Path(root),
                                 pathlib.Path(anchor_base), archive, historical_elf,
                                 elf_digests)


def run_cases(signing, base, anchor_base, archive, historical_elf, elf_digests):
    homes = {}
    pubs = base / "public-keyrings"
    pubs.mkdir()
    for role in ("publisher", "attacker", "witness"):
        homes[role] = signing / (role + "-gnupg")
        private_signer(homes[role], pubs / (role + ".gpg"), role)
    historical = create_prior_version_archive(archive, base / PRIOR_NAME)
    src_dir = base / "distinct-elf-source"
    src_dir.mkdir()
    distinct_historical = create_prior_version_archive(
        archive, src_dir / PRIOR_NAME, historical_elf)
    for case in CASES:
        directory = base / case
        directory.mkdir(mode=0o755)
        anchor_dir = anchor_base / case if case in ANCHOR_CASES else None
        prepare_case(directory, archive, case, homes, pubs, historical,
                     distinct_historical, elf_digests, anchor_dir)
        execute_case(case, directory, anchor_dir)
    print(f"PASS: {len(CASES)} real APT Agent package L2 OCI cases; "
          "all private keys destroyed, never host-installed or release-approved")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
