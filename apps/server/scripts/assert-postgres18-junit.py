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


# Minimum discovered tests in six independent Testcontainers classes.
# Review increases when acceptance cases are added; never lower to hide skips.
MIN_COUNTS = {
    "PostgreSqlEventIngestionIT": 20,
    "PostgreSqlDeviceActivationIT": 5,
    "PostgreSqlDeviceInventoryIT": 11,
    "PostgreSqlCertificateRegistrationIT": 5,
    "PostgreSqlDeviceRevocationIT": 6,
    "PostgreSqlDeviceDirectoryIT": 4,
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
}


def verify_reports(directory: Path) -> list[str]:
    """Return concise public-safe failure messages; never emit test payloads."""
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        return ["No Gradle integration JUnit XML reports found"]
    errors: list[str] = []
    observed: dict[str, set[str]] = defaultdict(set)
    total = 0

    for report in reports:
        try:
            root = ElementTree.parse(report).getroot()
        except (ElementTree.ParseError, OSError):
            errors.append(f"Unreadable integration JUnit XML: {report.name}")
            continue
        for case in root.iter("testcase"):
            total += 1
            qualified = case.get("classname", "")
            class_name = qualified.rsplit(".", 1)[-1]
            method_name = case.get("name", "").split("(", 1)[0]
            if not qualified or not method_name:
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
    print("PG18 synthetic JUnit evidence verified: all 6 Testcontainers classes, "
          ">=51 passing tests, mandatory DB/lock/revocation/rollback cases; "
          "no failed, errored or skipped cases.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
