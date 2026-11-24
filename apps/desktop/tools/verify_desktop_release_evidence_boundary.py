#!/usr/bin/env python3
"""L2B-16: verify the checked-in NOT-RELEASE-READY evidence boundary.

This is a local, offline regression contract, NOT a release authority,
GitHub attestation verifier, trust-root store, signature service or installer.
A PASS only proves that this repository still reports its audited GUI-only
Ubuntu L2 evidence accurately AND keeps every production release gate denied.
"""
import argparse
import json
import pathlib
import re
import stat
import sys

MAX_BYTES = 16384
ROOT = pathlib.Path(__file__).resolve().parents[3]
DEFAULT = ROOT / "docs/planning/desktop-release-evidence-boundary.json"

RUN = {
    "status": "PASSED_UNSIGNED_UBUNTU24_CI_ONLY",
    "source_sha": "c4f04e442be7da8181e9e62293764fbbb80e41d0",
    "github_run_id": 45322290248,
    "github_run_attempt": 1,
    "deb_name": "lanveriq-desktop_0.1.0_amd64.deb",
    "deb_sha256": "83ab54d1331213335e3ec77e383c6b8c384690f37826e42413ffc35d2efe504a",
    "oci_ubuntu24_digest": "ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b",
    "uploaded_for_distribution": False,
    "signed_by_release_publisher": False,
    "installed_during_audit": False,
}
PREVIEW = {
    "status": "PASSED_READONLY_UBUNTU24_CI_ONLY",
    "source_sha": "ea62c63cb9523c1ac34ac64c7f7134bbe58fbe3a",
    "github_run_id": 45322718592,
    "native_and_frontend_jobs_passed": True,
    "popos_device_proven": False,
}
GATES = {
    "G-02_independent_worm": "OPEN",
    "G-05_popos24_cosmic_physical_l3": "NOT_RUN",
    "G-07_legal_license": "OPEN",
    "G-08_operations_backup": "OPEN",
    "G-09_installer_ux": "OPEN",
    "trusted_publisher_signing": "NOT_APPROVED",
    "restricted_polkit_agent_install": "NOT_IMPLEMENTED",
    "agent_installer_l2": "NOT_RUN",
    "external_runtime_license_review": "NOT_APPROVED",
}
AUTHORITY = (
    "v01_release_ready",
    "agent_one_click_installer_available",
    "distributable_artifact_available",
    "production_agent_install_authorized",
    "production_network_authorized",
    "production_collection_authorized",
    "production_remote_control_authorized",
    "production_ca_issuance_authorized",
)
EXPECTED_TOP = {
    "schema", "evidence_date", "gui_only_debian_l2",
    "desktop_native_preview", "release_gates", "release_authority",
}


def require(ok, why):
    if not ok:
        raise ValueError(why)


def no_duplicate_keys(pairs):
    result = {}
    for key, val in pairs:
        require(key not in result, f"duplicate JSON key: {key}")
        result[key] = val
    return result


def reject_json_constant(constant):
    raise ValueError(f"non-standard JSON constant: {constant}")


def read_evidence(path):
    path = pathlib.Path(path)
    information = path.lstat()
    require(stat.S_ISREG(information.st_mode), "evidence must be regular, not symlink")
    require(0 < information.st_size <= MAX_BYTES, "evidence file size out of bounds")
    payload = path.read_bytes()
    require(len(payload) == information.st_size, "evidence bytes changed during read")
    return json.loads(
        payload.decode("utf-8"),
        object_pairs_hook=no_duplicate_keys,
        parse_constant=reject_json_constant,
    )


def same_record(actual, pinned, label):
    require(type(actual) is dict, f"{label}: not a JSON object")
    require(actual.keys() == pinned.keys(), f"{label}: missing or unknown fields")
    for name, value in pinned.items():
        got = actual[name]
        require(type(got) is type(value) and got == value,
                f"{label}: unverified or changed {name}")


def validate(data):
    require(type(data) is dict, "evidence root must be a JSON object")
    require(data.keys() == EXPECTED_TOP, "evidence root has missing or extra fields")
    require(data["schema"] == "lanveriq.desktop.release-evidence-boundary.v1",
            "unapproved evidence schema")
    require(type(data["evidence_date"]) is str
            and data["evidence_date"] == "2026-11-24", "unreviewed evidence date")
    same_record(data["gui_only_debian_l2"], RUN, "GUI-only native L2")
    same_record(data["desktop_native_preview"], PREVIEW, "readonly native preview")
    same_record(data["release_gates"], GATES, "release gates")
    authority = data["release_authority"]
    require(type(authority) is dict and authority.keys() == set(AUTHORITY),
            "unknown or missing release authority key")
    for name in AUTHORITY:
        require(authority[name] is False,
                f"release/production authority MUST remain disabled: {name}")
    return (
        "PASS: pinned GUI-only Ubuntu24 CI evidence and all release gates "
        "remain NOT APPROVED; no installer, trusted signer, PopOS L3 or "
        "production authority established"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=pathlib.Path, default=DEFAULT)
    args = parser.parse_args()
    print(validate(read_evidence(args.evidence)))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        print(f"NOT_RELEASE_READY: {exc}", file=sys.stderr)
        sys.exit(1)
