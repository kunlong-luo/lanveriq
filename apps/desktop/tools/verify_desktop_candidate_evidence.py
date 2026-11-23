#!/usr/bin/env python3
"""L2B candidate integrity only: unsigned, untrusted, no install authority."""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import re
import sys

self_dir = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("deb_guard", self_dir / "verify-desktop-deb.py")
deb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deb)
PACKAGE = "lanveriq-desktop_0.1.0_amd64.deb"
SOURCE_FILES = ("Cargo.lock", "pnpm-lock.yaml", "tauri.conf.json")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path, limit=120 * 1024 * 1024):
    path = pathlib.Path(path)
    require(path.is_file() and not path.is_symlink(), "missing or linked input")
    require(1 <= path.stat().st_size < limit, "evidence file size invalid")
    h = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            h.update(block)
    return h.hexdigest()


def pairs_unique(pairs):
    obj = {}
    for key, value in pairs:
        require(key not in obj, "duplicate JSON key")
        obj[key] = value
    return obj


def json_read(path, limit):
    digest(path, limit + 1)
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"),
                      object_pairs_hook=pairs_unique)


def spdx_count(path):
    doc = json_read(path, 12 * 1024 * 1024)
    require(isinstance(doc, dict) and doc.get("spdxVersion") == "SPDX-2.3",
            "invalid SPDX 2.3 document")
    require(doc.get("SPDXID") == "SPDXRef-DOCUMENT", "invalid SPDX identity")
    pkgs = doc.get("packages")
    require(isinstance(pkgs, list) and 2 <= len(pkgs) <= 10000,
            "source SPDX missing dependencies")
    names = set()
    for p in pkgs:
        require(isinstance(p, dict) and isinstance(p.get("name"), str)
                and 1 <= len(p["name"]) <= 240, "invalid SPDX package")
        name = p["name"].lower()
        require(not (name == "lanveriq-agent" or name.startswith("lanveriq-agent-")),
                "forbidden Agent dependency in Desktop SBOM")
        names.add(name)
    require({"react", "tauri"} <= names, "missing desktop dependencies in SPDX")
    return len(pkgs)


def receipt(package, sbom, source_inputs, sha, run, attempt):
    require(isinstance(sha, str) and re.fullmatch("[0-9a-f]{40}", sha),
            "invalid source SHA")
    require(type(run) is int and run > 0 and type(attempt) is int
            and 1 <= attempt <= 100, "invalid GitHub run metadata")
    package = pathlib.Path(package)
    require(package.name == PACKAGE, "wrong Debian package name")
    deb.validate(package)
    n = spdx_count(sbom)
    require(set(source_inputs) == set(SOURCE_FILES), "wrong source input list")
    hashes = {k: digest(source_inputs[k]) for k in SOURCE_FILES}
    return {
        "schema": "lanveriq.desktop.unsigned-receipt.v1",
        "repository": "kunlong-luo/lanveriq",
        "workflow": ".github/workflows/desktop-deb-candidate.yml",
        "source_sha": sha, "run_id": run, "attempt": attempt,
        "deb_name": PACKAGE, "deb_bytes": package.stat().st_size,
        "deb_sha256": digest(package),
        "source_sbom_type": "SPDX-2.3-source-not-binary",
        "source_sbom_sha256": digest(sbom, 12 * 1024 * 1024 + 1),
        "source_sbom_packages": n, "source_sha256": hashes,
        "signature": "UNSIGNED_UNTRUSTED", "install_authorized": False,
        "release_approved": False,
    }


def verify(path, package, sbom, inputs, sha, run, attempt):
    actual = json_read(path, 8192)
    require(actual == receipt(package, sbom, inputs, sha, run, attempt),
            "integrity mismatch or forged signed/release claim")
    return "PASS: candidate integrity only; NOT SIGNED, ATTESTED OR APPROVED"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("create", "verify"))
    for name in ("package", "sbom", "cargo-lock", "pnpm-lock", "tauri-config", "output"):
        p.add_argument("--" + name, required=True, type=pathlib.Path)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--run-id", required=True, type=int)
    p.add_argument("--attempt", required=True, type=int)
    a = p.parse_args()
    inputs = {"Cargo.lock": a.cargo_lock,
              "pnpm-lock.yaml": a.pnpm_lock, "tauri.conf.json": a.tauri_config}
    if a.mode == "create":
        data = receipt(a.package, a.sbom, inputs, a.source_sha, a.run_id, a.attempt)
        require(not a.output.exists(), "refuse to overwrite receipt")
        a.output.write_text(json.dumps(data, sort_keys=True, indent=2) + "\n")
        print("PASS: untrusted local receipt written; no signature or publish")
    else:
        print(verify(a.output, a.package, a.sbom, inputs, a.source_sha, a.run_id, a.attempt))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as err:
        print(f"NOT_APPROVED: {err}", file=sys.stderr)
        sys.exit(2)
