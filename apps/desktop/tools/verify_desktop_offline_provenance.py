#!/usr/bin/env python3
"""L2B-3 recipient-side OFFLINE verified-attestation intake. NO INSTALL RIGHTS.

Use GitHub CLI for Sigstore cryptographic verification. This verifier does not
approve an issuer or trusted root: they must be independently approved/custodied.
Passing this tool alone cannot authorize package distribution or installation.
"""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import re
import subprocess
import sys

BASE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("strict_desktop_deb", BASE / "verify-desktop-deb.py")
ARCHIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ARCHIVE)

EXPECTED_REPO = "kunlong-luo/lanveriq"
# Repo numeric ID obtained from GitHub repository API; independently pin/check
# before approving release. Name-only verification is insufficient offline.
EXPECTED_REPO_ID = "1439447217"
EXPECTED_SIGNER_WORKFLOW = "kunlong-luo/lanveriq/.github/workflows/desktop-release.yml"
EXPECTED_REF = "refs/heads/main"
EXPECTED_PREDICATE = "https://slsa.dev/provenance/v1"
SHA256 = re.compile(r"[a-f0-9]{64}\Z")
SHA1 = re.compile(r"[a-f0-9]{40}\Z")
MAX_BUNDLE = 4 * 1024 * 1024
MAX_ROOT = 4 * 1024 * 1024


def deny_unless(ok, detail):
    if not ok:
        raise ValueError(detail)


def regular(path, max_size):
    p = pathlib.Path(path)
    deny_unless(p.is_file() and not p.is_symlink(), "missing, linked or non-regular provenance input")
    deny_unless(1 <= p.stat().st_size <= max_size, "oversized/empty provenance input")
    return p


def digest(path):
    h = hashlib.sha256()
    with pathlib.Path(path).open("rb") as stream:
        while part := stream.read(1024 * 1024):
            h.update(part)
    return h.hexdigest()


def unique_json(pairs):
    out = {}
    for key, value in pairs:
        deny_unless(key not in out, "duplicate verified JSON field")
        out[key] = value
    return out


def check_verified_result(output, package_digest):
    """Read only GitHub CLI's successfully verified stdout, never user-supplied JSON."""
    deny_unless(isinstance(output, bytes) and 0 < len(output) <= 4 * 1024 * 1024,
                "empty or oversized GitHub attestation verification output")
    data = json.loads(output.decode("utf-8"), object_pairs_hook=unique_json)
    deny_unless(isinstance(data, list) and len(data) == 1,
                "require exactly one verified provenance statement")
    item = data[0]
    deny_unless(isinstance(item, dict), "invalid verified output")
    result = item.get("verificationResult")
    deny_unless(isinstance(result, dict), "missing cryptographically verified result")
    signature = result.get("signature")
    deny_unless(isinstance(signature, dict), "missing verified signer")
    certificate = signature.get("certificate")
    deny_unless(isinstance(certificate, dict), "missing verified certificate")
    deny_unless(str(certificate.get("sourceRepositoryIdentifier")) == EXPECTED_REPO_ID,
                "verified certificate repository ID mismatch or unpinned")
    deny_unless(certificate.get("sourceRepository") == EXPECTED_REPO,
                "verified certificate repository mismatch")
    timestamps = result.get("verifiedTimestamps")
    deny_unless(isinstance(timestamps, list) and len(timestamps) >= 1,
                "missing trusted verified timestamp (offline freshness still needs policy)")
    statement = result.get("statement")
    deny_unless(isinstance(statement, dict)
                and statement.get("predicateType") == EXPECTED_PREDICATE,
                "unapproved provenance predicate")
    subjects = statement.get("subject")
    deny_unless(isinstance(subjects, list) and len(subjects) == 1,
                "provenance must have exactly one subject")
    subject = subjects[0]
    deny_unless(isinstance(subject, dict)
                and subject.get("name") == "lanveriq-desktop_0.1.0_amd64.deb",
                "provenance subject name mismatch")
    subject_digest = subject.get("digest")
    deny_unless(isinstance(subject_digest, dict)
                and subject_digest.get("sha256") == package_digest,
                "provenance subject digest mismatch")
    return "PASS: cryptographic intake constraints checked; RELEASE/INSTALL NOT APPROVED"


def verify(package, bundle, trusted_root, pinned_root_sha256, source_sha):
    """Never elevate this offline intake to a release or install authorization."""
    deny_unless(isinstance(source_sha, str) and SHA1.fullmatch(source_sha),
                "invalid expected source commit")
    deny_unless(isinstance(pinned_root_sha256, str) and SHA256.fullmatch(pinned_root_sha256),
                "independent trusted root SHA-256 pin required")
    regular(package, 120 * 1024 * 1024)
    regular(bundle, MAX_BUNDLE)
    regular(trusted_root, MAX_ROOT)
    deny_unless(digest(trusted_root) == pinned_root_sha256,
                "offline trusted root does not match independent pin")
    deny_unless(pathlib.Path(package).name == "lanveriq-desktop_0.1.0_amd64.deb",
                "unapproved candidate identity")
    ARCHIVE.validate(pathlib.Path(package))
    package_digest = digest(package)
    cmd = [
        "gh", "attestation", "verify", str(package),
        "--repo", EXPECTED_REPO, "--bundle", str(bundle),
        "--custom-trusted-root", str(trusted_root),
        "--signer-workflow", EXPECTED_SIGNER_WORKFLOW,
        "--source-ref", EXPECTED_REF, "--source-digest", source_sha,
        "--cert-oidc-issuer", "https://token.actions.githubusercontent.com",
        "--predicate-type", EXPECTED_PREDICATE,
        "--deny-self-hosted-runners", "--format", "json",
    ]
    try:
        process = subprocess.run(cmd, shell=False, capture_output=True,
                                 timeout=30, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise ValueError("official GitHub attestation verifier unavailable") from exc
    deny_unless(process.returncode == 0, "official offline cryptographic attestation verification failed")
    return check_verified_result(process.stdout, package_digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("package", "bundle", "trusted-root"):
        parser.add_argument("--" + field, type=pathlib.Path, required=True)
    parser.add_argument("--trusted-root-sha256", required=True)
    parser.add_argument("--source-sha", required=True)
    opt = parser.parse_args()
    print(verify(opt.package, opt.bundle, opt.trusted_root,
                 opt.trusted_root_sha256, opt.source_sha))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as err:
        print(f"NOT_APPROVED: {err}", file=sys.stderr)
        sys.exit(2)
