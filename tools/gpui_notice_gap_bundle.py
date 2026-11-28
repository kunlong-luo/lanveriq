#!/usr/bin/env python3
"""Collect the 22 known missing-registry-license review candidates; never a release NOTICE."""
import base64
import hashlib
import json
import pathlib
import re
import sys
import tomllib
import urllib.request

MAX = 262144
REGISTRY = "registry+https://github.com/rust-lang/crates.io-index"

def require(ok, message):
    if not ok:
        raise ValueError(message)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git_blob(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "lanveriq-review",
        "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        require(response.geturl() == url, "unexpected material redirect")
        data = response.read(MAX + 1)
    require(0 < len(data) <= MAX, "material response size outside bound")
    return data

def collect(index, lock_bytes, out, fetcher=fetch):
    require(index["schema"] == "lanveriq.gpui.notice-gap-review-input-v1", "wrong index")
    require(sha(lock_bytes) == index["lock_sha256"], "wrong committed lock")
    lock = {p["name"]+"@"+p["version"]: p for p in tomllib.loads(lock_bytes.decode())["package"]}
    groups = index["groups"]
    downstream = index["downstream"]
    packages = [p for g in groups for p in g["packages"]] + [d["package"] for d in downstream]
    require(len(packages) == len(set(packages)) == 22 and len(downstream) == 3, "wrong gap coverage")
    for p in packages:
        require(p in lock and lock[p]["source"] == REGISTRY and
                re.fullmatch(r"[0-9a-f]{64}", lock[p]["checksum"]), "unknown registry package")
    out.mkdir(parents=True, exist_ok=False)
    materials = {}
    rows = {}
    def store(data, provenance):
        require(0 < len(data) <= MAX, "material size outside bound")
        data.decode("utf-8")
        require(b"\0" not in data, "nontext material")
        key = sha(data)
        name = key + ".txt"
        if key not in materials:
            (out / name).write_bytes(data)
            materials[key] = {"file": name, "bytes": len(data), "provenance": []}
        materials[key]["provenance"].append(provenance)
        return key
    for g in groups:
        repo = g["repository"]
        require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo), "bad repository")
        require(g["commits"] and all(re.fullmatch(r"[0-9a-f]{40}", x) for x in g["commits"]), "bad commit claim")
        refs = []
        for m in g["materials"]:
            blob = m["git_blob"]
            require(re.fullmatch(r"[0-9a-f]{40}", blob), "bad git blob")
            url = f"https://api.github.com/repos/{repo}/git/blobs/{blob}"
            raw = json.loads(fetcher(url))
            require(raw["encoding"] == "base64" and raw["sha"] == blob, "unexpected blob response")
            data = base64.b64decode("".join(raw["content"].split()), validate=True)
            require(raw["size"] == len(data) and git_blob(data) == blob, "git blob checksum mismatch")
            refs.append(store(data, {"kind": "UPSTREAM_BLOB_CANDIDATE", "repository": repo,
                "prior_commit_claims": g["commits"], "git_blob": blob, "label": m["label"], "url": url}))
        require(refs, "empty material group")
        for pkg in g["packages"]:
            rows[pkg] = {"material_sha256": refs, "review": "UPSTREAM_CANDIDATE_OPEN"}
    for d in downstream:
        url = d["url"]
        require(url.startswith("https://metadata.ftp-master.debian.org/changelogs/main/r/"), "unexpected copyright source")
        data = fetcher(url)
        require(sha(data) == d["sha256"], "Debian copyright checksum mismatch")
        key = store(data, {"kind": "DOWNSTREAM_CORROBORATION_ONLY", "url": url})
        rows[d["package"]] = {"material_sha256": [key], "review": "DOWNSTREAM_CANDIDATE_OPEN"}
    for pkg, row in rows.items():
        row["registry_checksum"] = lock[pkg]["checksum"]
        row["registry_material_missing"] = True
        row["legal_approved"] = False
    report = {"schema": "lanveriq.gpui.notice-gap-review-bundle-v1", "lock_sha256": sha(lock_bytes),
              "input_supply_chain_source": index["supply_chain_source"],
              "input_supply_chain_artifact": index["supply_chain_artifact"],
              "input_supply_chain_zip_sha256": index["supply_chain_zip_sha256"],
              "input_debian_artifact": index["debian_artifact"],
              "packages": rows, "materials": materials, "registry_gaps_closed": 0,
              "scope": "REVIEW_CANDIDATES_ONLY_NOT_DISTRIBUTION_NOTICE",
              "release_approved": False, "install_authorized": False}
    (out / "index.json").write_text(json.dumps(report, sort_keys=True, indent=2)+"\n")
    (out / "READ-ME-FIRST.txt").write_text(
        "REVIEW ONLY - NOT A DISTRIBUTION NOTICE.\n"
        "22 missing registry materials remain OPEN. The files are upstream or downstream candidates.\n"
        "Git blob hashes are checked; prior commit association is not re-proven here.\n"
        "No legal license choice, asset attribution, complete notices, or release permission is implied.\n")
    print("Review package count:", len(rows))
    print("Distinct material files:", len(materials))
    print("Registry gaps closed: 0")
    return report

def rebind(index, lock_bytes, out, previous, supply):
    """Replay pinned sources from verified archive bytes, not a new network claim."""
    old = json.loads(previous["index.json"])
    current = json.loads(supply["candidate-supply-chain.json"])
    require(old["schema"] == "lanveriq.gpui.notice-gap-review-bundle-v1"
            and old["registry_gaps_closed"] == 0
            and old["release_approved"] is False and old["install_authorized"] is False,
            "old notice review not open")
    require(current["schema"] == "lanveriq.gpui.untrusted-supply-chain-v1"
            and current["source_sha"] == index["supply_chain_source"]
            and current["lock_sha256"] == index["lock_sha256"] == sha(lock_bytes)
            and current["release_approved"] is False and current["install_authorized"] is False,
            "current supply identity mismatch")
    rows = {p["name"]+"@"+p["version"]: p for p in current["packages"]}
    require(len(rows) == len(current["packages"]), "duplicate supply package")
    gaps = {key: row for key, row in rows.items() if row["review"] == "MISSING_MATERIAL_REVIEW_OPEN"}
    require(len(gaps) == current["missing_license_material_count"] == 22
            and set(gaps) == set(old["packages"]), "gap coverage mismatch")
    require(all(row["registry_checksum"] == old["packages"][key]["registry_checksum"]
                and old["packages"][key]["registry_material_missing"] is True
                and old["packages"][key]["legal_approved"] is False
                for key, row in gaps.items()), "old registry identity mismatch")
    cached = {}
    expected = {"index.json", "READ-ME-FIRST.txt"}
    for key, material in old["materials"].items():
        require(re.fullmatch(r"[0-9a-f]{64}", key)
                and material["file"] == key + ".txt", "unsafe old material name")
        expected.add(material["file"])
        body = previous[material["file"]]
        require(0 < len(body) == material["bytes"] <= MAX and sha(body) == key,
                "old material digest mismatch")
        for provenance in material["provenance"]:
            url = provenance["url"]
            if provenance["kind"] == "UPSTREAM_BLOB_CANDIDATE":
                blob = provenance["git_blob"]
                require(git_blob(body) == blob, "old git blob mismatch")
                data = json.dumps({"encoding": "base64", "sha": blob, "size": len(body),
                    "content": base64.b64encode(body).decode()}).encode()
            else:
                require(provenance["kind"] == "DOWNSTREAM_CORROBORATION_ONLY",
                        "unexpected provenance")
                data = body
            require(url not in cached or cached[url] == data, "ambiguous cached source")
            cached[url] = data
    require(set(previous) == expected, "unexpected old archive content")
    def fetch_cached(url):
        require(url in cached, "pinned source absent from old archive")
        return cached[url]
    report = collect(index, lock_bytes, out, fetch_cached)
    require(report["packages"] == old["packages"]
            and report["materials"] == old["materials"], "rebound material drift")
    report["rebound_from_lock_sha256"] = old["lock_sha256"]
    (out / "index.json").write_text(json.dumps(report, sort_keys=True, indent=2)+"\n")
    print("PASS: unchanged review-only source bytes rebound to current lock")
    return report

if __name__ == "__main__":
    index = json.loads(pathlib.Path("tools/gpui_notice_gap_index.json").read_text())
    lock = pathlib.Path("apps/desktop/gpui-candidate/Cargo.lock").read_bytes()
    if len(sys.argv) == 6:
        from gpui_compiled_notice_review import read_archive
        previous = read_archive(pathlib.Path(sys.argv[2]), sys.argv[3])
        supply = read_archive(pathlib.Path(sys.argv[4]), index["supply_chain_zip_sha256"])
        require(sys.argv[5] == str(index["supply_chain_artifact"]), "supply artifact receipt mismatch")
        rebind(index, lock, pathlib.Path(sys.argv[1]), previous, supply)
    else:
        require(len(sys.argv) == 2, "unexpected arguments")
        collect(index, lock, pathlib.Path(sys.argv[1]))
