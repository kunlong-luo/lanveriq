#!/usr/bin/env python3
"""Offline, source-only developer-preview contract. Never publishes a release.

A PASS proves local source metadata is internally conservative; it is not a
signature, independent provenance witness, approval, tag or GitHub Release.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = Path(__file__).resolve().parents[2]
CONTRACT_PATH = "docs/planning/source-preview-v0.1.0-dev.1.json"
NOTES_PATH = "docs/operations/source-preview-v0.1.0-dev.1.md"
VERSION = "v0.1.0-dev.1"
GATES = tuple(f"G-{number:02d}" for number in range(1, 11))
SWITCHES = ("networking", "inventory_collection", "automatic_recovery",
            "remote_control", "ca_issuance")
FIELDS = frozenset((
    "schema_version", "candidate_version", "channel", "publication_state",
    "production_release_state", "distribution", "release_creation_authorized",
    "approved_publisher_signer", "independent_apt_highwater_approved",
    "trusted_release_clock_approved", "privileged_polkit_install_approved",
    "physical_popos_l3_approved", "attached_assets", "installable_agent_packages",
    "production_switches", "gates", "verified_synthetic_evidence",
))
EVIDENCE_FIELDS = frozenset((
    "functional_sha", "linux_l2_run_id", "agent_l1_run_id", "environment",
))
# Exact completed GitHub CI facts belong to this ONE dev.1 candidate.
# A syntactically valid but arbitrary SHA or run number is not evidence.
VERIFIED_SYNTHETIC_EVIDENCE = {
    "functional_sha": "db570181d603a7f069a85a23c009658e9a8bed4c",
    "linux_l2_run_id": 45554279736,
    "agent_l1_run_id": 45554279741,
    "environment": "disposable_ubuntu_24_04_only",
}


class PreviewRejected(Exception):
    """Only fixed literal validation labels; no secrets or raw artifacts."""


def require(ok: bool, label: str) -> None:
    if not ok:
        raise PreviewRejected(label)


def validate_contract(data: object) -> None:
    require(type(data) is dict and set(data) == FIELDS, "closed_contract_schema")
    expect = {
        "schema_version": 1,
        "candidate_version": VERSION,
        "channel": "developer_preview_source_only",
        "publication_state": "NOT_PUBLISHED",
        "production_release_state": "NOT_RELEASE_READY",
        "distribution": "github_generated_source_archives_only",
    }
    for key, value in expect.items():
        require(type(data[key]) is type(value) and data[key] == value,
                "non_product_source_preview_" + key)
    for key in (
        "release_creation_authorized", "approved_publisher_signer",
        "independent_apt_highwater_approved", "trusted_release_clock_approved",
        "privileged_polkit_install_approved", "physical_popos_l3_approved",
    ):
        require(data[key] is False, "approval_must_not_be_inferred_" + key)
    require(type(data["attached_assets"]) is list and
            not data["attached_assets"], "no_release_attachments")
    require(type(data["installable_agent_packages"]) is list and
            not data["installable_agent_packages"], "no_agent_installer")
    switches = data["production_switches"]
    require(type(switches) is dict and set(switches) == set(SWITCHES),
            "closed_production_switch_schema")
    require(all(switches[key] is False for key in SWITCHES),
            "production_features_must_stay_off")
    gates = data["gates"]
    require(type(gates) is dict and set(gates) == set(GATES),
            "exact_ten_release_gates")
    require(all(gates[gate] == "OPEN" for gate in GATES),
            "no_release_gate_can_be_approved_by_preview")
    evidence = data["verified_synthetic_evidence"]
    require(type(evidence) is dict and set(evidence) == EVIDENCE_FIELDS,
            "closed_ci_evidence")
    require(type(evidence["functional_sha"]) is str and
            re.fullmatch("[0-9a-f]{40}", evidence["functional_sha"]) is not None,
            "synthetic_functional_sha")
    for key in ("linux_l2_run_id", "agent_l1_run_id"):
        require(type(evidence[key]) is int and evidence[key] > 0,
                "synthetic_ci_run_id")
    require(evidence["environment"] == "disposable_ubuntu_24_04_only",
            "cannot_claim_physical_popos")
    require(evidence == VERIFIED_SYNTHETIC_EVIDENCE,
            "source_preview_requires_exact_verified_ci_evidence")
    # Explicitly NEVER call the GitHub API or inspect a candidate installable
    # payload: this tool is an offline contract check only.


def verify_repository(root: Path) -> dict:
    raw = (root / CONTRACT_PATH).read_bytes()
    require(len(raw) < 8192, "bounded_preview_contract")
    data = json.loads(raw)
    validate_contract(data)
    expected = (
        ("README.md", "v0.1 NOT RELEASE READY"),
        ("README.zh.md", "v0.1 NOT RELEASE READY"),
        ("docs/CURRENT_STATUS.md", "v0.1 NOT RELEASE READY"),
        ("docs/planning/release-gap-register.md", "G-10"),
        ("docs/operations/release.md", "v0.1 NOT RELEASE READY"),
        (NOTES_PATH, "NOT PUBLISHED"),
    )
    for path, literal in expected:
        require((root / path).is_file(), "required_preview_readme_missing")
        require(literal in (root / path).read_text(encoding="utf-8"),
                "release_boundary_must_remain_visible")
    notes = (root / NOTES_PATH).read_text(encoding="utf-8")
    for marker in ("SOURCE-ONLY", "NOT RELEASE READY", "G-01", "G-10",
                   "no .deb", "no GUI installer", "NOT PUBLISHED"):
        require(marker in notes, "preview_notes_omit_boundary")
    workflow = (root / ".github/workflows/source-preview-preflight.yml").read_text(
        encoding="utf-8")
    require("permissions:\n  contents: read" in workflow,
            "preflight_must_be_read_only")
    require("workflow_dispatch:" in workflow,
            "preflight_requires_explicit_dispatch_option")
    require("create release" not in workflow.lower() and
            "gh release" not in workflow.lower() and
            "softprops/action-gh-release" not in workflow.lower(),
            "preflight_must_not_publish")
    return data


def git_commit_sha(root: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        text=True, timeout=10,
    )
    sha = result.stdout.strip()
    require(re.fullmatch("[0-9a-f]{40}", sha) is not None,
            "exact_source_commit_sha")
    runner_sha = os.getenv("GITHUB_SHA")
    if runner_sha is not None:
        require(sha == runner_sha, "checkout_sha_must_match_gha_sha")
    return sha


def build_preview_manifest(root: Path, data: dict) -> dict:
    # An informational manifest with SHA-256 hashes of the exact checked-in
    # release boundary, NOT a signature, bill of materials or official release.
    files = (CONTRACT_PATH, NOTES_PATH, "README.md", "README.zh.md",
             "docs/CURRENT_STATUS.md", "docs/planning/release-gap-register.md")
    return {
        "schema_version": 1,
        "candidate_version": VERSION,
        "state": "SOURCE_ONLY_PREVIEW_PREFLIGHT_NOT_PUBLISHED",
        "source_commit_sha": git_commit_sha(root),
        "official_release_authorized": False,
        "attached_assets": [],
        "source_file_sha256": {
            path: hashlib.sha256((root / path).read_bytes()).hexdigest()
            for path in files
        },
        "previous_synthetic_l2_sha": data["verified_synthetic_evidence"][
            "functional_sha"],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Offline source preview refusal gate")
    parser.add_argument("--manifest", type=Path, default=None,
                        help="Optional informational JSON file outside Git tree")
    args = parser.parse_args()
    data = verify_repository(REPO)
    if args.manifest is not None:
        dest = args.manifest.resolve()
        # GitHub Actions writes only to its temporary directory. Never commit
        # a manifest as if its source digest were publisher-signed provenance.
        temp = os.getenv("RUNNER_TEMP")
        require(temp is not None and Path(temp).is_absolute() and
                dest.parent == Path(temp).resolve() and
                dest.name == "lanveriq-source-preview-preflight.json",
                "manifest_must_be_runner_temporary_only")
        dest.write_text(json.dumps(build_preview_manifest(REPO, data),
                                   indent=2, sort_keys=True) + "\n",
                        encoding="utf-8")
    print("PASS: v0.1.0-dev.1 local source-only preflight; no release/tag/"
          "installer/signing authority; G-01..G-10 OPEN; NOT PUBLISHED")


if __name__ == "__main__":
    try:
        main()
    except (PreviewRejected, OSError, ValueError, subprocess.SubprocessError):
        print("FAIL: source-preview preflight rejected unsafe or incomplete"
              " release evidence", file=sys.stderr)
        raise SystemExit(1) from None
