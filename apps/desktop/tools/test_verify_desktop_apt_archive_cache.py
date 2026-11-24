"""Hostile cache mutation negatives: offline only; no apt/network/installer calls."""
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "verify_desktop_apt_archive_cache", HERE / "verify_desktop_apt_archive_cache.py"
)
subject = importlib.util.module_from_spec(spec)
spec.loader.exec_module(subject)


class UbuntuAPTArchiveCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.alpha = self.root / "alpha_1.2_amd64.deb"
        self.beta = self.root / "beta_1%3a2.0_all.deb"
        self.alpha.write_bytes(b"synthetic alpha archive bytes")
        self.beta.write_bytes(b"synthetic beta archive bytes")
        self.metadata = {}
        for path in (self.alpha, self.beta):
            package, version, arch = subject.parse_archive_name(path.name)
            self.metadata[(package, version)] = [
                {
                    "Package": package,
                    "Version": version,
                    "Architecture": arch,
                    "SHA256": hashlib.sha256(path.read_bytes()).hexdigest(),
                    "Size": str(path.stat().st_size),
                }
            ]

    def tearDown(self):
        self.temp.cleanup()

    def lookup(self, package, version):
        return self.metadata.get((package, version), [])

    def fingerprint(self):
        entries = []
        for path in sorted(self.root.glob("*.deb"), key=str):
            entries.append(
                f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path}\n".encode()
            )
        return hashlib.sha256(b"".join(entries)).hexdigest()

    def verify(self, count=2, digest=None):
        return subject.manifest_and_verify(
            self.root,
            count,
            self.fingerprint() if digest is None else digest,
            self.lookup,
        )

    def test_accepts_matching_signed_metadata_and_epoch(self):
        self.assertEqual(subject.parse_archive_name(self.beta.name), ("beta", "1:2.0", "all"))
        self.assertEqual(self.verify(), (2, self.fingerprint()))

    def test_rejects_changed_package_bytes(self):
        self.alpha.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()

    def test_rejects_wrong_signed_index_hash(self):
        self.metadata[("alpha", "1.2")][0]["SHA256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()

    def test_rejects_wrong_signed_index_size(self):
        self.metadata[("alpha", "1.2")][0]["Size"] = "123"
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()

    def test_rejects_unlisted_version(self):
        self.alpha.rename(self.root / "alpha_2.5_amd64.deb")
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()

    def test_rejects_architecture_change(self):
        self.beta.rename(self.root / "beta_1%3a2.0_amd64.deb")
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()

    def test_rejects_bad_expected_manifest(self):
        with self.assertRaisesRegex(ValueError, "cached archive bytes or paths"):
            self.verify(digest="0" * 64)

    def test_rejects_bad_manifest_format(self):
        with self.assertRaisesRegex(ValueError, "invalid expected archive manifest"):
            self.verify(digest="not-a-digest")

    def test_rejects_count_drift(self):
        with self.assertRaisesRegex(ValueError, "independent archive count"):
            self.verify(count=1)

    def test_rejects_symlink_payload(self):
        self.beta.unlink()
        self.beta.symlink_to(self.alpha)
        with self.assertRaisesRegex(ValueError, "regular file"):
            self.verify()

    def test_rejects_duplicate_package_architecture(self):
        extra = self.root / "alpha_1.3_amd64.deb"
        extra.write_bytes(b"duplicate-version")
        with self.assertRaisesRegex(ValueError, "multiple versions"):
            self.verify(count=3)

    def test_rejects_unreviewed_package_name(self):
        self.alpha.rename(self.root / "bad!name_1.2_amd64.deb")
        with self.assertRaisesRegex(ValueError, "invalid Debian package"):
            self.verify()

    def test_rejects_missing_metadata(self):
        self.metadata.clear()
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
