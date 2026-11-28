import base64
import copy
import json
import pathlib
import tempfile
import unittest
from gpui_notice_gap_bundle import collect, git_blob, sha

class NoticeBundle(unittest.TestCase):
    def fixture(self):
        packages = [f"p{i}@1.0.0" for i in range(22)]
        lock = "".join(f'[[package]]\nname = "p{i}"\nversion = "1.0.0"\n'
            'source = "registry+https://github.com/rust-lang/crates.io-index"\n'
            f'checksum = "{"a"*64}"\n' for i in range(22)).encode()
        body = b"fixture copyright\n"
        blob = git_blob(body)
        url = "https://metadata.ftp-master.debian.org/changelogs/main/r/fixture"
        index = {"schema": "lanveriq.gpui.notice-gap-review-input-v1", "lock_sha256": sha(lock),
            "supply_chain_source": "a"*40, "supply_chain_artifact": 1, "supply_chain_zip_sha256": "b"*64,
            "debian_artifact": 2, "groups": [{"packages": packages[:19], "repository": "a/b",
                "commits": ["c"*40], "materials": [{"label": "fixture", "git_blob": blob}]}],
            "downstream": [{"package": p, "url": url, "sha256": sha(body)} for p in packages[19:]]}
        def fetch(u):
            if u == url:
                return body
            return json.dumps({"sha": blob, "encoding": "base64", "size": len(body),
                "content": base64.b64encode(body).decode()}).encode()
        return index, lock, fetch

    def test_dedup_and_keep_open(self):
        index, lock, fetch = self.fixture()
        with tempfile.TemporaryDirectory() as temp:
            report = collect(index, lock, pathlib.Path(temp)/"out", fetch)
            self.assertEqual(len(report["packages"]), 22)
            self.assertEqual(len(report["materials"]), 1)
            self.assertEqual(report["registry_gaps_closed"], 0)
            self.assertTrue(all(r["registry_material_missing"] for r in report["packages"].values()))
            self.assertFalse(report["release_approved"])

    def test_lock_and_duplicate_rejection(self):
        index, lock, fetch = self.fixture()
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "wrong committed lock"):
                collect(index, lock+b"\n", pathlib.Path(temp)/"out", fetch)
            index["groups"][0]["packages"][0] = index["groups"][0]["packages"][1]
            with self.assertRaisesRegex(ValueError, "wrong gap coverage"):
                collect(index, lock, pathlib.Path(temp)/"out", fetch)

    def test_checksum_drift_rejected(self):
        index, lock, fetch = self.fixture()
        for mode in ("blob", "debian"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temp:
                def tamper(url):
                    data = fetch(url)
                    if mode == "debian" and "debian.org" in url:
                        return b"changed"
                    if mode == "blob" and "api.github.com" in url:
                        raw = json.loads(data)
                        raw["content"] = base64.b64encode(b"changed").decode()
                        return json.dumps(raw).encode()
                    return data
                with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                    collect(index, lock, pathlib.Path(temp)/"out", tamper)
