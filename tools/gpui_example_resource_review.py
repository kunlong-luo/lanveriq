#!/usr/bin/env python3
"""Review six example resources in the exact locked gpui-pre archive.

No extraction, compilation, execution, binary absence or license claim.
"""
import hashlib
import io
import json
import os
import pathlib
import tarfile
import tomllib
import urllib.request

from gpui_debian_source_review import require, sha, REGISTRY

NAME, VERSION = "gpui-pre", "0.3.14"
ROOT = NAME + "-" + VERSION
URL = f"https://static.crates.io/crates/{NAME}/{ROOT}.crate"
MAX_ARCHIVE = 32 * 1024 * 1024
MAX_EXPANDED = 128 * 1024 * 1024
MAX_MEMBERS = 12000
ASSETS = {
    "examples/image/app-icon.png": (168173, "77453d98d46494b3b34b94aa863b686ea299e0190030a5c7ca5377b238332522"),
    "examples/image/arrow_circle.svg": (748, "1dc122d8c7f8f346e6e7b838594f9718e5670fc32a5d5024aaa5e7f28a3aa8c8"),
    "examples/image/black-cat-typing.gif": (4471092, "b50a33262f84725f9c922fcbf0bb616270a2cb611a5ea4e280c2dca46cea9622"),
    "examples/image/color.svg": (746, "d0949465578c8223c7124ff1f66ce308e07e6eb303fdf26c2814715332d88f3b"),
    "examples/image/exif-orientation-rotate-180.jpg": (378, "cb6c28e3e6065d62790ba38662d14f3f1510180375cacba18547720e8b48d9d5"),
    "examples/svg/dragon.svg": (20111, "a66278ec0c72d70152ab05fc9dd122ad094a262d96fa6f61fba6b12e128e0e47"),
}

def inspect(data, checksum):
    require(0 < len(data) <= MAX_ARCHIVE and sha(data) == checksum, "locked archive mismatch")
    seen, resources, references = set(), {}, {name: [] for name in ASSETS}
    manifest = None
    expanded = 0
    with tarfile.open(fileobj=io.BytesIO(data), mode="r|gz") as archive:
        for member in archive:
            name = member.name.rstrip("/")
            parts = name.split("/")
            require(parts[0] == ROOT and all(p not in ("", ".", "..") for p in parts)
                    and "\\" not in name and name not in seen, "invalid archive member")
            seen.add(name)
            require(len(seen) <= MAX_MEMBERS, "too many members")
            if member.isdir():
                continue
            require(member.isfile() and len(parts) > 1 and 0 <= member.size <= MAX_EXPANDED,
                    "unexpected member type or size")
            expanded += member.size
            require(expanded <= MAX_EXPANDED, "archive expanded size outside bound")
            with archive.extractfile(member) as stream:
                body = stream.read(member.size + 1)
            require(len(body) == member.size, "truncated archive member")
            relative = "/".join(parts[1:])
            if relative in ASSETS:
                require((len(body), sha(body)) == ASSETS[relative], "resource inventory drift")
                resources[relative] = {"bytes": len(body), "sha256": sha(body)}
            if relative == "Cargo.toml":
                require(len(body) <= 262144, "manifest too large")
                manifest = tomllib.loads(body.decode("utf-8"))
            if relative.endswith(".rs") or relative in ("Cargo.toml", "Cargo.toml.orig"):
                for asset in ASSETS:
                    if asset.rsplit("/", 1)[-1].encode() in body:
                        references[asset].append(relative)
    require(resources.keys() == ASSETS.keys() and manifest is not None, "missing inventory or manifest")
    require(manifest["package"]["name"] == NAME and manifest["package"]["version"] == VERSION,
            "package identity mismatch")
    rows = []
    for asset in sorted(ASSETS):
        paths = sorted(references[asset])
        outside = [path for path in paths if not path.startswith("examples/")]
        rows.append({"path": asset, **resources[asset], "literal_filename_references": paths,
                     "outside_examples": outside, "embedding": "NOT_ESTABLISHED",
                     "legal_approved": False})
    return {"registry_sha256": checksum, "resource_count": len(rows),
            "declared_example_targets": [{"name": t.get("name"), "path": t.get("path")}
                                         for t in manifest.get("example", [])],
            "resources": rows, "scope": "LITERAL_FILENAME_SOURCE_SEARCH_NOT_LINKAGE_OR_LICENSE"}

def main():
    root = pathlib.Path("apps/desktop/gpui-candidate")
    lock = (root / "Cargo.lock").read_bytes()
    require(sha(lock) == (root / "Cargo.lock.sha256").read_text().split()[0], "lock drift")
    packages = [p for p in tomllib.loads(lock.decode())["package"]
                if p["name"] == NAME and p["version"] == VERSION]
    require(len(packages) == 1 and packages[0]["source"] == REGISTRY, "locked package missing")
    req = urllib.request.Request(URL, headers={"User-Agent": "Lanveriq-nonrelease-resource-review"})
    with urllib.request.urlopen(req, timeout=30) as response:
        require(response.geturl() == URL, "unexpected redirect")
        data = response.read(MAX_ARCHIVE + 1)
    report = inspect(data, packages[0]["checksum"])
    report.update(schema="lanveriq.gpui.example-resource-review-v1", source_sha=os.environ["GITHUB_SHA"],
                  lock_sha256=sha(lock), release_approved=False, install_authorized=False)
    pathlib.Path("gpui-example-resource-review.json").write_text(json.dumps(report, indent=2, sort_keys=True)+"\n")
    for row in report["resources"]:
        print(row["path"], "references:", row["literal_filename_references"],
              "outside examples:", row["outside_examples"])

if __name__ == "__main__":
    main()
