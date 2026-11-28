#!/usr/bin/env python3
"""Compare three unresolved registry packages with same-version Debian orig archives.

No extraction, code execution, license selection or release authorization.
Debian HTTPS receipts are corroboration, not upstream Git provenance or a
verified Debian archive signature.
"""
import hashlib
import io
import json
import os
import pathlib
import tarfile
import tomllib
import urllib.request

TARGETS = {"mac": "0.1.1", "pathfinder_geometry": "0.5.1", "seahash": "4.1.0"}
MAX_ARCHIVE = 2 * 1024 * 1024
MAX_UNPACKED = 16 * 1024 * 1024
MAX_MEMBERS = 2048
REGISTRY = "registry+https://github.com/rust-lang/crates.io-index"

def require(ok, message):
    if not ok:
        raise ValueError(message)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Lanveriq-nonrelease-source-review"})
    with urllib.request.urlopen(req, timeout=30) as response:
        require(response.geturl() == url, "unexpected archive redirect")
        data = response.read(MAX_ARCHIVE + 1)
    require(0 < len(data) <= MAX_ARCHIVE, "archive size outside bound")
    return data

def manifest(data, root):
    files = {}
    seen = set()
    total = 0
    count = 0
    require(0 < len(data) <= MAX_ARCHIVE, "archive size outside bound")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r|gz") as archive:
        for member in archive:
            count += 1
            require(count <= MAX_MEMBERS, "too many archive members")
            name = member.name.rstrip("/")
            parts = name.split("/")
            require(parts[0] == root and all(p not in ("", ".", "..") for p in parts),
                    "unexpected archive path")
            require(name not in seen, "duplicate archive member")
            seen.add(name)
            if member.isdir():
                continue
            require(member.isfile() and len(parts) > 1 and 0 <= member.size <= MAX_UNPACKED,
                    "nonregular archive member")
            total += member.size
            require(total <= MAX_UNPACKED, "archive expanded size outside bound")
            with archive.extractfile(member) as stream:
                body = stream.read(member.size + 1)
            require(len(body) == member.size, "truncated archive member")
            files["/".join(parts[1:])] = sha(body)
    require("Cargo.toml" in files, "archive lacks Cargo.toml")
    return files

def compare(registry, debian, expected, root):
    require(sha(registry) == expected, "registry checksum disagrees with Cargo.lock")
    left, right = manifest(registry, root), manifest(debian, root)
    changed = sorted(p for p in left.keys() & right.keys() if left[p] != right[p])
    registry_only = sorted(left.keys() - right.keys())
    debian_only = sorted(right.keys() - left.keys())
    status = ("IDENTICAL_ARCHIVE_BYTES" if registry == debian else
              "IDENTICAL_FILE_CONTENTS" if not (changed or registry_only or debian_only)
              else "SOURCE_DIFFERENCES_OPEN")
    return {"registry_sha256": sha(registry), "debian_orig_sha256": sha(debian),
            "registry_files": len(left), "debian_files": len(right),
            "changed": changed, "registry_only": registry_only, "debian_only": debian_only,
            "comparison": status, "upstream_git_binding": "OPEN",
            "license_review": "OPEN_NOT_APPROVED"}

def main():
    root = pathlib.Path("apps/desktop/gpui-candidate")
    lock = (root / "Cargo.lock").read_bytes()
    expected_lock = (root / "Cargo.lock.sha256").read_text().split()[0]
    require(sha(lock) == expected_lock, "candidate lock digest mismatch")
    packages = tomllib.loads(lock.decode())["package"]
    rows = []
    for name, version in TARGETS.items():
        match = [p for p in packages if p["name"] == name and p["version"] == version]
        require(len(match) == 1 and match[0]["source"] == REGISTRY, "unexpected locked source")
        checksum = match[0]["checksum"]
        deb = "rust-" + name.replace("_", "-")
        registry_url = f"https://static.crates.io/crates/{name}/{name}-{version}.crate"
        debian_url = f"https://deb.debian.org/debian/pool/main/r/{deb}/{deb}_{version}.orig.tar.gz"
        copyright_url = (f"https://metadata.ftp-master.debian.org/changelogs/main/r/{deb}/"
                         f"{deb}_{version}-1_copyright")
        registry, orig = fetch(registry_url), fetch(debian_url)
        result = compare(registry, orig, checksum, f"{name}-{version}")
        copyright = fetch(copyright_url)
        require(b"Copyright" in copyright and b"License:" in copyright,
                "unexpected Debian copyright record")
        rows.append({"name": name, "version": version, "registry_url": registry_url,
                     "debian_orig_url": debian_url, "debian_copyright_url": copyright_url,
                     "debian_copyright_sha256": sha(copyright),
                     "debian_copyright_bytes": len(copyright), **result})
        print(name, version, result["comparison"], result["registry_files"], result["debian_files"])
    report = {"schema": "lanveriq.gpui.debian-source-corroboration-v1",
              "source_sha": os.environ["GITHUB_SHA"], "lock_sha256": sha(lock),
              "packages": rows, "debian_signature_verified": False,
              "scope": "SOURCE_ARCHIVE_CORROBORATION_NOT_UPSTREAM_GIT_OR_FINAL_SBOM",
              "license_review": "OPEN_NOT_APPROVED", "release_approved": False,
              "install_authorized": False}
    pathlib.Path("gpui-debian-source-review.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")

if __name__ == "__main__":
    main()
