"""Synthetic mutations of the existing native CI source policy."""
import pathlib
import shutil
import tempfile
import unittest

import verify_gpui_readonly as guard


class GPUIReadonlyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        shutil.copytree(guard.DESKTOP / "src", self.root / "src")
        shutil.copy2(guard.DESKTOP / "Cargo.toml", self.root / "Cargo.toml")

    def mutate(self, path, old, new):
        target = self.root / path
        source = target.read_text()
        self.assertIn(old, source)
        target.write_text(source.replace(old, new))

    def test_reviewed_source(self):
        self.assertIn("PASS", guard.validate(self.root))

    def test_publish_rejected(self):
        self.mutate("Cargo.toml", "publish = false", "publish = true")
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_unreviewed_dependency_rejected(self):
        self.mutate("Cargo.toml", "[dependencies]", '[dependencies]\nreqwest = "0.12"')
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_build_script_rejected(self):
        (self.root / "build.rs").write_text("fn main() {}")
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_network_rejected(self):
        p = self.root / "src/main.rs"
        p.write_text(p.read_text() + "\nuse std::net::TcpStream;\n")
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_shell_rejected(self):
        self.mutate("src/runtime_metadata.rs", 'Command::new("/usr/bin/timeout")', 'Command::new("/bin/sh")')
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_process_refresh_rejected(self):
        self.mutate("src/system_summary.rs", "RefreshKind::new()", "RefreshKind::new_all()")
        with self.assertRaises(AssertionError):
            guard.validate(self.root)

    def test_disabled_actions_rejected(self):
        self.mutate("src/main.rs", ".disabled(true)", ".disabled(false)")
        with self.assertRaises(AssertionError):
            guard.validate(self.root)


if __name__ == "__main__":
    unittest.main()
