#!/usr/bin/env python3
"""L2B-2 LAB ONLY: verify Ed25519 detached receipt signature with OpenSSL 3.

An ephemeral CI test key is NOT a release trust anchor. No path here approves
signing, production installation, Agent activation or distribution.
"""
import argparse
import hashlib
import importlib.util
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_candidate_evidence", HERE / "verify_desktop_candidate_evidence.py"
)
candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(candidate)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def bounded_file(path, max_size):
    path = pathlib.Path(path)
    require(path.is_file() and not path.is_symlink(), "missing, symlink or nonregular signature input")
    require(1 <= path.stat().st_size <= max_size, "signature input size invalid")
    return path


def verify_lab(receipt, signature, public_key, pinned_pubkey_sha256,
               package, sbom, inputs, source_sha, run_id, attempt):
    """Proof of crypto binding ONLY; cannot return a release approval."""
    receipt = bounded_file(receipt, 8192)
    signature = bounded_file(signature, 64)
    public_key = bounded_file(public_key, 4096)
    require(signature.stat().st_size == 64, "Ed25519 signature must be exactly 64 bytes")
    require(isinstance(pinned_pubkey_sha256, str)
            and re.fullmatch("[0-9a-f]{64}", pinned_pubkey_sha256) is not None,
            "invalid explicit lab key fingerprint")
    actual_hash = hashlib.sha256(public_key.read_bytes()).hexdigest()
    require(actual_hash == pinned_pubkey_sha256, "unrecognized lab key fingerprint")
    # This is the *existing* fail-closed archive, SPDX and source SHA/run check.
    candidate.verify(receipt, package, sbom, inputs, source_sha, run_id, attempt)
    try:
        result = subprocess.run([
            "openssl", "pkeyutl", "-verify", "-rawin", "-pubin",
            "-inkey", str(public_key), "-sigfile", str(signature), "-in", str(receipt)
        ], shell=False, capture_output=True, timeout=15, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise ValueError("OpenSSL signature verifier unavailable") from exc
    require(result.returncode == 0, "Ed25519 detached receipt signature rejected")
    return ("PASS: LAB Ed25519 signature cryptographically valid and candidate hashes match; "
            "EPHEMERAL TEST KEY NOT TRUSTED, NO RELEASE OR INSTALL APPROVAL")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("receipt", "signature", "public-key", "package", "sbom",
                 "cargo-lock", "pnpm-lock", "tauri-config"):
        parser.add_argument("--" + name, required=True, type=pathlib.Path)
    parser.add_argument("--lab-pubkey-sha256", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--attempt", type=int, required=True)
    args = parser.parse_args()
    inputs = {"Cargo.lock": args.cargo_lock, "pnpm-lock.yaml": args.pnpm_lock,
              "tauri.conf.json": args.tauri_config}
    print(verify_lab(args.receipt, args.signature, args.public_key,
                     args.lab_pubkey_sha256, args.package, args.sbom, inputs,
                     args.source_sha, args.run_id, args.attempt))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
