#!/usr/bin/env python3
"""Nonrelease audit of a disposable GPUI-only Debian archive. Never install or publish."""
import hashlib
import importlib.util
import os
import pathlib
import re
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

LOADER = "/lib64/ld-linux-x86-64.so.2"
DIVERTED = LOADER + ".usr-is-merged"
DIVERSION_HEADER = "dpkg-shlibdeps: warning: diversions involved - output may be incorrect"
LAYOUT_WARNING = "dpkg-shlibdeps: warning: binaries to analyze should already be installed in their package's directory"

def classify_diagnostics(raw):
    # Only the exact previously observed pair and synthetic-layout diagnostic
    # are eligible for independent corroboration. Never suppress warnings.
    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    known = []
    while lines:
        line = lines.pop(0)
        if line == LAYOUT_WARNING:
            check("layout" not in known, "duplicate layout warning")
            known.append("layout")
        elif line == DIVERSION_HEADER:
            check(bool(lines), "missing diversion detail")
            detail = lines.pop(0)
            kind = {"diversion by libc6 from: " + LOADER: "from",
                    "diversion by libc6 to: " + DIVERTED: "to"}.get(detail)
            check(kind is not None and kind not in known, "unreviewed diversion diagnostic")
            known.append(kind)
        else:
            raise ValueError("unreviewed dpkg-shlibdeps diagnostic")
    check(("from" in known) == ("to" in known), "incomplete loader diversion warning")
    return {"loader_diversion": "from" in known, "synthetic_layout": "layout" in known}

def verify_installed_floors(groups, apt_pkg):
    check({g[0]["name"] for g in groups} == DIRECT, "incomplete direct ABI package set")
    installed = {}
    for group in groups:
        item = group[0]
        name = item["name"]
        raw = run("/usr/bin/dpkg-query", "-W",
                  "-f=${Package}|${Architecture}|${Status}|${Version}\\n",
                  name + ":amd64", timeout=10).decode("utf-8").strip()
        fields = raw.split("|")
        check(len(fields) == 4 and fields[:3] == [name, "amd64", "install ok installed"]
              and re.fullmatch(r"[A-Za-z0-9.+:~_-]{1,180}", fields[3]) is not None,
              "unexpected installed ABI package metadata")
        check(not item["op"] or apt_pkg.version_compare(fields[3], item["version"]) >= 0,
              "installed direct package is below official symbol floor")
        installed[name] = fields[3]
    return installed

def corroborate_loader(binary, diagnostic):
    elf = run("/usr/bin/readelf", "-l", str(binary), timeout=15).decode("utf-8")
    interp = re.findall(r"\[Requesting program interpreter: ([^\]]+)\]", elf)
    check(interp == [LOADER], "unexpected ELF interpreter")
    real = pathlib.Path(LOADER).resolve(strict=True)
    check(str(real) == "/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2"
          and real.is_file(), "unexpected canonical loader")
    # Query the Debian database, not just the symlink name. usrmerge may
    # record the legacy /lib path; both names must resolve to the same file.
    names = []
    for candidate in (str(real), str(real).removeprefix("/usr")):
        if pathlib.Path(candidate).resolve(strict=True) != real:
            continue
        result = subprocess.run(["/usr/bin/dpkg-query", "-S", candidate], env=ENV,
                                capture_output=True, timeout=10)
        if result.returncode == 0:
            rows = result.stdout.decode("utf-8").splitlines()
            check(len(rows) == 1 and rows[0] in ("libc6:amd64: " + candidate,
                                                   "libc6: " + candidate),
                  "unexpected canonical loader package owner")
            names.append(candidate)
        else:
            check(result.returncode == 1, "loader package lookup failed")
    check(bool(names), "canonical loader package owner missing")
    divert_owner = run("/usr/bin/dpkg-divert", "--listpackage", LOADER, timeout=10).decode("utf-8").strip()
    truename = run("/usr/bin/dpkg-divert", "--truename", LOADER, timeout=10).decode("utf-8").strip()
    check((divert_owner, truename) in (("libc6", DIVERTED), ("libc6:amd64", DIVERTED), ("", LOADER)),
          "unreviewed registered loader diversion")
    check(not diagnostic["loader_diversion"] or (divert_owner in ("libc6", "libc6:amd64") and truename == DIVERTED),
          "warning not corroborated by registered loader diversion")
    return {"interpreter": LOADER, "canonical_loader": str(real),
            "canonical_loader_sha256": digest(real), "owner": "libc6:amd64",
            "registered_diversion_owner": divert_owner or "NONE",
            "registered_truename": truename,
            "warning_scope": diagnostic,
            "release_abi_approval": False}

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
        groups = abi.parse_relations(depends, apt_pkg)
        validate_relations(groups)
        # Keep actual diagnostics visible before rejecting an unknown warning.
        print("dpkg-shlibdeps diagnostic bytes:", len(warnings.encode("utf-8")))
        if warnings:
            print("dpkg-shlibdeps diagnostics (disposable CI):", warnings.replace(str(work), "<temporary-audit>"))
        diagnostic = classify_diagnostics(warnings)
        installed = verify_installed_floors(groups, apt_pkg)
        loader = corroborate_loader(binary, diagnostic)
        print("PASS: installed direct ABI package versions cover official floors:", installed)
        print("PASS: ELF loader and registered diversion corroborated:", loader)
        print("ABI scope: Ubuntu installed-state evidence only; warnings retained; release ABI OPEN")
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
        print("PASS: GUI-only archive exact control/payload/modes/digests; no hooks or services")
        print("original " + profile + " ELF bytes:", original.stat().st_size)
        print("stripped audit ELF bytes:", binary.stat().st_size)
        print("temporary .deb bytes:", deb.stat().st_size)
        print("temporary .deb SHA256:", digest(deb))
    print("PASS: temporary archive deleted; no install or upload; runtime closure, license, L3 OPEN")

if __name__ == "__main__":
    main()
