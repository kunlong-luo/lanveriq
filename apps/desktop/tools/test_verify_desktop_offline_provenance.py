"""Receiver-side provenance offline preflight: MOCKED positive CLI output, real denial."""
import hashlib
import importlib.util
import json
import pathlib
import subprocess
import unittest
from unittest import mock

BASE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "provenance_receiver", BASE / "verify_desktop_offline_provenance.py"
)
verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verifier)
from test_verify_desktop_deb import DesktopDebPayloadNegatives


class ProvenanceReceiverContractTests(unittest.TestCase):
    def setUp(self):
        self.fixture = DesktopDebPayloadNegatives(
            "test_synthetic_offline_desktop_shape_is_accepted"
        )
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        made = self.fixture.build()
        self.package = self.fixture.root / "lanveriq-desktop_0.1.0_amd64.deb"
        made.rename(self.package)
        self.bundle = self.fixture.root / "fake-untrusted-bundle.jsonl"
        self.bundle.write_text('{"not":"a real signature"}\n')
        self.root = self.fixture.root / "fake-untrusted-root.jsonl"
        self.root.write_text('{"not":"an approved root"}\n')
        self.pin = hashlib.sha256(self.root.read_bytes()).hexdigest()
        self.commit = "a" * 40

    def invented_verified_json(self):
        # This is deliberately an API-shape MOCK, NOT cryptographic proof.
        return [{
            "verificationResult": {
                "signature": {"certificate": {
                    "sourceRepositoryIdentifier": verifier.EXPECTED_REPO_ID,
                    "sourceRepository": verifier.EXPECTED_REPO
                }},
                "verifiedTimestamps": [{"timestamp": "mock only"}],
                "statement": {
                    "predicateType": verifier.EXPECTED_PREDICATE,
                    "subject": [{
                        "name": self.package.name,
                        "digest": {"sha256": verifier.digest(self.package)}
                    }]
                }
            }
        }]

    def fake_output(self, data):
        return json.dumps(data).encode("utf-8")

    def invoke(self, **overrides):
        values = {
            "package": self.package, "bundle": self.bundle,
            "trusted_root": self.root, "pinned_root_sha256": self.pin,
            "source_sha": self.commit
        }
        values.update(overrides)
        return verifier.verify(**values)

    def mock_gh(self, payload=None, code=0):
        if payload is None:
            payload = self.invented_verified_json()
        return mock.patch.object(
            verifier.subprocess, "run",
            return_value=subprocess.CompletedProcess(
                args=[], returncode=code,
                stdout=self.fake_output(payload), stderr=b""
            )
        )

    def test_mocked_success_checks_all_offline_signer_constraints(self):
        with self.mock_gh() as runner:
            output = self.invoke()
            self.assertIn("RELEASE/INSTALL NOT APPROVED", output)
            args = runner.call_args.args[0]
            for flag in ("--bundle", "--custom-trusted-root", "--source-digest",
                         "--source-ref", "--signer-workflow", "--repo",
                         "--deny-self-hosted-runners", "--cert-oidc-issuer",
                         "--predicate-type", "--format"):
                self.assertIn(flag, args)
            self.assertEqual(args[args.index("--source-digest") + 1], self.commit)
            self.assertEqual(args[args.index("--source-ref") + 1], "refs/heads/main")
            self.assertEqual(args[args.index("--signer-workflow") + 1],
                             verifier.EXPECTED_SIGNER_WORKFLOW)
            self.assertFalse(runner.call_args.kwargs["shell"])

    def test_deny_broken_cli_signature_return_code(self):
        with self.mock_gh(code=1):
            with self.assertRaisesRegex(ValueError, "cryptographic"):
                self.invoke()

    def test_deny_missing_real_bundle(self):
        self.bundle.unlink()
        with self.assertRaisesRegex(ValueError, "missing, linked"):
            self.invoke()

    def test_deny_symlinked_trusted_root(self):
        other = self.fixture.root / "root-material"
        self.root.rename(other)
        self.root.symlink_to(other)
        with self.assertRaisesRegex(ValueError, "missing, linked"):
            self.invoke()

    def test_deny_unpinned_or_wrong_trust_root(self):
        for pin in ("bad", "0" * 64):
            with self.subTest(pin=pin):
                with self.assertRaisesRegex(ValueError, "pin"):
                    self.invoke(pinned_root_sha256=pin)

    def test_deny_bad_source_commit(self):
        with self.assertRaisesRegex(ValueError, "source commit"):
            self.invoke(source_sha="main")

    def test_deny_tampered_deb_before_offline_gh(self):
        self.package.write_bytes(self.package.read_bytes() + b"tamper")
        with self.mock_gh() as runner:
            with self.assertRaises(ValueError):
                self.invoke()
            runner.assert_not_called()

    def test_deny_certificate_wrong_repository_id(self):
        for value in ("0", "1439447218", None):
            data = self.invented_verified_json()
            data[0]["verificationResult"]["signature"]["certificate"]["sourceRepositoryIdentifier"] = value
            with self.subTest(value=value), self.mock_gh(data):
                with self.assertRaisesRegex(ValueError, "repository ID"):
                    self.invoke()

    def test_deny_certificate_wrong_repo_name(self):
        data = self.invented_verified_json()
        data[0]["verificationResult"]["signature"]["certificate"]["sourceRepository"] = "someone-else/lanveriq"
        with self.mock_gh(data):
            with self.assertRaisesRegex(ValueError, "repository mismatch"):
                self.invoke()

    def test_deny_unknown_or_untrusted_timestamp(self):
        for value in ([], None, {}):
            data = self.invented_verified_json()
            data[0]["verificationResult"]["verifiedTimestamps"] = value
            with self.subTest(value=value), self.mock_gh(data):
                with self.assertRaisesRegex(ValueError, "timestamp"):
                    self.invoke()

    def test_deny_unsigned_or_wrong_predicate_type(self):
        data = self.invented_verified_json()
        data[0]["verificationResult"]["statement"]["predicateType"] = "https://other"
        with self.mock_gh(data):
            with self.assertRaisesRegex(ValueError, "predicate"):
                self.invoke()

    def test_deny_wrong_subject_checksum(self):
        data = self.invented_verified_json()
        data[0]["verificationResult"]["statement"]["subject"][0]["digest"]["sha256"] = "f" * 64
        with self.mock_gh(data):
            with self.assertRaisesRegex(ValueError, "digest"):
                self.invoke()

    def test_deny_extra_subject(self):
        data = self.invented_verified_json()
        data[0]["verificationResult"]["statement"]["subject"].append(
            {"name": "unrelated", "digest": {"sha256": "f" * 64}})
        with self.mock_gh(data):
            with self.assertRaisesRegex(ValueError, "exactly one subject"):
                self.invoke()

    def test_deny_wrong_subject_name(self):
        data = self.invented_verified_json()
        data[0]["verificationResult"]["statement"]["subject"][0]["name"] = "lanveriq-agent"
        with self.mock_gh(data):
            with self.assertRaisesRegex(ValueError, "subject name"):
                self.invoke()

    def test_deny_empty_or_nonarray_result(self):
        for value in ([], {}, "invalid"):
            with self.subTest(result=value), self.mock_gh(value):
                with self.assertRaisesRegex(ValueError, "exactly one"):
                    self.invoke()

    def test_deny_duplicate_verified_certificate_fields(self):
        # Mock the post-cryptographic CLI output to prove strict JSON parsing.
        raw = b'[{"verificationResult":{"signature":{"certificate":{"sourceRepositoryIdentifier":"1439447217","sourceRepositoryIdentifier":"0"}}}}]'
        with mock.patch.object(verifier.subprocess, "run",
                               return_value=subprocess.CompletedProcess([], 0, raw, b"")):
            with self.assertRaisesRegex(ValueError, "duplicate verified JSON"):
                self.invoke()

    def test_deny_unsigned_mock_verification_result(self):
        with self.mock_gh([{"attestation": {"fake": True}}]):
            with self.assertRaisesRegex(ValueError, "missing cryptographically"):
                self.invoke()

    def test_deny_verifier_missing(self):
        with mock.patch.object(verifier.subprocess, "run", side_effect=FileNotFoundError):
            with self.assertRaisesRegex(ValueError, "unavailable"):
                self.invoke()


if __name__ == "__main__":
    unittest.main()
