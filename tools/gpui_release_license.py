#!/usr/bin/env python3
"""Nonrelease: bind Cargo Release compiler artifacts to the license inventory.

Compiler artifacts include host/build dependencies and do not prove final linkage.
Never archive raw Cargo messages, absolute build paths, or executable contents.
"""
import hashlib
import json
import os
import pathlib
import re
import sys

NAME = "lanveriq-desktop-gpui-candidate"
REGISTRY = "registry+https://github.com/rust-lang/crates.io-index"
MAX_JSON = 64 * 1024 * 1024

def require(ok, reason):
    if not ok:
        raise ValueError(reason)

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def read_json(path):
    require(path.stat().st_size <= MAX_JSON, "JSON input too large")
    return json.loads(path.read_text())

def pair(row):
    name, version = row["name"], row["version"]
    require(isinstance(name, str) and re.fullmatch(r"[A-Za-z0-9_-]+", name)
            and isinstance(version, str) and re.fullmatch(r"[A-Za-z0-9.+-]+", version),
            "invalid package pair")
    return name, version

def capture(messages, metadata, binary, source, lock):
    packages = {p["id"]: p for p in metadata["packages"]}
    require(len(packages) == len(metadata["packages"]), "duplicate metadata ID")
    rows = {}
    finished = []
    executable_count = 0
    for msg in messages:
        reason = msg.get("reason")
        if reason == "build-finished":
            finished.append(msg.get("success"))
        if reason != "compiler-artifact":
            continue
        require(msg["package_id"] in packages, "compiler artifact package absent from metadata")
        p = packages[msg["package_id"]]
        key = pair(p)
        require(p["source"] == REGISTRY or (p["source"] is None and key == (NAME, "0.1.0")),
                "unexpected artifact source")
        target = msg["target"]
        kinds = target["kind"]
        require(isinstance(kinds, list) and bool(kinds) and all(isinstance(x, str) for x in kinds),
                "invalid artifact target kind")
        require(isinstance(msg["fresh"], bool), "missing artifact freshness")
        if key == (NAME, "0.1.0") and target["name"] == NAME and "bin" in kinds:
            require(msg.get("executable") and pathlib.Path(msg["executable"]).resolve() == binary.resolve(),
                    "unexpected final executable")
            executable_count += 1
        row = rows.setdefault(key, {"name": key[0], "version": key[1], "target_kinds": set(),
                                    "artifact_messages": 0, "fresh_messages": 0})
        row["target_kinds"].update(kinds)
        row["artifact_messages"] += 1
        row["fresh_messages"] += int(msg["fresh"])
    require(finished == [True] and executable_count == 1 and binary.is_file()
            and not binary.is_symlink(), "Release build or executable evidence incomplete")
    for row in rows.values():
        row["target_kinds"] = sorted(row["target_kinds"])
    return {"schema": "lanveriq.gpui.release-compiler-artifacts-v1", "source_sha": source,
            "lock_sha256": lock, "elf_sha256": digest(binary), "elf_bytes": binary.stat().st_size,
            "packages": [rows[k] for k in sorted(rows)],
            "scope": "COMPILER_ARTIFACTS_NOT_FINAL_LINKED_COMPONENTS", "release_approved": False}

def reconcile(artifacts, inventory):
    require(artifacts["schema"] == "lanveriq.gpui.release-compiler-artifacts-v1"
            and inventory["schema"] == "lanveriq.gpui.untrusted-supply-chain-v1",
            "unexpected report schema")
    for key, size in (("source_sha", 40), ("lock_sha256", 64)):
        require(artifacts[key] == inventory[key] and
                re.fullmatch(r"[0-9a-f]{" + str(size) + "}", artifacts[key]),
                "source or lock mismatch")
    require(artifacts["release_approved"] is False and inventory["release_approved"] is False,
            "unexpected approval")
    compiled = {pair(r): r for r in artifacts["packages"]}
    licenses = {pair(r): r for r in inventory["packages"]}
    require(len(compiled) == len(artifacts["packages"]) and len(licenses) == len(inventory["packages"]),
            "duplicate package rows")
    require((NAME, "0.1.0") in compiled and (NAME, "0.1.0") in licenses,
            "local candidate absent")
    require(len(licenses) == inventory["linux_normal_build_graph_count"], "inventory count mismatch")
    missing = sorted(compiled.keys() - licenses.keys())
    require(not missing, "compiled packages missing from license inventory")
    rows = []
    for key, row in sorted(compiled.items()):
        evidence = licenses[key]
        require(evidence["review"] in {"LOCAL_PRODUCT_LICENSE_OPEN", "MATERIAL_PRESENT_REVIEW_OPEN",
                                      "MISSING_MATERIAL_REVIEW_OPEN"}, "unexpected review status")
        require(evidence["graph_scope"] in {"NORMAL_PATH", "BUILD_PATH_ONLY"}, "unknown graph scope")
        require((evidence["review"] == "MATERIAL_PRESENT_REVIEW_OPEN") == bool(evidence["license_material"]),
                "material review mismatch")
        rows.append({**row, "declared_license": evidence["declared_license"],
                     "graph_scope": evidence["graph_scope"], "review": evidence["review"],
                     "registry_checksum": evidence.get("registry_checksum"),
                     "license_material": evidence["license_material"]})
    unresolved = [list(pair(r)) for r in rows if r["review"] == "MISSING_MATERIAL_REVIEW_OPEN"]
    return {"schema": "lanveriq.gpui.release-license-association-v1",
            "source_sha": artifacts["source_sha"], "lock_sha256": artifacts["lock_sha256"],
            "elf_sha256": artifacts["elf_sha256"], "elf_bytes": artifacts["elf_bytes"],
            "original_sbom_sha256": inventory["sbom_sha256"], "compiled_package_count": len(compiled),
            "inventory_package_count": len(licenses), "inventory_only": sorted(licenses.keys() - compiled.keys()),
            "compiled_registry_material_missing": unresolved, "packages": rows,
            "scope": "COMPILER_ARTIFACTS_NOT_FINAL_LINKED_COMPONENTS",
            "license_review": "OPEN_NOT_APPROVED", "release_approved": False, "install_authorized": False}

def write(path, data):
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")

def main():
    mode = sys.argv[1]
    if mode == "capture":
        root = pathlib.Path(".")
        raw = root / "candidate-release-build.jsonl"
        require(raw.stat().st_size <= MAX_JSON, "Cargo message stream too large")
        binary = root / "target/release" / NAME
        report = capture((json.loads(line) for line in raw.read_text().splitlines()),
                         read_json(root / "candidate-metadata.json"), binary,
                         os.environ["GITHUB_SHA"], digest(root / "Cargo.lock"))
        write(root / "candidate-release-artifacts.json", report)
        print("Release compiler package pairs:", len(report["packages"]))
    elif mode == "reconcile":
        artifacts, inventory = (read_json(pathlib.Path(p)) for p in sys.argv[2:4])
        require(artifacts["source_sha"] == os.environ["GITHUB_SHA"], "wrong CI source")
        report = reconcile(artifacts, inventory)
        report["compiler_report_sha256"] = digest(pathlib.Path(sys.argv[2]))
        report["license_inventory_sha256"] = digest(pathlib.Path(sys.argv[3]))
        write(pathlib.Path("candidate-release-license-association.json"), report)
        print("Release compiler pairs:", report["compiled_package_count"])
        print("License inventory pairs:", report["inventory_package_count"])
        print("Inventory-only pairs:", len(report["inventory_only"]))
        print("Compiled registry material missing:", len(report["compiled_registry_material_missing"]))
        for name, version in report["compiled_registry_material_missing"]:
            print("OPEN compiled license material:", name, version)
        print("PASS: artifact association; final linker and redistribution review OPEN")
    else:
        raise ValueError("unknown mode")

if __name__ == "__main__":
    main()
