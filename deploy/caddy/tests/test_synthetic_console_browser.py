"""Source-only browser security gate tests: no Docker, TLS, browser or data."""
from pathlib import Path
import ast
import importlib.util
import re
import subprocess
import sys
import tempfile
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
        self.assertIn('require_browser_post_metadata(observed_posts)', browser)
        self.assertIn('real_browser_origin_fetch_metadata_origin_post_', browser)
        self.assertIn('real_browser_origin_fetch_metadata_fetch_site_post_', browser)
        self.assertIn('csrf_must_rotate_after_login', browser)
        self.assertIn('real_browser_origin_fetch_metadata', browser)
        self.assertIn('strict_anonymous_401(fetch("/api/v1/devices")', browser)
        self.assertIn('strict_anonymous_401(fetch("/api/v1/monitoring/overview")', browser)
        self.assertIn('fresh_pg_scoped_overview_empty', browser)
        self.assertIn('logout_must_deny_overview', browser)
        self.assertIn('context.route(DISALLOWED_NETWORK_URL, restrict)', browser)
        self.assertNotIn('context.route("**/*", restrict)', browser)
        self.assertNotIn('route.continue_()', browser)
        self.assertIn('page.on("request", observe)', browser)
        self.assertNotIn('page.on("requestfinished", observe)', browser)
        self.assertIn('observed_posts.append(request)', browser)
        self.assertIn('browser_post_metadata(request)[0]', browser)
        self.assertIn('require_browser_post_metadata(wire_posts.checked_posts())', browser)
        self.assertIn('new_cdp_session(page)', browser)
        self.assertIn('Network.requestWillBeSentExtraInfo', browser)
        self.assertIn('Network.enable', browser)
        self.assertNotIn('Network.setExtraHTTPHeaders', browser)
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

    def test_actual_browser_metadata_gate_never_accepts_missing_or_wrong_headers(self):
        spec = importlib.util.spec_from_file_location("synthetic_browser_metadata", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        valid = [(True, True)] * 4
        module.require_browser_post_metadata(valid)
        for posts, expected in (
            (valid[:3], "real_browser_origin_fetch_metadata_post_count"),
            (valid + [(True, True)], "real_browser_origin_fetch_metadata_post_count"),
            ([(False, True)] + valid[1:],
             "real_browser_origin_fetch_metadata_origin_post_1"),
            (valid[:1] + [(True, False)] + valid[2:],
             "real_browser_origin_fetch_metadata_fetch_site_post_2"),
            (valid[:3] + [(False, False)],
             "real_browser_origin_fetch_metadata_origin_post_4"),
        ):
            with self.subTest(label=expected):
                with self.assertRaises(module.BrowserGateFailure) as failure:
                    module.require_browser_post_metadata(posts)
                self.assertEqual(str(failure.exception), expected)

    def test_cdp_final_headers_are_checked_without_storing_secrets(self):
        # CDP ExtraInfo is the source of network-stack Sec-Fetch-* values.
        # Events may arrive in any order; store only bools, not raw headers.
        spec = importlib.util.spec_from_file_location("synthetic_browser_wire", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        login = module.CONSOLE + "/api/v1/auth/login"
        logout = module.CONSOLE + "/api/v1/auth/logout"

        recorder = module.ChromiumWirePostMetadata()
        def wire_event(request_id, origin=module.CONSOLE,
                       fetch_site="same-origin"):
            return {"requestId": request_id, "headers": {
                "Origin": origin, "Sec-Fetch-Site": fetch_site,
                "Cookie": "DO-NOT-STORE", "X-CSRF-TOKEN": "DO-NOT-STORE",
            }}
        recorder.on_extra_info(wire_event("p2"))  # Before request event.
        for i, url in enumerate((login, login, logout, logout), 1):
            recorder.on_request({"requestId": "p" + str(i), "request": {
                "method": "POST", "url": url, "postData": "DO-NOT-STORE",
            }})
        for i in (1, 3, 4):
            recorder.on_extra_info(wire_event("p" + str(i)))
        expected = [(True, True)] * 4
        self.assertEqual(recorder.checked_posts(), expected)
        self.assertEqual(set(recorder.metadata_by_id), {"p1", "p2", "p3", "p4"})
        self.assertNotIn("DO-NOT-STORE", str(recorder.__dict__))
        module.require_browser_post_metadata(recorder.checked_posts())

        wrong = module.ChromiumWirePostMetadata()
        for i, url in enumerate((login, login, logout, logout), 1):
            rid = "p" + str(i)
            wrong.on_request({"requestId": rid, "request": {
                "method": "POST", "url": url,
            }})
            hdr = wire_event(rid)
            if i == 1:
                del hdr["headers"]["Sec-Fetch-Site"]
            wrong.on_extra_info(hdr)
        with self.assertRaises(module.BrowserGateFailure) as failed:
            module.require_browser_post_metadata(wrong.checked_posts())
        self.assertEqual(str(failed.exception),
                         "real_browser_origin_fetch_metadata_fetch_site_post_1")

        absent = module.ChromiumWirePostMetadata()
        absent.on_request({"requestId": "missing", "request": {
            "method": "POST", "url": login,
        }})
        with self.assertRaises(module.BrowserGateFailure) as failed:
            absent.checked_posts()
        self.assertEqual(str(failed.exception),
                         "real_browser_origin_fetch_metadata_cdp_post_count")
        for i, url in enumerate((login, login, logout, logout), 1):
            absent.on_request({"requestId": str(i), "request": {
                "method": "POST", "url": url,
            }})
        with self.assertRaises(module.BrowserGateFailure) as failed:
            absent.checked_posts()
        self.assertEqual(str(failed.exception),
                         "real_browser_origin_fetch_metadata_cdp_post_count")

        missing_extra = module.ChromiumWirePostMetadata()
        for i, url in enumerate((login, login, logout, logout), 1):
            rid = str(i)
            missing_extra.on_request({"requestId": rid, "request": {
                "method": "POST", "url": url,
            }})
            if i != 3:
                missing_extra.on_extra_info(wire_event(rid))
        with self.assertRaises(module.BrowserGateFailure) as failed:
            missing_extra.checked_posts()
        self.assertEqual(str(failed.exception),
                         "real_browser_origin_fetch_metadata_wire_headers_post_3")

        blocked = module.ChromiumWirePostMetadata()
        blocked.on_request({"requestId": "outside", "request": {
            "method": "POST", "url": "https://outside.test/api/v1/auth/login",
        }})
        blocked.on_extra_info(wire_event("outside"))
        with self.assertRaises(module.BrowserGateFailure):
            blocked.checked_posts()

    def test_browser_post_headers_are_read_once_after_request_capture(self):
        # Preserve full-header checks (never infer Origin/Fetch-Site from URL).
        # The request event records actual POST references before any response
        # body drains, preventing requestfinished timing from reducing count.
        spec = importlib.util.spec_from_file_location("synthetic_browser_posts", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        class FakeBrowserRequest:
            def __init__(self, headers):
                self._headers = headers
                self.calls = 0

            def all_headers(self):
                self.calls += 1
                return self._headers

        for headers, expected in (
            ({"origin": module.CONSOLE, "sec-fetch-site": "same-origin"}, (True, True)),
            ({"origin": module.CONSOLE}, (True, False)),
            ({"sec-fetch-site": "same-origin"}, (False, True)),
            ({"origin": "https://outside.invalid", "sec-fetch-site": "cross-site"},
             (False, False)),
        ):
            with self.subTest(fields=tuple(headers.keys())):
                req = FakeBrowserRequest(headers)
                self.assertEqual(module.browser_post_metadata(req), expected)
                self.assertEqual(req.calls, 1)

    def test_browser_header_observation_does_not_intercept_trusted_origin(self):
        # Playwright's route.continue_() can hide network-stack-owned
        # Sec-Fetch-* headers in request.all_headers(), even when the real
        # server actually receives them. Only non-Console URLs are routed.
        spec = importlib.util.spec_from_file_location("synthetic_browser_route", BROWSER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        untrusted_route = module.DISALLOWED_NETWORK_URL
        for url in (
            "https://console.lanveriq.internal",
            "https://console.lanveriq.internal/",
            "https://console.lanveriq.internal/__synthetic_browser_probe",
            "https://console.lanveriq.internal/api/v1/auth/login",
            "https://console.lanveriq.internal/api/v1/auth/logout",
            "https://console.lanveriq.internal:443/api/v1/auth/csrf",
            "https://console.lanveriq.internal/api/v1/devices?limit=50",
        ):
            with self.subTest(not_intercepted=url):
                self.assertTrue(module.is_synthetic_console_url(url))
                self.assertIsNone(untrusted_route.search(url))
        for url in (
            "http://console.lanveriq.internal/api/v1/auth/login",
            "https://agent.lanveriq.internal/api/v1/auth/login",
            "https://enroll.lanveriq.internal/",
            "https://console.lanveriq.internal:8443/api/v1/auth/login",
            "https://console.lanveriq.internal:444/api/v1/auth/login",
            "https://127.0.0.1:443/",
            "https://console.lanveriq.internal.evil.test/",
            "https://console.lanveriq.internal@evil.test/",
            "https://user:password@console.lanveriq.internal/",
            "https://console.lanveriq.internal//other",
            "https://console.lanveriq.internal/path#fragment",
            "wss://console.lanveriq.internal/api/v1/updates",
            "file:///etc/passwd",
        ):
            with self.subTest(abort=url):
                self.assertFalse(module.is_synthetic_console_url(url))
                self.assertIsNotNone(untrusted_route.search(url))

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
        self.assertIn("inputs.nextjs_same_origin_probe && !inputs.browser_probe", workflow)
        self.assertEqual(workflow.count("default: false"), 3)
        self.assertIn("synthetic_auth_ui_probe:", workflow)
        self.assertIn(
            "inputs.synthetic_auth_ui_probe && (!inputs.browser_probe || !inputs.nextjs_same_origin_probe)",
            workflow)
        self.assertIn('LANVERIQ_SYNTHETIC_AUTH_UI_PROBE', workflow)
        self.assertIn('node scripts/install-synthetic-auth-fixture.mjs', workflow)
        self.assertIn('verify_nextjs_synthetic_auth_ui(context, password)', browser)
        self.assertIn('synthetic_nextjs_auth_wire_fetch_site_', browser)
        self.assertIn('AUTH_UI_MODE="${LANVERIQ_SYNTHETIC_AUTH_UI_PROBE:-0}"', harness)
        self.assertIn('"$AUTH_UI_MODE" == 0 || "$NEXTJS_MODE" == 1', harness)
        self.assertNotIn('synthetic-only-auth-probe', reference)
        self.assertIn("Declare exact manual acceptance scope (request, not pass)", workflow)
        self.assertIn("Verified result", workflow)
        self.assertIn('>> "$GITHUB_STEP_SUMMARY"', workflow)
        self.assertIn("SKIPPED (browser_probe=false)", workflow)
        self.assertIn("SKIPPED (nextjs_same_origin_probe=false)", workflow)
        self.assertIn("A green workflow only proves the requested gates", workflow)
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
        self.assertIn('startswith(("/api/", "/actuator/"))', browser)
        self.assertIn("same_origin_nextjs_must_not_open_websocket", browser)
        self.assertIn("same_origin_nextjs_navigation_must_not_open_websocket", browser)
        self.assertIn("same_origin_nextjs_must_not_create_session", browser)
        self.assertIn("same_origin_login_submit_must_remain_disabled", browser)
        self.assertIn("same_origin_nextjs_client_navigation", browser)
        self.assertIn('page.wait_for_url(CONSOLE + "/devices"', browser)
        self.assertNotIn("LANVERIQ_CONSOLE_AUTH_ENABLED", workflow)
        self.assertNotIn("ignore_https_errors=True", browser)
        self.assertNotIn("--ignore-certificate-errors", workflow + harness + browser)

    def test_mounted_auth_fixture_is_solely_manual_and_keeps_independent_gates(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        browser = BROWSER.read_text(encoding="utf-8")
        harness = HARNESS.read_text(encoding="utf-8")
        installer = (ROOT / "apps/web-next/scripts/install-synthetic-auth-fixture.mjs"
                     ).read_text(encoding="utf-8")
        route_guard = (ROOT / "apps/web-next/scripts/verify-route-contract.mjs"
                       ).read_text(encoding="utf-8")
        source = (ROOT / "apps/web-next/src/synthetic-only-auth-probe.tsx"
                  ).read_text(encoding="utf-8")
        normal_page = (ROOT / "apps/web-next/src/app/(auth)/login/page.tsx"
                       ).read_text(encoding="utf-8")

        self.assertIn("src/app/__synthetic_auth_probe", route_guard)
        self.assertIn("GITHUB_ACTIONS", installer)
        self.assertIn("synthetic_auth_ui_probe:", workflow)
        self.assertIn("verify:routes", workflow)
        self.assertLess(workflow.index("pnpm run verify:routes"),
                        workflow.index("node scripts/install-synthetic-auth-fixture.mjs"))
        self.assertIn("LANVERIQ_SYNTHETIC_AUTH_UI_PROBE", installer)
        self.assertIn('node scripts/install-synthetic-auth-fixture.mjs', workflow)
        self.assertIn('verify_nextjs_synthetic_auth_ui(context, password)', browser)
        self.assertIn('wire.checked_posts(expected_count=2)', browser)
        self.assertIn('AUTH_UI_MODE="${LANVERIQ_SYNTHETIC_AUTH_UI_PROBE:-0}"', harness)
        self.assertIn("createStagedNextConsoleAuth", source)
        self.assertIn("createStagedConsoleReader", source)
        self.assertNotIn("synthetic-only-auth-probe", normal_page)
        self.assertFalse(
            (ROOT / "apps/web-next/src/app/__synthetic_auth_probe").exists())
        self.assertIn("LANVERIQ_SYNTHETIC_AUTH_UI_PROBE", workflow)
        self.assertNotIn("LANVERIQ_CONSOLE_AUTH_ENABLED", workflow)
        self.assertNotIn("ignore_https_errors=True", browser)

    def test_disposable_caddy_config_generation_keeps_api_and_nextjs_separate(self):
        # Execute only the embedded Python template renderer against a COPY
        # of the checked-in reference; no Caddy, Docker, browser or listener.
        source = HARNESS.read_text(encoding="utf-8")
        match = re.search(
            r'''python3 - "\$CADDY_REFERENCE" "\$BROWSER_MODE" "\$NEXTJS_MODE" <<'PY'\n(.*?)\nPY''',
            source, re.DOTALL,
        )
        self.assertIsNotNone(match)
        renderer = match.group(1)
        ast.parse(renderer)
        original = REFERENCE.read_text(encoding="utf-8")
        marker = 'respond "Console not enabled in this reference configuration" 503'
        self.assertEqual(original.count(marker), 1)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "Caddyfile.synthetic"
            for browser_mode, nextjs_mode in (("0", "0"), ("1", "0"), ("1", "1")):
                with self.subTest(browser=browser_mode, nextjs=nextjs_mode):
                    path.write_text(original, encoding="utf-8")
                    subprocess.run(
                        [sys.executable, "-c", renderer, str(path),
                         browser_mode, nextjs_mode],
                        check=True, capture_output=True, text=True,
                    )
                    result = path.read_text(encoding="utf-8")
                    self.assertNotIn(marker, result)
                    self.assertEqual(result.count(
                        "reverse_proxy https://127.0.0.1:8443"), 2)
                    self.assertEqual(
                        "handle /__synthetic_browser_probe" in result,
                        browser_mode == "1",
                    )
                    self.assertEqual(
                        "handle /api/v1/*" in result, nextjs_mode == "1")
                    self.assertEqual(
                        "reverse_proxy http://127.0.0.1:3100" in result,
                        nextjs_mode == "1")
                    self.assertIn(
                        "header_up Host console.lanveriq.internal:443", result)
                    self.assertIn(
                        "respond \"Pairing not enabled in this reference configuration\" 503",
                        result)
        self.assertEqual(REFERENCE.read_text(encoding="utf-8"), original)

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
