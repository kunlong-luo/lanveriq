#!/usr/bin/env python3
"""C18h: offline *intake* checks for independently reviewed WORM lab evidence.

Never verifies actual media, claims, keys, signatures or external custody. This
CLI intentionally has no 'approved' or 'G-02 passed' outcome. No network, no
provider SDK, no code path to Spring/Agent or production storage.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

MAX_BYTES = 128 * 1024
SCHEMA = "lanveriq.worm-evidence-intake.v1"
TEST_IDS = tuple(f"WORM-{n:02d}" for n in range(1, 12))
ROLES = (
    "application", "database", "storage_api", "storage_root", "backup",
    "witness_signer", "high_water", "recovery", "trusted_time", "reviewer",
)
UNTRUSTED_ROLES = ("application", "database", "storage_api", "storage_root", "backup")
PROTECTED_ROLES = ("witness_signer", "high_water", "recovery", "trusted_time", "reviewer")
MEDIUMS = ("LTO_WORM", "MANAGED_IMMUTABLE", "CEPH_WITH_EXTERNAL_CUSTODY")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
GIT_SHA = re.compile(r"[0-9a-f]{40}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z")
TOKEN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:/@#-]{2,255}\Z")
ALLOWED_STATUSES = ("PASS", "FAIL", "BLOCKED", "NOT_RUN", "NOT_APPLICABLE")


class InvalidBundle(ValueError):
    """Invalid, incomplete or ambiguous claimed lab evidence."""


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InvalidBundle(f"duplicate JSON key: {key!r}")
        result[key] = value
    return result


def reject_nonfinite(value):
    raise InvalidBundle(f"non-JSON numeric token: {value}")


def read_bundle(path: Path):
    content = path.read_bytes()
    if len(content) > MAX_BYTES:
        raise InvalidBundle("oversized evidence intake (128 KiB maximum)")
    try:
        return json.loads(content.decode("utf-8"), object_pairs_hook=no_duplicate_keys,
                          parse_constant=reject_nonfinite)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise InvalidBundle(f"malformed UTF-8 / JSON: {exc}") from exc


def structure(value, required, where):
    if type(value) is not dict:
        raise InvalidBundle(f"{where} must be an object")
    got = set(value)
    expected = set(required)
    if got != expected:
        raise InvalidBundle(f"{where} schema mismatch; missing={sorted(expected-got)}, unknown={sorted(got-expected)}")


def token(value, where):
    if type(value) is not str or not TOKEN.fullmatch(value):
        raise InvalidBundle(f"{where} must be a non-empty opaque reference token (3–256 characters)")
    return value


def hex_digest(value, where, pattern=SHA256):
    if type(value) is not str or not pattern.fullmatch(value):
        raise InvalidBundle(f"{where} must be lowercase exact-length hex")
    return value


def positive_int(value, where):
    if type(value) is not int or value < 1 or value > 2**63 - 1:
        raise InvalidBundle(f"{where} must be a positive signed-64-bit integer")
    return value


def iso_time(value):
    if type(value) is not str or not re.fullmatch(
        r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|[+-]\d\d:\d\d)", value
    ):
        raise InvalidBundle("checkpoint.independent_time must be RFC3339 with explicit timezone")
    try:
        stamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if stamp.utcoffset() is None or not (-24*3600 < stamp.utcoffset().total_seconds() < 24*3600):
            raise ValueError("invalid timezone")
        # Format validation only; does NOT authenticate the clock source.
        stamp.astimezone(timezone.utc)
    except ValueError as exc:
        raise InvalidBundle("checkpoint.independent_time invalid") from exc


def inspect(bundle):
    """Return missing-evidence errors; empty means intake complete ONLY.

    A complete, even maliciously fabricated, JSON document must still be reviewed
    against external systems by a genuinely independent human custodian.
    """
    errors = []
    try:
        structure(bundle, ("schema", "environment", "medium", "functional_sha",
                           "authorization", "custody", "checkpoint", "tests"), "bundle")
        if bundle["schema"] != SCHEMA:
            raise InvalidBundle("unsupported schema; never silently downgrade")
        if bundle["environment"] != "DISPOSABLE_SYNTHETIC_LAB":
            raise InvalidBundle("production/shared equipment is forbidden for this intake")
        if bundle["medium"] not in MEDIUMS:
            raise InvalidBundle("unknown/unapproved medium classification")
        hex_digest(bundle["functional_sha"], "functional_sha", GIT_SHA)

        auth = bundle["authorization"]
        structure(auth, ("ticket_ref", "scope_ref", "nonproduction_only",
                         "destructive_scope_approved"), "authorization")
        token(auth["ticket_ref"], "authorization.ticket_ref")
        token(auth["scope_ref"], "authorization.scope_ref")
        if auth["nonproduction_only"] is not True or auth["destructive_scope_approved"] is not True:
            raise InvalidBundle("independent written lab authorization not declared")

        custody = bundle["custody"]
        structure(custody, ("domains", "shared_root_or_break_glass",
                            "privilege_inventory_ref", "topology_ref", "key_custody_ref",
                            "time_attestation_ref"), "custody")
        if custody["shared_root_or_break_glass"] is not False:
            raise InvalidBundle("joint root/break-glass control is not independently protected")
        for name in ("privilege_inventory_ref", "topology_ref", "key_custody_ref", "time_attestation_ref"):
            token(custody[name], f"custody.{name}")
        domains = custody["domains"]
        structure(domains, ROLES, "custody.domains")
        for role, domain in domains.items():
            token(domain, f"custody.domains.{role}")
        for protected in PROTECTED_ROLES:
            for untrusted in UNTRUSTED_ROLES:
                if domains[protected] == domains[untrusted]:
                    raise InvalidBundle(f"shared administration: {protected} and {untrusted}")
        protected_ids = [domains[role] for role in PROTECTED_ROLES]
        if len(set(protected_ids)) != len(protected_ids):
            raise InvalidBundle("key, high-water, recovery, time and independent reviewer require distinct declared domains")

        checkpoint = bundle["checkpoint"]
        structure(checkpoint, ("stream_id", "previous_sequence", "sequence",
                               "head_sha256", "previous_head_sha256", "witness_key_fingerprint_sha256",
                               "primary_version", "recovery_version", "independent_time",
                               "readback_sha256", "external_publication_ref"), "checkpoint")
        if type(checkpoint["stream_id"]) is not str or not UUID.fullmatch(checkpoint["stream_id"]):
            raise InvalidBundle("checkpoint.stream_id must be a canonical lowercase UUID")
        seq = positive_int(checkpoint["sequence"], "checkpoint.sequence")
        previous = positive_int(checkpoint["previous_sequence"], "checkpoint.previous_sequence")
        if seq != previous + 1:
            raise InvalidBundle("checkpoint monotonic sequence must extend predecessor by exactly 1")
        for key in ("head_sha256", "previous_head_sha256", "witness_key_fingerprint_sha256", "readback_sha256"):
            hex_digest(checkpoint[key], f"checkpoint.{key}")
        if checkpoint["head_sha256"] == checkpoint["previous_head_sha256"]:
            raise InvalidBundle("checkpoint head must differ from predecessor")
        for key in ("primary_version", "recovery_version", "external_publication_ref"):
            token(checkpoint[key], f"checkpoint.{key}")
        if checkpoint["primary_version"] == checkpoint["recovery_version"]:
            raise InvalidBundle("same copy/version cannot be both primary and recovery")
        iso_time(checkpoint["independent_time"])

        tests = bundle["tests"]
        if type(tests) is not list or len(tests) != len(TEST_IDS):
            raise InvalidBundle("tests must contain exactly WORM-01 through WORM-11")
        seen = set()
        evidence_refs = set()
        for index, entry in enumerate(tests):
            structure(entry, ("id", "status", "actor_role", "evidence_ref", "reviewer_ref"),
                      f"tests[{index}]")
            case_id = entry["id"]
            if type(case_id) is not str or case_id not in TEST_IDS or case_id in seen:
                raise InvalidBundle(f"unknown or duplicate WORM test ID: {case_id!r}")
            seen.add(case_id)
            if entry["status"] not in ALLOWED_STATUSES:
                raise InvalidBundle(f"{case_id} has unknown test status")
            token(entry["actor_role"], f"{case_id}.actor_role")
            token(entry["reviewer_ref"], f"{case_id}.reviewer_ref")
            token(entry["evidence_ref"], f"{case_id}.evidence_ref")
            if entry["evidence_ref"] in evidence_refs:
                raise InvalidBundle("multiple WORM scenarios cite the same sole evidence reference")
            evidence_refs.add(entry["evidence_ref"])
            if entry["status"] == "NOT_APPLICABLE" and not (
                case_id == "WORM-09" and bundle["medium"] != "LTO_WORM"
            ):
                raise InvalidBundle(f"{case_id} cannot be marked NOT_APPLICABLE")
            if entry["status"] != "PASS" and entry["status"] != "NOT_APPLICABLE":
                errors.append(f"{case_id} remains {entry['status']}; not eligible for independent review")
            if case_id == "WORM-09" and bundle["medium"] == "LTO_WORM" and entry["status"] != "PASS":
                errors.append("LTO_WORM requires physical WORM-09 PASS; marker is not media proof")
        if seen != set(TEST_IDS):
            raise InvalidBundle("missing WORM test IDs")
    except InvalidBundle as exc:
        errors.insert(0, str(exc))
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description="Offline WORM evidence completeness intake (NOT proof of security)")
    parser.add_argument("bundle", type=Path, help="synthetic lab JSON metadata; NEVER private keys or production data")
    args = parser.parse_args(argv)
    try:
        errors = inspect(read_bundle(args.bundle))
    except (InvalidBundle, OSError) as exc:
        errors = [str(exc)]
    if errors:
        print("INCOMPLETE — FAIL CLOSED: " + "; ".join(errors))
        print("No WORM or G-02 approval; no actual media, role, signature, time or custody validated.")
        return 2
    print("READY_FOR_INDEPENDENT_REVIEW (metadata completeness ONLY; never an approval)")
    print("Not validated: actual permissions/root, physical immutability, versions, signatures, KMS, time, high-water or recovery. G-02/G-08 OPEN.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
