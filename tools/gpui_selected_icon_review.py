#!/usr/bin/env python3
"""Current selected-icon source check; historic default bundle evidence is not current."""
import hashlib
import json
import os
import pathlib
import sys
import tomllib

from gpui_default_icon_attribution import git_blob
from gpui_release_license import require, write

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / "apps/desktop"
ADAPTER_BLOB = "bd433a6b103ca7b0402b4562f185b85576546dec"
INDEX_BLOB = "0e126a6860ccaad437c8b51aa668c0090912fe69"
EXTRA = {
    "assets/icons/x.svg": "e31794349d3eecea1b3ae8e974b68bb8f4becda7",
    "assets/icons/arrow-down-right.svg": "049b2ea7b38e6df0760907ee3ba18ed923cc1cfc",
}


def selection(index):
    rows = index["rows"]
    require(len(rows) == len({r["path"] for r in rows}) == 106, "default index drift")
    selected = {r["path"]: r["gpui_blob"] for r in rows if r["gpui_blob"] == r["lucide_blob"]}
    excluded = {r["path"]: r["gpui_blob"] for r in rows if r["gpui_blob"] != r["lucide_blob"]}
    require(not selected.keys() & EXTRA.keys(), "unexpected extra overlap")
    selected.update(EXTRA)
    require(len(selected) == 96 and len(excluded) == 12
            and not selected.keys() & excluded.keys(), "selection drift")
    return selected, excluded


def verify_file(root, name, expected):
    path = root / name
    require(path.is_file() and not path.is_symlink()
            and path.resolve().is_relative_to(root)
            and 0 < path.stat().st_size <= 262144, "unreviewed registry path: " + name)
    require(git_blob(path.read_bytes()) == expected, "registry blob mismatch: " + name)


def review(metadata):
    lock = (APP / "Cargo.lock").read_bytes()
    digest = hashlib.sha256(lock).hexdigest()
    require(digest == (APP / "Cargo.lock.sha256").read_text().split()[0], "lock drift")
    raw = (ROOT / "tools/gpui_default_icon_source_index.json").read_bytes()
    require(git_blob(raw) == INDEX_BLOB, "historic source index drift")
    index = json.loads(raw)
    selected, excluded = selection(index)
    def package(rows):
        matches = [p for p in rows if (p["name"], p["version"]) == (index["package"], index["version"])]
        require(len(matches) == 1, "asset package identity drift")
        return matches[0]
    k = package(tomllib.loads(lock.decode())["package"])
    m = package(metadata["packages"])
    require(m["source"] == k["source"] == "registry+https://github.com/rust-lang/crates.io-index"
            and len(k["checksum"]) == 64, "registry identity mismatch")
    registry = (pathlib.Path(os.environ.get("CARGO_HOME", pathlib.Path.home() / ".cargo")) / "registry/src").resolve(strict=True)
    root = pathlib.Path(m["manifest_path"]).parent.resolve(strict=True)
    require(root.is_relative_to(registry), "registry root escaped")
    require(git_blob((APP / "src/candidate_assets.rs").read_bytes()) == ADAPTER_BLOB,
            "reviewed asset adapter changed")
    main = (APP / "src/main.rs").read_bytes()
    require(main.count(b".with_assets(") == 1 and b".with_assets(candidate_assets::CandidateAssets)" in main,
            "asset registration drift")
    for path in (APP / "src").rglob("*.rs"):
        if path.name != "candidate_assets.rs":
            require(not any(x in path.read_bytes() for x in
                        (b"AllAssets", b"icon_assets!", b"include_bytes!", b"impl AssetSource", b"gpui_kit::assets::Assets")),
                    "additional asset source requires review")
    for name, expected in {**index["files"], **selected, **excluded}.items():
        verify_file(root, name, expected)
    return {
        "schema": "lanveriq.gpui.selected-icon-source-review-v1",
        "review_source_sha": os.environ["GITHUB_SHA"],
        "lock_sha256": digest,
        "registry_checksum": k["checksum"],
        "adapter_git_blob": ADAPTER_BLOB,
        "historical_index_git_blob": INDEX_BLOB,
        "selected_canonical_count": len(selected),
        "excluded_original_count": len(excluded),
        "scope": "SOURCE_ONLY_NO_FINAL_ELF_OR_LEGAL_APPROVAL",
        "license_review": "OPEN_NOT_APPROVED",
        "release_approved": False, "install_authorized": False,
    }


if __name__ == "__main__":
    require(len(sys.argv) == 3, "expected metadata and output")
    report = review(json.loads(pathlib.Path(sys.argv[1]).read_text()))
    write(pathlib.Path(sys.argv[2]), report)
    print("PASS: selected icon adapter, 96 canonical and 12 excluded original registry payloads; source only")
