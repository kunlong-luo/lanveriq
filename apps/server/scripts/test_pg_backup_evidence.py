"""Synthetic-only tests for the same-admin PG18 archive evidence guard."""

import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest

from pg_backup_evidence import FIXED, InvalidEvidence, create, inspect_archive, main, read_manifest, verify


class LocalBackupEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix="lanveriq-backup-evidence-")
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.archive = self.root / "inventory.dump"
        self.manifest = self.root / "inventory.evidence.json"
        self.archive.write_bytes(b"PGDMP" + b"synthetic-not-a-real-pg-dump" * 200)
        self.archive.chmod(0o600)

    def _create(self):
        create(self.archive, self.manifest)

    def _rejected(self, task):
        with self.assertRaises((InvalidEvidence, OSError)):
            task()

    def test_create_and_verify_local_digest_only(self):
        self._create()
        verify(self.archive, self.manifest)
        data = read_manifest(self.manifest)
        self.assertFalse(data["production_restore_authorized"])
        self.assertFalse(data["independent_custody_verified"])
        self.assertEqual(set(FIXED).difference(data), set())

    def test_equal_size_content_tamper_refused(self):
        self._create()
        raw = self.archive.read_bytes()
        self.archive.write_bytes(raw[:-1] + bytes([raw[-1] ^ 1]))
        self._rejected(lambda: verify(self.archive, self.manifest))

    def test_missing_or_truncated_archive_refused(self):
        self._create()
        self.archive.write_bytes(self.archive.read_bytes()[:100])
        self._rejected(lambda: verify(self.archive, self.manifest))
        self.archive.unlink()
        self._rejected(lambda: verify(self.archive, self.manifest))

    def test_corrupt_custom_header_refused(self):
        self._create()
        raw = self.archive.read_bytes()
        self.archive.write_bytes(b"NOTPG" + raw[5:])
        self._rejected(lambda: verify(self.archive, self.manifest))

    def test_duplicate_or_unknown_json_fields_refused(self):
        self._create()
        obj = json.loads(self.manifest.read_text())
        self.manifest.write_text(self.manifest.read_text().replace('"schema":', '"schema":"tampered","schema":'))
        self._rejected(lambda: read_manifest(self.manifest))
        obj["approved"] = True
        self.manifest.write_text(json.dumps(obj))
        self._rejected(lambda: read_manifest(self.manifest))

    def test_false_auth_claim_must_remain_false(self):
        self._create()
        data = json.loads(self.manifest.read_text())
        data["production_restore_authorized"] = True
        self.manifest.write_text(json.dumps(data))
        self._rejected(lambda: read_manifest(self.manifest))
        data["production_restore_authorized"] = False
        data["scope"] = "PRODUCTION"
        self.manifest.write_text(json.dumps(data))
        self._rejected(lambda: read_manifest(self.manifest))

    def test_bool_as_archive_size_refused(self):
        self._create()
        data = json.loads(self.manifest.read_text())
        data["archive_bytes"] = True
        self.manifest.write_text(json.dumps(data))
        self._rejected(lambda: read_manifest(self.manifest))

    def test_exposed_permissions_refused(self):
        self._create()
        self.archive.chmod(0o644)
        self._rejected(lambda: verify(self.archive, self.manifest))
        self.archive.chmod(0o600)
        self.manifest.chmod(0o644)
        self._rejected(lambda: verify(self.archive, self.manifest))

    def test_symlink_archive_and_manifest_refused(self):
        self._create()
        link = self.root / "archive-link.dump"
        link.symlink_to(self.archive)
        self._rejected(lambda: inspect_archive(link))
        manifest_link = self.root / "evidence-link.json"
        manifest_link.symlink_to(self.manifest)
        self._rejected(lambda: read_manifest(manifest_link))

    def test_hardlink_archive_refused(self):
        duplicate = self.root / "duplicate.dump"
        os.link(self.archive, duplicate)
        self._rejected(lambda: inspect_archive(self.archive))

    def test_overwrite_existing_evidence_refused(self):
        self._create()
        self._rejected(self._create)

    def test_cli_reports_no_production_authority(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(main(["create", str(self.archive), str(self.manifest)]), 0)
            self.assertEqual(main(["verify", str(self.archive), str(self.manifest)]), 0)
        self.assertIn("same-admin synthetic only", output.getvalue())
        self.assertIn("G-08 OPEN", output.getvalue())
        self.archive.write_bytes(b"NOTPG" + self.archive.read_bytes()[5:])
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(main(["verify", str(self.archive), str(self.manifest)]), 2)


if __name__ == "__main__":
    unittest.main()
