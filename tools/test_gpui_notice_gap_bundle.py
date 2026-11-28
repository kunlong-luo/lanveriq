import base64
import copy
import json
import pathlib
import tempfile
import unittest
from gpui_notice_gap_bundle import collect, git_blob, sha, rebind

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

    def test_rebind_verified_old_bytes_and_refuse_drift(self):
        index, lock, fetch = self.fixture()
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            old = collect(index, lock, root/"old", fetch)
            previous = {p.name: p.read_bytes() for p in (root/"old").iterdir()}
            lock += b"\n"
            index["lock_sha256"] = sha(lock)
            index["supply_chain_source"] = "d"*40
            current = {"schema": "lanveriq.gpui.untrusted-supply-chain-v1",
                "source_sha": index["supply_chain_source"], "lock_sha256": sha(lock),
                "release_approved": False, "install_authorized": False,
                "missing_license_material_count": 22, "packages": [
                    {"name": key.split("@")[0], "version": key.split("@")[1],
                     "registry_checksum": row["registry_checksum"],
                     "review": "MISSING_MATERIAL_REVIEW_OPEN"}
                    for key, row in old["packages"].items()]}
            supply = {"candidate-supply-chain.json": json.dumps(current).encode()}
            result = rebind(index, lock, root/"new", previous, supply)
            self.assertEqual(result["packages"], old["packages"])
            self.assertEqual(result["rebound_from_lock_sha256"], old["lock_sha256"])
            self.assertEqual(result["registry_gaps_closed"], 0)
            for mode in ("material", "registry", "lock"):
                with self.subTest(mode=mode):
                    p, s = dict(previous), copy.deepcopy(current)
                    if mode == "material":
                        p[next(k for k in p if len(k) == 68)] = b"tampered"
                    elif mode == "registry":
                        s["packages"][0]["registry_checksum"] = "f"*64
                    else:
                        s["lock_sha256"] = "f"*64
                    with self.assertRaises(ValueError):
                        rebind(index, lock, root/mode, p,
                               {"candidate-supply-chain.json": json.dumps(s).encode()})
