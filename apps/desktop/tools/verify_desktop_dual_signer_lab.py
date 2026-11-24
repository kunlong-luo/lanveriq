#!/usr/bin/env python3
"""L2B-18: cryptographic TWO-role Ed25519 evidence lab; NEVER release approval.

OpenSSL 3 verifies detached signatures. CLI-supplied key fingerprints are NOT
independently approved trust anchors, even if the signatures are valid. A
publisher AND a separate reviewer key must sign the SAME bounded statement.
No private keys, polkit, systemctl, signing, download or installation here.
"""
import argparse
import hashlib
import json
import pathlib
import re
import stat
import subprocess
import sys

MAX_STATEMENT = 8192
MAX_PACKAGE = 120 * 1024 * 1024
MAX_PUBLIC_KEY = 4096
HEX40 = re.compile(r"[0-9a-f]{40}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
NAME = "lanveriq-desktop_0.1.0_amd64.deb"
TYPE = "lanveriq.desktop.dual-signature-lab.v1"
RESULT = (
    "PASS: LAB two independent Ed25519 keys verified exact package and "
    "source/run identity; UNTRUSTED TEST KEYS, NOT RELEASE APPROVED"
)


def require(ok, detail):
    if not ok:
        raise ValueError(detail)


def regular(path, limit, exact=None):
    path = pathlib.Path(path)
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode), "missing, symlink or nonregular LAB input")
    require(0 < info.st_size <= limit, "LAB input size out of bounds")
    if exact is not None:
        require(info.st_size == exact, "Ed25519 signature length must be 64 bytes")
    return path


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as f:
        while data := f.read(1024 * 1024):
            digest.update(data)
    return digest.hexdigest()


def unique_json(pairs):
    obj = {}
    for name, value in pairs:
        require(name not in obj, "duplicate LAB statement JSON key")
        obj[name] = value
    return obj


def reject_constant(value):
    raise ValueError("nonstandard LAB statement JSON number")


def parse_statement(path):
    statement = regular(path, MAX_STATEMENT)
    raw = statement.read_bytes()
    data = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_json,
                      parse_constant=reject_constant)
    require(type(data) is dict, "LAB statement must be object")
    require(set(data) == {"schema", "purpose", "release_approved", "artifact"},
            "unknown or missing LAB statement keys")
    require(data["schema"] == TYPE, "unknown statement protocol")
    require(data["purpose"] == "cryptographic-interoperability-only",
            "unapproved statement purpose")
    require(data["release_approved"] is False,
            "forged release approval in LAB statement")
    a = data["artifact"]
    require(type(a) is dict and set(a) == {
        "name", "sha256", "source_sha", "run_id", "attempt"
    }, "unknown or missing artifact fields")
    require(a["name"] == NAME, "only historical GUI-only .deb can be tested")
    require(type(a["sha256"]) is str and HEX64.fullmatch(a["sha256"]),
            "invalid package hash")
    require(type(a["source_sha"]) is str and HEX40.fullmatch(a["source_sha"]),
            "invalid source SHA")
    require(type(a["run_id"]) is int and a["run_id"] > 0,
            "invalid CI run id")
    require(type(a["attempt"]) is int and 1 <= a["attempt"] <= 1000,
            "invalid CI attempt")
    return data


def openssl_verify(statement, signature, key):
    try:
        output = subprocess.run(
            ["openssl", "pkeyutl", "-verify", "-rawin", "-pubin",
             "-inkey", str(key), "-sigfile", str(signature),
             "-in", str(statement)],
            shell=False, capture_output=True, timeout=15, check=False,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise ValueError("OpenSSL 3 verification not available") from exc
    require(output.returncode == 0, "detached Ed25519 LAB signature invalid")


def verify_dual(statement, package, publisher_sig, publisher_pub,
                publisher_pub_sha256, reviewer_sig, reviewer_pub,
                reviewer_pub_sha256, source_sha, run_id, attempt):
    statement = regular(statement, MAX_STATEMENT)
    package = regular(package, MAX_PACKAGE)
    publisher_sig = regular(publisher_sig, 64, exact=64)
    reviewer_sig = regular(reviewer_sig, 64, exact=64)
    publisher_pub = regular(publisher_pub, MAX_PUBLIC_KEY)
    reviewer_pub = regular(reviewer_pub, MAX_PUBLIC_KEY)
    for label, fingerprint in (
        ("publisher", publisher_pub_sha256), ("reviewer", reviewer_pub_sha256)
    ):
        require(type(fingerprint) is str and HEX64.fullmatch(fingerprint),
                f"invalid {label} key fingerprint")
    require(publisher_pub_sha256 != reviewer_pub_sha256,
            "publisher and reviewer public key must be different")
    require(sha256(publisher_pub) == publisher_pub_sha256,
            "publisher LAB public key fingerprint mismatch")
    require(sha256(reviewer_pub) == reviewer_pub_sha256,
            "reviewer LAB public key fingerprint mismatch")
    require(type(source_sha) is str and HEX40.fullmatch(source_sha),
            "invalid expected source SHA")
    require(type(run_id) is int and run_id > 0
            and type(attempt) is int and 1 <= attempt <= 1000,
            "invalid expected CI identity")
    parsed = parse_statement(statement)
    artifact = parsed["artifact"]
    require(artifact["sha256"] == sha256(package),
            "package digest differs from signed statement")
    require(artifact["source_sha"] == source_sha
            and artifact["run_id"] == run_id
            and artifact["attempt"] == attempt,
            "source SHA/run/attempt replay or mismatch")
    openssl_verify(statement, publisher_sig, publisher_pub)
    openssl_verify(statement, reviewer_sig, reviewer_pub)
    return RESULT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for value in ("statement", "package", "publisher-sig", "publisher-pub",
                  "reviewer-sig", "reviewer-pub"):
        parser.add_argument("--" + value, type=pathlib.Path, required=True)
    for value in ("publisher-pub-sha256", "reviewer-pub-sha256", "source-sha"):
        parser.add_argument("--" + value, required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--attempt", type=int, required=True)
    args = parser.parse_args()
    print(verify_dual(
        args.statement, args.package, args.publisher_sig, args.publisher_pub,
        args.publisher_pub_sha256, args.reviewer_sig, args.reviewer_pub,
        args.reviewer_pub_sha256, args.source_sha, args.run_id, args.attempt,
    ))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
