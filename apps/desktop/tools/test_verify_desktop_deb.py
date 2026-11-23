"""Synthetic hostile .deb fixtures; never install a service or package."""
import importlib.util
import pathlib
import subprocess
import tempfile
import unittest

BASE = pathlib.Path(__file__).resolve().parent
MODULE = BASE / "verify-desktop-deb.py"
spec = importlib.util.spec_from_file_location("desktop_deb_validator", MODULE)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
ELF_MAGIC = b"\x7fELF"
CONTROL = """Package: lanveriq-desktop
Version: 0.1.0
Section: utils
Priority: optional
Architecture: amd64
Maintainer: Lanveriq maintainers
Description: Offline-only GUI preview; not approved for deployment
"""
DESKTOP = """[Desktop Entry]
Type=Application
Name=Lanveriq Desktop (Offline Preview)
Exec=lanveriq-desktop
Terminal=false
Categories=Utility;
"""


class DesktopDebPayloadNegatives(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.stage = self.root / "stage"
        (self.stage / "DEBIAN").mkdir(parents=True)
        (self.stage / "DEBIAN/control").write_text(CONTROL, encoding="utf-8")
        self.write("usr/bin/lanveriq-desktop", ELF_MAGIC + b"fake-synthetic-elf-only", 0o755)
        self.write("usr/share/applications/lanveriq-desktop.desktop", DESKTOP.encode(), 0o644)
        self.write("usr/share/icons/hicolor/32x32/apps/lanveriq-desktop.png", PNG_MAGIC + b"fake", 0o644)

    def tearDown(self):
        self.temp.cleanup()

    def write(self, name, data, mode):
        path = self.stage / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        path.chmod(mode)
        return path

    def build(self):
        archive = self.root / "candidate.deb"
        subprocess.run(
            ["dpkg-deb", "--build", "--root-owner-group", str(self.stage), str(archive)],
            check=True, capture_output=True,
        )
        return archive

    def refused(self, reason):
        with self.assertRaisesRegex(ValueError, reason):
            audit.validate(self.build())

    def test_synthetic_offline_desktop_shape_is_accepted(self):
        value = audit.validate(self.build())
        self.assertIn("GUI-only", value)
        self.assertIn("NOT installed/signed/approved", value)

    def test_refuse_wrong_identity(self):
        file = self.stage / "DEBIAN/control"
        file.write_text(CONTROL.replace("lanveriq-desktop\n", "other-tool\n"), encoding="utf-8")
        self.refused("identity")

    def test_refuse_wrong_version(self):
        file = self.stage / "DEBIAN/control"
        file.write_text(CONTROL.replace("0.1.0\n", "9.9.9\n"), encoding="utf-8")
        self.refused("version")

    def test_refuse_wrong_architecture(self):
        file = self.stage / "DEBIAN/control"
        file.write_text(CONTROL.replace("amd64\n", "arm64\n"), encoding="utf-8")
        self.refused("architecture")

    def test_refuse_postinst_even_if_it_does_nothing(self):
        self.write("DEBIAN/postinst", b"#!/bin/sh\nexit 0\n", 0o755)
        self.refused("maintainer scripts")

    def test_refuse_actual_agent_executable(self):
        self.write("usr/bin/lanveriq-agent", ELF_MAGIC + b"example", 0o755)
        self.refused("unapproved GUI package file")

    def test_refuse_system_service(self):
        self.write("usr/lib/systemd/system/lanveriq-agent.service", b"service", 0o644)
        self.refused("unapproved GUI package file")

    def test_refuse_added_configuration(self):
        self.write("etc/lanveriq-agent/agent.toml", b"schema_version=1\n", 0o644)
        self.refused("system or user directory in GUI package")

    def test_refuse_user_session_autostart(self):
        self.write("etc/xdg/autostart/lanveriq.desktop", DESKTOP.encode(), 0o644)
        self.refused("system or user directory in GUI package")

    def test_refuse_shell_launcher(self):
        self.write("usr/share/applications/lanveriq-desktop.desktop", DESKTOP.replace(
            "Exec=lanveriq-desktop", "Exec=/bin/sh -c lanveriq-desktop"
        ).encode(), 0o644)
        self.refused("launcher Exec")

    def test_refuse_hidden_desktop_launcher(self):
        self.write("usr/share/applications/lanveriq-desktop.desktop",
                   (DESKTOP + "NoDisplay=true\n").encode(), 0o644)
        self.refused("hidden/autostart")

    def test_refuse_enabled_autostart(self):
        self.write("usr/share/applications/lanveriq-desktop.desktop",
                   (DESKTOP + "X-GNOME-Autostart-enabled=true\n").encode(), 0o644)
        self.refused("hidden/autostart")

    def test_refuse_no_binary(self):
        (self.stage / "usr/bin/lanveriq-desktop").unlink()
        self.refused("exactly one GUI executable")

    def test_refuse_bad_elf_magic(self):
        self.write("usr/bin/lanveriq-desktop", b"not-an-elf", 0o755)
        self.refused("not ELF")

    def test_refuse_no_icon(self):
        (self.stage / "usr/share/icons/hicolor/32x32/apps/lanveriq-desktop.png").unlink()
        self.refused("only approved icon")

    def test_refuse_symlink_binary(self):
        binpath = self.stage / "usr/bin/lanveriq-desktop"
        binpath.unlink()
        binpath.symlink_to("/bin/sh")
        self.refused("non-regular payload")

    def test_refuse_invalid_icon(self):
        self.write("usr/share/icons/hicolor/32x32/apps/lanveriq-desktop.png", b"not-png", 0o644)
        self.refused("not a PNG")

    def test_refuse_extra_launcher(self):
        self.write("usr/share/applications/com.lanveriq.desktop.desktop", DESKTOP.encode(), 0o644)
        self.refused("exactly one launcher")


    def test_refuse_agent_dependency_even_without_agent_files(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Depends: lanveriq-agent-offline-preview\n", encoding="utf-8")
        self.refused("unapproved GUI package dependency")

    def test_refuse_predepends_privileged_helper(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Pre-Depends: lanveriq-install-helper\n", encoding="utf-8")
        self.refused("forbidden Debian control field")

    def test_refuse_recommends_agent(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Recommends: lanveriq-agent-offline-preview\n", encoding="utf-8")
        self.refused("forbidden Debian control field")

    def test_refuse_dependency_alternative_to_agent(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Depends: libgtk-3-0 | lanveriq-agent\n", encoding="utf-8")
        self.refused("malformed or alternative Debian dependency")

    def test_refuse_multiline_dependency_extension(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Depends: libc6,\n lanveriq-agent\n", encoding="utf-8")
        self.refused("unexpected multiline control field")

    def test_accept_known_framework_library_dependencies(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Depends: libc6 (>= 2.39), libwebkit2gtk-4.1-0, libgtk-3-0\n", encoding="utf-8")
        self.assertIn("GUI-only", audit.validate(self.build()))

    def test_refuse_conflicts_and_replaces(self):
        path = self.stage / "DEBIAN/control"
        path.write_text(CONTROL + "Replaces: lanveriq-agent-offline-preview\n", encoding="utf-8")
        self.refused("forbidden Debian control field")

    def test_source_only_no_real_installs(self):
        code = MODULE.read_text(encoding="utf-8")
        for word in ("--install", "systemctl start", "dpkg -i", "apt install", "sudo "):
            self.assertNotIn(word, code)


if __name__ == "__main__":
    unittest.main()
