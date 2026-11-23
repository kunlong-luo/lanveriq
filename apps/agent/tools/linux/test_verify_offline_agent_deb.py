"""Offline native dpkg-deb candidate mutation tests; no dpkg install/systemctl.

Dummy ELF is ONLY a fixture for package-shape negatives. Real CI separately
builds the locked Rust release and checks that exact candidate package.
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


HERE = pathlib.Path(__file__).resolve().parent
VERIFY = HERE / "verify-offline-agent-deb.py"
BUILD = HERE / "build-offline-agent-deb.sh"
SOURCE_UNIT = HERE.parents[1] / "deploy/systemd/lanveriq-agent.service"


class NativePackageSafetyTest(unittest.TestCase):
    def make_package(self, modifier=None):
        folder = tempfile.TemporaryDirectory(prefix="lanveriq-deb-unit-")
        self.addCleanup(folder.cleanup)
        base = pathlib.Path(folder.name)
        pkg = base / "pkg"
        control = pkg / "DEBIAN"
        binary = pkg / "usr/bin/lanveriq-agent"
        unit = pkg / "usr/lib/systemd/system/lanveriq-agent.service"
        control.mkdir(parents=True)
        binary.parent.mkdir(parents=True)
        unit.parent.mkdir(parents=True)
        (control / "control").write_text(
            "Package: lanveriq-agent-offline-preview\n"
            "Version: 0.1.0~dev1\nSection: admin\nPriority: optional\n"
            "Architecture: amd64\nMaintainer: Lanveriq maintainers\n"
            "Description: Synthetic packaging regression fixture\n"
            " No real Agent or deployment permitted.\n", encoding="utf-8"
        )
        # A disposable real ELF header, not a claimed Lanveriq binary.
        shutil.copyfile("/bin/true", binary)
        os.chmod(binary, 0o755)
        shutil.copyfile(SOURCE_UNIT, unit)
        os.chmod(unit, 0o644)
        if modifier is not None:
            modifier(pkg)
        archive = base / "synthetic-test-only.deb"
        built = subprocess.run(
            ["dpkg-deb", "--build", "--root-owner-group", str(pkg), str(archive)],
            text=True, capture_output=True, check=False
        )
        self.assertEqual(built.returncode, 0, built.stderr)
        return archive

    def check(self, archive, optimized=True):
        command = [sys.executable, "-O"] if optimized else [sys.executable]
        return subprocess.run(
            command + [str(VERIFY), str(archive)],
            capture_output=True, text=True, check=False
        )

    def denied(self, modifier, expected):
        result = self.check(self.make_package(modifier))
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn(expected, result.stderr)

    def test_synthetic_package_shape_passes_disposable_rehearsal_only(self):
        result = self.check(self.make_package())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NOT dpkg install/purge or Pop!_OS L3", result.stdout)

    def test_extra_configuration_is_rejected(self):
        def alter(pkg):
            config = pkg / "etc/lanveriq-agent/agent.toml"
            config.parent.mkdir(parents=True)
            config.write_text("synthetic-only\n", encoding="utf-8")
        self.denied(alter, "payload files/directories not allowlisted")

    def test_unexpected_postinst_cannot_hide_in_control_tar(self):
        def alter(pkg):
            script = pkg / "DEBIAN/postinst"
            script.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            os.chmod(script, 0o755)
        self.denied(alter, "unapproved postinst")

    def test_payload_symlink_rejected_before_extraction(self):
        def alter(pkg):
            binary = pkg / "usr/bin/lanveriq-agent"
            binary.unlink()
            binary.symlink_to("/usr/bin/true")
        self.denied(alter, "unexpected regular file type/permissions")

    def test_unit_mutation_cannot_be_packaged(self):
        def alter(pkg):
            unit = pkg / "usr/lib/systemd/system/lanveriq-agent.service"
            unit.write_text(
                unit.read_text().replace("PrivateNetwork=yes", "PrivateNetwork=no"),
                encoding="utf-8"
            )
        self.denied(alter, "candidate service differs")

    def test_mode_relaxation_rejected(self):
        def alter(pkg):
            os.chmod(pkg / "usr/bin/lanveriq-agent", 0o777)
        self.denied(alter, "unexpected regular file type/permissions")

    def test_wrong_elf_is_rejected(self):
        def alter(pkg):
            (pkg / "usr/bin/lanveriq-agent").write_text(
                "not an ELF executable", encoding="utf-8"
            )
        self.denied(alter, "extracted binary is not ELF")

    def test_missing_payload_unit_rejected(self):
        def alter(pkg):
            (pkg / "usr/lib/systemd/system/lanveriq-agent.service").unlink()
        self.denied(alter, "payload files/directories not allowlisted")

    def test_usr_local_file_rejected(self):
        def alter(pkg):
            file = pkg / "usr/local/bin/lanveriq-agent"
            file.parent.mkdir(parents=True)
            file.write_bytes(b"do-not-place-debian-packages-in-usr-local")
        self.denied(alter, "payload files/directories not allowlisted")

    def test_package_identity_downgrade_rejected(self):
        def alter(pkg):
            ctl = pkg / "DEBIAN/control"
            ctl.write_text(
                ctl.read_text().replace(
                    "lanveriq-agent-offline-preview", "lanveriq-agent"
                ), encoding="utf-8"
            )
        self.denied(alter, "unapproved package identity")

    def test_archive_symlink_does_not_get_opened(self):
        archive = self.make_package()
        alias = archive.parent / "alias.deb"
        alias.symlink_to(archive)
        result = self.check(alias)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("symlink candidate", result.stderr)

    def test_invalid_missing_archive_denied(self):
        result = self.check(pathlib.Path("/missing-g05-package/agent.deb"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing archive", result.stderr)

    def test_no_implicit_builder_from_arbitrary_elf(self):
        with tempfile.TemporaryDirectory() as folder:
            result = subprocess.run(
                ["bash", str(BUILD), "/bin/true", folder],
                text=True, capture_output=True, check=False
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("only the repository Cargo release build", result.stderr)

    def test_no_builder_archive_overwrite(self):
        # The build path is source-bound; no privileged or user-host install.
        source = BUILD.read_text(encoding="utf-8")
        self.assertIn('if [[ -e "$dest" || -L "$dest" ]]', source)
        self.assertNotIn("systemctl ", source)
        self.assertNotIn("dpkg --install", source)

    def test_validator_never_invokes_installer(self):
        text = VERIFY.read_text(encoding="utf-8")
        self.assertIn('"dpkg-deb", "--extract"', text)
        for banned in ('"systemctl"', '"dpkg", "--install"', '"apt"', "subprocess.Popen"):
            self.assertNotIn(banned, text)


if __name__ == "__main__":
    unittest.main()
