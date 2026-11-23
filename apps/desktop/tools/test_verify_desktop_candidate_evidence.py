"""Offline hostile unsigned candidate receipt tests; no install or signing."""
import importlib.util
import json
import pathlib
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("candidate", HERE / "verify_desktop_candidate_evidence.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
from test_verify_desktop_deb import DesktopDebPayloadNegatives


class ReceiptTests(unittest.TestCase):
    def setUp(self):
        self.fx = DesktopDebPayloadNegatives("test_synthetic_offline_desktop_shape_is_accepted")
        self.fx.setUp()
        self.addCleanup(self.fx.tearDown)
        generated = self.fx.build()
        self.package = self.fx.root / guard.PACKAGE
        generated.rename(self.package)
        self.sbom = self.fx.root / "sbom.json"
        self.inputs = {}
        for key in guard.SOURCE_FILES:
            self.inputs[key] = self.fx.root / key
            self.inputs[key].write_text("fixture-" + key)
        self.set_spdx()
        self.out = self.fx.root / "receipt.json"
        self.out.write_text(json.dumps(self.generate()))

    def set_spdx(self, names=("react", "tauri", "serde")):
        self.sbom.write_text(json.dumps({
            "spdxVersion": "SPDX-2.3", "SPDXID": "SPDXRef-DOCUMENT",
            "packages": [{"name": name} for name in names]
        }))

    def generate(self, sha="a" * 40, run=123, attempt=1):
        return guard.receipt(self.package, self.sbom, self.inputs, sha, run, attempt)

    def check(self, sha="a" * 40, run=123, attempt=1):
        return guard.verify(self.out, self.package, self.sbom, self.inputs, sha, run, attempt)

    def test_valid_is_still_unsigned(self):
        self.assertIn("NOT SIGNED", self.check())

    def test_refuse_tampered_native_deb(self):
        self.package.write_bytes(self.package.read_bytes() + b"malicious")
        with self.assertRaises(ValueError):
            self.check()

    def test_refuse_tampered_sbom(self):
        self.set_spdx(("react", "tauri", "new-lib"))
        with self.assertRaisesRegex(ValueError, "mismatch"):
            self.check()

    def test_refuse_tampered_source_locks(self):
        for key, path in self.inputs.items():
            with self.subTest(key=key):
                old = path.read_bytes()
                path.write_bytes(old + b"!tamper")
                with self.assertRaisesRegex(ValueError, "mismatch"):
                    self.check()
                path.write_bytes(old)

    def test_refuse_sha_or_ci_run_replay(self):
        for sha, run, attempt in (("b" * 40, 123, 1),
                                  ("a" * 40, 124, 1),
                                  ("a" * 40, 123, 2)):
            with self.subTest(sha=sha, run=run, attempt=attempt):
                with self.assertRaisesRegex(ValueError, "mismatch"):
                    self.check(sha, run, attempt)

    def test_refuse_wrong_dependencies(self):
        for names in (("react", "serde"), ("tauri", "serde"),
                      ("react", "tauri", "lanveriq-agent-offline-preview")):
            with self.subTest(names=names):
                self.set_spdx(names)
                with self.assertRaises(ValueError):
                    self.check()

    def test_refuse_duplicate_spdx_fields(self):
        self.sbom.write_text('{"spdxVersion":"SPDX-2.3","spdxVersion":"SPDX-2.2"}')
        with self.assertRaisesRegex(ValueError, "duplicate JSON"):
            self.check()

    def test_refuse_wrong_spdx_version(self):
        self.sbom.write_text('{"spdxVersion":"SPDX-2.2"}')
        with self.assertRaisesRegex(ValueError, "SPDX"):
            self.check()

    def test_refuse_claim_of_signature_or_approval(self):
        original = json.loads(self.out.read_text())
        for key, value in (("signature", "SIGNED"), ("install_authorized", True),
                           ("release_approved", True), ("trusted_provenance", True)):
            with self.subTest(key=key):
                self.out.write_text(json.dumps(original | {key: value}))
                with self.assertRaisesRegex(ValueError, "forged"):
                    self.check()

    def test_refuse_duplicate_receipt_fields(self):
        self.out.write_text('{"release_approved":false,"release_approved":true}')
        with self.assertRaisesRegex(ValueError, "duplicate JSON"):
            self.check()

    def test_refuse_symlink_or_missing_input(self):
        path = self.inputs["Cargo.lock"]
        old = path.read_bytes()
        path.unlink()
        with self.assertRaisesRegex(ValueError, "missing or linked"):
            self.check()
        target = self.fx.root / "target-lock"
        target.write_bytes(old)
        path.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "missing or linked"):
            self.check()

    def test_refuse_malformed_sha_and_run(self):
        with self.assertRaisesRegex(ValueError, "source SHA"):
            self.generate(sha="short")
        with self.assertRaisesRegex(ValueError, "run metadata"):
            self.generate(run=0)


if __name__ == "__main__":
    unittest.main()
