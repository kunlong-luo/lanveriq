#!/usr/bin/env python3
"""Observe nonzero LLD map input sections from exact Cargo rlib artifacts.
This is neither an exhaustive linked-code inventory nor a redistribution decision.
"""
import json
import os
import pathlib
import re

from gpui_release_license import NAME, digest, pair, read_json, require, write

MAX_MAP = 256 * 1024 * 1024
MAX_LINE = 1024 * 1024
ROW = re.compile(r"^\s*[0-9a-fA-F]+\s+[0-9a-fA-F]+\s+([0-9a-fA-F]+)\s+\d+\s+([^()\s]+\.rlib)\(([^()\r\n]+)\):\(")

def archive_index(messages, metadata, deps):
    packages = {p["id"]: p for p in metadata["packages"]}
    require(len(packages) == len(metadata["packages"]), "duplicate package ID")
    result = {}
    deps = deps.resolve()
    for msg in messages:
        if msg.get("reason") != "compiler-artifact":
            continue
        require(msg["package_id"] in packages, "unknown artifact package")
        key = pair(packages[msg["package_id"]])
        for filename in msg["filenames"]:
            path = pathlib.Path(filename)
            if path.suffix != ".rlib":
                continue
            path = path.resolve()
            # Cargo also reports a top-level copy of the local library.
            if path.parent != deps:
                continue
            require(path.is_file(), "missing Cargo rlib")
            require(path not in result or result[path] == key, "ambiguous Cargo archive owner")
            result[path] = key
    require(bool(result), "no Cargo rlib artifacts")
    return result

def observe(lines, index, deps):
    deps = deps.resolve()
    header = next(lines, "")
    require(header.split() == ["VMA", "LMA", "Size", "Align", "Out", "In", "Symbol"],
            "unsupported linker map format")
    rows = {}
    other = set()
    total = 0
    for line in lines:
        require(len(line) <= MAX_LINE, "oversize linker map line")
        match = ROW.match(line)
        if not match or int(match[1], 16) == 0:
            continue
        path = pathlib.Path(match[2]).resolve()
        member = match[3]
        total += 1
        if path not in index:
            require(path.parent != deps, "unbound Cargo rlib in linker map")
            other.add(path)
            continue
        key = index[path]
        row = rows.setdefault(key, {"name": key[0], "version": key[1],
                                    "nonzero_input_sections": 0, "members": set()})
        row["nonzero_input_sections"] += 1
        row["members"].add((path, member))
    require(bool(rows), "no mapped Cargo rlib sections")
    return {"observed_packages": [
                {**row, "members": len(row["members"])} for _, row in sorted(rows.items())],
            "observed_nonzero_rlib_section_count": total,
            "unbound_external_rlib_archive_count": len(other),
            "compiler_rlib_packages_not_observed": sorted(set(index.values()) - rows.keys())}

def main():
    root = pathlib.Path(".")
    raw = root / "candidate-release-build.jsonl"
    mapping = root / "candidate-release-link.map"
    require(raw.stat().st_size <= 64 * 1024 * 1024, "Cargo messages too large")
    require(0 < mapping.stat().st_size <= MAX_MAP, "linker map size outside bound")
    binary = root / "target/release" / NAME
    artifacts = read_json(root / "candidate-release-artifacts.json")
    require(artifacts["schema"] == "lanveriq.gpui.release-compiler-artifacts-v1"
            and artifacts["source_sha"] == os.environ["GITHUB_SHA"]
            and artifacts["lock_sha256"] == digest(root / "Cargo.lock")
            and artifacts["elf_sha256"] == digest(binary)
            and artifacts["release_approved"] is False, "compiler artifact identity mismatch")
    deps = root / "target/release/deps"
    index = archive_index((json.loads(line) for line in raw.read_text().splitlines()),
                          read_json(root / "candidate-metadata.json"), deps)
    with mapping.open(encoding="utf-8", errors="strict") as stream:
        result = observe(iter(stream), index, deps)
    require({pair(p) for p in result["observed_packages"]} <= {pair(p) for p in artifacts["packages"]},
            "map package missing from compiler evidence")
    report = {"schema": "lanveriq.gpui.release-link-map-v1",
              "source_sha": artifacts["source_sha"], "lock_sha256": artifacts["lock_sha256"],
              "elf_sha256": artifacts["elf_sha256"], "map_sha256": digest(mapping),
              "compiler_report_sha256": digest(root / "candidate-release-artifacts.json"), **result,
              "scope": "LLD_NONZERO_RLIB_INPUT_SECTIONS_NOT_COMPLETE_BINARY_COMPOSITION",
              "license_review": "OPEN_NOT_APPROVED", "release_approved": False,
              "install_authorized": False}
    write(root / "candidate-release-link-map.json", report)
    print("Observed Cargo rlib package pairs:", len(result["observed_packages"]))
    print("Compiler rlib packages not observed:", len(result["compiler_rlib_packages_not_observed"]))
    print("Unbound external rlib archives:", result["unbound_external_rlib_archive_count"])
    print("PASS: bounded LLD map observation; final composition and license review OPEN")

if __name__ == "__main__":
    main()
