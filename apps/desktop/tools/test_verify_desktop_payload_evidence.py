"""Synthetic L2B-4 hostile Debian payload inventory, no install privileges."""
import importlib.util
import json
import pathlib
import subprocess
import unittest
from test_verify_desktop_deb import DesktopDebPayloadNegatives

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("payload_checker", HERE / "verify_desktop_payload_evidence.py")
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class PayloadEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.fx = DesktopDebPayloadNegatives("test_synthetic_offline_desktop_shape_is_accepted")
        self.fx.setUp()
        self.addCleanup(self.fx.tearDown)
        package = self.fx.build()
        self.package = self.fx.root / guard.base.PACKAGE
        package.rename(self.package)
        self.root = self.fx.root / "scan-root"
        self.root.mkdir()
        subprocess.run(["dpkg-deb", "--extract", str(self.package), str(self.root)],
                       check=True, capture_output=True)
        self.source_spdx = self.fx.root / "source.spdx.json"
        self.source_spdx.write_text(json.dumps({"spdxVersion": "SPDX-2.3",
            "SPDXID": "SPDXRef-DOCUMENT", "packages": [{"name": "tauri"}, {"name": "react"}]}))
        self.payload_spdx = self.fx.root / "payload.spdx.json"
        self.scan([])
        self.inputs = {}
        for name in guard.base.SOURCE_FILES:
            self.inputs[name] = self.fx.root / name
            self.inputs[name].write_text("fixture-" + name)
        self.sha = "a" * 40
        self.run = 42
        self.attempt = 1
        self.source_receipt = self.fx.root / "receipt.json"
        self.source_receipt.write_text(json.dumps(guard.base.receipt(
            self.package, self.source_spdx, self.inputs, self.sha, self.run, self.attempt)))
        self.output = self.fx.root / "payload.json"
        self.output.write_text(json.dumps(self.make()))

    def scan(self, names):
        self.payload_spdx.write_text(json.dumps({"spdxVersion": "SPDX-2.3",
            "SPDXID": "SPDXRef-DOCUMENT", "packages": [{"name": x} for x in names]}))

    def args(self):
        return (self.package, self.root, self.payload_spdx, self.source_receipt,
                self.source_spdx, self.inputs, self.sha, self.run, self.attempt)

    def make(self):
        return guard.evidence(*self.args())

    def check(self):
        return guard.verify(self.output, *self.args())

    def test_legitimate_synthetic_payload_remains_untrusted(self):
        self.assertIn("NOT APPROVED", self.check())
        self.assertEqual(len(self.make()["payload_files"]), 3)

    def test_zero_detected_packages_does_not_mean_complete_sbom(self):
        self.assertEqual(self.make()["payload_spdx_detected_packages"], 0)
        self.assertIn("not-complete", self.make()["scope"])

    def test_refuse_bigger_extracted_binary(self):
        file = self.root / "usr/bin/lanveriq-desktop"
        file.write_bytes(file.read_bytes() + b"mod")
        with self.assertRaisesRegex(ValueError, "size mismatch"):
            self.check()

    def test_refuse_same_size_elf_tamper_even_on_create(self):
        file = self.root / "usr/bin/lanveriq-desktop"
        data = bytearray(file.read_bytes())
        data[-1] ^= 1
        file.write_bytes(data)
        with self.assertRaisesRegex(ValueError, "digest mismatch"):
            self.make()

    def test_refuse_extra_file(self):
        (self.root / "usr/bin/unwanted").write_bytes(b"bad")
        with self.assertRaisesRegex(ValueError, "extra extracted"):
            self.check()

    def test_refuse_missing_file(self):
        (self.root / "usr/bin/lanveriq-desktop").unlink()
        with self.assertRaisesRegex(ValueError, "missing extracted"):
            self.check()

    def test_refuse_symlinked_binary(self):
        file = self.root / "usr/bin/lanveriq-desktop"
        file.unlink()
        file.symlink_to("/bin/sh")
        with self.assertRaisesRegex(ValueError, "symlink"):
            self.check()

    def test_refuse_modified_executable_mode(self):
        (self.root / "usr/bin/lanveriq-desktop").chmod(0o644)
        with self.assertRaisesRegex(ValueError, "mode mismatch"):
            self.check()

    def test_refuse_modified_source_receipt(self):
        obj = json.loads(self.source_receipt.read_text())
        obj["release_approved"] = True
        self.source_receipt.write_text(json.dumps(obj))
        with self.assertRaisesRegex(ValueError, "forged"):
            self.check()

    def test_refuse_swapped_payload_spdx(self):
        self.scan(["tampered"])
        with self.assertRaisesRegex(ValueError, "payload evidence mismatch"):
            self.check()

    def test_refuse_agent_in_payload_spdx(self):
        self.scan(["lanveriq-agent"])
        with self.assertRaisesRegex(ValueError, "forbidden Agent"):
            self.check()

    def test_refuse_duplicate_spdx_fields(self):
        self.payload_spdx.write_text('{"spdxVersion":"SPDX-2.3","spdxVersion":"SPDX-2.2"}')
        with self.assertRaisesRegex(ValueError, "duplicate JSON"):
            self.check()

    def test_refuse_replayed_ci_run(self):
        self.run += 1
        with self.assertRaisesRegex(ValueError, "mismatch"):
            self.check()

    def test_refuse_forged_signing_and_install_permission(self):
        original = json.loads(self.output.read_text())
        for key, value in (("signature", "SIGNED"), ("release_approved", True),
                           ("install_authorized", True), ("trusted_release", True)):
            with self.subTest(key=key):
                self.output.write_text(json.dumps(original | {key: value}))
                with self.assertRaisesRegex(ValueError, "forged release"):
                    self.check()

    def test_refuse_package_append(self):
        self.package.write_bytes(self.package.read_bytes() + b"altered")
        with self.assertRaises(ValueError):
            self.check()

    def test_refuse_symlinked_scan_root(self):
        link = self.fx.root / "linked"
        link.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "linked extraction root"):
            guard.evidence(self.package, link, self.payload_spdx, self.source_receipt,
                           self.source_spdx, self.inputs, self.sha, self.run, self.attempt)


if __name__ == "__main__":
    unittest.main()
