#!/usr/bin/env python3
"""Synthetic negative tests for stable official RustDesk release provenance."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import tempfile
from pathlib import Path
from unittest import TestCase, main

spec = importlib.util.spec_from_file_location(
    "verify_client_release", Path(__file__).with_name("verify_client_release.py")
)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class ClientBinaryTrustTests(TestCase):
    def setUp(self):
        self.original = json.loads(check.MANIFEST.read_text(encoding="utf-8"))
        self.assets = check.verify_lock(self.original)

    def must_fail(self, mutate):
        d = copy.deepcopy(self.original)
        mutate(d)
        with self.assertRaises(check.AssetRejected):
            check.verify_lock(d)

    def test_fixed_release_is_official_and_not_nightly(self):
        self.assertEqual(len(self.assets), 4)
        self.assertEqual(self.assets["linux-amd64-deb"]["sha256"],
                         "b5d682ad90cb7571ecbdbf26eef58d3c4514e726a74ef6d08ce3f0b114ef0bd9")
        self.assertEqual(self.assets["windows-amd64-exe"]["sha256"],
                         "319206042390ebfcba934cf0baa2f612f34de463c78c843b7cb8c22097c4bf7b")

    def test_rejects_nightly_or_different_release(self):
        self.must_fail(lambda d: d.update(tag="nightly"))
        self.must_fail(lambda d: d.update(version="1.5.2"))
        self.must_fail(lambda d: d.update(prerelease=True))

    def test_rejects_redirected_or_untrusted_source(self):
        self.must_fail(lambda d: d.update(source="https://example.org/releases/1.5.1"))
        self.must_fail(lambda d: d["assets"]["linux-amd64-deb"].update(
            url="https://downloads.example.net/rustdesk.deb"))
        self.must_fail(lambda d: d["assets"]["linux-amd64-deb"].update(
            filename="rustdesk-1.5.1-nightly.deb"))

    def test_rejects_digest_and_size_mismatch_in_manifest(self):
        self.must_fail(lambda d: d["assets"]["linux-amd64-deb"].update(sha256="0" * 64))
        self.must_fail(lambda d: d["assets"]["linux-amd64-deb"].update(size_bytes=0))
        self.must_fail(lambda d: d["assets"]["linux-amd64-deb"].update(size_bytes=True))

    def test_rejects_extra_or_absent_assets(self):
        self.must_fail(lambda d: d["assets"].pop("windows-amd64-msi"))
        self.must_fail(lambda d: d["assets"].update(unknown={}))

    def test_file_digest_and_size_are_both_required(self):
        with tempfile.TemporaryDirectory() as t:
            path = Path(t) / "test.deb"
            path.write_bytes(b"synthetic-test-only")
            assets = {"linux-amd64-deb": {
                "size_bytes": path.stat().st_size,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }}
            result = check.verify_file(assets, "linux-amd64-deb", path)
            self.assertTrue(result["sha256_verified"])
            self.assertFalse(result["remote_session_verified"])
            assets["linux-amd64-deb"]["sha256"] = "0" * 64
            with self.assertRaises(check.AssetRejected):
                check.verify_file(assets, "linux-amd64-deb", path)
            assets["linux-amd64-deb"]["size_bytes"] += 1
            with self.assertRaises(check.AssetRejected):
                check.verify_file(assets, "linux-amd64-deb", path)

    def test_never_trust_symlinks_as_downloads(self):
        with tempfile.TemporaryDirectory() as t:
            target = Path(t) / "real"
            target.write_bytes(b"x")
            link = Path(t) / "link"
            link.symlink_to(target)
            with self.assertRaises(check.AssetRejected):
                check.verify_file(self.assets, "linux-amd64-deb", link)


if __name__ == "__main__":
    main()
