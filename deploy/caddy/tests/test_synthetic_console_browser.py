"""Source-only browser security gate tests: no Docker, TLS, browser or data."""
from pathlib import Path
import importlib.util
import unittest

ROOT = Path(__file__).resolve().parents[3]
HARNESS = ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh"
WORKFLOW = ROOT / ".github/workflows/console-https-synthetic.yml"
BROWSER = ROOT / "apps/server/scripts/verify-synthetic-console-browser.py"
REFERENCE = ROOT / "deploy/caddy/Caddyfile.reference"


class SyntheticChromiumGateTest(unittest.TestCase):
    def test_browser_probe_requires_explicit_manual_opt_in(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        script = HARNESS.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("browser_probe:", workflow)
        self.assertIn("default: false", workflow)
        self.assertNotIn("push:", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertIn('BROWSER_MODE="${LANVERIQ_SYNTHETIC_BROWSER_PROBE:-0}"', script)
        self.assertIn('[[ "$BROWSER_MODE" == 0 || "$CONSOLE_MODE" == 1 ]]', script)
        self.assertIn('if [[ "$BROWSER_MODE" == 1 ]]; then', script)
        self.assertIn('LANVERIQ_BROWSER_PYTHON', script)
        self.assertIn('verify-synthetic-console-session.sh', script)
        self.assertLess(script.index('verify-synthetic-console-session.sh'),
                        script.index('verify-synthetic-console-browser.py'))
        self.assertIn('playwright==1.64.1', workflow)
        self.assertIn('libnss3-tools', workflow)
        self.assertIn('if: ${{ inputs.browser_probe }}', workflow)

    def test_ephemeral_only_caddy_probe_and_no_certificate_bypass(self):
        harness = HARNESS.read_text(encoding="utf-8")
        browser = BROWSER.read_text(encoding="utf-8")
        ref = REFERENCE.read_text(encoding="utf-8")
        self.assertIn('handle /__synthetic_browser_probe', harness)
        self.assertNotIn('__synthetic_browser_probe', ref)
        self.assertIn('certutil -d "sql:$BROWSER_HOME/.pki/nssdb" -A', harness)
        self.assertIn('ignore_https_errors=False', browser)
        self.assertIn('untrusted_browser_must_fail_ca_validation', browser)
        self.assertIn('untrusted_browser_accepted_private_ca', browser)
        self.assertIn('ERR_CERT_AUTHORITY_INVALID', browser)
        self.assertIn('env={**os.environ, "HOME": untrusted_home}', browser)
        self.assertIn('host_only_cookie_other_vhost', browser)
        self.assertIn('credentials: \'same-origin\'', browser)
        self.assertIn('session_fixation_rotation', browser)
        self.assertIn('prelogin_csrf_must_fail_after_fixation', browser)
        self.assertIn('authenticated_cookie_other_vhost', browser)
        self.assertIn('len(observed_posts) == 4', browser)
        self.assertIn('csrf_must_rotate_after_login', browser)
        self.assertIn('real_browser_origin_fetch_metadata', browser)
        self.assertNotIn('--ignore-certificate-errors', harness + browser)
        self.assertNotIn('ignore_https_errors=True', browser)
        self.assertNotIn('context.tracing.start(', browser)
        self.assertNotIn('page.screenshot(', browser)

    def test_browser_assertions_import_without_playwright_or_network(self):
        spec = importlib.util.spec_from_file_location("synthetic_browser_gate", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(
            module.strict_json(
                {"status": 200, "cacheControl": "private, no-store",
                 "contentType": "application/json", "json": {"items": []}},
                200, "synthetic"),
            {"items": []},
        )
        with self.assertRaises(module.BrowserGateFailure):
            module.strict_json(
                {"status": 200, "cacheControl": "private, max-age=60",
                 "contentType": "application/json", "json": {}},
                200, "reject_cacheable",
            )


if __name__ == "__main__":
    unittest.main()
