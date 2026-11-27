#!/usr/bin/env python3
"""Nonrelease audit of a disposable GPUI-only Debian archive. Never install or publish."""
import hashlib
import importlib.util
import os
import pathlib
import shutil
import subprocess
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
OLD_TOOLS = ROOT.parent / "tools"
NAME = "lanveriq-desktop-gpui-candidate"
VERSION = "0.1.0~dev1"
EXECUTABLE = "usr/bin/" + NAME
LAUNCHER = "usr/share/applications/" + NAME + ".desktop"
NOTICE = "usr/share/doc/" + NAME + "/README.nonrelease"
FILES = {EXECUTABLE: 0o755, LAUNCHER: 0o644, NOTICE: 0o644}
DIRECT = {"libc6", "libgcc-s1", "libxcb1", "libxkbcommon0", "libxkbcommon-x11-0"}
ENV = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"}
DESKTOP = f"""[Desktop Entry]
Type=Application
Name=Lanveriq Desktop (Offline Candidate)
Exec=/{EXECUTABLE}
Terminal=false
Categories=Utility;
"""
DISCLAIMER = """NONRELEASE - NOT FOR DISTRIBUTION OR INSTALLATION.
GUI-only temporary archive for static audit. No Agent, service, privilege broker,
signing approval or production authorization. Full runtime closure, third-party
license/resource notices, SBOM and Pop!_OS acceptance remain unverified.
"""

def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, OLD_TOOLS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

archive = load("desktop_archive_common", "verify-desktop-deb.py")
abi = load("desktop_abi_common", "verify_desktop_deb_shlibdeps_audit.py")
check = archive.check

def run(*args, timeout=90):
    return subprocess.run(args, check=True, capture_output=True, env=ENV,
                          timeout=timeout).stdout

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def validate_relations(groups):
    check(bool(groups), "missing generated ABI dependencies")
    names = set()
    for group in groups:
        check(len(group) == 1, "alternative ABI dependency not reviewed")
        item = group[0]
        name = item["name"]
        check(name in DIRECT and name not in names, "unexpected or duplicate ABI dependency")
        check(item["op"] in ("", ">=") and bool(item["version"]) == bool(item["op"]),
              "unreviewed dependency operator")
        names.add(name)
    check("libc6" in names, "libc ABI dependency missing")

def validate_members(control, payload):
    check(set(control) == {"control"}, "unexpected control member (scripts/triggers forbidden)")
    member = control["control"]
    check(member.isfile() and member.uid == 0 and member.gid == 0
          and member.mode & 0o7777 == 0o644, "unsafe control file")
    dirs = {str(parent) for name in FILES for parent in pathlib.PurePosixPath(name).parents
            if str(parent) != "."}
    check(set(payload) == set(FILES) | dirs, "unexpected or missing GUI payload member")
    for path, entry in payload.items():
        check(entry.uid == 0 and entry.gid == 0, "non-root archive ownership")
        if path in FILES:
            check(entry.isfile() and entry.mode & 0o7777 == FILES[path],
                  "unsafe file type or mode")
        else:
            check(entry.isdir() and entry.mode & 0o7777 == 0o755,
                  "unsafe directory type or mode")

def main(profile="debug"):
    import apt_pkg
    apt_pkg.init_system()
    check(os.getuid() != 0 and os.geteuid() != 0, "non-root CI only")
    check(profile in ("debug", "release"), "unsupported audit profile")
    original = ROOT / "target" / profile / NAME
    check(original.is_file() and not original.is_symlink(), "compiled GUI missing")
    original_sha = digest(original)
    with tempfile.TemporaryDirectory(prefix="lanveriq-gpui-deb-audit-") as temp:
        work = pathlib.Path(temp)
        stage = work / "stage"
        stage.mkdir(mode=0o755)
        for name in FILES:
            (stage / name).parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        binary = stage / EXECUTABLE
        shutil.copyfile(original, binary)
        run("/usr/bin/strip", "--strip-unneeded", str(binary))
        binary.chmod(0o755)
        # Old reviewed helper invokes real dpkg-shlibdeps, not guessed floors.
        depends, warnings = abi.official_deps(binary)
        validate_relations(abi.parse_relations(depends, apt_pkg))
        for name, value in ((LAUNCHER, DESKTOP), (NOTICE, DISCLAIMER)):
            (stage / name).write_text(value, encoding="utf-8")
            (stage / name).chmod(0o644)
        run("/usr/bin/desktop-file-validate", str(stage / LAUNCHER))
        control_dir = stage / "DEBIAN"
        control_dir.mkdir(mode=0o755)
        control = f"""Package: {NAME}
Version: {VERSION}
Architecture: amd64
Section: utils
Priority: optional
Maintainer: Lanveriq CI <noreply@example.invalid>
Depends: {depends}
Description: Lanveriq GPUI nonrelease static audit candidate
 Not for distribution or installation.
"""
        (control_dir / "control").write_text(control, encoding="utf-8")
        (control_dir / "control").chmod(0o644)
        # Root ownership in the archive only, no sudo or package transaction.
        deb = work / (NAME + ".deb")
        run("/usr/bin/dpkg-deb", "--root-owner-group", "--build", str(stage), str(deb))
        check(256 <= deb.stat().st_size < archive.ARCHIVE_LIMIT, "archive size limit")
        ctrl = archive.members(archive.run("dpkg-deb", "--ctrl-tarfile", str(deb)), "control")
        data = archive.members(archive.run("dpkg-deb", "--fsys-tarfile", str(deb)), "data")
        validate_members(ctrl, data)
        # Extract only after the archive path/type/mode allowlist passes.
        control_out = work / "control-out"
        dest = work / "uninstalled-root"
        run("/usr/bin/dpkg-deb", "--control", str(deb), str(control_out))
        check((control_out / "control").read_text(encoding="utf-8") == control, "control drift")
        run("/usr/bin/dpkg-deb", "--extract", str(deb), str(dest))
        for name in FILES:
            check(digest(dest / name) == digest(stage / name), "payload digest mismatch")
        run("/usr/bin/desktop-file-validate", str(dest / LAUNCHER))
        check(digest(original) == original_sha, "original CI executable changed")
        print("PASS: official dpkg-shlibdeps direct version floors: " + depends)
        print("dpkg-shlibdeps diagnostic bytes:", len(warnings.encode("utf-8")))
        if warnings:
            print("dpkg-shlibdeps diagnostics (disposable CI):", warnings.replace(str(work), "<temporary-audit>"))
        print("PASS: GUI-only archive exact control/payload/modes/digests; no hooks or services")
        print("original " + profile + " ELF bytes:", original.stat().st_size)
        print("stripped audit ELF bytes:", binary.stat().st_size)
        print("temporary .deb bytes:", deb.stat().st_size)
        print("temporary .deb SHA256:", digest(deb))
    print("PASS: temporary archive deleted; no install or upload; runtime closure, license, L3 OPEN")

if __name__ == "__main__":
    main()
