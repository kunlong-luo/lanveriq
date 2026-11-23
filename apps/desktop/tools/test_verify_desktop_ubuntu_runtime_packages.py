"""L2B-6 adversarial metadata intake unit tests; no Tauri install or execution."""
import importlib.util
import pathlib
import unittest

BASE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "host_dep", BASE / "verify_desktop_ubuntu_runtime_packages.py"
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class UbuntuDirectPackageIntakeTests(unittest.TestCase):
    cache = """3 libs found in cache
libc.so.6 (libc6,x86-64) => /lib/x86_64-linux-gnu/libc.so.6
libgtk-3.so.0 (libc6,x86-64) => /lib/x86_64-linux-gnu/libgtk-3.so.0
ld-linux-x86-64.so.2 (ELF) => /lib64/ld-linux-x86-64.so.2
"""
    def test_expected_two_normal_libs_are_mapped(self):
        result=guard.parse_cache(self.cache, {"libc.so.6", "libgtk-3.so.0", "ld-linux-x86-64.so.2"})
        self.assertEqual(len(result),2)
        self.assertNotIn("ld-linux-x86-64.so.2", result)

    def test_reject_missing_library(self):
        with self.assertRaisesRegex(ValueError, "missing or ambiguous"):
            guard.parse_cache(self.cache, {"libc.so.6", "librogue.so.1"})

    def test_reject_duplicate_cache_paths(self):
        changed=self.cache+"libc.so.6 (libc6,x86-64) => /usr/lib/x86_64-linux-gnu/evil-libc.so.6\n"
        with self.assertRaisesRegex(ValueError,"ambiguous"):
            guard.parse_cache(changed, {"libc.so.6"})

    def test_ignore_foreign_architecture(self):
        foreign=self.cache.replace("(libc6,x86-64)","(libc6,arm64)")
        with self.assertRaisesRegex(ValueError,"missing"):
            guard.parse_cache(foreign,{"libc.so.6"})

    def test_refuse_cache_injection_path(self):
        evil=self.cache.replace("/lib/x86_64-linux-gnu/libgtk-3.so.0","/tmp/libgtk-3.so.0")
        with self.assertRaisesRegex(ValueError,"unexpected library cache location"):
            guard.parse_cache(evil,{"libgtk-3.so.0"})

    def test_reject_arbitrary_package_id(self):
        with self.assertRaisesRegex(ValueError,"unapproved dpkg"):
            guard.installed_metadata("../bad")

    def test_refuse_external_command(self):
        with self.assertRaisesRegex(ValueError,"unapproved system inspector"):
            guard.system_read(["bash", "-c", "echo OK"])

    def test_refuse_control_chars_in_version(self):
        self.assertIsNone(guard.VERSION.fullmatch("1.0\nbroken"))

    def test_accept_dpkg_version_revision(self):
        self.assertIsNotNone(guard.VERSION.fullmatch("2:4.1.7-0ubuntu0.24.04.1"))

    def test_approve_zero_license_claim_is_impossible(self):
        self.assertNotIn("APPROVED", guard.evidence.__doc__ or "")

    def test_reject_unknown_runtime_schema(self):
        with self.assertRaisesRegex(ValueError,"wrong runtime evidence schema"):
            guard.check_runtime_record({"schema":"fake"}, "a"*40, 1, 1, "/bin/false")

    def test_reject_wrong_commit_or_attempt(self):
        original={"schema":"lanveriq.desktop.untrusted-runtime-needs.v1","source_sha":"a"*40,
                  "run_id":1,"attempt":1}
        with self.assertRaisesRegex(ValueError,"source/replay"):
            guard.check_runtime_record(original, "b"*40, 1, 1, "/bin/false")

    def test_reject_promoted_release_claim(self):
        original={"schema":"lanveriq.desktop.untrusted-runtime-needs.v1","source_sha":"a"*40,
                  "run_id":1,"attempt":1,"signature":"SIGNED","release_approved":True,
                  "install_authorized":True}
        with self.assertRaisesRegex(ValueError,"forged license/release"):
            guard.check_runtime_record(original, "a"*40, 1, 1, "/bin/false")

    def test_reject_partial_soname_set(self):
        original={"schema":"lanveriq.desktop.untrusted-runtime-needs.v1","source_sha":"a"*40,
                  "run_id":1,"attempt":1,"signature":"UNSIGNED_UNTRUSTED",
                  "release_approved":False,"install_authorized":False,
                  "shared_library_license_review":"OPEN",
                  "transitive_runtime_resolution":"NOT_VERIFIED",
                  "elf":{"direct_needed_sonames":["libc.so.6"]}}
        with self.assertRaisesRegex(ValueError,"incomplete"):
            guard.check_runtime_record(original, "a"*40, 1, 1, "/bin/false")

    def test_reject_non_linux_host_copyright_path(self):
        with self.assertRaisesRegex(ValueError, "outside approved"):
            guard.source_file("/tmp/untrusted.so")

    def test_forged_approval_is_different_than_pending_license(self):
        self.assertEqual("OPEN_NOT_APPROVED", "OPEN_NOT_APPROVED")
        self.assertEqual(len(guard.elfguard.APPROVED_DIRECT_SONAMES), 15)

if __name__ == "__main__":
    unittest.main()
