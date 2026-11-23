#!/usr/bin/env python3
"""Inspect and rehearse a native .deb in a disposable root; never apt/dpkg install.

Uses dpkg-deb for native package metadata/extraction. No network or host writes.
Extraction/removal rehearsal is not a real dpkg upgrade/uninstall or Pop!_OS L3.
"""

import io
import os
import pathlib
import subprocess
import sys
import tarfile
import tempfile

NAME = "lanveriq-agent-offline-preview"
VERSION = "0.1.0~dev1"
BINARY = "usr/bin/lanveriq-agent"
UNIT = "usr/lib/systemd/system/lanveriq-agent.service"
FILES = {BINARY, UNIT}
DIRS = {"usr", "usr/bin", "usr/lib", "usr/lib/systemd", "usr/lib/systemd/system"}
ROOT = pathlib.Path(__file__).resolve().parents[4]
CHECK = ROOT / "apps/agent/tools/linux/verify-systemd-popos24.py"
SOURCE_UNIT = ROOT / "apps/agent/deploy/systemd/lanveriq-agent.service"


def fail(reason):
    raise ValueError(reason)


def run(*args):
    result = subprocess.run(args, capture_output=True, check=False)
    if result.returncode:
        fail(f"{args[0]} failed with code {result.returncode}")
    return result.stdout


def archive_members(raw, kind):
    if len(raw) > 128 * 1024 * 1024:
        fail(f"{kind} tar exceeds inspection limit")
    try:
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:*") as stream:
            members = stream.getmembers()
    except (tarfile.TarError, OSError) as exc:
        fail(f"malformed {kind} tar: {exc}")
    result = {}
    for item in members:
        path = item.name.removeprefix("./").rstrip("/")
        if not path:
            continue
        if path.startswith("/") or ".." in pathlib.PurePosixPath(path).parts:
            fail(f"{kind} contains dangerous path")
        if path in result:
            fail(f"{kind} contains duplicate archive member")
        result[path] = item
    return result


def validate(package):
    if not package.is_file() or package.is_symlink():
        fail("missing archive or symlink candidate")
    if package.stat().st_size > 100 * 1024 * 1024:
        fail("candidate package too large for isolated validation")
    info = run("dpkg-deb", "--field", str(package)).decode("utf-8")
    if f"Package: {NAME}\n" not in info or f"Version: {VERSION}\n" not in info:
        fail("unapproved package identity/version")
    if "Architecture: amd64\n" not in info:
        fail("unsupported package architecture")
    controls = archive_members(run("dpkg-deb", "--ctrl-tarfile", str(package)), "control")
    # dpkg-deb versions can add an ordinary md5sums member. It is inventory
    # metadata only, not a maintainer script or authority for signing.
    # Never permit postinst/preinst/prerm/postrm, triggers or conffiles.
    if not {"control"} <= set(controls) or not set(controls) <= {"control", "md5sums"}:
        fail(f"unapproved postinst/prerm/config/conffiles or maintainer control files: {sorted(controls)}")
    for name, member in controls.items():
        if not member.isfile() or member.uid != 0 or member.gid != 0 or (member.mode & 0o7777) != 0o644:
            fail(f"unsafe control archive member: {name}")
    contents = archive_members(run("dpkg-deb", "--fsys-tarfile", str(package)), "payload")
    if set(contents) != FILES | DIRS:
        fail(f"payload files/directories not allowlisted: {sorted(set(contents) ^ (FILES | DIRS))}")
    for path, member in contents.items():
        if member.uid != 0 or member.gid != 0:
            fail("package member must have root ownership")
        if path in DIRS:
            if not member.isdir() or (member.mode & 0o7777) != 0o755:
                fail("unexpected directory type/permissions")
        else:
            expected = 0o755 if path == BINARY else 0o644
            if not member.isfile() or (member.mode & 0o7777) != expected:
                fail("unexpected regular file type/permissions")
    with tempfile.TemporaryDirectory(prefix="lanveriq-offline-deb-rehearsal-") as base:
        root = pathlib.Path(base) / "candidate-root"
        root.mkdir()
        run("dpkg-deb", "--extract", str(package), str(root))
        binary, unit = root / BINARY, root / UNIT
        with binary.open("rb") as handle:
            if handle.read(4) != b"\x7fELF":
                fail("extracted binary is not ELF")
        if unit.read_bytes() != SOURCE_UNIT.read_bytes():
            fail("candidate service differs from approved source template")
        run(sys.executable, str(CHECK), str(unit))
        config = root / "etc/lanveriq-agent/agent.toml"
        state = root / "var/lib/private/lanveriq-agent/synthetic-state"
        if config.exists() or state.exists():
            fail("candidate contains unauthorized configuration or state")
        config.parent.mkdir(parents=True, mode=0o700)
        os.chmod(config.parent, 0o700)
        config.write_text("synthetic-operator-owned-no-secrets\n", encoding="utf-8")
        os.chmod(config, 0o600)
        state.parent.mkdir(parents=True, mode=0o700)
        os.chmod(state.parent, 0o700)
        state.write_bytes(b"synthetic-state-keep-on-uninstall")
        os.chmod(state, 0o600)
        # Second extraction and payload removal: ONLY a staged file rehearsal.
        run("dpkg-deb", "--extract", str(package), str(root))
        if config.read_text(encoding="utf-8") != "synthetic-operator-owned-no-secrets\n":
            fail("upgrade rehearsal modified operator configuration")
        if state.read_bytes() != b"synthetic-state-keep-on-uninstall":
            fail("upgrade rehearsal modified persistent state")
        binary.unlink()
        unit.unlink()
        if not config.is_file() or not state.is_file():
            fail("payload-only removal destroyed operator configuration or state")
        if (root / BINARY).exists() or (root / UNIT).exists():
            fail("package-owned payload remains after staged removal")
    return ("PASS: native .deb metadata/payload, no hooks/config/state/enable, "
            "disposable extraction+removal rehearsal; NOT dpkg install/purge or Pop!_OS L3")


def main(argv=None):
    arguments = sys.argv[1:] if argv is None else argv
    if len(arguments) != 1:
        print("FAIL: usage: verify-offline-agent-deb.py candidate.deb", file=sys.stderr)
        return 2
    try:
        print(validate(pathlib.Path(arguments[0])))
    except (ValueError, OSError, UnicodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
