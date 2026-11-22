"""Default server runtime must not acquire a cloud-vendor SDK for WORM.

This static architecture gate does not claim independent WORM works.
Historical C18c/d design documents may reference AWS; source runtime may not.
"""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SERVER = ROOT / "apps" / "server"


class CloudNeutralWitnessBoundaryTest(unittest.TestCase):
    def test_default_gradle_has_no_vendor_object_storage_dependency(self):
        gradle = (SERVER / "build.gradle").read_text(encoding="utf-8")
        for token in (
            "software.amazon.awssdk",
            "com.amazonaws",
            "aws-java-sdk",
            "io.minio",
        ):
            with self.subTest(dependency=token):
                self.assertNotIn(token, gradle)

    def test_main_java_does_not_embed_cloud_specific_witness_adapter(self):
        sources = SERVER / "src" / "main" / "java"
        forbidden = ("software.amazon.awssdk", "S3Client",
                     "StagedS3VersionedWitness")
        for java in sources.rglob("*.java"):
            text = java.read_text(encoding="utf-8")
            for token in forbidden:
                with self.subTest(path=str(java.relative_to(ROOT)), token=token):
                    self.assertNotIn(token, text)

    def test_vendor_neutral_witness_contract_exists_without_network(self):
        verifier = (SERVER / "src" / "main" / "java" / "com" /
                    "lanveriq" / "server" / "security" /
                    "StagedImmutableWitnessArchiveReader.java").read_text(
                        encoding="utf-8")
        for required in ("ReadOnlyArchivePort", "VersionLocator",
                         "IndependentlyAssertedPin", "NON_BYPASSABLE_WORM",
                         "UntrustedArchiveMatch"):
            self.assertIn(required, verifier)
        for forbidden in ("S3Client", "HttpClient", "@Bean",
                          "@Component", "@Service"):
            self.assertNotIn(forbidden, verifier)


if __name__ == "__main__":
    unittest.main()
