#!/usr/bin/env python3
"""Read-only Ubuntu APT archive verification against the host's apt-cache index.

The caller must run a successful signed apt-get update before invoking this
script. The restored cache is UNTRUSTED. Never installs or executes a .deb.
"""
import argparse
import hashlib
import pathlib
import re
import stat
import subprocess
import sys
from urllib.parse import unquote

NAME = re.compile(r"[a-z0-9][a-z0-9+.-]*")
VERSION = re.compile(r"[A-Za-z0-9.+:~%-]+")
ARCH = frozenset({"amd64", "all"})
SHA = re.compile(r"[0-9a-f]{64}")
MAX_ARCHIVES = 500
MAX_ARCHIVE_BYTES = 160 * 1024 * 1024


def reject(condition, reason):
    if condition:
        raise ValueError(reason)


def parse_archive_name(filename):
    parts = filename.removesuffix(".deb").split("_")
    reject(not filename.endswith(".deb") or len(parts) != 3, "invalid Debian archive filename")
    package, encoded_version, arch = parts
    version = unquote(encoded_version)
    reject(
        not NAME.fullmatch(package)
        or not VERSION.fullmatch(version)
        or arch not in ARCH
        or "/" in version or "\\" in version,
        "invalid Debian package name/version/architecture",
    )
    return package, version, arch


def apt_records(package, version):
    """Read metadata only; apt-cache never downloads, installs, or executes a package."""
    try:
        result = subprocess.run(
            ["apt-cache", "show", f"{package}={version}"],
            check=True,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        raise ValueError(f"APT signed-index metadata unavailable: {package}") from exc
    reject(len(result.stdout) > 1024 * 1024, "APT metadata unexpectedly large")
    records = []
    for paragraph in result.stdout.split("\n\n"):
        fields = {}
        for line in paragraph.splitlines():
            if line.startswith((" ", "\t")):
                continue
            key, separator, value = line.partition(":")
            if separator:
                fields[key] = value.strip()
        if fields.get("Package") == package and fields.get("Version") == version:
            records.append(fields)
    return records


def manifest_and_verify(directory, expected_count, expected_manifest, lookup=apt_records):
    """Return verified package count and manifest; reject any mismatch."""
    root = pathlib.Path(directory)
    reject(not root.is_dir() or root.is_symlink(), "archive directory missing or symlink")
    files = sorted(root.glob("*.deb"), key=lambda item: str(item))
    reject(not files or len(files) > MAX_ARCHIVES, "archive count outside allowed range")
    reject(len(files) != expected_count, "independent archive count differs")
    reject(not SHA.fullmatch(expected_manifest), "invalid expected archive manifest")
    seen = set()
    rows = []
    for path in files:
        info = path.lstat()
        reject(not stat.S_ISREG(info.st_mode), "archive must be a regular file, not symlink")
        reject(info.st_size <= 0 or info.st_size > MAX_ARCHIVE_BYTES, "archive size outside allowed range")
        package, version, architecture = parse_archive_name(path.name)
        reject((package, architecture) in seen, "multiple versions of package in cache")
        seen.add((package, architecture))
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        actual = digest.hexdigest()
        valid = False
        for record in lookup(package, version):
            if (
                record.get("Package") == package
                and record.get("Version") == version
                and record.get("Architecture") == architecture
                and record.get("SHA256") == actual
                and record.get("Size") == str(info.st_size)
            ):
                valid = True
                break
        reject(not valid, f"cached archive missing exact signed-index SHA256/size: {package}")
        rows.append(f"{actual}  {path}\n".encode("utf-8"))
    computed = hashlib.sha256(b"".join(rows)).hexdigest()
    reject(computed != expected_manifest, "cached archive bytes or paths differ from producer")
    return len(files), computed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive-dir", type=pathlib.Path, required=True)
    parser.add_argument("--expected-count", type=int, required=True)
    parser.add_argument("--expected-manifest", required=True)
    args = parser.parse_args()
    count, digest = manifest_and_verify(
        args.archive_dir, args.expected_count, args.expected_manifest
    )
    print(f"PASS: {count} archives match signed-index version, architecture, SHA256 and size")
    print(f"PASS: independent runner SHA256 manifest {digest}")
    print("BOUNDARY: no packages installed; not trusted Builder, native L2, or release approval")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
