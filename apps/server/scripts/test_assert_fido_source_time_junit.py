"""Invented XML fixtures for the strict FIDO source-time unit evidence gate."""

import runpy
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

gate = runpy.run_path(str(Path(__file__).with_name("assert-fido-source-time-junit.py")))
verify = gate["verify"]
required = gate["REQUIRED"]
klass = gate["CLASS"]


def write_fake(path: Path, *, omit=None, fail=None, skip=None, duplicate=None):
    root = ElementTree.Element("testsuite", name=klass)
    for method in sorted(required):
        if method == omit:
            continue
        case = ElementTree.SubElement(
            root, "testcase", classname=klass, name=f"{method}()")
        if method == fail:
            ElementTree.SubElement(case, "failure")
        if method == skip:
            ElementTree.SubElement(case, "skipped")
        if method == duplicate:
            ElementTree.SubElement(
                root, "testcase", classname=klass, name=f"{method}()")
    ElementTree.ElementTree(root).write(path, encoding="utf-8")


class SourceTimeEvidenceGateTest(unittest.TestCase):
    def test_complete_synthetic_report_is_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "TEST-fake.xml"
            write_fake(path)
            self.assertEqual([], verify(path))

    def test_missing_or_unreadable_report_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "TEST-fake.xml"
            self.assertTrue(verify(path))
            path.write_text("not XML", encoding="utf-8")
            self.assertTrue(verify(path))

    def test_every_mandatory_method_must_execute_once(self):
        for method in sorted(required):
            with self.subTest(method=method), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "TEST-fake.xml"
                write_fake(path, omit=method)
                self.assertTrue(verify(path))
                write_fake(path, duplicate=method)
                self.assertTrue(verify(path))

    def test_failing_or_skipped_case_blocks_acceptance(self):
        for field in ("fail", "skip"):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "TEST-fake.xml"
                write_fake(path, **{field: next(iter(sorted(required)))})
                self.assertTrue(verify(path))


if __name__ == "__main__":
    unittest.main()
