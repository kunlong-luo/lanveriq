#!/usr/bin/env python3
"""Fail closed if the offline source-time v2 crypto regressions did not run.

Reads Gradle's real unit-test XML for the package-private verifier; never
claims FIDO MDS production-source approval or access to real credentials.
"""
from collections import Counter
from pathlib import Path
import sys
from xml.etree import ElementTree

CLASS = "com.lanveriq.server.security.StagedFidoMdsSourceVerifierTest"
REQUIRED = frozenset({
    "ecdsaAndRsaSignaturesWithAnchoredPkixAndFreshCrlOnlyReturnUntrustedEvidence",
    "sourceTimeV2BindsExactJwsEvenWhenBothJwsSignaturesAreValid",
    "sourceTimeV2BindsExactCrlBytesAndOrderIndependentCrlSet",
    "sourceTimeV2BindsRootFingerprintDespiteSameRootKeyAndIssuer",
    "sourceTimeV2RejectsLegacyUnboundTimestampSequenceMutationAndMutableProof",
    "signedValidFidoShapedEntriesRemainUntrustedAfterStructureScreen",
    "cryptographicallyValidButMalformedEntryShapesAreDenied",
    "cryptographicallyValidDuplicateModelIdsStillRejectBeforeUntrustedEvidence",
    "signedJwsWithDuplicateJsonFieldsAtAnyDepthRejectsEvenWithValidTimeAndCrl",
})
DEFAULT_REPORT = Path("apps/server/build/test-results/test") / f"TEST-{CLASS}.xml"


def verify(report: Path) -> list[str]:
    if not report.is_file():
        return [f"Missing real Gradle JUnit XML: {report}"]
    try:
        root = ElementTree.parse(report).getroot()
    except (ElementTree.ParseError, OSError) as error:
        return [f"Unreadable JUnit XML: {error}"]

    cases = [
        item for item in root.iter("testcase")
        if item.attrib.get("classname") == CLASS
    ]
    names = Counter(item.attrib.get("name", "").removesuffix("()")
                    for item in cases)
    errors = []
    if not cases:
        errors.append(f"No executed JUnit cases for {CLASS}")
    for method in sorted(REQUIRED):
        if names[method] != 1:
            errors.append(f"Mandatory source-time test must occur once: {method} ({names[method]})")
    for case in cases:
        if any(case.find(tag) is not None
               for tag in ("skipped", "failure", "error")):
            errors.append(f"Failed/skipped verifier unit test: {case.attrib.get('name')}")
    if len(cases) != len(set(names)):
        errors.append("Duplicate verifier test names in JUnit XML")
    return errors


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REPORT
    issues = verify(target)
    if issues:
        for issue in issues:
            print("FAIL:", issue, file=sys.stderr)
        sys.exit(1)
    print(f"PASS: real Gradle JUnit evidence includes all {len(REQUIRED)} "
          "required synthetic offline source-time tests")
