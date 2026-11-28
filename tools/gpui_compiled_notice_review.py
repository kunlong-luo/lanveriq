#!/usr/bin/env python3
"""Join existing nonrelease compiler/notice evidence without compiling or extracting.
Use trusted CI archive SHA256 receipts; different source SHAs are explicit.
No legal choice, final link inference or redistribution authorization.
"""
import hashlib
import json
import pathlib
import re
import sys
import zipfile

from gpui_release_license import digest, pair, require, write

MAX_ZIP = 16 * 1024 * 1024
MAX_TOTAL = 32 * 1024 * 1024

def sha(data):
    return hashlib.sha256(data).hexdigest()

def read_archive(path, expected):
    require(path.stat().st_size <= MAX_ZIP and digest(path) == expected, "archive digest or size mismatch")
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        require(len(entries) <= 1024 and sum(e.file_size for e in entries) <= MAX_TOTAL,
                "archive expanded size outside bound")
        names = [e.filename for e in entries]
        require(len(names) == len(set(names)) and all(re.fullmatch(r"[A-Za-z0-9_.-]+", n)
                and n not in (".", "..") for n in names), "unexpected archive entry")
        return {e.filename: archive.read(e) for e in entries}

def join(association, compiler, supply, notice):
    a = json.loads(association["candidate-release-license-association.json"])
    cbytes = compiler["candidate-release-artifacts.json"]
    sbytes = supply["candidate-supply-chain.json"]
    c, s, n = json.loads(cbytes), json.loads(sbytes), json.loads(notice["index.json"])
    for obj, schema in ((a, "lanveriq.gpui.release-license-association-v1"),
                        (c, "lanveriq.gpui.release-compiler-artifacts-v1"),
                        (s, "lanveriq.gpui.untrusted-supply-chain-v1"),
                        (n, "lanveriq.gpui.notice-gap-review-bundle-v1")):
        require(obj["schema"] == schema and obj["release_approved"] is False,
                "unexpected schema or approval")
    require(all(obj["install_authorized"] is False for obj in (a, s, n))
            and n["registry_gaps_closed"] == 0, "unexpected authorization or closure")
    require(a["compiler_report_sha256"] == sha(cbytes) and a["license_inventory_sha256"] == sha(sbytes),
            "compiler or inventory digest mismatch")
    boms = [data for name, data in supply.items() if name.endswith(".cdx.json") or name == "bom.json"]
    require(len(boms) == 1 and sha(boms[0]) == a["original_sbom_sha256"] == s["sbom_sha256"],
            "original SBOM digest mismatch")
    require(a["source_sha"] == c["source_sha"] == s["source_sha"]
            and a["elf_sha256"] == c["elf_sha256"] and a["elf_bytes"] == c["elf_bytes"],
            "compiler evidence identity mismatch")
    require(a["lock_sha256"] == c["lock_sha256"] == s["lock_sha256"] == n["lock_sha256"],
            "lock mismatch")
    def rows(obj):
        result = {"@".join(pair(row)): row for row in obj["packages"]}
        require(len(result) == len(obj["packages"]), "duplicate package")
        return result
    ar, cr, sr = rows(a), rows(c), rows(s)
    require(set(ar) == set(cr) and set(ar) <= set(sr)
            and len(ar) == a["compiled_package_count"] and len(sr) == a["inventory_package_count"],
            "package coverage mismatch")
    require({"@".join(p) for p in a["inventory_only"]} == set(sr) - set(ar), "inventory-only mismatch")
    for key, row in ar.items():
        require(all(row[k] == cr[key][k] for k in ("target_kinds", "artifact_messages", "fresh_messages"))
                and all(row.get(k) == sr[key].get(k) for k in
                        ("review", "declared_license", "graph_scope", "registry_checksum", "license_material")),
                "package evidence mismatch")
    gaps = {k for k, row in ar.items() if row["review"] == "MISSING_MATERIAL_REVIEW_OPEN"}
    require(gaps == {"@".join(p) for p in a["compiled_registry_material_missing"]}
            and gaps == set(n["packages"]), "notice coverage mismatch")
    expected_files = {"index.json", "READ-ME-FIRST.txt"}
    used = set()
    for key, m in n["materials"].items():
        require(re.fullmatch(r"[0-9a-f]{64}", key) and m["file"] == key + ".txt",
                "unsafe material reference")
        body = notice[m["file"]]
        require(0 < len(body) == m["bytes"] <= 262144 and sha(body) == key, "material digest mismatch")
        expected_files.add(m["file"])
    require(set(notice) == expected_files, "unexpected notice archive file")
    result = []
    for key, row in sorted(ar.items()):
        item = {"name": row["name"], "version": row["version"], "review": row["review"],
                "registry_checksum": row.get("registry_checksum"), "target_kinds": row["target_kinds"],
                "graph_scope": row["graph_scope"], "declared_license": row["declared_license"],
                "registry_material": row["license_material"], "auxiliary_material_sha256": []}
        if key in gaps:
            auxiliary = n["packages"][key]
            require(auxiliary["registry_checksum"] == row["registry_checksum"]
                    and auxiliary["registry_material_missing"] is True and auxiliary["legal_approved"] is False,
                    "notice package binding mismatch")
            refs = auxiliary["material_sha256"]
            require(refs and set(refs) <= n["materials"].keys(), "unbound material")
            used.update(refs)
            item["auxiliary_material_sha256"] = refs
        result.append(item)
    require(used == n["materials"].keys(), "unused notice materials")
    return {"schema": "lanveriq.gpui.compiled-notice-review-v1", "source_sha": a["source_sha"],
            "notice_inventory_source_sha": n["input_supply_chain_source"],
            "lock_sha256": a["lock_sha256"], "elf_sha256": a["elf_sha256"],
            "original_sbom_sha256": a["original_sbom_sha256"], "compiled_package_count": len(ar),
            "compiled_gaps_with_auxiliary_material": len(gaps), "distinct_auxiliary_materials": len(used),
            "registry_gaps_closed": 0, "packages": result,
            "scope": "COMPILED_PACKAGE_REVIEW_NOT_FINAL_LINK_OR_DISTRIBUTION_NOTICE",
            "license_review": "OPEN_NOT_APPROVED", "release_approved": False, "install_authorized": False}

def add_link_observation(report, compiler):
    """Augment the review with same-ELF nonzero rlib observations, never exclusions."""
    raw = compiler["candidate-release-artifacts.json"]
    c = json.loads(raw)
    m = json.loads(compiler["candidate-release-link-map.json"])
    require(m["schema"] == "lanveriq.gpui.release-link-map-v1"
            and m["scope"] == "LLD_NONZERO_RLIB_INPUT_SECTIONS_NOT_COMPLETE_BINARY_COMPOSITION"
            and m["license_review"] == "OPEN_NOT_APPROVED"
            and m["release_approved"] is False and m["install_authorized"] is False,
            "unexpected map scope or approval")
    require(all(report[k] == m[k] == c[k] for k in ("source_sha", "lock_sha256", "elf_sha256"))
            and m["compiler_report_sha256"] == sha(raw)
            and re.fullmatch(r"[0-9a-f]{64}", m["map_sha256"]), "map identity mismatch")
    compiled = {pair(p): p for p in report["packages"]}
    observed = {pair(p): p for p in m["observed_packages"]}
    require(len(compiled) == len(report["packages"]) and len(observed) == len(m["observed_packages"])
            and bool(observed) and observed.keys() <= compiled.keys(), "map package coverage mismatch")
    unseen = [tuple(p) for p in m["compiler_rlib_packages_not_observed"]]
    require(all(len(p) == 2 for p in unseen) and len(unseen) == len(set(unseen))
            and set(unseen) <= compiled.keys() and not (set(unseen) & observed.keys()),
            "unobserved map package coverage mismatch")
    def count(v):
        return type(v) is int and v >= 0
    require(count(m["unbound_external_rlib_archive_count"])
            and count(m["observed_nonzero_rlib_section_count"]), "invalid map counts")
    for p in observed.values():
        require(count(p["members"]) and count(p["nonzero_input_sections"])
                and 0 < p["members"] <= p["nonzero_input_sections"], "invalid section counts")
    mapped_sections = sum(p["nonzero_input_sections"] for p in observed.values())
    require(mapped_sections <= m["observed_nonzero_rlib_section_count"]
            and m["unbound_external_rlib_archive_count"] <=
                m["observed_nonzero_rlib_section_count"] - mapped_sections,
            "inconsistent map section totals")
    rows = []
    gaps_observed = []
    gaps_other = []
    for key, row in sorted(compiled.items()):
        found = observed.get(key)
        status = ("NONZERO_RLIB_SECTIONS_OBSERVED" if found else
                  "RLIB_NOT_OBSERVED" if key in unseen else "NO_RLIB_OBSERVATION")
        rows.append({**row, "link_observation": status,
                     "observed_members": found["members"] if found else 0,
                     "observed_nonzero_input_sections": found["nonzero_input_sections"] if found else 0})
        if row["review"] == "MISSING_MATERIAL_REVIEW_OPEN":
            (gaps_observed if found else gaps_other).append(list(key))
    return {**report, "schema": "lanveriq.gpui.linked-notice-review-v1",
            "scope": "OBSERVED_RLIB_SECTIONS_NOT_COMPLETE_LINKED_SBOM_OR_NOTICE",
            "link_map_report_sha256": sha(compiler["candidate-release-link-map.json"]),
            "raw_map_sha256": m["map_sha256"], "observed_package_count": len(observed),
            "not_observed_rlib_package_count": len(unseen),
            "no_rlib_observation_package_count": len(compiled) - len(observed) - len(unseen),
            "unbound_external_rlib_archive_count": m["unbound_external_rlib_archive_count"],
            "mapped_nonzero_input_sections": mapped_sections,
            "observed_registry_material_gaps": gaps_observed,
            "other_registry_material_gaps": gaps_other,
            "packages": rows}

if __name__ == "__main__":
    # CLI: output.json association.zip SHA compiler.zip SHA supply.zip SHA notice.zip SHA
    require(len(sys.argv) == 10, "expected output and four archive/digest pairs")
    inputs = [read_archive(pathlib.Path(sys.argv[i]), sys.argv[i+1]) for i in (2, 4, 6, 8)]
    report = join(*inputs)
    if "candidate-release-link-map.json" in inputs[1]:
        report = add_link_observation(report, inputs[1])
        print("Observed rlib packages:", report["observed_package_count"])
        print("Observed registry material gaps:", len(report["observed_registry_material_gaps"]))
        print("Other registry material gaps:", len(report["other_registry_material_gaps"]))
        print("Unbound external rlib archives:", report["unbound_external_rlib_archive_count"])
    report["input_zip_sha256"] = dict(zip(("association", "compiler", "supply", "notice"),
                                          sys.argv[3:10:2]))
    write(pathlib.Path(sys.argv[1]), report)
    print("Compiled packages:", report["compiled_package_count"])
    print("Gaps with auxiliary material:", report["compiled_gaps_with_auxiliary_material"])
    print("Distinct auxiliary material texts:", report["distinct_auxiliary_materials"])
    print("Registry gaps closed: 0")
