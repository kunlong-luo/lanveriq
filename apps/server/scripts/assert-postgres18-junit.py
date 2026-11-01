#!/usr/bin/env python3
"""Fail-closed evidence gate for the manually run synthetic PostgreSQL 18 suite.

Use Gradle's own JUnit XML, not source-file counts or a zero-exit code alone.
This script never starts PostgreSQL or runs a test; it validates what Gradle
actually reported after the explicit manual integrationTest execution.
"""

import sys
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree


# Bind evidence to the exact seven Java test classes and Gradle XML paths.
# A duplicate simple class name in another package is not database evidence.
PACKAGES = {
    "PostgreSqlEventIngestionIT": "com.lanveriq.server.events",
    "PostgreSqlDeviceActivationIT": "com.lanveriq.server.events",
    "PostgreSqlDeviceInventoryIT": "com.lanveriq.server.events",
    "PostgreSqlCertificateRegistrationIT": "com.lanveriq.server.enrollment",
    "PostgreSqlDeviceRevocationIT": "com.lanveriq.server.events",
    "PostgreSqlDeviceDirectoryIT": "com.lanveriq.server.devices",
    "PostgreSqlAdminAuditAtomicityIT": "com.lanveriq.server.security",
}

# Minimum discovered tests in seven independent Testcontainers classes.
# Review increases when acceptance cases are added; never lower to hide skips.
MIN_COUNTS = {
    "PostgreSqlEventIngestionIT": 20,
    "PostgreSqlDeviceActivationIT": 5,
    "PostgreSqlDeviceInventoryIT": 11,
    "PostgreSqlCertificateRegistrationIT": 5,
    "PostgreSqlDeviceRevocationIT": 6,
    "PostgreSqlDeviceDirectoryIT": 4,
    "PostgreSqlAdminAuditAtomicityIT": 7,
}

# Independent mandatory negative/transaction evidence. A future suite may
# contain additional tests, but none of these gates may disappear silently.
REQUIRED_CASES = {
    "PostgreSqlEventIngestionIT": {
        "postgres18ExecutesAllEightFlywayMigrationsAndNativeUuidv7",
        "reusedSequenceWithChangedContentRollsBackEarlierNewEventInSameBatch",
        "forbiddenMutationFailsAgainstRealPostgresqlTrigger",
        "concurrentCredentialRevocationWinsBeforeEventWriteWithoutAnAck",
        "anotherActiveDeviceCannotUseTheFirstDevicesCertificateForEventsOrHeartbeat",
        "revokedCertificateCannotAcknowledgeCommittedEventReplayOrRefreshHeartbeat",
    },
    "PostgreSqlDeviceActivationIT": {
        "validActivationCommitsBothRowsAndExactSameCertificateRetryIsIdempotent",
        "pendingCredentialCannotSendHeartbeatBeforeActivation",
        "expiredPendingEnrollmentAndRevokedCertificateRemainDenied",
        "twoConcurrentSameCertificateActivationsSerializeAndConverge",
    },
    "PostgreSqlDeviceInventoryIT": {
        "v8StoresMultipleNicsAndIpv4Ipv6ThenReplacesCompleteChildren",
        "postgresNicInsertFailurePreservesLastCommittedCompleteInventory",
        "postgresAddressInsertFailurePreservesLastCommittedCompleteInventory",
        "postgresVolumeInsertFailurePreservesLastCommittedCompleteInventory",
        "competingHigherAndLowerRevisionsCannotRegressCurrentInventory",
        "certificateRegisteredToOneDeviceCannotWriteAnotherActiveDevicesInventory",
    },
    "PostgreSqlCertificateRegistrationIT": {
        "scopedOwnerReviewsAndRegistersOnlyPendingCredentialWithAudit",
        "unscopedAdminCannotReviewOrCreateCertificate",
        "failedTransactionRevertsReviewCertificateAndAuditTogether",
        "silentlyDroppedAuditInsertRollsBackCertificateAndReview",
        "disablingAdminDuringDeviceLockWaitRejectsStaleReviewAndAudit",
    },
    "PostgreSqlDeviceRevocationIT": {
        "scopedOwnerRevokesActiveIdentityAndCredentialsOnceAndDeniesHeartbeat",
        "wrongGroupAndDisabledSecurityAdminCannotRevokeOrAudit",
        "failedAuditInsertRollsBackBothCredentialAndDeviceRevocation",
        "silentlyDroppedAuditInsertRollsBackRevocationAndPermitsRetry",
        "disablingAdminWhileRevocationWaitsForDeviceLockDeniesWithoutMutation",
    },
    "PostgreSqlDeviceDirectoryIT": {
        "groupScopedKeysetAndDetailDoNotLeakOtherGroupOrPrivateNetwork",
        "disabledAdministratorLosesAllHealthScopeDespiteStalePrincipal",
        "monitoringOverviewCountsOnlyCurrentGroupsAndExcludesPendingFromOffline",
        "monitoringOverviewDisablesAllCountsAfterCurrentGrantIsRemoved",
    },
    "PostgreSqlAdminAuditAtomicityIT": {
        "suppressedBootstrapGrantRollsBackUserAndGroupWithoutAudit",
        "suppressedBootstrapAuditRollsBackUserGroupAndGrant",
        "successfulBootstrapHasOneScopedOwnerAndNoImplicitDataGrant",
        "suppressedSessionAuditRollsBackVersionAndAllowsRetry",
        "wrongGroupAndDisabledIssuerCannotChangeSessionVersionOrAudit",
        "successfulSessionRevocationCommitsExactlyOneAuditPerIncrement",
        "revokedOwnerGrantDuringTargetLockWaitDeniesWithoutSessionMutation",
    },
}


def verify_reports(directory: Path) -> list[str]:
    """Return concise public-safe failure messages; never emit test payloads."""
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        return ["No Gradle integration JUnit XML reports found"]
    errors: list[str] = []
    observed: dict[str, set[str]] = defaultdict(set)
    total = 0

    expected_reports = {
        f"TEST-{package}.{class_name}.xml": (package, class_name)
        for class_name, package in PACKAGES.items()
    }
    for report in reports:
        expected = expected_reports.get(report.name)
        if expected is None:
            errors.append(f"Unknown integration test report: {report.name}")
            continue
        package, class_name = expected
        expected_qualified = f"{package}.{class_name}"
        try:
            root = ElementTree.parse(report).getroot()
        except (ElementTree.ParseError, OSError):
            errors.append(f"Unreadable integration JUnit XML: {report.name}")
            continue
        if root.tag != "testsuite" or root.get("name") != expected_qualified:
            errors.append(f"Wrong JUnit suite identity: {report.name}")
            continue

        cases = list(root.iter("testcase"))
        declared_tests = root.get("tests")
        if declared_tests is not None and (
                not declared_tests.isdecimal() or int(declared_tests) != len(cases)):
            errors.append(f"Inconsistent JUnit test count: {report.name}")
        for status in ("failures", "errors", "skipped"):
            value = root.get(status)
            if value is not None and (not value.isdecimal() or int(value) != 0):
                errors.append(f"Nonzero or invalid JUnit {status}: {report.name}")

        for case in cases:
            total += 1
            qualified = case.get("classname", "")
            method_name = case.get("name", "").split("(", 1)[0]
            if qualified != expected_qualified:
                errors.append(f"Wrong JUnit testcase class: {report.name}")
                continue
            if not method_name:
                errors.append(f"JUnit testcase without identity: {report.name}")
                continue
            if any(case.find(status) is not None
                   for status in ("failure", "error", "skipped")):
                errors.append(f"Failed, errored or skipped: {class_name}.{method_name}")
                continue
            if method_name in observed[class_name]:
                errors.append(f"Duplicate test result: {class_name}.{method_name}")
                continue
            observed[class_name].add(method_name)

    for class_name, minimum in MIN_COUNTS.items():
        count = len(observed[class_name])
        if count < minimum:
            errors.append(f"{class_name}: only {count} passing tests; require >= {minimum}")
        missing = REQUIRED_CASES[class_name] - observed[class_name]
        for name in sorted(missing):
            errors.append(f"Missing mandatory test: {class_name}.{name}")

    if total == 0:
        errors.append("JUnit reports contained no executed testcases")
    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: assert-postgres18-junit.py <Gradle integrationTest XML directory>",
              file=sys.stderr)
        return 2
    directory = Path(sys.argv[1])
    errors = verify_reports(directory)
    if errors:
        for error in errors:
            print("PG18 EVIDENCE BLOCKED: " + error, file=sys.stderr)
        return 1
    print("PG18 synthetic JUnit evidence verified: all 7 Testcontainers classes, "
          ">=58 passing tests, mandatory DB/lock/revocation/rollback cases; "
          "no failed, errored or skipped cases.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
