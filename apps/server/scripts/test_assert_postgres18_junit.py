"""Synthetic-only unit tests for the manual PostgreSQL JUnit evidence gate."""

import runpy
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

gate = runpy.run_path(str(Path(__file__).with_name("assert-postgres18-junit.py")))
verify_reports = gate["verify_reports"]
minimums = gate["MIN_COUNTS"]
mandatory = gate["REQUIRED_CASES"]


def synthetic_reports(directory: Path, *, omit=None, skipped=None,
                      failure=None, duplicate=None):
    """Generate wholly invented Gradle-shaped results, not DB evidence."""
    for cls, minimum in minimums.items():
        suite = ElementTree.Element("testsuite", name=f"com.lanveriq.server.events.{cls}")
        methods = sorted(mandatory[cls])
        methods += [f"syntheticExtra{i}" for i in range(minimum - len(methods))]
        for name in methods:
            if (cls, name) == omit:
                continue
            case = ElementTree.SubElement(suite, "testcase", {
                "classname": f"com.lanveriq.server.events.{cls}",
                "name": f"{name}()",
            })
            if (cls, name) == skipped:
                ElementTree.SubElement(case, "skipped")
            if (cls, name) == failure:
                ElementTree.SubElement(case, "failure")
            if (cls, name) == duplicate:
                ElementTree.SubElement(suite, "testcase", {
                    "classname": f"com.lanveriq.server.events.{cls}",
                    "name": f"{name}()",
                })
        ElementTree.ElementTree(suite).write(
            directory / f"TEST-com.lanveriq.server.events.{cls}.xml",
            encoding="utf-8", xml_declaration=True)


class PostgresEvidenceGateTest(unittest.TestCase):
    def test_complete_synthetic_junit_matrix_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            synthetic_reports(Path(tmp))
            self.assertEqual([], verify_reports(Path(tmp)))

    def test_no_reports_cannot_claim_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIn("No Gradle integration JUnit XML reports found",
                          verify_reports(Path(tmp)))

    def test_missing_required_case_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing = ("PostgreSqlDeviceRevocationIT",
                       "silentlyDroppedAuditInsertRollsBackRevocationAndPermitsRetry")
            synthetic_reports(Path(tmp), omit=missing)
            self.assertTrue(any("Missing mandatory test" in error
                                for error in verify_reports(Path(tmp))))

    def test_skipped_and_failed_tests_fail_even_when_counts_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            key = ("PostgreSqlDeviceRevocationIT",
                   "scopedOwnerRevokesActiveIdentityAndCredentialsOnceAndDeniesHeartbeat")
            synthetic_reports(Path(tmp), skipped=key)
            self.assertTrue(any("skipped" in error for error in verify_reports(Path(tmp))))
        with tempfile.TemporaryDirectory() as tmp:
            key = ("PostgreSqlDeviceInventoryIT",
                   "postgresNicInsertFailurePreservesLastCommittedCompleteInventory")
            synthetic_reports(Path(tmp), failure=key)
            self.assertTrue(any("Failed, errored" in error
                                for error in verify_reports(Path(tmp))))

    def test_duplicate_case_and_corrupt_junit_xml_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            key = ("PostgreSqlDeviceActivationIT",
                   "pendingCredentialCannotSendHeartbeatBeforeActivation")
            synthetic_reports(Path(tmp), duplicate=key)
            self.assertTrue(any("Duplicate test result" in error
                                for error in verify_reports(Path(tmp))))
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "TEST-corrupt.xml").write_text("<testsuite><testcase")
            self.assertTrue(any("Unreadable integration JUnit XML" in error
                                for error in verify_reports(Path(tmp))))


if __name__ == "__main__":
    unittest.main()
