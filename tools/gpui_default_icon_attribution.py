#!/usr/bin/env python3
"""Review default native icon source and attribution without claiming binary embedding."""
import hashlib
import json
import os
import pathlib
import re
import sys
import tomllib

from gpui_compiled_notice_review import read_archive, sha
from gpui_distribution_material_review import source_file
from gpui_release_license import require, write

def git_blob(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def classify(data, row):
    actual = git_blob(data)
    require(actual == row["gpui_blob"], "GPUI icon upstream blob mismatch")
    return ("LUCIDE_EXACT_SAME_PATH_NOTICE_CANDIDATE" if actual == row["lucide_blob"]
            else "RETAINED_OR_CHANGED_ICON_ATTRIBUTION_OPEN")

def review(index, supply, metadata, lock_bytes, registry, app_root):
    require(index["schema"] == "lanveriq.gpui.default-icon-source-index-v1"
            and supply["schema"] == "lanveriq.gpui.untrusted-supply-chain-v1"
            and supply["release_approved"] is False and supply["install_authorized"] is False,
            "unexpected evidence")
    require(supply["source_sha"] == index["build_source"]
            and supply["lock_sha256"] == sha(lock_bytes), "source or lock mismatch")
    key = (index["package"], index["version"])
    def select(rows):
        matches = [p for p in rows if (p["name"],p["version"]) == key]
        require(len(matches) == 1, "expected unique asset package")
        return matches[0]
    p = select(supply["packages"])
    m = select(metadata["packages"])
    k = select(tomllib.loads(lock_bytes.decode())["package"])
    require(m["source"] == k["source"] == "registry+https://github.com/rust-lang/crates.io-index"
            and p["registry_checksum"] == k["checksum"], "registry identity mismatch")
    root = pathlib.Path(m["manifest_path"]).parent.resolve(strict=True)
    require(root.is_relative_to(registry.resolve(strict=True)), "registry root escaped")
    main = (app_root/"src/main.rs").read_bytes()
    require(git_blob(main) == index["app_main_git_blob"]
            and main.count(b".with_assets(gpui_kit::assets::Assets)") == 1, "asset registration drift")
    for f in (app_root/"src").rglob("*.rs"):
        data = f.read_bytes()
        require(not any(x in data for x in (b"AllAssets", b"icon_assets!", b"include_bytes!", b"impl AssetSource")),
                "additional asset source requires review")
    for name, blob in index["files"].items():
        path = root/name
        require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(root)
                and path.stat().st_size <= 262144 and git_blob(path.read_bytes()) == blob,
                "upstream asset source file mismatch: "+name)
    require(index["files"]["LICENSE-LUCIDE"] == index["lucide_license_blob"],
            "upstream license mismatch")
    listed = ["assets/"+x for x in (root/"default-icons.txt").read_text().splitlines() if x]
    rows = {r["path"]:r for r in index["rows"]}
    require(len(listed) == len(set(listed)) == len(rows) == len(index["rows"])
            and set(listed) == rows.keys(), "default icon list mismatch")
    catalog = {r["path"]:r for r in p["gpui_resource_inventory"]}
    require(len(catalog) == len(p["gpui_resource_inventory"]) and rows.keys() <= catalog.keys(),
            "default icon absent from inventory")
    license_record = [r for r in p["license_material"] if r["path"] == "LICENSE-LUCIDE"]
    require(len(license_record) == 1, "Lucide notice missing")
    notice = source_file(root, license_record[0])
    result = []
    for name in sorted(rows):
        require(re.fullmatch(r"assets/icons/[a-z0-9_.-]+\.svg", name), "unexpected icon path")
        f = source_file(root, catalog[name])
        result.append({**catalog[name], **rows[name], "source_review": classify(f.read_bytes(), rows[name]),
                       "embedded": "NOT_ESTABLISHED", "legal_approved": False})
    exact = sum(r["source_review"].startswith("LUCIDE_EXACT") for r in result)
    return {"schema":"lanveriq.gpui.default-icon-attribution-review-v1",
            "build_source_sha":supply["source_sha"], "review_source_sha":os.environ["GITHUB_SHA"],
            "lock_sha256":supply["lock_sha256"], "registry_checksum":k["checksum"],
            "gpui_commit":index["gpui_commit"], "lucide_commit":index["lucide_commit"],
            "lucide_notice_sha256":sha(notice.read_bytes()),
            "default_icon_count":len(result), "canonical_same_path_count":exact,
            "default_attribution_open_count":len(result)-exact,
            "catalog_resource_candidate_count":len(catalog),
            "outside_default_list_count":len(catalog)-len(result),
            "other_gpui_resource_candidate_count":sum(len(x["gpui_resource_inventory"]) for x in supply["packages"] if x is not p),
            "notice_scope":"LUCIDE_ISC_AND_FEATHER_MIT_TEXT_CANDIDATE_NOT_LEGAL_SELECTION",
            "scope":"DEFAULT_ASSET_SOURCE_NOT_FINAL_BINARY_EMBEDDING",
            "rows":result, "release_approved":False, "install_authorized":False, "license_review":"OPEN_NOT_APPROVED"}

if __name__ == "__main__":
    require(len(sys.argv)==5, "expected metadata, supply zip/digest and output")
    app = pathlib.Path("apps/desktop/gpui-candidate")
    index = json.loads(pathlib.Path("tools/gpui_default_icon_source_index.json").read_text())
    supply = json.loads(read_archive(pathlib.Path(sys.argv[2]),sys.argv[3])["candidate-supply-chain.json"])
    registry = pathlib.Path(os.environ.get("CARGO_HOME",pathlib.Path.home()/".cargo"))/"registry/src"
    report = review(index,supply,json.loads(pathlib.Path(sys.argv[1]).read_text()),(app/"Cargo.lock").read_bytes(),registry,app)
    write(pathlib.Path(sys.argv[4]),report)
    for k in ("default_icon_count","canonical_same_path_count","default_attribution_open_count",
              "catalog_resource_candidate_count","outside_default_list_count","other_gpui_resource_candidate_count"):
        print(k+":",report[k])
