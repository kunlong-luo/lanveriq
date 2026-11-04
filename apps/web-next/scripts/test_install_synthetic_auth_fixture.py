"""Reject test-only Next.js auth route activation outside explicit CI flags.

Uses an invented temporary web-next workspace and Node's standard library.
No real app files, credentials, network, browser or Docker are accessed.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

INSTALLER = Path(__file__).resolve().parent / "install-synthetic-auth-fixture.mjs"
FLAGS = (
    "LANVERIQ_SYNTHETIC_BROWSER_PROBE",
    "LANVERIQ_SYNTHETIC_NEXTJS_PROBE",
    "LANVERIQ_SYNTHETIC_AUTH_UI_PROBE",
)


class SyntheticFixtureInstallerTest(unittest.TestCase):
    def test_fails_closed_without_all_three_flags_or_ci_runner(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "web-next"
            (root / "src/app/(auth)/login").mkdir(parents=True)
            (root / "src/synthetic-only-auth-probe.tsx").write_text(
                "// fake disposable workspace", encoding="utf-8")
            (root / "src/app/(auth)/login/page.tsx").write_text(
                "// deliberately unconnected", encoding="utf-8")
            target = root / "src/app/synthetic-auth-probe/page.tsx"
            base = {**os.environ, **{flag: "1" for flag in FLAGS},
                    "GITHUB_ACTIONS": "true"}
            for key in (*FLAGS, "GITHUB_ACTIONS"):
                env = {**base}
                env[key] = "0" if key != "GITHUB_ACTIONS" else "false"
                with self.subTest(disabled=key):
                    result = subprocess.run(
                        ["node", str(INSTALLER)], cwd=root,
                        env=env, capture_output=True, text=True, check=False)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(target.exists())
            # A successful opt-in writes only a fixed route importing the
            # unmounted test component, not the ordinary real login page.
            accepted = subprocess.run(
                ["node", str(INSTALLER)], cwd=root, env=base,
                capture_output=True, text=True, check=False)
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            rendered = target.read_text(encoding="utf-8")
            self.assertIn("SyntheticOnlyAuthProbe", rendered)
            self.assertNotIn("password", rendered)
            self.assertNotIn("/api/v1/", rendered)
            second = subprocess.run(
                ["node", str(INSTALLER)], cwd=root, env=base,
                capture_output=True, text=True, check=False)
            self.assertNotEqual(second.returncode, 0)
            self.assertEqual(target.read_text(encoding="utf-8"), rendered)


if __name__ == "__main__":
    unittest.main()
