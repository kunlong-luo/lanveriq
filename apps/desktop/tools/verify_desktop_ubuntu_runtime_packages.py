#!/usr/bin/env python3
"""L2B-6 Ubuntu 24.04 CI installed-library ownership/metadata intake.

Read only ldconfig -p, dpkg-query and installed Debian copyright notices.
The executable being audited is NEVER executed; no ldd; no license approval.
This report does not resolve transitive dependencies or prove Pop!_OS ABI.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_elf", HERE / "verify_desktop_elf_runtime_evidence.py"
)
elfguard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(elfguard)

def require(ok, message):
    if not ok:
        raise ValueError(message)

LIB_CACHE_LINE = re.compile(r"^\s*(\S+)\s+\(([^)]+)\)\s+=>\s+(/\S+)\s*$")
PKG_NAME = re.compile(r"[a-z0-9][a-z0-9+.-]{0,119}(?::amd64)?\Z")
VERSION = re.compile(r"[A-Za-z0-9.+:~_-]{1,180}\Z")
RUN_ID = re.compile(r"[0-9a-f]{40}\Z")
ALLOWED_LIB_PREFIXES = ("/lib/x86_64-linux-gnu/", "/usr/lib/x86_64-linux-gnu/",
                        "/lib64/", "/usr/lib64/")
MAX_COPYRIGHT_BYTES = 2 * 1024 * 1024

def system_read(argv):
    """Only approved local, non-executing introspection utilities."""
    require(isinstance(argv, list) and argv and argv[0] in
            ("ldconfig", "dpkg-query", "dpkg"), "unapproved system inspector")
    require(len(argv) <= 6 and all(isinstance(a, str) for a in argv),
            "invalid system inspector args")
    try:
        result = subprocess.run(argv, shell=False, check=False, capture_output=True,
                                timeout=15, env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin",
                                                 "LC_ALL": "C", "LANG": "C"})
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise ValueError("local package inventory tool unavailable") from exc
    require(result.returncode == 0 and 0 < len(result.stdout) < 2 * 1024 * 1024,
            "local package inventory command failed")
    return result.stdout.decode("utf-8")

def parse_cache(raw, expected):
    """Reject missing/ambiguous candidates rather than guessing loader precedence."""
    require(isinstance(raw, str) and len(raw) < 2 * 1024 * 1024,
            "invalid ldconfig cache listing")
    entries = {}
    for line in raw.splitlines():
        match = LIB_CACHE_LINE.fullmatch(line)
        if not match:
            continue
        soname, arch, path = match.groups()
        if soname not in expected:
            continue
        if soname == "ld-linux-x86-64.so.2":
            continue
        if "x86-64" not in arch or "x86-64-v" in arch:
            continue
        require(path.startswith(ALLOWED_LIB_PREFIXES) and ".." not in pathlib.PurePosixPath(path).parts,
                "unexpected library cache location")
        entries.setdefault(soname, set()).add(path)
    result = {}
    for soname in expected:
        if soname == "ld-linux-x86-64.so.2":
            continue
        paths = entries.get(soname, set())
        require(len(paths) == 1, "missing or ambiguous SONAME in ldconfig cache: " + soname)
        result[soname] = next(iter(paths))
    return result

def source_file(path):
    """Canonical actual installed file must stay in a reviewed system directory."""
    p = pathlib.Path(path)
    require(p.is_absolute() and str(p).startswith(ALLOWED_LIB_PREFIXES),
            "library outside approved system location")
    real = p.resolve(strict=True)
    require(real.is_file() and str(real).startswith(ALLOWED_LIB_PREFIXES),
            "linked library escaped system directory")
    require(1024 <= real.stat().st_size <= 120 * 1024 * 1024,
            "unexpected installed library size")
    return real

def owner_for_path(path):
    """dpkg-query -S on real installed file (not ABI assumptions)."""
    candidates = list(dict.fromkeys((str(path), str(path.resolve(strict=True)))))
    owners = set()
    for candidate in candidates:
        out = subprocess.run(["dpkg-query", "--search", candidate], shell=False,
                             capture_output=True, timeout=10,
                             env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"})
        if out.returncode:
            continue
        for line in out.stdout.decode("utf-8").splitlines():
            if ": " not in line:
                continue
            prefix, actual = line.rsplit(": ", 1)
            if actual != candidate:
                continue
            for pkg in prefix.split(", "):
                require(PKG_NAME.fullmatch(pkg) is not None,
                        "invalid installed library owner")
                owners.add(pkg)
        if owners:
            break
    require(len(owners) == 1, "missing/ambiguous dpkg package ownership")
    return next(iter(owners))

def installed_metadata(pkg):
    require(PKG_NAME.fullmatch(pkg) is not None, "unapproved dpkg package ID")
    fmt = r"\${binary:Package}\t\${Version}\t\${Status}\t\${Depends}\n"
    # dpkg-query format is intentionally passed as a literal, not shell text.
    fmt = fmt.replace(r"\$", "$")
    raw = system_read(["dpkg-query", "--show", "--showformat="+fmt, pkg])
    data = raw.rstrip("\n").split("\t", 3)
    require(len(data) == 4 and data[0] == pkg and VERSION.fullmatch(data[1])
            and data[2] == "install ok installed",
            "installed package status/version mismatch")
    require("\n" not in data[3] and "\r" not in data[3] and len(data[3]) <= 8192,
            "invalid package dependency expression")
    return {"name": pkg, "version": data[1],
            "direct_debian_depends_expression": data[3]}

def copyright_receipt(pkg):
    base = pkg.split(":")[0]
    doc = pathlib.Path("/usr/share/doc") / base / "copyright"
    require(doc.exists() and doc.is_file(),
            "installed package lacks copyright notice: " + base)
    target = doc.resolve(strict=True)
    require(str(target).startswith("/usr/share/doc/"),
            "copyright notice escaped Debian docs root")
    require(1 <= target.stat().st_size <= MAX_COPYRIGHT_BYTES,
            "missing/oversized package copyright material")
    raw = target.read_bytes()
    require(len(raw) == target.stat().st_size, "unstable copyright material")
    return {"copyright_path": str(doc),
            "copyright_sha256": hashlib.sha256(raw).hexdigest(),
            "copyright_review": "MATERIAL_PRESENT_LEGAL_REVIEW_OPEN"}

def check_runtime_record(raw, source_sha, run_id, attempt, binary):
    """Check identity/authority of existing locally unsigned L2B-5 report."""
    require(isinstance(raw, dict) and raw.get("schema") ==
            "lanveriq.desktop.untrusted-runtime-needs.v1",
            "wrong runtime evidence schema")
    require(RUN_ID.fullmatch(source_sha) is not None
            and raw.get("source_sha") == source_sha
            and type(run_id) is int and run_id > 0
            and type(attempt) is int and 1 <= attempt <= 100
            and raw.get("run_id") == run_id and raw.get("attempt") == attempt,
            "runtime evidence source/replay mismatch")
    require(raw.get("signature") == "UNSIGNED_UNTRUSTED"
            and raw.get("release_approved") is False
            and raw.get("install_authorized") is False
            and raw.get("shared_library_license_review") == "OPEN"
            and raw.get("transitive_runtime_resolution") == "NOT_VERIFIED",
            "forged license/release authorization")
    elf = raw.get("elf")
    require(isinstance(elf, dict), "missing ELF context")
    observed = elf.get("direct_needed_sonames")
    require(isinstance(observed, list) and len(observed) == len(set(observed))
            and set(observed) == elfguard.APPROVED_DIRECT_SONAMES,
            "incomplete or altered approved 15 direct SONAME set")
    require(elf.get("machine") == "x86_64"
            and elf.get("interpreter") in elfguard.APPROVED_INTERPRETERS,
            "wrong runtime architecture/interpreter")
    require(pathlib.Path(binary).is_file() and not pathlib.Path(binary).is_symlink()
            and raw.get("elf_sha256") == elfguard.payload.base.digest(binary),
            "runtime evidence ELF digest mismatch")
    return observed, elf["interpreter"]

def evidence(runtime_report, binary, source_sha, run_id, attempt):
    runtime_report = pathlib.Path(runtime_report)
    original = elfguard.payload.base.json_read(runtime_report, 16384)
    names, interp = check_runtime_record(original, source_sha, run_id, attempt, binary)
    require(system_read(["dpkg", "--print-architecture"]).strip() == "amd64",
            "not amd64 dpkg host")
    os_release = pathlib.Path("/etc/os-release").read_text()
    require(re.search(r'^ID=ubuntu$', os_release, re.M) is not None
            and re.search(r'^VERSION_ID="?24\.04"?$', os_release, re.M) is not None,
            "host is not Ubuntu 24.04; Pop!_OS needs a separate L3")
    cache = parse_cache(system_read(["ldconfig", "-p"]), set(names))
    cache["ld-linux-x86-64.so.2"] = interp
    entries = []
    metadata = {}
    for name in sorted(names):
        system_path = source_file(cache[name])
        owner = owner_for_path(system_path)
        if owner not in metadata:
            metadata[owner] = installed_metadata(owner) | copyright_receipt(owner)
        entries.append({"soname": name, "host_path": str(system_path),
                        "owner": owner, "installed_version": metadata[owner]["version"]})
    return {
        "schema": "lanveriq.desktop.untrusted-ubuntu-package-map.v1",
        "source_sha": source_sha, "run_id": run_id, "attempt": attempt,
        "runtime_evidence_sha256": elfguard.payload.base.digest(runtime_report),
        "elf_sha256": original["elf_sha256"],
        "ubuntu_release": "24.04", "architecture": "amd64",
        "soname_owner_map": entries,
        "installed_owner_packages": [metadata[n] for n in sorted(metadata)],
        "declared_debian_dependencies": original["declared_debian_dependencies"],
        "dependency_expression_capture": "DIRECT_ONLY_NOT_RECURSIVELY_RESOLVED",
        "transitive_runtime_closure": "NOT_VERIFIED",
        "pop_os_24_runtime": "NOT_TESTED",
        "license_review": "OPEN_NOT_APPROVED",
        "signed_provenance": "NOT_AVAILABLE",
        "release_approved": False, "install_authorized": False,
    }

def verify(output, *args):
    actual = elfguard.payload.base.json_read(output, 49152)
    require(actual == evidence(*args), "package mapping evidence drift or forged approval")
    return "PASS: Ubuntu 24 direct library ownership metadata only; LICENSE/RELEASE NOT APPROVED"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "verify"))
    for field in ("runtime-report", "binary", "output"):
        parser.add_argument("--" + field, type=pathlib.Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--attempt", type=int, required=True)
    opts = parser.parse_args()
    args = (opts.runtime_report, opts.binary, opts.source_sha,
            opts.run_id, opts.attempt)
    if opts.mode == "create":
        require(not opts.output.exists(), "refuse overwrite of untrusted report")
        opts.output.write_text(json.dumps(evidence(*args), indent=2, sort_keys=True) + "\n")
        print("PASS: unsigned Ubuntu host runtime metadata only; license/PopOS NOT APPROVED")
    else:
        print(verify(opts.output, *args))

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError,
            subprocess.SubprocessError, FileNotFoundError) as exc:
        print("NOT_APPROVED: " + str(exc), file=sys.stderr)
        sys.exit(2)
