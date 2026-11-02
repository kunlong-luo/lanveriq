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
        self.assertIn('strict_anonymous_401(fetch("/api/v1/devices")', browser)
        self.assertIn('strict_anonymous_401(fetch("/api/v1/monitoring/overview")', browser)
        self.assertIn('fresh_pg_scoped_overview_empty', browser)
        self.assertIn('logout_must_deny_overview', browser)
        self.assertIn('context.route("**/*", restrict)', browser)
        self.assertIn('page.on("websocket"', browser)
        self.assertIn('browser_must_not_contact_external_origin_or_websocket', browser)
        self.assertNotIn('--ignore-certificate-errors', harness + browser)
        self.assertNotIn('ignore_https_errors=True', browser)
        self.assertNotIn('context.tracing.start(', browser)
        self.assertNotIn('page.screenshot(', browser)

    def test_real_chromium_request_allowlist_never_reaches_agent_or_external_hosts(self):
        spec = importlib.util.spec_from_file_location("synthetic_browser_origin", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        allowed = module.is_synthetic_console_url
        for url in (
            "https://console.lanveriq.internal/__synthetic_browser_probe",
            "https://console.lanveriq.internal/api/v1/auth/csrf",
            "https://console.lanveriq.internal/api/v1/auth/session",
            "https://console.lanveriq.internal/api/v1/devices?limit=50",
            "https://console.lanveriq.internal:443/api/v1/auth/login",
        ):
            with self.subTest(allowed=url):
                self.assertTrue(allowed(url))
        for url in (
            "http://console.lanveriq.internal/api/v1/auth/session",
            "https://agent.lanveriq.internal/api/v1/devices",
            "https://enroll.lanveriq.internal/",
            "https://console.lanveriq.internal:8443/api/v1/auth/login",
            "https://console.lanveriq.internal:444/",
            "https://127.0.0.1:443/",
            "https://console.lanveriq.internal.evil.test/",
            "https://console.lanveriq.internal@evil.test/",
            "https://user:password@console.lanveriq.internal/",
            "https://console.lanveriq.internal//other",
            "https://console.lanveriq.internal/path#fragment",
            "https://console.lanveriq.internal:invalid/",
            "wss://console.lanveriq.internal/api/v1/updates",
            "file:///etc/passwd",
            "data:text/html,hello",
        ):
            with self.subTest(rejected=url):
                self.assertFalse(allowed(url))

    def test_anonymous_browser_api_response_rejects_html_or_cacheable_401(self):
        spec = importlib.util.spec_from_file_location("synthetic_browser_401", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        safe = {
            "status": 401,
            "contentType": "",
            "cacheControl": "private, no-store",
            "json": None,
        }
        module.strict_anonymous_401(safe, "synthetic")
        for mutated in (
            {"status": 403},
            {"contentType": "text/html; charset=utf-8"},
            {"cacheControl": "public, max-age=100"},
            {"cacheControl": "private, x-no-store"},
            {"json": {"login": "should-not-be-here"}},
        ):
            with self.subTest(rejected=tuple(mutated.keys())):
                response = {**safe, **mutated}
                with self.assertRaises(module.BrowserGateFailure):
                    module.strict_anonymous_401(response, "synthetic")

    def test_manual_nextjs_same_origin_bridge_is_explicit_opt_in_and_ui_stays_inert(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        harness = HARNESS.read_text(encoding="utf-8")
        browser = BROWSER.read_text(encoding="utf-8")
        reference = REFERENCE.read_text(encoding="utf-8")
        self.assertIn("nextjs_same_origin_probe:", workflow)
        self.assertIn("default: false", workflow)
        self.assertIn("inputs.nextjs_same_origin_probe", workflow)
        self.assertIn("inputs.browser_probe", workflow)
        self.assertIn("pnpm install --frozen-lockfile", workflow)
        self.assertIn("pnpm run verify:routes", workflow)
        self.assertIn("next start --hostname 127.0.0.1 --port 3100", workflow)
        self.assertIn('LANVERIQ_SYNTHETIC_NEXTJS_PROBE', workflow)
        self.assertIn('NEXTJS_MODE="${LANVERIQ_SYNTHETIC_NEXTJS_PROBE:-0}"', harness)
        self.assertIn('"$NEXTJS_MODE" == 0 || "$BROWSER_MODE" == 1', harness)
        self.assertIn('handle /api/v1/*', harness)
        self.assertIn('reverse_proxy http://127.0.0.1:3100', harness)
        self.assertIn('header_up Host 127.0.0.1:3100', harness)
        self.assertIn('header_up Host console.lanveriq.internal:443', harness)
        self.assertNotIn("reverse_proxy http://127.0.0.1:3100", reference)
        self.assertNotIn("handle /api/v1/*", reference)
        self.assertIn("verify_nextjs_same_origin(context)", browser)
        self.assertIn("same_origin_nextjs_must_not_request_business_api", browser)
        self.assertIn("same_origin_nextjs_must_not_create_session", browser)
        self.assertIn("same_origin_login_submit_must_remain_disabled", browser)
        self.assertIn("same_origin_nextjs_client_navigation", browser)
        self.assertIn('page.wait_for_url(CONSOLE + "/devices"', browser)
        self.assertNotIn("LANVERIQ_CONSOLE_AUTH_ENABLED", workflow)
        self.assertNotIn("ignore_https_errors=True", browser)
        self.assertNotIn("--ignore-certificate-errors", workflow + harness + browser)

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
        with self.assertRaises(module.BrowserGateFailure):
            module.strict_json(
                {"status": 200, "cacheControl": "private, x-no-store",
                 "contentType": "application/json", "json": {}},
                200, "reject_fake_no_store",
            )


if __name__ == "__main__":
    unittest.main()
