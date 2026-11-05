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


    def test_fourth_flag_is_explicit_and_multi_admin_route_is_never_ordinary(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "web-next"
            (root / "src/app/(auth)/login").mkdir(parents=True)
            (root / "src/synthetic-only-auth-probe.tsx").write_text(
                "// source-only test", encoding="utf-8")
            (root / "src/synthetic-only-multi-admin-probe.tsx").write_text(
                "// source-only multi-admin test", encoding="utf-8")
            (root / "src/staged-console-workspace.tsx").write_text(
                "// source-only unmounted Console candidate", encoding="utf-8")
            (root / "src/staged-console-route-boundary.tsx").write_text(
                "// source-only unmounted multi-route boundary", encoding="utf-8")
            (root / "src/app/(auth)/login/page.tsx").write_text(
                "// disabled login", encoding="utf-8")
            base = {**os.environ, **{flag: "1" for flag in FLAGS},
                    "GITHUB_ACTIONS": "true"}
            main = root / "src/app/synthetic-auth-probe/page.tsx"
            multi = root / "src/app/synthetic-multi-admin-probe/page.tsx"
            candidate = root / "src/app/synthetic-console-candidate/page.tsx"
            route_root = root / "src/app/synthetic-console-route"
            # Explicitly OFF: only the original one-admin temporary route.
            off = subprocess.run(
                ["node", str(INSTALLER)], cwd=root,
                env={**base, "LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE": "0"},
                capture_output=True, text=True, check=False)
            self.assertEqual(off.returncode, 0, off.stderr)
            self.assertTrue(main.exists())
            self.assertFalse(multi.exists())
            self.assertFalse(candidate.exists())
            self.assertFalse(route_root.exists())
            self.assertFalse((root / "src/proxy.ts").exists())

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "web-next"
            (root / "src/app/(auth)/login").mkdir(parents=True)
            (root / "src/synthetic-only-auth-probe.tsx").write_text(
                "// source-only test", encoding="utf-8")
            (root / "src/synthetic-only-multi-admin-probe.tsx").write_text(
                "// source-only multi-admin test", encoding="utf-8")
            (root / "src/staged-console-workspace.tsx").write_text(
                "// source-only unmounted Console candidate", encoding="utf-8")
            (root / "src/staged-console-route-boundary.tsx").write_text(
                "// source-only unmounted multi-route boundary", encoding="utf-8")
            (root / "src/app/(auth)/login/page.tsx").write_text(
                "// disabled login", encoding="utf-8")
            enabled = {**base, "LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE": "1"}
            accepted = subprocess.run(
                ["node", str(INSTALLER)], cwd=root, env=enabled,
                capture_output=True, text=True, check=False)
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            self.assertIn("SyntheticMultiAdminProbe",
                          (root / "src/app/synthetic-multi-admin-probe/page.tsx")
                          .read_text(encoding="utf-8"))
            candidate_page = root / "src/app/synthetic-console-candidate/page.tsx"
            self.assertIn("StagedConsoleWorkspace",
                          candidate_page.read_text(encoding="utf-8"))
            proxy = (root / "src/proxy.ts").read_text(encoding="utf-8")
            self.assertIn("export function proxy(", proxy)
            self.assertIn("/synthetic-console-route/:path*", proxy)
            self.assertIn("timingSafeEqual", proxy)
            self.assertIn("LANVERIQ_SYNTHETIC_EDGE_SECRET", proxy)
            self.assertIn("status: 404", proxy)
            self.assertIn("Cache-Control", proxy)
            self.assertNotIn("synthetic-browser-a", proxy)
            self.assertNotIn("/api/v1/", proxy)
            for view in ("overview", "devices", "monitoring"):
                nested = root / "src/app/synthetic-console-route" / view / "page.tsx"
                self.assertTrue(nested.is_file())
                source = nested.read_text(encoding="utf-8")
                self.assertIn("StagedConsoleRouteBoundary", source)
                self.assertIn(f'view="{view}"', source)
                self.assertNotIn("password", source)
                self.assertNotIn("/api/v1/", source)
            self.assertNotIn("password", candidate_page.read_text(encoding="utf-8"))
            self.assertNotIn("/api/v1/", candidate_page.read_text(encoding="utf-8"))
            self.assertNotIn("/api/v1/",
                (root / "src/app/synthetic-multi-admin-probe/page.tsx")
                .read_text(encoding="utf-8"))
            self.assertNotIn("synthetic-browser-b",
                (root / "src/app/synthetic-multi-admin-probe/page.tsx")
                .read_text(encoding="utf-8"))
            denied = subprocess.run(
                ["node", str(INSTALLER)], cwd=root, env=enabled,
                capture_output=True, text=True, check=False)
            self.assertNotEqual(denied.returncode, 0)


if __name__ == "__main__":
    unittest.main()
