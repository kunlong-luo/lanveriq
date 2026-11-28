#!/usr/bin/env python3
"""Assemble actual locked-registry notice material plus existing auxiliary texts.
Review-only, never installed or distributed. Resource files remain metadata-only.
"""
import json
import os
import pathlib
import re
import sys
import tomllib

from gpui_compiled_notice_review import read_archive, sha
from gpui_release_license import digest, pair, require, write

MAX_FILE = 32 * 1024 * 1024
MAX_BYTES = 64 * 1024 * 1024
REGISTRY = "registry+https://github.com/rust-lang/crates.io-index"

def source_file(root, record):
    name = record["path"]
    path = pathlib.PurePosixPath(name)
    require(not path.is_absolute() and ".." not in path.parts and path.as_posix() == name
            and name not in ("", ".") and "\\" not in name, "unsafe registry path")
    actual = root.joinpath(*path.parts)
    require(actual.is_file() and not actual.is_symlink() and actual.resolve().is_relative_to(root),
            "missing or escaped registry material")
    require(0 <= record["bytes"] <= MAX_FILE and actual.stat().st_size == record["bytes"]
            and digest(actual) == record["sha256"], "registry material drift")
    return actual

def assemble(supply, linked, auxiliary, metadata, lock_bytes, registry, out):
    s = json.loads(supply["candidate-supply-chain.json"])
    l = json.loads(linked["compiled-notice-review.json"])
    n = json.loads(auxiliary["index.json"])
    require(s["schema"] == "lanveriq.gpui.untrusted-supply-chain-v1"
            and l["schema"] == "lanveriq.gpui.linked-notice-review-v1"
            and n["schema"] == "lanveriq.gpui.notice-gap-review-bundle-v1", "wrong evidence schema")
    require(all(x["release_approved"] is False and x["install_authorized"] is False
                for x in (s,l,n)) and l["registry_gaps_closed"] == n["registry_gaps_closed"] == 0,
            "unexpected approval")
    require(s["source_sha"] == l["source_sha"]
            and s["lock_sha256"] == l["lock_sha256"] == n["lock_sha256"] == sha(lock_bytes),
            "source or lock mismatch")
    boms = [v for k,v in supply.items() if k.endswith(".cdx.json") or k == "bom.json"]
    require(len(boms) == 1 and sha(boms[0]) == l["original_sbom_sha256"] == s["sbom_sha256"],
            "original BOM mismatch")
    def rows(x):
        r = {pair(p): p for p in x["packages"]}
        require(len(r) == len(x["packages"]), "duplicate package")
        return r
    sr, lr, mr = rows(s), rows(l), rows(metadata)
    lock = rows({"packages": tomllib.loads(lock_bytes.decode())["package"]})
    require(sr.keys() == lr.keys() and sr.keys() <= lock.keys() and lock.keys() == mr.keys()
            and len(lr) == l["compiled_package_count"], "package coverage mismatch")
    out.mkdir(parents=True, exist_ok=False)
    texts = out / "materials"
    texts.mkdir()
    materials = {}
    total = 0
    def store(data):
        nonlocal total
        key = sha(data)
        if key not in materials:
            total += len(data)
            require(total <= MAX_BYTES, "material total exceeds bound")
            # Retain original bytes, including line endings and any legacy encoding.
            name = key + ".license"
            (texts / name).write_bytes(data)
            materials[key] = {"path": "materials/" + name, "bytes": len(data)}
        return key
    resources, packages, gaps = [], [], set()
    registry = registry.resolve(strict=True)
    registry_file_count = 0
    for key, p in sorted(sr.items()):
        observed = lr[key]
        require(p["review"] == observed["review"] and p["license_material"] == observed["registry_material"]
                and p.get("registry_checksum") == observed.get("registry_checksum"), "review mismatch")
        name = "@".join(key)
        item = {"name": key[0], "version": key[1], "declared_license": p["declared_license"],
                "review": p["review"], "link_observation": observed["link_observation"],
                "registry_checksum": p.get("registry_checksum"), "registry_material": [],
                "auxiliary_material_sha256": []}
        if mr[key]["source"] is None:
            require(p["review"] == "LOCAL_PRODUCT_LICENSE_OPEN" and not p["license_material"]
                    and not p["gpui_resource_inventory"], "unexpected local material")
        else:
            require(mr[key]["source"] == lock[key]["source"] == REGISTRY
                    and lock[key]["checksum"] == p["registry_checksum"], "registry identity mismatch")
            root = pathlib.Path(mr[key]["manifest_path"]).parent.resolve(strict=True)
            require(root.is_relative_to(registry), "registry root escaped")
            for material in p["license_material"]:
                actual = source_file(root, material)
                item["registry_material"].append({"source_path": material["path"],
                                                    "sha256": store(actual.read_bytes())})
                registry_file_count += 1
            for resource in p["gpui_resource_inventory"]:
                source_file(root, resource)
                resources.append({"package": name, **resource, "attribution": "OPEN",
                                  "embedded": "NOT_ESTABLISHED"})
            if p["review"] == "MISSING_MATERIAL_REVIEW_OPEN":
                gaps.add(name)
                aux = n["packages"][name]
                require(aux["registry_checksum"] == p["registry_checksum"]
                        and aux["registry_material_missing"] is True and aux["legal_approved"] is False
                        and aux["material_sha256"] == observed["auxiliary_material_sha256"],
                        "auxiliary binding mismatch")
                for digest_key in aux["material_sha256"]:
                    record = n["materials"][digest_key]
                    require(re.fullmatch(r"[0-9a-f]{64}", digest_key)
                            and record["file"] == digest_key + ".txt", "unsafe auxiliary path")
                    data = auxiliary[record["file"]]
                    require(sha(data) == digest_key and len(data) == record["bytes"], "auxiliary drift")
                    item["auxiliary_material_sha256"].append(store(data))
        packages.append(item)
    require(gaps == set(n["packages"]) and len(gaps) == l["compiled_gaps_with_auxiliary_material"],
            "missing gap coverage")
    report = {"schema": "lanveriq.gpui.distribution-material-review-v1",
              "source_sha": l["source_sha"], "elf_sha256": l["elf_sha256"],
              "lock_sha256": l["lock_sha256"], "original_sbom_sha256": s["sbom_sha256"],
              "package_count": len(packages), "registry_material_file_count": registry_file_count,
              "distinct_material_count": len(materials), "resource_candidate_count": len(resources),
              "registry_gaps_closed": 0, "packages": packages, "materials": materials,
              "resources": resources, "scope": "REVIEW_ONLY_NOT_DISTRIBUTION_NOTICE",
              "license_review": "OPEN_NOT_APPROVED", "release_approved": False, "install_authorized": False}
    write(out / "index.json", report)
    (out / "READ-ME-FIRST.txt").write_text(
        "REVIEW ONLY - NOT A DISTRIBUTION NOTICE.\n"
        "Registry source texts and auxiliary candidates are retained without legal license selection.\n"
        "Resource inventory is metadata only, not proof of embedding or asset attribution.\n"
        "No registry gaps closed; local product license, OS/native dependencies, assets and legal approval remain open.\n")
    return report

if __name__ == "__main__":
    require(len(sys.argv) == 10, "expected output metadata lock and three archive/digest pairs")
    out, metadata, lock = map(pathlib.Path, sys.argv[1:4])
    inputs = [read_archive(pathlib.Path(sys.argv[i]), sys.argv[i+1]) for i in (4,6,8)]
    registry = pathlib.Path(os.environ.get("CARGO_HOME", pathlib.Path.home()/".cargo")) / "registry/src"
    r = assemble(*inputs, json.loads(metadata.read_text()), lock.read_bytes(), registry, out)
    print("Compiled package rows:", r["package_count"])
    print("Registry material files:", r["registry_material_file_count"])
    print("Distinct material contents:", r["distinct_material_count"])
    print("Resource candidates reverified:", r["resource_candidate_count"])
    print("Registry gaps closed: 0")
