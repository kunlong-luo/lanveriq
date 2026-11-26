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

    def test_accepts_exact_current_apt_dependency_closure(self):
        plan = subject.parse_install_plan(
            "Reading package lists...\n"
            "Inst alpha (1.2 Ubuntu:noble [amd64])\n"
            "Inst beta (1:2.0 Ubuntu:noble [all])\n"
        )
        self.assertEqual(plan, {"alpha": "1.2", "beta": "1:2.0"})
        self.assertEqual(
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan
            ),
            (2, self.fingerprint()),
        )

    def test_rejects_apt_plan_removal(self):
        with self.assertRaisesRegex(ValueError, "proposes removal"):
            subject.parse_install_plan("Inst alpha (1.2 Ubuntu [amd64])\nRemv beta [1:2.0]\n")

    def test_audited_superset_allows_only_current_runner_install_plan_subset(self):
        # Both archives remain independently signed-index verified, even if
        # beta is already installed and therefore omitted from Inst lines.
        plan = {"alpha": "1.2"}
        self.assertEqual(
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan,
                allow_signed_superset=True,
            ),
            (2, self.fingerprint()),
        )
        with self.assertRaisesRegex(ValueError, "APT closure package count differs"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan,
            )

    def test_signed_superset_still_rejects_tampered_extra_archive(self):
        # An unused archive never escapes SHA256/size/arch attestation.
        self.metadata[("beta", "1:2.0")][0]["SHA256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "signed-index SHA256"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup,
                {"alpha": "1.2"}, allow_signed_superset=True,
            )

    def test_signed_superset_rejects_missing_required_or_changed_version(self):
        for plan, reason in [
            ({"alpha": "1.2", "gamma": "3.0"}, "omit current APT"),
            ({"alpha": "999"}, "not in current APT"),
        ]:
            with self.subTest(plan=plan):
                with self.assertRaisesRegex(ValueError, reason):
                    subject.manifest_and_verify(
                        self.root, 2, self.fingerprint(), self.lookup,
                        plan, allow_signed_superset=True,
                    )

    def test_signed_superset_rejects_more_required_packages_than_cached(self):
        with self.assertRaisesRegex(ValueError, "APT closure exceeds signed cache"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup,
                {"alpha": "1.2", "beta": "1:2.0", "gamma": "1.0"},
                allow_signed_superset=True,
            )

    def test_rejects_unexpected_cached_package_outside_plan(self):
        plan = {"alpha": "1.2", "other": "1:2.0"}
        with self.assertRaisesRegex(ValueError, "not in current APT simulated closure"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan
            )

    def test_rejects_version_drift_from_current_apt_plan(self):
        plan = {"alpha": "1.3", "beta": "1:2.0"}
        with self.assertRaisesRegex(ValueError, "not in current APT simulated closure"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan
            )

    def test_rejects_apt_closure_missing_archive(self):
        plan = {"alpha": "1.2", "beta": "1:2.0", "gamma": "1.0"}
        with self.assertRaisesRegex(ValueError, "APT closure package count differs"):
            subject.manifest_and_verify(
                self.root, 2, self.fingerprint(), self.lookup, plan
            )

    def test_rejects_duplicate_apt_plan_package(self):
        with self.assertRaisesRegex(ValueError, "duplicate APT package"):
            subject.parse_install_plan(
                "Inst alpha (1.2 Ubuntu [amd64])\n"
                "Inst alpha [1.2] (2.3 Ubuntu [amd64])\n"
            )

    def test_rejects_malformed_apt_plan_inst_line(self):
        with self.assertRaisesRegex(ValueError, "malformed APT Inst"):
            subject.parse_install_plan("Inst alpha broken text\n")

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
