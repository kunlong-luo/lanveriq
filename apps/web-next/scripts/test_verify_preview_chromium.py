"""Fast source-only regression for the MANUAL NXT-02 Chromium probe.

Runs under normal Web CI without opening a browser or any network listener.
"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "apps/web-next/scripts/verify-preview-chromium.py"
WORKFLOW = ROOT / ".github/workflows/nextjs-browser-preview-synthetic.yml"
LOGIN = ROOT / "apps/web-next/src/components/login-preview.tsx"


class NextPreviewChromiumSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("nxt02_browser_smoke", SCRIPT)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_loopback_only_and_explicitly_no_business_api(self):
        allows = self.module.allows_preview_request
        for value in (
            "http://127.0.0.1:3100/overview",
            "http://127.0.0.1:3100/_next/static/chunks/a.js",
            "http://127.0.0.1:3100/devices?test=1",
        ):
            with self.subTest(allowed=value):
                self.assertTrue(allows(value))
        for value in (
            "https://127.0.0.1:3100/overview",
            "http://localhost:3100/overview",
            "http://127.0.0.1:3101/overview",
            "http://127.0.0.1:3100/api/v1/devices",
            "http://127.0.0.1:3100/actuator/health",
            "http://127.0.0.1:3100//evil.example/",
            "http://user:pass@127.0.0.1:3100/overview",
            "https://outside.example/collect",
            "wss://127.0.0.1:3100/api/v1/updates",
            "file:///etc/passwd",
        ):
            with self.subTest(denied=value):
                self.assertFalse(allows(value))

    def test_manual_only_and_unchanged_inert_login(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        login = LOGIN.read_text(encoding="utf-8")
        script = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("  push:", workflow)
        self.assertNotIn("  pull_request:", workflow)
        self.assertIn("playwright==1.64.1", workflow)
        self.assertIn("pnpm install --frozen-lockfile", workflow)
        self.assertIn("next start --hostname 127.0.0.1 --port 3100", workflow)
        self.assertNotIn("LANVERIQ_CONSOLE_AUTH_ENABLED", workflow)
        self.assertIn('type="password" disabled', login)
        self.assertIn('type="text" disabled', login)
        self.assertIn('button type="button" disabled', login)
        self.assertIn("context.route(\"**/*\", restrict)", script)
        self.assertIn('page.on("websocket"', script)
        self.assertIn('preview_must_not_set_session_cookie', script)
        self.assertNotIn("screenshot(", script)
        self.assertNotIn("tracing.start(", script)

    def test_never_report_success_for_failed_predicate(self):
        with self.assertRaises(self.module.PreviewGateFailure):
            self.module.require(False, "negative_check")
        self.module.require(True, "positive_check")


if __name__ == "__main__":
    unittest.main()
