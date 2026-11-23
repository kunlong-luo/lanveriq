#!/usr/bin/env python3
"""Audit an *unapproved* native Tauri Desktop .deb without installing it.

No host installation, package mutation, service control or privileged system access.
Passing this verifier is narrow archive evidence, not release signing or Pop!_OS L3.
"""
import io
import pathlib
import re
import stat
import subprocess
import sys
import tarfile
import tempfile

PACKAGE_NAMES = {"lanveriq-desktop", "lanveriq-desktop-offline-preview"}
EXECUTABLE = "usr/bin/lanveriq-desktop"
DESKTOP_NAMES = {
    "usr/share/applications/lanveriq-desktop.desktop",
    "usr/share/applications/com.lanveriq.desktop.desktop",
    "usr/share/applications/lanveriq-desktop-offline-preview.desktop",
}
ICON = re.compile(
    r"usr/share/icons/hicolor/(?:[0-9]{2,3}x[0-9]{2,3}|scalable)/apps/"
    r"(?:lanveriq-desktop(?:-offline-preview)?|com\.lanveriq\.desktop)\.png$"
)
FILES_MAX = 28
ARCHIVE_LIMIT = 120 * 1024 * 1024
# Only shared GUI framework runtime libraries may be resolved through dpkg.
# Anything that could install Lanveriq Agent, polkit helpers or system services
# is out of scope for this unapproved GUI-only package candidate.
APPROVED_DEPENDENCIES = frozenset({
    "libc6", "libgtk-3-0", "libwebkit2gtk-4.1-0",
    "libayatana-appindicator3-1", "libappindicator3-1",
    "librsvg2-2", "libssl3", "libssl3t64", "libjavascriptcoregtk-4.1-0",
    "libgdk-pixbuf-2.0-0", "libglib2.0-0", "libpango-1.0-0",
})
APPROVED_CONTROL_FIELDS = frozenset({
    "Package", "Version", "Architecture", "Section", "Priority", "Maintainer",
    "Description", "Installed-Size", "Homepage", "Depends",
})
DEPENDENCY = re.compile(
    r"([a-z0-9][a-z0-9+.-]*)(?: \((?:>=|<=|=|<<|>>) [0-9a-zA-Z.+:~\\-]+\))?"
)
ROOT = pathlib.Path(__file__).resolve().parents[3]


def check(condition, message):
    if not condition:
        raise ValueError(message)


def run(*args):
    result = subprocess.run(
        args, check=False, capture_output=True, timeout=25
    )
    check(result.returncode == 0, f"read-only dpkg-deb failed ({args[1]})")
    check(len(result.stdout) < ARCHIVE_LIMIT, "excessive archive output")
    return result.stdout


def members(raw, kind):
    check(len(raw) < ARCHIVE_LIMIT, f"{kind} archive too large")
    result = {}
    try:
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:*") as tar:
            for item in tar:
                path = item.name.removeprefix("./").rstrip("/")
                if path in ("", "."):
                    check(item.isdir(), f"unexpected {kind} root entry")
                    continue
                parts = pathlib.PurePosixPath(path).parts
                check(
                    not item.name.startswith("/") and ".." not in parts and path not in result,
                    f"unsafe or duplicate {kind} path",
                )
                check(len(result) < FILES_MAX, f"excessive {kind} archive members")
                result[path] = item
    except (tarfile.TarError, OSError) as exc:
        raise ValueError(f"malformed {kind} tar: {exc}") from exc
    return result


def exact_control(raw):
    check(len(raw) <= 32768, "unbounded Debian control metadata")
    fields = {}
    prior_key = None
    for line in raw.decode("utf-8").splitlines():
        if line == "":
            continue
        if line.startswith((" ", "\t")):
            # Multiline Description is standard Debian metadata; a continued
            # Depends/Pre-Depends/Package field must never be silently ignored.
            check(prior_key == "Description", "unexpected multiline control field")
            continue
        check(":" in line, "malformed Debian control field")
        k, v = line.split(":", 1)
        check(k not in fields, "duplicate Debian control field")
        check(k in APPROVED_CONTROL_FIELDS, f"forbidden Debian control field: {k}")
        fields[k] = v.strip()
        prior_key = k
    check(fields.get("Package") in PACKAGE_NAMES, "unexpected GUI package identity")
    check(fields.get("Version") == "0.1.0", "unexpected GUI package version")
    check(fields.get("Architecture") == "amd64", "unexpected GUI architecture")
    dependency_string = fields.get("Depends", "")
    if dependency_string:
        items = [item.strip() for item in dependency_string.split(",")]
        for item in items:
            match = DEPENDENCY.fullmatch(item)
            check(match is not None, "malformed or alternative Debian dependency")
            check(match.group(1) in APPROVED_DEPENDENCIES,
                  f"unapproved GUI package dependency: {match.group(1)}")
    return fields


def inspect_desktop_entry(contents):
    # No user desktop autostart and no shell/commands besides the fixed GUI.
    data = contents.decode("utf-8")
    check(len(data) < 8192, "unbounded desktop launcher")
    keys = {}
    section = ""
    for line in data.splitlines():
        value = line.strip()
        if value.startswith("["):
            section = value
        elif "=" in value and not value.startswith("#") and section == "[Desktop Entry]":
            key, val = value.split("=", 1)
            check(key not in keys, "duplicate desktop entry key")
            keys[key] = val
    check(keys.get("Type") == "Application", "not an application launcher")
    check(keys.get("Exec") in ("lanveriq-desktop", "/usr/bin/lanveriq-desktop"), "unapproved launcher Exec")
    check(keys.get("Terminal") != "true", "interactive terminal launcher forbidden")
    for key in ("Hidden", "NoDisplay", "X-GNOME-Autostart-enabled"):
        check(keys.get(key) != "true", f"hidden/autostart desktop entry: {key}")
    check(not any(k.startswith("X-GNOME-Autostart") for k in keys), "autostart desktop metadata forbidden")
    return data


def validate(package):
    check(package.is_file() and not package.is_symlink(), "not a regular package")
    check(256 <= package.stat().st_size < ARCHIVE_LIMIT, "package archive size unacceptable")
    fields = exact_control(run("dpkg-deb", "--field", str(package)))
    ctrl = members(run("dpkg-deb", "--ctrl-tarfile", str(package)), "control")
    check(set(ctrl) >= {"control"}, "missing Debian control metadata")
    check(set(ctrl) <= {"control", "md5sums"}, "maintainer scripts/triggers/conffiles forbidden")
    for name, entry in ctrl.items():
        check(entry.isfile() and entry.uid == 0 and entry.gid == 0, f"unsafe control entry {name}")
        check((entry.mode & 0o7777) == 0o644, f"unsafe control mode {name}")

    payload = members(run("dpkg-deb", "--fsys-tarfile", str(package)), "data")
    binaries = []
    launchers = []
    icons = []
    for path, entry in payload.items():
        check(entry.uid == 0 and entry.gid == 0, "non-root-owned payload")
        mode = entry.mode & 0o7777
        if entry.isdir():
            check(mode == 0o755, f"unsafe directory permissions: {path}")
            check(path.startswith("usr/") or path == "usr", "system or user directory in GUI package")
            continue
        check(entry.isfile() and not entry.issym() and not entry.islnk(), f"non-regular payload: {path}")
        if path == EXECUTABLE:
            check(mode == 0o755, "GUI ELF must be executable")
            binaries.append(path)
        elif path in DESKTOP_NAMES:
            check(mode == 0o644, "desktop entry mode unacceptable")
            launchers.append(path)
        elif ICON.fullmatch(path):
            check(mode == 0o644, "icon mode unacceptable")
            icons.append(path)
        else:
            check(False, f"unapproved GUI package file: {path}")

    check(binaries == [EXECUTABLE], "exactly one GUI executable required")
    check(len(launchers) == 1, "exactly one launcher required")
    check(1 <= len(icons) <= 15,
          "only approved icon PNG files required; check the explicit Tauri bundle.icon list")
    check(len(payload) < FILES_MAX, "too many package payload entries")
    check(
        all(path in {"usr", "usr/bin", "usr/share", "usr/share/applications", "usr/share/icons", "usr/share/icons/hicolor"} or
            any(path == str(pathlib.PurePosixPath(item).parent) or
                str(pathlib.PurePosixPath(item).parent).startswith(path + "/")
                for item in binaries + launchers + icons)
            for path, item in payload.items() if item.isdir()),
        "directory not an ancestor of approved GUI assets",
    )
    with tempfile.TemporaryDirectory(prefix="lanveriq-gui-deb-readonly-") as tmp:
        destination = pathlib.Path(tmp) / "uninstalled-root"
        destination.mkdir()
        run("dpkg-deb", "--extract", str(package), str(destination))
        with (destination / EXECUTABLE).open("rb") as fd:
            check(fd.read(4) == b"\x7fELF", "GUI binary is not ELF")
        inspect_desktop_entry((destination / launchers[0]).read_bytes())
        for icon in icons:
            with (destination / icon).open("rb") as fd:
                check(fd.read(8) == b"\x89PNG\r\n\x1a\n", "GUI icon not a PNG")
        check(not (destination / "etc").exists(), "GUI cannot install configuration")
        check(not (destination / "usr/lib/systemd").exists(), "GUI cannot package a daemon")
    return (
        f"PASS: unapproved GUI-only {fields['Package']} {fields['Version']} "
        "Debian archive has one native launcher, icons, no hooks, no Agent, "
        "no service/config; temporary extraction only, NOT installed/signed/approved"
    )


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("usage: verify-desktop-deb.py candidate.deb")
        print(validate(pathlib.Path(sys.argv[1])))
    except (ValueError, OSError, UnicodeError, subprocess.TimeoutExpired) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
