#!/usr/bin/env python3
"""CI-only observed X11 library ownership and installed APT graph; never install.
Only the child created by window_smoke is observed. No host data collection in GUI.
"""
import hashlib
import json
import os
import pathlib
import re

from package_audit import ROOT, NAME, DIRECT, check, load, digest

chain = load("gpui_apt_chain", "verify_desktop_apt_installed_closure.py")
owner = chain.owner
BINARY = ROOT / "target/debug" / NAME
SNAPSHOT = ROOT / "candidate-mapped-libraries.json"
REPORT = ROOT / "candidate-runtime-evidence.json"
SYSTEM_ROOTS = ("/usr/lib/x86_64-linux-gnu/", "/lib/x86_64-linux-gnu/",
                "/usr/lib64/", "/lib64/")
TOOL_ROOTS = {"coreutils", "dpkg", "systemd"}
MAX_MAP_BYTES = 2 * 1024 * 1024
MAX_LIBS = 256

def system_library(path):
    return (isinstance(path, str) and len(path) <= 512
            and path.startswith(SYSTEM_ROOTS)
            and ".." not in pathlib.PurePosixPath(path).parts
            and re.fullmatch(r"[a-zA-Z0-9_./+:-]+", path) is not None
            and re.search(r"\.so(?:\.[a-zA-Z0-9._+-]+)?$", path) is not None)

def parse_maps(raw, executable):
    check(len(raw) <= MAX_MAP_BYTES, "maps bound exceeded")
    libs = set()
    unexpected = 0
    for line in raw.splitlines():
        fields = line.split(None, 5)
        check(len(fields) >= 5, "malformed maps record")
        if "x" not in fields[1] or len(fields) < 6:
            continue
        path = fields[5]
        if path == executable or path in ("[vdso]", "[vsyscall]", "[vectors]"):
            continue
        if system_library(path):
            libs.add(path)
        else:
            # Do not persist arbitrary file names, addresses or deleted paths.
            unexpected += 1
    check(0 < len(libs) <= MAX_LIBS, "missing or excessive executable library mappings")
    return sorted(libs), unexpected

def capture_child(process, binary):
    check(process.poll() is None and os.getuid() != 0, "expected running non-root CI child")
    binary = binary.resolve(strict=True)
    proc = pathlib.Path("/proc") / str(process.pid)
    check((proc / "exe").resolve(strict=True) == binary, "unexpected child executable")
    with (proc / "maps").open("r", encoding="utf-8") as stream:
        text = stream.read(MAX_MAP_BYTES + 1)
    libraries, unexpected = parse_maps(text, str(binary))
    check(process.poll() is None and (proc / "exe").resolve(strict=True) == binary,
          "child disappeared or executable changed")
    return {"schema": "lanveriq.gpui.x11-executable-mappings-v1",
            "source_sha": os.environ["GITHUB_SHA"],
            "elf_sha256": digest(binary), "system_libraries": libraries,
            "unclassified_executable_mappings": unexpected,
            "scope": "SYNTHETIC_X11_SINGLE_POST_RESIZE_SNAPSHOT"}

def owner_of(path):
    # Ubuntu usrmerge may list a /lib path for the same /usr/lib inode.
    real = pathlib.Path(path).resolve(strict=True)
    check(real.is_file() and system_library(str(real)), "mapped file escaped system library root")
    choices = [path, str(real)]
    for candidate in tuple(choices):
        if candidate.startswith("/usr/lib/") or candidate.startswith("/usr/lib64/"):
            choices.append(candidate.removeprefix("/usr"))
        elif candidate.startswith("/lib/") or candidate.startswith("/lib64/"):
            choices.append("/usr" + candidate)
    for candidate in dict.fromkeys(choices):
        p = pathlib.Path(candidate)
        if not p.is_file() or p.resolve(strict=True) != real:
            continue
        try:
            return owner.owner_for_path(p)
        except ValueError:
            continue
    return None

def roots_from_cache(cache, packages):
    result = {}
    for name in sorted(packages):
        check(name in cache and cache[name].installed is not None, "missing installed package root: " + name)
        key, version = chain.name_version(cache[name].installed)
        result[key] = version
    return result

def main():
    import apt
    import apt_pkg
    apt_pkg.init_system()
    check(os.getuid() != 0 and SNAPSHOT.is_file() and SNAPSHOT.stat().st_size <= 200_000,
          "missing or oversized CI snapshot")
    snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    check(snapshot.get("schema") == "lanveriq.gpui.x11-executable-mappings-v1"
          and snapshot.get("source_sha") == os.environ["GITHUB_SHA"]
          and snapshot.get("elf_sha256") == digest(BINARY)
          and snapshot.get("scope") == "SYNTHETIC_X11_SINGLE_POST_RESIZE_SNAPSHOT",
          "snapshot provenance mismatch")
    libs = snapshot["system_libraries"]
    check(isinstance(libs, list) and 0 < len(libs) <= MAX_LIBS
          and all(system_library(p) for p in libs) and len(set(libs)) == len(libs),
          "invalid mapped library inventory")
    other = snapshot["unclassified_executable_mappings"]
    check(type(other) is int and 0 <= other <= 10000, "invalid unclassified count")
    observed = []
    owners = set()
    for path in libs:
        package = owner_of(path)
        if package is not None:
            owners.add(package)
        observed.append({"system_library": path, "installed_owner": package})
    cache = apt.Cache()
    roots = roots_from_cache(cache, DIRECT | TOOL_ROOTS | owners)
    graph = chain.inspect_installed(cache, roots)
    for entry in graph["packages"]:
        entry.update(chain.copyright_input(entry["name"]))
    missing = sum(p["copyright_review"] == "MISSING_MATERIAL_LEGAL_REVIEW_OPEN"
                  for p in graph["packages"])
    unresolved = sum(p["installed_owner"] is None for p in observed)
    evidence = {
        "schema": "lanveriq.gpui.untrusted-runtime-evidence-v1",
        "source_sha": snapshot["source_sha"], "elf_sha256": snapshot["elf_sha256"],
        "snapshot_sha256": digest(SNAPSHOT),
        "observed_x11_libraries": observed, "unresolved_library_owners": unresolved,
        "unclassified_executable_mappings": other,
        "installed_root_versions": roots, "graph": graph,
        "copyright_missing_count": missing,
        "scope": "SYNTHETIC_X11_OBSERVATION_AND_INSTALLED_APT_MANDATORY_SATISFIERS",
        "runtime_closure": "OPEN_NOT_COMPLETE",
        "wayland_gpu_ime_and_other_paths": "NOT_TESTED",
        "license_review": "OPEN_NOT_APPROVED", "install_authorized": False,
        "release_approved": False,
    }
    payload = json.dumps(evidence, indent=2, sort_keys=True) + "\n"
    check(len(payload.encode("utf-8")) <= 4_000_000, "runtime evidence bound exceeded")
    with REPORT.open("x", encoding="utf-8") as out:
        out.write(payload)
    print("Observed X11 executable system libraries:", len(libs))
    print("Unresolved library owners:", unresolved, "Unclassified executable mappings:", other)
    print("APT installed mandatory satisfier nodes:", len(graph["packages"]))
    print("APT installed mandatory dependency groups:", len(graph["mandatory_dependency_groups"]))
    print("Missing Debian copyright materials:", missing)
    # Preserve gaps as evidence; they are not interpreted as a complete closure.
    print("PASS: bounded runtime evidence generated; closure/license/Pop!_OS remain OPEN")

if __name__ == "__main__":
    main()
