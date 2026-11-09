#!/usr/bin/env python3
"""Read-only authenticity check for official pinned RustDesk 1.5.1 assets.

Never downloads, installs, executes, launches or connects a remote client.
A PASS proves only local file size and SHA-256 against the approved manifest.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys


MANIFEST = pathlib.Path(__file__).with_name("client-release-lock.json")
OFFICIAL_PREFIX = "https://github.com/rustdesk/rustdesk/releases/download/1.5.1/"
RELEASE_API = "https://api.github.com/repos/rustdesk/rustdesk/releases/tags/1.5.1"
ALLOWED_NAMES = {
    "linux-amd64-deb": "rustdesk-1.5.1-x86_64.deb",
    "linux-amd64-appimage": "rustdesk-1.5.1-x86_64.AppImage",
    "windows-amd64-exe": "rustdesk-1.5.1-x86_64.exe",
    "windows-amd64-msi": "rustdesk-1.5.1-x86_64.msi",
}
# Stable release's official GitHub API digests as of 2026-11-08.
OFFICIAL_SHA256 = {
    "linux-amd64-deb": "b5d682ad90cb7571ecbdbf26eef58d3c4514e726a74ef6d08ce3f0b114ef0bd9",
    "linux-amd64-appimage": "bb8b7ccb3f50a201dccdc89b46385cff90f33e60b4730faeda9fa5504ac4dce6",
    "windows-amd64-exe": "319206042390ebfcba934cf0baa2f612f34de463c78c843b7cb8c22097c4bf7b",
    "windows-amd64-msi": "71069183cbc6124273138684ab4032d2211e83efa035886f9c774fe1d3eba3f0",
}


class AssetRejected(ValueError):
    pass


def verify_lock(document: dict) -> dict:
    if not isinstance(document, dict) or document.get("schema_version") != 1:
        raise AssetRejected("Unknown RustDesk release lock schema")
    if (document.get("source") != RELEASE_API
            or document.get("repository") != "rustdesk/rustdesk"
            or document.get("version") != "1.5.1"
            or document.get("tag") != "1.5.1"
            or document.get("prerelease") is not False
            or document.get("published_at") != "2026-11-01T16:13:27Z"):
        raise AssetRejected("Must pin the official stable RustDesk 1.5.1 release, not nightly")
    assets = document.get("assets")
    if not isinstance(assets, dict) or set(assets) != set(ALLOWED_NAMES):
        raise AssetRejected("Missing or unexpected RustDesk release assets")
    for name, filename in ALLOWED_NAMES.items():
        asset = assets[name]
        if not isinstance(asset, dict):
            raise AssetRejected("Malformed asset entry")
        if asset.get("filename") != filename or asset.get("url") != OFFICIAL_PREFIX + filename:
            raise AssetRejected(f"Unapproved RustDesk asset filename or download URL: {name}")
        if asset.get("sha256") != OFFICIAL_SHA256[name]:
            raise AssetRejected(f"RustDesk asset SHA-256 not matching independently checked release: {name}")
        size = asset.get("size_bytes")
        if isinstance(size, bool) or not isinstance(size, int) or not 1_000_000 <= size <= 150_000_000:
            raise AssetRejected(f"Invalid RustDesk asset size: {name}")
    return assets


def verify_file(assets: dict, asset: str, file: pathlib.Path) -> dict:
    if asset not in ALLOWED_NAMES:
        raise AssetRejected("Unknown RustDesk asset identifier")
    if not file.is_file() or file.is_symlink():
        raise AssetRejected("Asset path must be a regular non-symlink file")
    stat = file.stat()
    if stat.st_size != assets[asset]["size_bytes"]:
        raise AssetRejected(f"RustDesk file size mismatch ({asset})")
    sha = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    if sha.hexdigest() != assets[asset]["sha256"]:
        raise AssetRejected(f"RustDesk SHA-256 mismatch ({asset})")
    return {"asset": asset, "verification": "PASS_OFFICIAL_RELEASE_BYTES_ONLY",
            "version": "1.5.1", "size_bytes": stat.st_size,
            "sha256_verified": True, "signature_verified": False,
            "executable_started": False, "remote_session_verified": False}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset", choices=sorted(ALLOWED_NAMES))
    parser.add_argument("--file", type=pathlib.Path)
    args = parser.parse_args(argv)
    if (args.asset is None) != (args.file is None):
        parser.error("Use both --asset and --file, or neither for lock-only validation")
    try:
        assets = verify_lock(json.loads(MANIFEST.read_text(encoding="utf-8")))
        result = (verify_file(assets, args.asset, args.file)
                  if args.asset is not None
                  else {"verification": "PASS_RELEASE_LOCK_ONLY",
                        "version": "1.5.1", "assets_checked": len(assets),
                        "download_or_execution_performed": False})
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(json.dumps({"verification": "FAIL", "reason": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
