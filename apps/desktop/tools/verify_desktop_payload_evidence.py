#!/usr/bin/env python3
"""L2B-4: read-only Debian file inventory and partial Syft SPDX, NOT a release."""
import argparse
import hashlib
import importlib.util
import io
import json
import pathlib
import re
import stat
import sys
import tarfile

HERE = pathlib.Path(__file__).resolve().parent


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


deb = load("desktop_archive", "verify-desktop-deb.py")
base = load("desktop_source_receipt", "verify_desktop_candidate_evidence.py")


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def archive_files(package):
    raw = deb.run("dpkg-deb", "--fsys-tarfile", str(package))
    entries = deb.members(raw, "data")
    hashes = {}
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:*") as archive:
        for member in archive:
            name = member.name.removeprefix("./").rstrip("/")
            if name and member.isfile():
                f = archive.extractfile(member)
                require(f is not None, "unreadable archive payload")
                h = hashlib.sha256()
                while block := f.read(1024 * 1024):
                    h.update(block)
                hashes[name] = h.hexdigest()
    require(set(hashes) == {name for name, member in entries.items() if member.isfile()},
            "incomplete archive file hashes")
    return entries, hashes


def inventory(package, extracted):
    root = pathlib.Path(extracted)
    require(root.is_dir() and not root.is_symlink(), "missing or linked extraction root")
    entries, hashes = archive_files(package)
    files = {}
    for file in root.rglob("*"):
        require(not file.is_symlink(), "symlink in extracted payload")
        require(file.is_dir() or file.is_file(), "special extracted payload entry")
        name = file.relative_to(root).as_posix()
        mode = stat.S_IMODE(file.lstat().st_mode)
        member = entries.get(name)
        if member is None:
            require(file.is_dir() and mode == 0o755
                    and any(p.startswith(name + "/") for p in entries),
                    "extra extracted file or directory")
            continue
        require(file.is_dir() == member.isdir() and file.is_file() == member.isfile(),
                "payload entry type mismatch")
        require(mode == (member.mode & 0o7777), "extracted payload mode mismatch")
        if file.is_file():
            require(file.stat().st_size == member.size, "extracted file size mismatch")
            checksum = base.digest(file)
            require(checksum == hashes[name], "extracted file digest mismatch")
            files[name] = {"path": name, "bytes": member.size,
                           "mode": format(mode, "04o"), "sha256": checksum}
    require(set(files) == set(hashes), "missing extracted payload file")
    require("usr/bin/lanveriq-desktop" in files, "missing native GUI executable")
    return [files[name] for name in sorted(files)]


def partial_spdx(path):
    obj = base.json_read(path, 12 * 1024 * 1024)
    require(isinstance(obj, dict) and obj.get("spdxVersion") == "SPDX-2.3"
            and obj.get("SPDXID") == "SPDXRef-DOCUMENT", "invalid payload SPDX")
    pkgs = obj.get("packages")
    require(isinstance(pkgs, list) and len(pkgs) <= 10000, "invalid SPDX packages")
    for pkg in pkgs:
        require(isinstance(pkg, dict) and isinstance(pkg.get("name"), str)
                and 1 <= len(pkg["name"]) <= 240, "invalid SPDX package")
        name = pkg["name"].lower()
        require(name != "lanveriq-agent" and not name.startswith("lanveriq-agent-"),
                "forbidden Agent payload SPDX component")
    return len(pkgs)


def evidence(package, extracted, payload_spdx, source_receipt, source_spdx,
             source_inputs, source_sha, run, attempt):
    package = pathlib.Path(package)
    require(package.name == base.PACKAGE, "incorrect Debian filename")
    require(isinstance(source_sha, str) and re.fullmatch(r"[0-9a-f]{40}", source_sha),
            "invalid source commit")
    require(type(run) is int and run > 0 and type(attempt) is int
            and 1 <= attempt <= 100, "invalid CI run metadata")
    deb.validate(package)
    base.verify(source_receipt, package, source_spdx, source_inputs, source_sha, run, attempt)
    files = inventory(package, extracted)
    scanned = partial_spdx(payload_spdx)
    return {
        "schema": "lanveriq.desktop.untrusted-payload-evidence.v1",
        "repository": "kunlong-luo/lanveriq",
        "source_sha": source_sha, "run_id": run, "attempt": attempt,
        "deb_sha256": base.digest(package),
        "source_receipt_sha256": base.digest(source_receipt),
        "source_spdx_sha256": base.digest(source_spdx),
        "payload_spdx_sha256": base.digest(payload_spdx),
        "payload_spdx_detected_packages": scanned,
        "payload_files": files,
        "scope": "actual-deb-files-not-complete-runtime-dependencies",
        "signature": "UNSIGNED_UNTRUSTED",
        "release_approved": False, "install_authorized": False,
    }


def verify(output, *args):
    require(base.json_read(output, 16384) == evidence(*args),
            "payload evidence mismatch or forged release")
    return "PASS: local payload inventory + partial SPDX, UNSIGNED AND NOT APPROVED"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "verify"))
    for key in ("package", "extracted", "payload-spdx", "source-receipt", "source-spdx",
                "cargo-lock", "pnpm-lock", "tauri-config", "output"):
        parser.add_argument("--" + key, type=pathlib.Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--attempt", type=int, required=True)
    x = parser.parse_args()
    inputs = {"Cargo.lock": x.cargo_lock, "pnpm-lock.yaml": x.pnpm_lock,
              "tauri.conf.json": x.tauri_config}
    args = (x.package, x.extracted, x.payload_spdx, x.source_receipt,
            x.source_spdx, inputs, x.source_sha, x.run_id, x.attempt)
    if x.mode == "create":
        require(not x.output.exists(), "refuse overwriting payload evidence")
        x.output.write_text(json.dumps(evidence(*args), sort_keys=True, indent=2) + "\n")
        print("PASS: local unsigned payload evidence created, not published")
    else:
        print(verify(x.output, *args))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as error:
        print(f"NOT_APPROVED: {error}", file=sys.stderr)
        sys.exit(2)
