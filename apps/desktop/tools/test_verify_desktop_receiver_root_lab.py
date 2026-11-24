"""L2B-19: disposable OpenSSL 3 receiver-root LAB; never production trust."""
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
    "l2b19_receiver_root_lab", HERE / "verify_desktop_receiver_root_lab.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class ReceiverRootLabTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys_dir = tempfile.TemporaryDirectory()
        base = pathlib.Path(cls.keys_dir.name)
        cls.publisher_private = base / "synthetic-publisher.key"
        cls.publisher_public = base / "synthetic-publisher.pub"
        cls.reviewer_private = base / "synthetic-reviewer.key"
        cls.reviewer_public = base / "synthetic-reviewer.pub"
        for private, public in (
            (cls.publisher_private, cls.publisher_public),
            (cls.reviewer_private, cls.reviewer_public),
        ):
            cls.openssl("genpkey", "-algorithm", "ED25519", "-out", str(private))
            cls.openssl("pkey", "-in", str(private), "-pubout", "-out", str(public))

    @classmethod
    def tearDownClass(cls):
        cls.keys_dir.cleanup()

    @staticmethod
    def openssl(*args):
        subprocess.run(("openssl",) + args, check=True, capture_output=True, timeout=15)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        base = pathlib.Path(self.temp.name)
        self.root_path = base / "lab-preprovisioned-root.json"
        self.statement = base / "dual-statement.json"
        self.package = base / guard.dual.NAME
        self.publisher_sig = base / "publisher.sig"
        self.reviewer_sig = base / "reviewer.sig"
        self.package.write_bytes(b"synthetic GUI-only content - NOT an installable package\n")
        self.claim = {
            "schema": guard.dual.TYPE,
            "purpose": "cryptographic-interoperability-only",
            "release_approved": False,
            "artifact": {
                "name": guard.dual.NAME,
                "sha256": guard.dual.sha256(self.package),
                "source_sha": "a" * 40,
                "run_id": 999,
                "attempt": 1,
            },
        }
        self.root_data = {
            "schema": guard.FORMAT,
            "purpose": "offline-interoperability-only",
            "release_approved": False,
            "version": 3,
            "not_before": "2026-01-01T00:00:00Z",
            "expires_at": "2027-01-01T00:00:00Z",
            "roles": {
                "publisher": {
                    "role": "synthetic-publisher",
                    "key_sha256": guard.dual.sha256(self.publisher_public),
                },
                "reviewer": {
                    "role": "synthetic-reviewer",
                    "key_sha256": guard.dual.sha256(self.reviewer_public),
                },
            },
            "revoked_fingerprints": [],
        }
        self.write_root()
        self.write_statement_and_sign()

    def write_root(self):
        self.root_path.write_text(json.dumps(self.root_data, sort_keys=True),
                                  encoding="utf-8")

    def write_statement_and_sign(self):
        self.statement.write_text(json.dumps(self.claim, sort_keys=True),
                                  encoding="utf-8")
        for private, sig in (
            (self.publisher_private, self.publisher_sig),
            (self.reviewer_private, self.reviewer_sig),
        ):
            self.openssl("pkeyutl", "-sign", "-rawin", "-inkey", str(private),
                         "-in", str(self.statement), "-out", str(sig))

    def verify(self, **changes):
        args = {
            "root": self.root_path,
            "pinned_root_sha256": guard.dual.sha256(self.root_path),
            "trusted_time": "2026-11-24T04:00:00Z",
            "minimum_root_version": 3,
            "statement": self.statement,
            "package": self.package,
            "publisher_sig": self.publisher_sig,
            "publisher_pub": self.publisher_public,
            "reviewer_sig": self.reviewer_sig,
            "reviewer_pub": self.reviewer_public,
            "source_sha": "a" * 40,
            "run_id": 999,
            "attempt": 1,
        }
        args.update(changes)
        return guard.verify_receiver(**args)

    def modify_root(self, action, expected):
        action(self.root_data)
        self.write_root()
        with self.assertRaisesRegex((ValueError, TypeError), expected):
            self.verify()

    def test_valid_lab_is_explicitly_not_release_approval(self):
        self.assertEqual(self.verify(), guard.OUTPUT)
        self.assertIn("NO PRODUCTION TRUST ROOT", guard.OUTPUT)

    def test_reject_modified_receiver_root_without_repin(self):
        previous_pin = guard.dual.sha256(self.root_path)
        self.root_data["version"] += 1
        self.write_root()
        with self.assertRaisesRegex(ValueError, "root changed after"):
            self.verify(pinned_root_sha256=previous_pin)

    def test_reject_self_proclaimed_root_pin_in_artifact(self):
        self.claim["root_public_key_sha256"] = "a" * 64
        self.write_statement_and_sign()
        with self.assertRaisesRegex(ValueError, "unknown or missing LAB statement"):
            self.verify()

    def test_reject_no_root_pin(self):
        with self.assertRaisesRegex(ValueError, "invalid independently supplied"):
            self.verify(pinned_root_sha256="")

    def test_reject_pinned_wrong_root_sha(self):
        with self.assertRaisesRegex(ValueError, "root changed after"):
            self.verify(pinned_root_sha256="f" * 64)

    def test_reject_root_metadata_rollback(self):
        self.root_data["version"] = 2
        self.write_root()
        with self.assertRaisesRegex(ValueError, "metadata rollback"):
            self.verify()

    def test_reject_version_boolean(self):
        self.modify_root(lambda r: r.update(version=True), "invalid receiver root version")

    def test_reject_minimum_version_boolean(self):
        with self.assertRaisesRegex(ValueError, "invalid LAB high-water version"):
            self.verify(minimum_root_version=True)

    def test_reject_revoked_publisher_even_with_valid_signatures(self):
        fingerprint = self.root_data["roles"]["publisher"]["key_sha256"]
        self.modify_root(lambda r: r["revoked_fingerprints"].append(fingerprint),
                         "receiver role key revoked")

    def test_reject_revoked_reviewer(self):
        fingerprint = self.root_data["roles"]["reviewer"]["key_sha256"]
        self.modify_root(lambda r: r["revoked_fingerprints"].append(fingerprint),
                         "receiver role key revoked")

    def test_reject_duplicate_revocation_entries(self):
        self.modify_root(lambda r: r["revoked_fingerprints"].extend(["a"*64, "a"*64]),
                         "invalid or duplicate revoked")

    def test_reject_malformed_revocation_fingerprint(self):
        self.modify_root(lambda r: r["revoked_fingerprints"].append("not-a-hash"),
                         "invalid or duplicate revoked")

    def test_reject_root_expiry(self):
        with self.assertRaisesRegex(ValueError, "root expired"):
            self.verify(trusted_time="2027-01-01T00:00:00Z")

    def test_reject_root_not_yet_valid(self):
        with self.assertRaisesRegex(ValueError, "root expired"):
            self.verify(trusted_time="2025-12-31T23:59:59Z")

    def test_reject_invalid_caller_time(self):
        with self.assertRaisesRegex(ValueError, "invalid explicit LAB time"):
            self.verify(trusted_time="today")

    def test_reject_calendar_invalid_time(self):
        with self.assertRaisesRegex(ValueError, "invalid explicit LAB timestamp"):
            self.verify(trusted_time="2026-02-30T12:00:00Z")

    def test_reject_root_zero_duration(self):
        self.modify_root(lambda r: r.update(expires_at=r["not_before"]),
                         "time interval invalid")

    def test_reject_root_releases_claim(self):
        self.modify_root(lambda r: r.update(release_approved=True),
                         "cannot approve production release")

    def test_reject_root_numeric_release_bool(self):
        self.modify_root(lambda r: r.update(release_approved=0),
                         "cannot approve production release")

    def test_reject_self_signed_approved_publisher_role(self):
        self.modify_root(lambda r: r["roles"]["publisher"].update(
            role="approved-prod-publisher"), "unrecognized synthetic signer role")

    def test_reject_missing_reviewer_role(self):
        self.modify_root(lambda r: r["roles"].pop("reviewer"),
                         "must define two distinct roles")

    def test_reject_unrecognized_role_key(self):
        self.modify_root(lambda r: r["roles"]["publisher"].update(
            key_sha256="f" * 64), "publisher not provisioned")

    def test_reject_equal_role_keys(self):
        pub = self.root_data["roles"]["publisher"]["key_sha256"]
        self.modify_root(lambda r: r["roles"]["reviewer"].update(key_sha256=pub),
                         "role keys must differ")

    def test_reject_unknown_root_field(self):
        self.modify_root(lambda r: r.update(
            production_approved=True), "missing or unknown receiver root fields")

    def test_reject_root_purpose_drift(self):
        self.modify_root(lambda r: r.update(
            purpose="approved-agent-installer"), "cannot assert real signer approval")

    def test_reject_missing_root_input(self):
        self.root_path.unlink()
        with self.assertRaises(FileNotFoundError):
            self.verify()

    def test_reject_symlink_root_file(self):
        moved = self.root_path.with_suffix(".original")
        self.root_path.rename(moved)
        self.root_path.symlink_to(moved)
        with self.assertRaisesRegex(ValueError, "symlink or nonregular"):
            self.verify()

    def test_reject_root_duplicate_json_key(self):
        self.root_path.write_text('{"schema":"first","schema":"second"}')
        with self.assertRaisesRegex(ValueError, "duplicate LAB statement JSON key"):
            self.verify()

    def test_reject_package_bytes_tampering(self):
        self.package.write_bytes(self.package.read_bytes() + b"x")
        with self.assertRaisesRegex(ValueError, "package digest differs"):
            self.verify()

    def test_reject_replayed_source_sha(self):
        with self.assertRaisesRegex(ValueError, "source SHA/run/attempt replay"):
            self.verify(source_sha="b" * 40)

    def test_reject_replayed_run(self):
        with self.assertRaisesRegex(ValueError, "source SHA/run/attempt replay"):
            self.verify(run_id=1000)

    def test_reject_signed_claim_of_approval(self):
        self.claim["release_approved"] = True
        self.write_statement_and_sign()
        with self.assertRaisesRegex(ValueError, "forged release approval"):
            self.verify()

    def test_reject_swapped_signer_keys(self):
        with self.assertRaisesRegex(ValueError, "publisher not provisioned"):
            self.verify(publisher_pub=self.reviewer_public)

    def test_reject_untrusted_reviewer_key(self):
        other = pathlib.Path(self.temp.name) / "rogue.key"
        rogue_public = pathlib.Path(self.temp.name) / "rogue.pub"
        self.openssl("genpkey", "-algorithm", "ED25519", "-out", str(other))
        self.openssl("pkey", "-in", str(other), "-pubout", "-out", str(rogue_public))
        with self.assertRaisesRegex(ValueError, "reviewer not provisioned"):
            self.verify(reviewer_pub=rogue_public)


if __name__ == "__main__":
    unittest.main()
