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
packages = gate["PACKAGES"]


def synthetic_reports(directory: Path, *, omit=None, skipped=None,
                      failure=None, duplicate=None):
    """Generate wholly invented Gradle-shaped results, not DB evidence."""
    for cls, minimum in minimums.items():
        qualified = f"{packages[cls]}.{cls}"
        suite = ElementTree.Element("testsuite", name=qualified)
        methods = sorted(mandatory[cls])
        methods += [f"syntheticExtra{i}" for i in range(minimum - len(methods))]
        for name in methods:
            if (cls, name) == omit:
                continue
            case = ElementTree.SubElement(suite, "testcase", {
                "classname": qualified,
                "name": f"{name}()",
            })
            if (cls, name) == skipped:
                ElementTree.SubElement(case, "skipped")
            if (cls, name) == failure:
                ElementTree.SubElement(case, "failure")
            if (cls, name) == duplicate:
                ElementTree.SubElement(suite, "testcase", {
                    "classname": qualified,
                    "name": f"{name}()",
                })
        ElementTree.ElementTree(suite).write(
            directory / f"TEST-{qualified}.xml",
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

    def test_new_certificate_audit_and_lock_negatives_cannot_be_omitted(self):
        for required in (
                "silentlyDroppedAuditInsertRollsBackCertificateAndReview",
                "disablingAdminDuringDeviceLockWaitRejectsStaleReviewAndAudit"):
            with tempfile.TemporaryDirectory() as tmp:
                synthetic_reports(Path(tmp), omit=("PostgreSqlCertificateRegistrationIT", required))
                self.assertTrue(any(
                    "Missing mandatory test: PostgreSqlCertificateRegistrationIT." + required
                    in error for error in verify_reports(Path(tmp))))

    def test_new_admin_audit_atomicity_suite_is_mandatory(self):
        required = (
            "suppressedBootstrapGrantRollsBackUserAndGroupWithoutAudit",
            "suppressedBootstrapAuditRollsBackUserGroupAndGrant",
            "suppressedSessionAuditRollsBackVersionAndAllowsRetry",
            "wrongGroupAndDisabledIssuerCannotChangeSessionVersionOrAudit",
            "successfulSessionRevocationCommitsExactlyOneAuditPerIncrement",
            "revokedOwnerGrantDuringTargetLockWaitDeniesWithoutSessionMutation",
        )
        for method in required:
            with self.subTest(method=method), tempfile.TemporaryDirectory() as tmp:
                synthetic_reports(Path(tmp), omit=("PostgreSqlAdminAuditAtomicityIT", method))
                self.assertTrue(any(
                    "Missing mandatory test: PostgreSqlAdminAuditAtomicityIT." + method
                    in error for error in verify_reports(Path(tmp))))

    def test_seventh_suite_cannot_be_replaced_by_six_old_suites(self):
        with tempfile.TemporaryDirectory() as tmp:
            synthetic_reports(Path(tmp))
            (Path(tmp) / ("TEST-" + packages["PostgreSqlAdminAuditAtomicityIT"]
                          + ".PostgreSqlAdminAuditAtomicityIT.xml")).unlink()
            errors = verify_reports(Path(tmp))
            self.assertTrue(any("PostgreSqlAdminAuditAtomicityIT: only 0 passing tests"
                                in error for error in errors))

    def test_mismatched_gradle_suite_and_case_class_are_rejected(self):
        cls = "PostgreSqlAdminAuditAtomicityIT"
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            synthetic_reports(directory)
            path = directory / f"TEST-{packages[cls]}.{cls}.xml"
            tree = ElementTree.parse(path)
            tree.getroot().set("name", f"com.lanveriq.server.events.{cls}")
            tree.write(path, encoding="utf-8")
            self.assertTrue(any("Wrong JUnit suite identity" in error
                                for error in verify_reports(directory)))
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            synthetic_reports(directory)
            path = directory / f"TEST-{packages[cls]}.{cls}.xml"
            tree = ElementTree.parse(path)
            tree.getroot().find("testcase").set(
                "classname", f"com.lanveriq.server.events.{cls}")
            tree.write(path, encoding="utf-8")
            self.assertTrue(any("Wrong JUnit testcase class" in error
                                for error in verify_reports(directory)))

    def test_unknown_report_and_inconsistent_suite_metadata_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            synthetic_reports(directory)
            (directory / "TEST-unknown.Spoof.xml").write_text(
                '<testsuite name="unknown.Spoof"><testcase name="other" '
                'classname="unknown.Spoof"/></testsuite>')
            self.assertTrue(any("Unknown integration test report" in error
                                for error in verify_reports(directory)))
        cls = "PostgreSqlEventIngestionIT"
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            synthetic_reports(directory)
            path = directory / f"TEST-{packages[cls]}.{cls}.xml"
            tree = ElementTree.parse(path)
            tree.getroot().set("tests", "999")
            tree.getroot().set("skipped", "1")
            tree.write(path, encoding="utf-8")
            errors = verify_reports(directory)
            self.assertTrue(any("Inconsistent JUnit test count" in error
                                for error in errors))
            self.assertTrue(any("Nonzero or invalid JUnit skipped" in error
                                for error in errors))

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
