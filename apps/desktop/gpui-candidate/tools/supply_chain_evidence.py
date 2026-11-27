#!/usr/bin/env python3
"""Review-only evidence from locked registry sources and upstream CycloneDX output."""
import hashlib
import json
import os
import pathlib
import re
import tomllib

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAX_FILE = 32 * 1024 * 1024
MAX_FILES = 12000
NAME = "lanveriq-desktop-gpui-candidate"
ASSET_EXTS = {".svg", ".png", ".jpg", ".jpeg", ".webp", ".ico", ".ttf", ".otf",
              ".woff", ".woff2", ".ttc", ".icns", ".gif", ".wav", ".mp3"}

def require(ok, reason):
    if not ok:
        raise ValueError(reason)

def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def license_name(name):
    return bool(re.fullmatch(r"(?:licen[cs]e|copying|copyright|notice|ofl|unlicense)(?:[._ -].*|\d.*)?",
                             name.lower()))

def record(path, root):
    require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(root),
            "source evidence escaped registry package")
    require(path.stat().st_size <= MAX_FILE, "source evidence file too large")
    return {"path": path.relative_to(root).as_posix(), "bytes": path.stat().st_size,
            "sha256": sha(path)}

def graph_pairs(raw):
    result = set()
    for line in raw.splitlines():
        if not line.strip():
            continue
        match = re.match(r"^([A-Za-z0-9_-]+) v([^\s]+)(?:\s|$)", line)
        require(match is not None, "unexpected cargo tree format")
        result.add(match.groups())
    require(bool(result), "empty Linux graph")
    return result

def registry_root(package, source_root):
    manifest = pathlib.Path(package["manifest_path"])
    root = manifest.parent.resolve(strict=True)
    require(root.is_relative_to(source_root) and manifest.is_file()
            and package["source"] == "registry+https://github.com/rust-lang/crates.io-index",
            "unexpected registry source")
    return root

def material(package, root):
    candidates = {p for p in root.iterdir() if p.is_file() and license_name(p.name)}
    if package.get("license_file"):
        relative = pathlib.Path(package["license_file"])
        require(not relative.is_absolute(), "absolute license-file metadata")
        explicit = root / relative
        if explicit.is_file():
            candidates.add(explicit)
    assets = []
    if package["name"].startswith("gpui"):
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if license_name(p.name):
                candidates.add(p)
            elif p.suffix.lower() in ASSET_EXTS:
                assets.append(p)
            require(len(candidates) + len(assets) <= MAX_FILES, "resource inventory bound")
    return {"license_material": [record(p, root) for p in sorted(candidates)],
            "gpui_resource_inventory": [record(p, root) for p in sorted(assets)]}

def missing_review(package, root):
    # Nested notices may belong to vendored/example code, not the whole crate.
    # Keep them as candidates; never substitute them for missing package terms.
    nested = []
    visited = 0
    for path in root.rglob("*"):
        visited += 1
        require(visited <= 100000, "nested source review bound")
        if (path.parent != root and path.is_file() and license_name(path.name)
                and path.suffix.lower() not in {".rs", ".json", ".toml", ".html", ".js", ".py"}):
            nested.append(record(path, root))
            require(len(nested) <= 256, "nested license candidate bound")
    vcs = root / ".cargo_vcs_info.json"
    origin = None
    if vcs.is_file():
        require(not vcs.is_symlink() and vcs.stat().st_size <= 8192, "unsafe VCS metadata")
        data = json.loads(vcs.read_text())
        commit = data.get("git", {}).get("sha1")
        subdir = data.get("path_in_vcs", "")
        if (isinstance(commit, str) and re.fullmatch(r"[0-9a-f]{40}", commit)
                and isinstance(subdir, str) and len(subdir) <= 512
                and not pathlib.PurePosixPath(subdir).is_absolute()
                and ".." not in pathlib.PurePosixPath(subdir).parts):
            origin = {"git_sha1_claim": commit, "path_in_vcs_claim": subdir,
                      "vcs_metadata_sha256": sha(vcs),
                      "dirty_claim": data.get("git", {}).get("dirty")}
    return {"repository_claim": package.get("repository"), "cargo_vcs_claim": origin,
            "nested_license_candidates": nested,
            "explicit_license_file_claim": package.get("license_file"),
            "review": "PACKAGE_MATERIAL_MISSING_UPSTREAM_REVIEW_OPEN"}

def components(bom):
    result = set()
    def visit(component):
        require(isinstance(component, dict), "invalid component")
        if component.get("name") and component.get("version"):
            result.add((component["name"], component["version"]))
        for child in component.get("components", []):
            visit(child)
    visit(bom["metadata"]["component"])
    for component in bom.get("components", []):
        visit(component)
    return result

def main():
    metadata = json.loads((ROOT / "candidate-metadata.json").read_text())
    linux = graph_pairs((ROOT / "candidate-linux-packages.txt").read_text())
    normal = graph_pairs((ROOT / "candidate-linux-normal-packages.txt").read_text())
    require(normal <= linux, "normal graph outside normal/build graph")
    lock = tomllib.loads((ROOT / "Cargo.lock").read_text())["package"]
    locked = {(p["name"], p["version"]): p for p in lock}
    packages = {(p["name"], p["version"]): p for p in metadata["packages"]}
    require(len(locked) == 813 and set(locked) == set(packages) and linux <= set(locked),
            "lock/metadata/Linux graph mismatch")
    source_root = (pathlib.Path(os.environ.get("CARGO_HOME", pathlib.Path.home() / ".cargo"))
                   / "registry/src").resolve(strict=True)
    files = sorted(ROOT.glob("*.cdx.json"))
    # Upstream filename varies with describe mode; CI uses a single crate BOM.
    if not files:
        files = sorted(ROOT.glob("bom.json"))
    require(len(files) == 1, "expected one generated CycloneDX JSON")
    bomfile = files[0]
    bom = json.loads(bomfile.read_text())
    require(bom["bomFormat"] == "CycloneDX" and bom["specVersion"] == "1.6",
            "unexpected SBOM format")
    pairs = components(bom)
    require((NAME, "0.1.0") in pairs and ("gpui-kit", "0.11.0") in pairs,
            "missing candidate or GPUI Kit in SBOM")
    rows = []
    total = 0
    for key in sorted(linux):
        p = packages[key]
        if p["source"] is None:
            require(p["name"] == NAME, "unexpected local package")
            rows.append({"name": key[0], "version": key[1], "declared_license": p.get("license"),
                         "license_material": [], "gpui_resource_inventory": [],
                         "review": "LOCAL_PRODUCT_LICENSE_OPEN"})
            continue
        m = material(p, registry_root(p, source_root))
        total += len(m["license_material"]) + len(m["gpui_resource_inventory"])
        require(total <= MAX_FILES, "total inventory bound")
        rows.append({"name": key[0], "version": key[1], "declared_license": p.get("license"),
                     "registry_checksum": locked[key]["checksum"], **m,
                     "review": "MATERIAL_PRESENT_REVIEW_OPEN" if m["license_material"]
                               else "MISSING_MATERIAL_REVIEW_OPEN"})
    missing = sum(row["review"] == "MISSING_MATERIAL_REVIEW_OPEN" for row in rows)
    missing_bom = sorted(linux - pairs)
    gaps = []
    for row in rows:
        key = (row["name"], row["version"])
        row["graph_scope"] = "NORMAL_PATH" if key in normal else "BUILD_PATH_ONLY"
        if row["review"] == "MISSING_MATERIAL_REVIEW_OPEN":
            p = packages[key]
            gaps.append({"name": key[0], "version": key[1], "declared_license": row["declared_license"],
                         "graph_scope": row["graph_scope"], **missing_review(p, registry_root(p, source_root))})
    report = {"schema": "lanveriq.gpui.untrusted-supply-chain-v1",
              "source_sha": os.environ["GITHUB_SHA"], "lock_sha256": sha(ROOT / "Cargo.lock"),
              "sbom_sha256": sha(bomfile), "sbom_tool": "cargo-cyclonedx 0.5.10",
              "linux_normal_build_graph_count": len(linux), "sbom_component_pair_count": len(pairs),
              "linux_pairs_missing_from_sbom": missing_bom,
              "sbom_pairs_outside_linux_graph": sorted(pairs - linux),
              "missing_license_material_count": missing, "packages": rows,
              "normal_path_pair_count": len(normal), "license_gap_review": gaps,
              "scope": "CARGO_RESOLVED_LINUX_NORMAL_BUILD_NOT_FINAL_BINARY_SBOM",
              "resource_scope": "GPUI_REGISTRY_FILE_CANDIDATES_NOT_EMBEDDED_ASSET_PROOF",
              "license_review": "OPEN_NOT_APPROVED", "release_approved": False,
              "install_authorized": False}
    data = json.dumps(report, sort_keys=True, indent=2) + "\n"
    require(len(data.encode()) <= 12_000_000, "inventory report too large")
    (ROOT / "candidate-supply-chain.json").write_text(data)
    print("Linux normal/build package pairs:", len(linux))
    print("CycloneDX component pairs:", len(pairs))
    print("Linux graph pairs not in SBOM:", len(missing_bom))
    print("Missing registry license materials:", missing)
    print("Hashed license/resource candidates:", total)
    print("Normal path pairs:", len(normal))
    for gap in gaps:
        print("OPEN license material:", gap["name"], gap["version"], gap["graph_scope"],
              "nested candidates:", len(gap["nested_license_candidates"]),
              "VCS claim:", bool(gap["cargo_vcs_claim"]))
    print("SBOM extra pairs (including target subcomponents):", len(pairs - linux))
    print("PASS: evidence generated; discrepancies, embedded assets, license/release remain OPEN")

if __name__ == "__main__":
    main()
