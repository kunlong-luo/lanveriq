"""L2B-18: disposable 2-key OpenSSL 3 lab, no release private keys/production."""
import copy
import hashlib
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_dual_signer_lab", HERE / "verify_desktop_dual_signer_lab.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class DualSignerLabTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys = tempfile.TemporaryDirectory()
        base = pathlib.Path(cls.keys.name)
        cls.pub_private = base / "publisher.key"
        cls.pub_public = base / "publisher.pub"
        cls.rev_private = base / "reviewer.key"
        cls.rev_public = base / "reviewer.pub"
        for private, public in (
            (cls.pub_private, cls.pub_public),
            (cls.rev_private, cls.rev_public),
        ):
            cls.openssl("genpkey", "-algorithm", "ED25519", "-out", str(private))
            cls.openssl("pkey", "-in", str(private), "-pubout", "-out", str(public))

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    @staticmethod
    def openssl(*args):
        subprocess.run(("openssl",) + args, check=True, capture_output=True, timeout=15)

    def setUp(self):
        self.root_temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.root_temp.cleanup)
        self.root = pathlib.Path(self.root_temp.name)
        self.package = self.root / guard.NAME
        self.package.write_bytes(b"NOT A RELEASE DEB - ONLY DISPOSABLE LAB BYTES\n")
        self.statement = self.root / "statement.json"
        self.publisher_sig = self.root / "publisher.sig"
        self.reviewer_sig = self.root / "reviewer.sig"
        self.data = {
            "schema": guard.TYPE,
            "purpose": "cryptographic-interoperability-only",
            "release_approved": False,
            "artifact": {
                "name": guard.NAME,
                "sha256": hashlib.sha256(self.package.read_bytes()).hexdigest(),
                "source_sha": "a" * 40,
                "run_id": 151,
                "attempt": 1,
            },
        }
        self.save_and_sign()

    def save_and_sign(self):
        self.statement.write_text(json.dumps(self.data, sort_keys=True),
                                  encoding="utf-8")
        for private, signature in (
            (self.pub_private, self.publisher_sig),
            (self.rev_private, self.reviewer_sig),
        ):
            self.openssl(
                "pkeyutl", "-sign", "-rawin", "-inkey", str(private),
                "-in", str(self.statement), "-out", str(signature),
            )

    def verify(self, **overrides):
        values = {
            "statement": self.statement,
            "package": self.package,
            "publisher_sig": self.publisher_sig,
            "publisher_pub": self.pub_public,
            "publisher_pub_sha256": guard.sha256(self.pub_public),
            "reviewer_sig": self.reviewer_sig,
            "reviewer_pub": self.rev_public,
            "reviewer_pub_sha256": guard.sha256(self.rev_public),
            "source_sha": "a" * 40,
            "run_id": 151,
            "attempt": 1,
        }
        values.update(overrides)
        return guard.verify_dual(**values)

    def mutate(self, key, value):
        self.data[key] = value
        self.save_and_sign()

    def mutate_artifact(self, key, value):
        self.data["artifact"][key] = value
        self.save_and_sign()

    def test_two_valid_lab_signatures_do_not_grant_release(self):
        result = self.verify()
        self.assertEqual(result, guard.RESULT)
        self.assertIn("NOT RELEASE APPROVED", result)

    def test_deny_unsigned_publisher_signature(self):
        self.publisher_sig.write_bytes(b"0" * 64)
        with self.assertRaisesRegex(ValueError, "signature invalid"):
            self.verify()

    def test_deny_unsigned_reviewer_signature(self):
        self.reviewer_sig.write_bytes(b"0" * 64)
        with self.assertRaisesRegex(ValueError, "signature invalid"):
            self.verify()

    def test_deny_single_reused_publisher_key(self):
        with self.assertRaisesRegex(ValueError, "must be different"):
            self.verify(reviewer_pub=self.pub_public,
                        reviewer_pub_sha256=guard.sha256(self.pub_public))

    def test_deny_key_role_swap(self):
        with self.assertRaisesRegex(ValueError, "signature invalid"):
            self.verify(publisher_pub=self.rev_public,
                        publisher_pub_sha256=guard.sha256(self.rev_public),
                        reviewer_pub=self.pub_public,
                        reviewer_pub_sha256=guard.sha256(self.pub_public))

    def test_deny_publisher_fingerprint_substitution(self):
        with self.assertRaisesRegex(ValueError, "publisher LAB public key fingerprint mismatch"):
            self.verify(publisher_pub_sha256="0" * 64)

    def test_deny_reviewer_fingerprint_substitution(self):
        with self.assertRaisesRegex(ValueError, "reviewer LAB public key fingerprint mismatch"):
            self.verify(reviewer_pub_sha256="0" * 64)

    def test_deny_bad_fingerprint_format(self):
        with self.assertRaisesRegex(ValueError, "invalid publisher key fingerprint"):
            self.verify(publisher_pub_sha256="SIGNING-KEY")

    def test_deny_tampered_deb_bytes(self):
        self.package.write_bytes(self.package.read_bytes() + b"appended")
        with self.assertRaisesRegex(ValueError, "package digest differs"):
            self.verify()

    def test_deny_forged_signed_release_approval(self):
        self.mutate("release_approved", True)
        with self.assertRaisesRegex(ValueError, "forged release approval"):
            self.verify()

    def test_deny_numeric_approval_bool(self):
        self.mutate("release_approved", 0)
        with self.assertRaisesRegex(ValueError, "forged release approval"):
            self.verify()

    def test_deny_extra_signed_install_authority(self):
        self.mutate("polkit_grant", True)
        with self.assertRaisesRegex(ValueError, "unknown or missing LAB statement keys"):
            self.verify()

    def test_deny_modified_statement_without_resigning(self):
        self.statement.write_bytes(self.statement.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "signature invalid"):
            self.verify()

    def test_deny_replayed_source(self):
        with self.assertRaisesRegex(ValueError, "source SHA/run/attempt replay"):
            self.verify(source_sha="b" * 40)

    def test_deny_replayed_run(self):
        with self.assertRaisesRegex(ValueError, "source SHA/run/attempt replay"):
            self.verify(run_id=152)

    def test_deny_replayed_attempt(self):
        with self.assertRaisesRegex(ValueError, "source SHA/run/attempt replay"):
            self.verify(attempt=2)

    def test_deny_signed_artifact_hash_drift(self):
        self.mutate_artifact("sha256", "f" * 64)
        with self.assertRaisesRegex(ValueError, "package digest differs"):
            self.verify()

    def test_deny_signed_artifact_name_drift(self):
        self.mutate_artifact("name", "lanveriq-agent_0.1.0_amd64.deb")
        with self.assertRaisesRegex(ValueError, "only historical GUI-only"):
            self.verify()

    def test_deny_unsigned_extra_source_field(self):
        self.mutate_artifact("supply_chain_approved", True)
        with self.assertRaisesRegex(ValueError, "unknown or missing artifact fields"):
            self.verify()

    def test_deny_float_run_id(self):
        self.mutate_artifact("run_id", 151.0)
        with self.assertRaisesRegex(ValueError, "invalid CI run id"):
            self.verify()

    def test_deny_bool_attempt(self):
        self.mutate_artifact("attempt", True)
        with self.assertRaisesRegex(ValueError, "invalid CI attempt"):
            self.verify()

    def test_deny_incorrect_statement_purpose(self):
        self.mutate("purpose", "production-release")
        with self.assertRaisesRegex(ValueError, "unapproved statement purpose"):
            self.verify()

    def test_deny_duplicate_json_key_even_if_signed(self):
        self.statement.write_text(
            '{"schema":"first","schema":"second"}', encoding="utf-8")
        for private, sig in (
            (self.pub_private, self.publisher_sig), (self.rev_private, self.reviewer_sig)
        ):
            self.openssl("pkeyutl", "-sign", "-rawin", "-inkey", str(private),
                         "-in", str(self.statement), "-out", str(sig))
        with self.assertRaisesRegex(ValueError, "duplicate LAB statement"):
            self.verify()

    def test_deny_symlink_statement(self):
        self.statement.rename(self.root / "payload.json")
        self.statement.symlink_to(self.root / "payload.json")
        with self.assertRaisesRegex(ValueError, "symlink or nonregular"):
            self.verify()

    def test_deny_symlink_pubkey(self):
        with self.assertRaisesRegex(ValueError, "symlink or nonregular"):
            linked = self.root / "fake.pub"
            linked.symlink_to(self.pub_public)
            self.verify(publisher_pub=linked)

    def test_deny_short_signature(self):
        self.reviewer_sig.write_bytes(b"short")
        with self.assertRaisesRegex(ValueError, "signature length must be 64"):
            self.verify()

    def test_deny_large_statement(self):
        self.statement.write_bytes(b"x" * (guard.MAX_STATEMENT + 1))
        with self.assertRaisesRegex(ValueError, "size out of bounds"):
            self.verify()

    def test_deny_invalid_expected_source_shape(self):
        with self.assertRaisesRegex(ValueError, "invalid expected source SHA"):
            self.verify(source_sha="source")

    def test_deny_missing_statement(self):
        self.statement.unlink()
        with self.assertRaises(FileNotFoundError):
            self.verify()

    def test_deny_negative_attempt(self):
        with self.assertRaisesRegex(ValueError, "invalid expected CI identity"):
            self.verify(attempt=-1)


if __name__ == "__main__":
    unittest.main()
