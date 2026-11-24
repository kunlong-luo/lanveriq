#!/usr/bin/env python3
"""L2B-19: OFFLINE LAB receiver pre-provisioned root pin/expiry/revoke/rollback.

The root hash/time/version are provided by the TEST CALLER, not an independent
production provisioning service. All successful checks return LAB_ONLY and
NEVER imply identity, signer custody, publisher approval, install or release.
For production use adopt an audited TUF/Sigstore implementation, not this lab.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "l2b18_dual_signer", HERE / "verify_desktop_dual_signer_lab.py"
)
dual = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dual)

FORMAT = "lanveriq.desktop.receiver-root-lab.v1"
HEX = re.compile(r"[0-9a-f]{64}\Z")
TIME = re.compile(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ\Z")
OUTPUT = (
    "LAB_ONLY: pinned receiver test root, expiry, revocation, rollback and "
    "dual signatures verified; NO PRODUCTION TRUST ROOT OR RELEASE AUTHORITY"
)


def need(ok, why):
    if not ok:
        raise ValueError(why)


def utc(text):
    need(type(text) is str and TIME.fullmatch(text),
         "invalid explicit LAB time format")
    try:
        parsed = datetime.strptime(text, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc)
    except ValueError as exc:
        raise ValueError("invalid explicit LAB timestamp") from exc
    return parsed


def parse_receiver_root(path):
    root = dual.regular(path, 8192)
    content = root.read_bytes()
    need(content, "empty receiver root")
    try:
        value = json.loads(content.decode("utf-8"), object_pairs_hook=dual.unique_json,
                           parse_constant=dual.reject_constant)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("receiver root JSON invalid") from exc
    need(type(value) is dict and set(value) == {
        "schema", "purpose", "release_approved", "version",
        "not_before", "expires_at", "roles", "revoked_fingerprints"
    }, "missing or unknown receiver root fields")
    need(value["schema"] == FORMAT, "unsupported receiver root schema")
    need(value["purpose"] == "offline-interoperability-only",
         "receiver root cannot assert real signer approval")
    need(value["release_approved"] is False,
         "receiver root cannot approve production release")
    need(type(value["version"]) is int and 1 <= value["version"] <= 100000000,
         "invalid receiver root version")
    before, expires = utc(value["not_before"]), utc(value["expires_at"])
    need(before < expires, "receiver root time interval invalid")
    roles = value["roles"]
    need(type(roles) is dict and set(roles) == {"publisher", "reviewer"},
         "receiver root must define two distinct roles")
    fingerprints = []
    for name in ("publisher", "reviewer"):
        entry = roles[name]
        need(type(entry) is dict and set(entry) == {"role", "key_sha256"},
             "invalid receiver root role entry")
        need(entry["role"] == f"synthetic-{name}",
             "unrecognized synthetic signer role")
        value_hash = entry["key_sha256"]
        need(type(value_hash) is str and HEX.fullmatch(value_hash),
             "invalid receiver role key fingerprint")
        fingerprints.append(value_hash)
    need(fingerprints[0] != fingerprints[1], "receiver role keys must differ")
    revoked = value["revoked_fingerprints"]
    need(type(revoked) is list and len(revoked) <= 128,
         "invalid receiver root revocation evidence")
    need(all(type(item) is str and HEX.fullmatch(item) for item in revoked)
         and len(revoked) == len(set(revoked)),
         "invalid or duplicate revoked key fingerprint")
    return value


def verify_receiver(root, pinned_root_sha256, trusted_time, minimum_root_version,
                    statement, package, publisher_sig, publisher_pub,
                    reviewer_sig, reviewer_pub, source_sha, run_id, attempt):
    """A TEST ONLY assertion; never return real trust/authorization."""
    need(type(pinned_root_sha256) is str and HEX.fullmatch(pinned_root_sha256),
         "invalid independently supplied LAB root pin")
    root = dual.regular(root, 8192)
    need(dual.sha256(root) == pinned_root_sha256,
         "receiver root changed after pre-provisioned LAB pin")
    data = parse_receiver_root(root)
    need(type(minimum_root_version) is int and 1 <= minimum_root_version <= 100000000,
         "invalid LAB high-water version")
    need(data["version"] >= minimum_root_version,
         "receiver root metadata rollback detected")
    now = utc(trusted_time)
    need(utc(data["not_before"]) <= now < utc(data["expires_at"]),
         "receiver LAB root expired or not yet valid")
    publisher_sha = data["roles"]["publisher"]["key_sha256"]
    reviewer_sha = data["roles"]["reviewer"]["key_sha256"]
    need(publisher_sha not in data["revoked_fingerprints"]
         and reviewer_sha not in data["revoked_fingerprints"],
         "receiver role key revoked")
    need(dual.sha256(dual.regular(publisher_pub, dual.MAX_PUBLIC_KEY)) == publisher_sha,
         "publisher not provisioned by receiver LAB root")
    need(dual.sha256(dual.regular(reviewer_pub, dual.MAX_PUBLIC_KEY)) == reviewer_sha,
         "reviewer not provisioned by receiver LAB root")
    dual.verify_dual(
        statement, package, publisher_sig, publisher_pub, publisher_sha,
        reviewer_sig, reviewer_pub, reviewer_sha, source_sha, run_id, attempt,
    )
    return OUTPUT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("root", "statement", "package", "publisher-sig", "publisher-pub",
                 "reviewer-sig", "reviewer-pub"):
        parser.add_argument("--" + name, required=True, type=pathlib.Path)
    for name in ("pinned-root-sha256", "trusted-time", "source-sha"):
        parser.add_argument("--" + name, required=True)
    for name in ("minimum-root-version", "run-id", "attempt"):
        parser.add_argument("--" + name, required=True, type=int)
    args = parser.parse_args()
    print(verify_receiver(
        args.root, args.pinned_root_sha256, args.trusted_time,
        args.minimum_root_version, args.statement, args.package,
        args.publisher_sig, args.publisher_pub, args.reviewer_sig,
        args.reviewer_pub, args.source_sha, args.run_id, args.attempt,
    ))


if __name__ == "__main__":
    try:
        main()
    except (OSError, UnicodeError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
