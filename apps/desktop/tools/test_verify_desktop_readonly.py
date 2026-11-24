"""Fail-closed synthetic source mutations; no shell/service/host calls."""
import importlib.util
import json
import pathlib
import shutil
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "desktop_guard", HERE / "verify_desktop_readonly.py"
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class DesktopReadonlyContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name) / "desktop"
        shutil.copytree(guard.DESKTOP, self.root, ignore=shutil.ignore_patterns(
            "node_modules", "target", "dist", "pnpm-lock.yaml", "Cargo.lock"
        ))

    def tearDown(self):
        self.temp.cleanup()

    def mutate(self, path, old, new):
        target = self.root / path
        source = target.read_text(encoding="utf-8")
        self.assertIn(old, source)
        target.write_text(source.replace(old, new), encoding="utf-8")

    def test_current_preview_passes(self):
        self.assertIn("PASS", guard.validate(self.root))

    def test_reject_missing_native_launcher_icon(self):
        (self.root / "src-tauri/icons/icon.png").unlink()
        with self.assertRaisesRegex(ValueError, "missing or invalid native launcher icon"):
            guard.validate(self.root)

    def test_reject_unreviewed_package_identity(self):
        self.mutate("src-tauri/tauri.conf.json", '"productName": "lanveriq-desktop"', '"productName": "Lanveriq Desktop (Offline Preview)"')
        with self.assertRaisesRegex(ValueError, "desktop package identity drift"):
            guard.validate(self.root)

    def test_reject_missing_bundle_icon_reference(self):
        self.mutate("src-tauri/tauri.conf.json", '"icon": ["icons/icon.png"]', '"icon": []')
        with self.assertRaisesRegex(ValueError, "desktop bundle icon"):
            guard.validate(self.root)

    def test_reject_arbitrary_bundle_icon_reference(self):
        self.mutate("src-tauri/tauri.conf.json", '"icon": ["icons/icon.png"]', '"icon": ["icons/unknown.png"]')
        with self.assertRaisesRegex(ValueError, "desktop bundle icon"):
            guard.validate(self.root)

    def test_reject_enabled_installer_bundle(self):
        self.mutate("src-tauri/tauri.conf.json", '"active": false', '"active": true')
        with self.assertRaisesRegex(ValueError, "bundle must stay OFF"):
            guard.validate(self.root)

    def test_reject_autostart_tray(self):
        target = self.root / "src-tauri/tauri.conf.json"
        obj = json.loads(target.read_text(encoding="utf-8"))
        obj["app"]["trayIcon"] = {"iconPath": "icons/default.png"}
        target.write_text(json.dumps(obj), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "resident tray forbidden"):
            guard.validate(self.root)

    def test_reject_extra_capabilities(self):
        self.mutate("src-tauri/capabilities/default.json", '"core:default"', '"shell:allow-execute"')
        with self.assertRaisesRegex(ValueError, "unapproved desktop capabilities"):
            guard.validate(self.root)

    def test_reject_native_start_command(self):
        self.mutate("src-tauri/src/lib.rs",
                    "            inspect_popos_preflight\n        ])",
                    "            inspect_popos_preflight, start_agent\n        ])")
        with self.assertRaisesRegex(ValueError, "unexpected handler dispatch"):
            guard.validate(self.root)

    def test_reject_bundled_preflight_replaced_with_shell(self):
        self.mutate("src-tauri/src/lib.rs",
                    'Command::new("/usr/bin/python3")',
                    'Command::new("/bin/sh")')
        with self.assertRaisesRegex(ValueError,"bundled read-only PopOS preflight protocol drift|unapproved runtime operation"):
            guard.validate(self.root)

    def test_reject_preflight_ipc_removed(self):
        self.mutate("src/DesktopApp.tsx",
                    'invoke<string>("inspect_popos_preflight")',
                    'invoke<string>("not_a_reviewed_command")')
        with self.assertRaisesRegex(ValueError,"desktop must invoke only read-only"):
            guard.validate(self.root)

    def test_reject_privileged_shell_execution(self):
        # Insert the exact dangerous token, not an escaped string that cannot
        # match the actual source-level policy detector.
        self.mutate(
            "src-tauri/src/lib.rs",
            "const UNIT: &str",
            'const DANGEROUS: &str = r#"Command::new("/bin/sh")"#;\nconst UNIT: &str',
        )
        with self.assertRaisesRegex(ValueError, "unapproved runtime operation"):
            guard.validate(self.root)

    def test_reject_released_install_button(self):
        self.mutate("src/safety.ts", "installEnabled: false", "installEnabled: true")
        with self.assertRaisesRegex(ValueError, "release lock lifted"):
            guard.validate(self.root)

    def test_reject_extra_frontend_ipc(self):
        self.mutate("src/DesktopApp.tsx",
                    'invoke<HostSnapshot>("inspect_host")',
                    'invoke<HostSnapshot>("inspect_host"); invoke<void>("install_agent")')
        with self.assertRaisesRegex(ValueError, "extra IPC"):
            guard.validate(self.root)


if __name__ == "__main__":
    unittest.main()
