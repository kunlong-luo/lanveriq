"""L2B-2 synthetic Ed25519 test key denial cases; no release or installation."""
import hashlib
import importlib.util
import json
import pathlib
import subprocess
import unittest

import test_verify_desktop_deb as package_fixtures

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "lab_signature_verifier", HERE / "verify_desktop_lab_signature.py"
)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class LabSignatureTests(unittest.TestCase):
    def setUp(self):
        self.fixture = package_fixtures.DesktopDebPayloadNegatives(
            "test_synthetic_offline_desktop_shape_is_accepted"
        )
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        made = self.fixture.build()
        self.package = self.fixture.root / audit.candidate.PACKAGE
        made.rename(self.package)
        self.sbom = self.fixture.root / "desktop.spdx.json"
        self.sbom.write_text(json.dumps({
            "spdxVersion": "SPDX-2.3", "SPDXID": "SPDXRef-DOCUMENT",
            "packages": [{"name": "tauri"}, {"name": "react"}]
        }), encoding="utf-8")
        self.inputs = {}
        for name in audit.candidate.SOURCE_FILES:
            self.inputs[name] = self.fixture.root / name
            self.inputs[name].write_text("synthetic-" + name, encoding="utf-8")
        self.receipt = self.fixture.root / "receipt.json"
        data = audit.candidate.receipt(self.package, self.sbom,
                                       self.inputs, "a" * 40, 99, 1)
        self.receipt.write_text(json.dumps(data), encoding="utf-8")
        self.private = self.fixture.root / "ephemeral.lab.key"
        self.public = self.fixture.root / "ephemeral.lab.pub"
        self.signature = self.fixture.root / "receipt.sig"
        self.exec_openssl("genpkey", "-algorithm", "ED25519", "-out", str(self.private))
        self.exec_openssl("pkey", "-in", str(self.private), "-pubout",
                          "-out", str(self.public))
        self.sign()
        self.fingerprint = hashlib.sha256(self.public.read_bytes()).hexdigest()

    def exec_openssl(self, *args):
        subprocess.run(("openssl",) + args, check=True, capture_output=True, timeout=15)

    def sign(self):
        self.exec_openssl("pkeyutl", "-sign", "-rawin", "-inkey", str(self.private),
                          "-in", str(self.receipt), "-out", str(self.signature))

    def verify(self, **override):
        kwargs = {
            "receipt": self.receipt, "signature": self.signature,
            "public_key": self.public, "pinned_pubkey_sha256": self.fingerprint,
            "package": self.package, "sbom": self.sbom, "inputs": self.inputs,
            "source_sha": "a" * 40, "run_id": 99, "attempt": 1,
        }
        kwargs.update(override)
        return audit.verify_lab(**kwargs)

    def test_lab_only_cryptographic_verification(self):
        result = self.verify()
        self.assertIn("NO RELEASE OR INSTALL APPROVAL", result)
        self.assertIn("EPHEMERAL TEST KEY", result)

    def test_deny_tampered_signature(self):
        raw = bytearray(self.signature.read_bytes())
        raw[0] ^= 1
        self.signature.write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "signature rejected"):
            self.verify()

    def test_deny_unsigned_receipt_tamper(self):
        self.receipt.write_bytes(self.receipt.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "signature rejected"):
            self.verify()

    def test_deny_key_replacement_even_with_new_fingerprint(self):
        other = self.fixture.root / "other.key"
        self.exec_openssl("genpkey", "-algorithm", "ED25519", "-out", str(other))
        self.exec_openssl("pkey", "-in", str(other), "-pubout", "-out", str(self.public))
        new_hash = hashlib.sha256(self.public.read_bytes()).hexdigest()
        with self.assertRaisesRegex(ValueError, "signature rejected"):
            self.verify(pinned_pubkey_sha256=new_hash)

    def test_deny_public_key_fingerprint_mismatch(self):
        with self.assertRaisesRegex(ValueError, "unrecognized lab key"):
            self.verify(pinned_pubkey_sha256="0" * 64)

    def test_deny_invalid_fingerprint_shape(self):
        with self.assertRaisesRegex(ValueError, "invalid explicit lab key"):
            self.verify(pinned_pubkey_sha256="not-a-key")

    def test_deny_broken_signature_length(self):
        self.signature.write_bytes(b"short")
        with self.assertRaisesRegex(ValueError, "64 bytes"):
            self.verify()

    def test_deny_missing_signature(self):
        self.signature.unlink()
        with self.assertRaisesRegex(ValueError, "missing, symlink"):
            self.verify()

    def test_deny_symlink_signature(self):
        target = self.fixture.root / "signature-target"
        self.signature.rename(target)
        self.signature.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "missing, symlink"):
            self.verify()

    def test_deny_symlink_public_key(self):
        target = self.fixture.root / "public-target"
        self.public.rename(target)
        self.public.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "missing, symlink"):
            self.verify()

    def test_deny_forged_approval_even_if_signed_by_lab_key(self):
        data = json.loads(self.receipt.read_text())
        data["release_approved"] = True
        self.receipt.write_text(json.dumps(data), encoding="utf-8")
        self.sign()
        with self.assertRaisesRegex(ValueError, "forged signed/release claim"):
            self.verify()

    def test_deny_replayed_source_commit(self):
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            self.verify(source_sha="b" * 40)

    def test_deny_replayed_run(self):
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            self.verify(run_id=100)

    def test_deny_mutated_source_spdx(self):
        self.sbom.write_bytes(self.sbom.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            self.verify()

    def test_deny_mutated_debian_candidate(self):
        self.package.write_bytes(self.package.read_bytes() + b"tamper")
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            self.verify()

    def test_deny_mutated_lockfile(self):
        self.inputs["Cargo.lock"].write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
