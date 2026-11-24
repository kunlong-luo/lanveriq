#!/usr/bin/env python3
"""L2B-11: strictly read-only Pop!_OS24/COSMIC host compatibility preflight.

For a later operator-authorized local UI. Never installs, updates apt metadata,
starts the Agent, reports user/host IDs or verifies real GUI execution.
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent
CONFIG = ROOT.parent / "src-tauri" / "tauri.conf.json"
ABI = (
    "libc6 (>= 2.39)", "libcairo2 (>= 1.10.0)",
    "libdbus-1-3 (>= 1.9.14)", "libgcc-s1 (>= 4.2)",
    "libgdk-pixbuf-2.0-0 (>= 2.36.9)",
    "libglib2.0-0t64 (>= 2.66.0)", "libgtk-3-0t64 (>= 3.21.5)",
    "libjavascriptcoregtk-4.1-0", "libsoup-3.0-0 (>= 3.0.3)",
    "libwebkit2gtk-4.1-0 (>= 2.41.90)",
)
COSMIC = ("cosmic-session", "cosmic-comp", "xdg-desktop-portal-cosmic")
VERSION = re.compile(r"[A-Za-z0-9.+:~_-]{1,180}\Z")
HASH = re.compile(r"[0-9a-f]{64}\Z")
OS_FIELD = re.compile(r'^(ID|VERSION_ID)=(?:"([^"\n]{1,64})"|([A-Za-z0-9._+!-]{1,64}))$')

def require(ok, error):
    if not ok:
        raise ValueError(error)

def os_identity(raw):
    require(isinstance(raw, str) and 0 < len(raw) < 65536, "INVALID_OS_RELEASE")
    parsed = {}
    for line in raw.splitlines():
        if not line.startswith(("ID=", "VERSION_ID=")):
            continue
        match = OS_FIELD.fullmatch(line)
        require(match is not None, "INVALID_OS_RELEASE")
        key, quoted, plain = match.groups()
        require(key not in parsed, "DUPLICATE_OS_IDENTITY")
        parsed[key] = quoted if quoted is not None else plain
    require(parsed.get("ID") == "pop" and parsed.get("VERSION_ID") == "24.04",
            "NOT_POP_OS_TARGET")
    return {"id": "pop", "version": "24.04"}

def session_claim(env):
    d = env.get("XDG_CURRENT_DESKTOP", "")
    t = env.get("XDG_SESSION_TYPE", "")
    w = env.get("WAYLAND_DISPLAY", "")
    require(isinstance(d, str) and len(d) <= 256 and
            "COSMIC" in d.upper().split(":") and t == "wayland" and
            isinstance(w, str) and re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", w),
            "NOT_COSMIC_WAYLAND_SESSION")
    return {"environment": "COSMIC_WAYLAND_CLAIM_ONLY",
            "compositor_running_proven": False,
            "display_socket_saved": False}

def sources_hash(root=pathlib.Path("/etc/apt")):
    root = pathlib.Path(root)
    entries = []
    legacy = root / "sources.list"
    if legacy.exists() or legacy.is_symlink():
        entries.append(legacy)
    directory = root / "sources.list.d"
    if directory.exists():
        require(directory.is_dir() and not directory.is_symlink(),
                "INVALID_APT_SOURCES_DIRECTORY")
        entries.extend(sorted((x for x in directory.iterdir()
                               if x.suffix in (".sources", ".list")), key=lambda x: x.name))
    require(1 <= len(entries) <= 64, "MISSING_OR_EXCESS_APT_SOURCES")
    h = hashlib.sha256()
    count = 0
    for path in entries:
        require(path.is_file() and not path.is_symlink(), "UNTRUSTED_APT_SOURCE_PATH")
        data = path.read_bytes()
        count += len(data)
        require(0 < len(data) <= 131072 and count <= 1048576,
                "EXCESS_APT_SOURCES_BYTES")
        name = path.name.encode("utf-8")
        h.update(len(name).to_bytes(4, "big") + name)
        h.update(len(data).to_bytes(8, "big") + data)
    # Do not expose APT URIs, credentials, source file names or signing keys.
    return {"file_count": len(entries), "contents_sha256": h.hexdigest(),
            "apt_repository_signature_trust": "NOT_VERIFIED"}

def reviewed_config(path=CONFIG):
    path = pathlib.Path(path)
    require(path.is_file() and not path.is_symlink()
            and path.stat().st_size < 65536, "INVALID_DESKTOP_CONFIG")
    bundle = json.loads(path.read_text(encoding="utf-8")).get("bundle", {})
    require(bundle.get("active") is False and bundle.get("targets") == [],
            "RELEASE_BUNDLE_LOCK_LIFTED")
    require(set(bundle) <= {"active", "targets", "icon", "linux"}
            and set(bundle.get("linux", {})) == {"deb"}
            and set(bundle["linux"]["deb"]) == {"depends"},
            "UNREVIEWED_DEBIAN_INSTALLER_HOOK_OR_ASSET")
    deps = bundle["linux"]["deb"].get("depends")
    require(type(deps) is list and tuple(deps) == ABI,
            "REVIEWED_ABI_BASELINE_DRIFT")
    return deps

def checked_version(s):
    require(isinstance(s, str) and VERSION.fullmatch(s) is not None,
            "UNSAFE_APT_PACKAGE_VERSION")
    return s

def check_packages(cache, apt_pkg, deps):
    require(tuple(deps) == ABI, "REVIEWED_ABI_BASELINE_DRIFT")
    observed = []
    for dep in deps:
        try:
            parsed = apt_pkg.parse_depends(dep, False)
        except (TypeError, ValueError, SystemError) as err:
            raise ValueError("INVALID_APT_RELATION") from err
        require(len(parsed) == 1 and len(parsed[0]) == 1, "ABI_ALTERNATIVE_NOT_REVIEWED")
        name, minimum, op = parsed[0][0]
        require(op in ("", ">=") and (bool(minimum) == (op == ">="))
                and re.fullmatch(r"[a-z0-9][a-z0-9.+-]{0,120}", name),
                "INVALID_ABI_PACKAGE_RELATION")
        require(name in cache and cache[name].candidate is not None,
                "NO_LOCAL_ABI_APT_CANDIDATE: " + name)
        candidate = checked_version(cache[name].candidate.version)
        if minimum:
            require(apt_pkg.version_compare(candidate, minimum) >= 0,
                    "ABI_CANDIDATE_VERSION_TOO_OLD: " + name)
        observed.append({"package": name, "minimum": minimum or None,
                         "cached_candidate": candidate,
                         "installed": cache[name].installed is not None})
    cosmic = []
    for name in COSMIC:
        require(name in cache and cache[name].installed is not None,
                "COSMIC_COMPONENT_NOT_INSTALLED: " + name)
        cosmic.append({"package": name,
                       "installed_version": checked_version(cache[name].installed.version)})
    return observed, cosmic

def facts(release, arch, env, apt_sources, config, cache, apt_pkg, root):
    require(root is False, "PRIVILEGED_GUI_PREFLIGHT_FORBIDDEN")
    parsed = os_identity(release)
    require(arch == "amd64", "UNSUPPORTED_POP_OS_ARCH")
    session = session_claim(env)
    deps = reviewed_config(config)
    packages, cosmic = check_packages(cache, apt_pkg, deps)
    require(apt_sources["file_count"] > 0 and
            HASH.fullmatch(apt_sources["contents_sha256"]),
            "INVALID_REPOSITORY_FINGERPRINT")
    return {
        "schema": "lanveriq.desktop.untrusted-popos-local-preflight.v1",
        "host_os": parsed, "architecture": "amd64",
        "session": session, "reviewed_abi_cache": packages,
        "cosmic_components": cosmic, "apt_sources": apt_sources,
        "apt_cache_updated": False, "popos_install_solver": "NOT_RUN",
        "physical_l3": "NOT_APPROVED", "gui_actual_launch": "NOT_TESTED",
        "signed_package_provenance": "NOT_VERIFIED",
        "license_approval": "OPEN", "release_approved": False,
        "install_authorized": False, "agent_start_authorized": False,
    }

def inspect():
    require(os.geteuid() != 0, "PRIVILEGED_GUI_PREFLIGHT_FORBIDDEN")
    os_file = pathlib.Path("/etc/os-release").read_text(encoding="utf-8")
    os_identity(os_file)  # Refuse Ubuntu *before* scanning files or apt sources.
    dpkg = subprocess.run(["/usr/bin/dpkg", "--print-architecture"],
                          shell=False, capture_output=True, timeout=10, check=False)
    require(dpkg.returncode == 0, "DPKG_ARCH_UNAVAILABLE")
    env = {k: os.environ.get(k, "") for k in
           ("XDG_CURRENT_DESKTOP", "XDG_SESSION_TYPE", "WAYLAND_DISPLAY")}
    session_claim(env)
    repo = sources_hash()
    try:
        import apt
        import apt_pkg
    except ImportError as err:
        raise ValueError("SYSTEM_PYTHON_APT_UNAVAILABLE") from err
    apt_pkg.init_system()
    return facts(os_file, dpkg.stdout.decode("utf-8").strip(), env,
                 repo, CONFIG, apt.Cache(), apt_pkg, False)

def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("mode", choices=("inspect",))
    cli.add_argument("--output", type=pathlib.Path, required=True)
    args = cli.parse_args()
    require(not args.output.exists() and not args.output.is_symlink(),
            "PREFLIGHT_RECEIPT_ALREADY_EXISTS")
    record = inspect()
    with os.fdopen(os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600),
                   "w", encoding="utf-8") as out:
        json.dump(record, out, sort_keys=True, indent=2)
        out.write("\n")
    print("PASS: Pop!_OS 24.04 read-only local preflight only; physical L3 NOT APPROVED")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError,
            subprocess.SubprocessError) as err:
        print("NOT_APPROVED: " + str(err), file=sys.stderr)
        sys.exit(2)
