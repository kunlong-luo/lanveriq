#!/usr/bin/env python3
"""Local PG18 custom archive evidence; NEVER production backup authorization.

The archive and manifest are controlled by the SAME OS principal. An attacker
able to rewrite both can generate a new matching digest. This is explicitly
not a WORM witness, a signature, proof of pg_restore or trusted time.
"""

import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import stat
import sys


SCHEMA = "lanveriq.pg18.local-backup-evidence.v1"
SCOPE = "DISPOSABLE_SYNTHETIC_SAME_ADMIN"
FIXED = {
    "schema": SCHEMA,
    "scope": SCOPE,
    "postgresql_major": 18,
    "format": "pg_dump_custom",
    "production_restore_authorized": False,
    "independent_custody_verified": False,
}
ALL_KEYS = set(FIXED) | {"archive_bytes", "sha256"}
MAX_MANIFEST_BYTES = 4096
MIN_ARCHIVE_BYTES = 2048


class InvalidEvidence(Exception):
    """Cannot prove the requested same-admin file integrity check."""


def _open_regular_private(path: Path):
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    fd = os.open(path, flags)
    try:
        metadata = os.fstat(fd)
        if (not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1
                or metadata.st_uid != os.getuid()
                or metadata.st_mode & 0o077
                or not metadata.st_mode & stat.S_IRUSR):
            raise InvalidEvidence("not a single-owner, private, regular file")
        return os.fdopen(fd, "rb", closefd=True), metadata.st_size
    except BaseException:
        os.close(fd)
        raise


def inspect_archive(path: Path):
    file, initial_size = _open_regular_private(path)
    with file:
        if initial_size < MIN_ARCHIVE_BYTES:
            raise InvalidEvidence("missing or incomplete custom archive")
        digest = hashlib.sha256()
        total = 0
        header = b""
        while True:
            block = file.read(1024 * 1024)
            if not block:
                break
            if not header:
                header = block[:5]
            digest.update(block)
            total += len(block)
        if header != b"PGDMP" or total != initial_size:
            raise InvalidEvidence("invalid archive header or modified file size")
        if os.fstat(file.fileno()).st_size != initial_size:
            raise InvalidEvidence("archive changed during read")
        return {"archive_bytes": total, "sha256": digest.hexdigest()}


def _reject_duplicate_pairs(pairs):
    values = {}
    for key, value in pairs:
        if key in values:
            raise InvalidEvidence("duplicate evidence field")
        values[key] = value
    return values


def _reject_nonfinite(_):
    raise InvalidEvidence("non-JSON number")


def read_manifest(path: Path):
    file, length = _open_regular_private(path)
    with file:
        if length <= 0 or length > MAX_MANIFEST_BYTES:
            raise InvalidEvidence("empty or oversized evidence")
        raw = file.read(MAX_MANIFEST_BYTES + 1)
        if len(raw) != length:
            raise InvalidEvidence("manifest changed during read")
    try:
        data = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_reject_duplicate_pairs,
            parse_constant=_reject_nonfinite,
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InvalidEvidence("malformed evidence JSON") from exc
    if not isinstance(data, dict) or set(data) != ALL_KEYS:
        raise InvalidEvidence("unknown or missing evidence fields")
    for key, value in FIXED.items():
        if type(data[key]) is not type(value) or data[key] != value:
            raise InvalidEvidence("evidence scope/version/authority mismatch")
    if type(data["archive_bytes"]) is not int or data["archive_bytes"] < MIN_ARCHIVE_BYTES:
        raise InvalidEvidence("invalid archive size")
    if not isinstance(data["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", data["sha256"]):
        raise InvalidEvidence("invalid archive digest")
    return data


def create(archive: Path, manifest: Path):
    evidence = FIXED | inspect_archive(archive)
    encoded = (json.dumps(evidence, sort_keys=True, separators=(",", ":")) + "\n").encode()
    if len(encoded) > MAX_MANIFEST_BYTES:
        raise InvalidEvidence("evidence oversized")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(manifest, flags, 0o600)
    try:
        with os.fdopen(fd, "wb", closefd=True) as file:
            file.write(encoded)
            file.flush()
            os.fsync(file.fileno())
    except BaseException:
        try:
            os.unlink(manifest)
        except OSError:
            pass
        raise


def verify(archive: Path, manifest: Path):
    evidence = read_manifest(manifest)
    actual = inspect_archive(archive)
    if actual["archive_bytes"] != evidence["archive_bytes"]:
        raise InvalidEvidence("archive size does not match local evidence")
    if not hmac.compare_digest(actual["sha256"], evidence["sha256"]):
        raise InvalidEvidence("archive SHA-256 does not match local evidence")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("create", "verify"))
    parser.add_argument("archive", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.action == "create":
            create(args.archive, args.manifest)
        else:
            verify(args.archive, args.manifest)
    except (InvalidEvidence, OSError, ValueError) as exc:
        print(f"INCOMPLETE: local PG18 archive evidence refused ({exc}); G-08 OPEN", file=sys.stderr)
        return 2
    print(f"PASS: local PG18 backup evidence {args.action} (same-admin synthetic only; G-08 OPEN)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
