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

    def test_case_remains_ci_only_and_not_publisher_authority(self):
        self.assertIn("signed-valid-old-index-acceptance", lab.CASES)
        self.assertIn("signed-valid-old-release-acceptance", lab.CASES)
        self.assertEqual(len(lab.CASES), 11)
        self.assertIn("ubuntu@sha256:", lab.IMAGE)
        self.assertNotIn("APPROVED", lab.PRIOR_DESCRIPTION.decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
