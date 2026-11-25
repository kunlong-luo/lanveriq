"""L2B-29: isolated source-only negative tests for DIFFERENT historic Packages.

The actual APT acceptance result comes ONLY from the disposable OCI workflow.
"""
import importlib.util
from pathlib import Path
import unittest

source = Path(__file__).with_name("verify_desktop_agent_signed_apt_oci_lab.py")
spec = importlib.util.spec_from_file_location("signed_apt_native_fixture", source)
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


class NativeSignedIndexFixtureTests(unittest.TestCase):
    def test_old_signed_packages_metadata_differs_without_claiming_binary_downgrade(self):
        current = (
            b"Package: lanveriq-agent-offline-preview\n"
            b"Version: 0.1.0~dev1\nArchitecture: amd64\n"
            b"SHA256: " + b"a" * 64 + b"\n"
            + lab.CURRENT_DESCRIPTION + b"\n"
            b" NOT APPROVED for release or real endpoint installation.\n\n"
        )
        historical = lab.previous_packages_index(current)
        self.assertNotEqual(current, historical)
        self.assertIn(lab.CURRENT_DESCRIPTION, current)
        self.assertNotIn(lab.CURRENT_DESCRIPTION, historical)
        self.assertIn(lab.PRIOR_DESCRIPTION, historical)
        for identity in (
            b"Package: lanveriq-agent-offline-preview",
            b"Version: 0.1.0~dev1",
            b"SHA256: " + b"a" * 64,
            b"NOT APPROVED for release",
        ):
            self.assertIn(identity, historical)

    def test_reject_missing_duplicate_or_injected_prior_marker(self):
        for candidate in (
            b"Package: x\n",
            lab.CURRENT_DESCRIPTION * 2,
            lab.CURRENT_DESCRIPTION + b"\n" + lab.PRIOR_DESCRIPTION,
        ):
            with self.subTest(candidate=candidate[:20]):
                with self.assertRaises(ValueError):
                    lab.previous_packages_index(candidate)

    def test_distinct_source_elf_requires_both_elf_magic_and_different_hashes(self):
        first = bytes.fromhex("7f454c46") + b"new-rust-compiled-source"
        other = bytes.fromhex("7f454c46") + b"historic-rust-compiled-source"
        hashes = lab.require_distinct_elf_bytes(first, other)
        self.assertNotEqual(hashes[0], hashes[1])
        for a, b in ((first, first), (first, b"invalid-non-elf"),
                     (b"non-elf", other)):
            with self.subTest(a=a[:10], b=b[:10]):
                with self.assertRaises(ValueError):
                    lab.require_distinct_elf_bytes(a, b)

    def test_signed_witness_binds_sequence_and_real_signed_release_digest(self):
        import hashlib

        sample = b"-----BEGIN PGP SIGNED MESSAGE-----" + bytes([10]) + b"test"
        manifest = lab.witness_manifest(2, sample)
        self.assertIn(b"sequence=2" + bytes([10]), manifest)
        self.assertIn(("release_sha256=" +
                       hashlib.sha256(sample).hexdigest()).encode(), manifest)
        self.assertEqual(len(manifest.splitlines()), 3)
        self.assertNotEqual(manifest, lab.witness_manifest(1, sample))
        self.assertNotEqual(manifest, lab.witness_manifest(2, sample + b"old"))
        for gen, release in ((0, sample), (3, sample), (2, b"not APT signed")):
            with self.subTest(gen=gen, release=release[:12]):
                with self.assertRaises(ValueError):
                    lab.witness_manifest(gen, release)

    def test_case_remains_ci_only_and_not_publisher_authority(self):
        self.assertIn("signed-old-different-packages-retained", lab.CASES)
        self.assertIn("signed-valid-old-release-acceptance", lab.CASES)
        self.assertEqual(len(lab.CASES), 17)
        self.assertEqual(len(lab.WITNESS_CASES), 2)
        self.assertEqual(len(lab.VERSION_CASES), 2)
        self.assertEqual(len(lab.SOURCE_DISTINCT_CASES), 2)
        self.assertRegex(lab.PINNED_HISTORICAL_AGENT_SHA, r"^[0-9a-f]{40}$")
        self.assertIn("dev0", lab.PRIOR_NAME)
        self.assertIn("dev1", lab.NAME)
        self.assertIn("ubuntu@sha256:", lab.IMAGE)
        self.assertNotIn("APPROVED", lab.PRIOR_DESCRIPTION.decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
