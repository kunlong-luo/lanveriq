#!/usr/bin/env python3
"""Review-only comparison of locked Cargo metadata, tree and CycloneDX scopes."""
import hashlib
import json
import os
import pathlib
import re
import sys
import tomllib
from collections import deque

NAME = "lanveriq-desktop-gpui-candidate"
TARGET = "x86_64-unknown-linux-gnu"
MAX_JSON = 32 * 1024 * 1024
REGISTRY = "registry+https://github.com/rust-lang/crates.io-index"

def require(ok, reason):
    if not ok:
        raise ValueError(reason)

def read_json(path):
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_JSON,
            "missing or oversized JSON evidence")
    return json.loads(path.read_text(encoding="utf-8"))

def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def pair(p):
    return (p["name"], p["version"])

def graph_pairs(raw):
    result = set()
    for line in raw.splitlines():
        if not line.strip():
            continue
        m = re.match(r"^([A-Za-z0-9_-]+) v([^\s]+)(?:\s|$)", line)
        require(m is not None, "unexpected tree line")
        result.add(m.groups())
    require(bool(result), "empty Cargo tree")
    return result

def resolved(metadata, locked):
    packages = {p["id"]: p for p in metadata["packages"]}
    nodes = {n["id"]: n for n in metadata["resolve"]["nodes"]}
    require(len(packages) == len(metadata["packages"]) <= 1500
            and len(nodes) == len(metadata["resolve"]["nodes"]) <= 1500,
            "duplicate or excessive Cargo metadata")
    require(len({pair(p) for p in packages.values()}) == len(packages),
            "ambiguous package/version pairs")
    require(all(pair(p) in locked and (p.get("source") == REGISTRY or
                (p.get("source") is None and p["name"] == NAME))
                for p in packages.values()), "unexpected source or unlocked package")
    root = metadata["resolve"]["root"]
    require(root in packages and pair(packages[root]) == (NAME, "0.1.0"),
            "wrong root")
    seen, edges, queue = set(), [], deque([root])
    while queue:
        ident = queue.popleft()
        if ident in seen:
            continue
        require(ident in nodes and ident in packages, "unresolved reachable metadata node")
        seen.add(ident)
        for d in nodes[ident]["deps"]:
            kinds = d["dep_kinds"]
            require(bool(kinds) and all(k["kind"] in (None, "build", "dev") for k in kinds),
                    "unknown dependency kind")
            allowed = [k for k in kinds if k["kind"] != "dev"]
            if not allowed:
                continue
            dest = d["pkg"]
            require(dest in nodes and dest in packages, "dangling dependency edge")
            edges.append({"parent": pair(packages[ident]), "child": pair(packages[dest]),
                          "dependency_name": d["name"], "kinds": allowed})
            require(len(edges) <= 30000, "metadata edge bound")
            queue.append(dest)
    features = {pair(packages[i]): sorted(nodes[i]["features"]) for i in seen}
    return set(features), features, edges

def bom_scopes(bom, locked):
    require(bom["bomFormat"] == "CycloneDX" and bom["specVersion"] == "1.6",
            "wrong BOM schema")
    root = bom["metadata"]["component"]
    require(pair(root) == (NAME, "0.1.0"), "unexpected BOM root")
    registry = {}
    for c in bom.get("components", []):
        k = pair(c)
        require(k in locked and k not in registry and k != pair(root), "unknown/duplicate BOM component")
        registry[k] = c.get("scope", "unspecified")
    targets = [pair(c) for c in root.get("components", [])]
    return set(registry) | {pair(root)}, registry, targets

def compare(metadata, tree, bom, locked):
    m, features, edges = resolved(metadata, locked)
    b, scopes, targets = bom_scopes(bom, locked)
    require(tree <= locked and (NAME, "0.1.0") in tree, "unexpected tree scope")
    extra = b - tree
    return {
        "metadata_reachable_count": len(m), "tree_pair_count": len(tree),
        "bom_package_pair_count": len(b), "bom_local_target_pairs": sorted(set(targets)),
        "metadata_only_vs_tree": sorted(m - tree), "tree_only_vs_metadata": sorted(tree - m),
        "bom_only_vs_metadata": sorted(b - m), "metadata_only_vs_bom": sorted(m - b),
        "bom_only_vs_tree": sorted(extra), "tree_only_vs_bom": sorted(tree - b),
        "extra_bom_package_details": [
            {"name": k[0], "version": k[1], "bom_scope": scopes[k],
             "metadata_reachable": k in m, "metadata_features": features.get(k),
             "metadata_incoming": [e for e in edges if tuple(e["child"]) == k]}
            for k in sorted(extra)],
        "metadata_boundary_edges": [e for e in edges if tuple(e["parent"]) in tree
                                    and tuple(e["child"]) not in tree],
        "scope": "TARGET_FILTERED_METADATA_NONDEV_REACHABILITY_NOT_COMPILER_LINK_INPUT",
        "final_binary_composition": "NOT_VERIFIED",
        "license_review": "OPEN_NOT_APPROVED", "release_approved": False,
        "install_authorized": False,
    }

def main():
    root = pathlib.Path(sys.argv[1]).resolve(strict=True)
    lock = root / "Cargo.lock"
    entries = tomllib.loads(lock.read_text())["package"]
    locked = {pair(p) for p in entries}
    require(len(locked) == len(entries) == 813, "unexpected candidate lock count")
    md = root / "scope-target-metadata.json"
    treefile = root / "scope-linux-tree.txt"
    boms = sorted(root.glob("*.cdx.json")) or sorted(root.glob("bom.json"))
    require(len(boms) == 1, "expected a single upstream BOM")
    require(treefile.stat().st_size <= 2 * 1024 * 1024, "tree bound")
    data = compare(read_json(md), graph_pairs(treefile.read_text()), read_json(boms[0]), locked)
    data.update({"schema": "lanveriq.gpui.untrusted-metadata-scope-v1",
                 "source_sha": os.environ["GITHUB_SHA"], "target": TARGET,
                 "lock_sha256": sha(lock), "metadata_sha256": sha(md),
                 "tree_sha256": sha(treefile), "bom_sha256": sha(boms[0])})
    output = json.dumps(data, indent=2, sort_keys=True) + "\n"
    require(len(output.encode()) <= 4 * 1024 * 1024, "output bound")
    with (root / "scope-reconciliation.json").open("x", encoding="utf-8") as stream:
        stream.write(output)
    for k in ("metadata_reachable_count", "tree_pair_count", "bom_package_pair_count",
              "metadata_only_vs_tree", "tree_only_vs_metadata", "bom_only_vs_metadata",
              "metadata_only_vs_bom", "bom_only_vs_tree", "tree_only_vs_bom"):
        v = data[k]
        print(k + ":", len(v) if isinstance(v, list) else v)
    print("PASS: scope evidence generated; final binary, license and release remain OPEN")

if __name__ == "__main__":
    main()
