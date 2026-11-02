"""Source-only regression guard for opt-in synthetic Spring Console flags.

The manual Caddy/Tomcat/PostgreSQL test harness uses a 0/1 shell switch.
Spring @ConditionalOnProperty(havingValue="true") requires literal "true".
This test never runs a server, container, agent, or network request.
"""

from pathlib import Path
import ast
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
HARNESS = ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh"
SPRING_SECURITY = ROOT / "apps/server/src/main/java/com/lanveriq/server/security"
FLAGS = (
    "lanveriq.console.auth.enabled",
    "lanveriq.device-directory.enabled",
    "lanveriq.device-directory.http-enabled",
    "lanveriq.monitoring.overview-http-enabled",
)


class SyntheticConsoleBooleanFlagsTest(unittest.TestCase):
    def test_default_off_and_boolean_literal_conversion(self):
        script = HARNESS.read_text(encoding="utf-8")
        self.assertIn('CONSOLE_MODE="${LANVERIQ_SYNTHETIC_CONSOLE_SESSION:-0}"', script)
        self.assertRegex(
            script,
            re.compile(
                r"CONSOLE_ENABLED=false\s+"
                r'if \[\[ "\$CONSOLE_MODE" == 1 \]\]; then\s+'
                r"CONSOLE_ENABLED=true\s+fi"
            ),
        )
        self.assertIn('[[ "$CONSOLE_MODE" == 0 || "$CONSOLE_MODE" == 1 ]]', script)
        self.assertIn('[[ "$CONSOLE_MODE" == 0 || "$BRIDGE_MODE" == 1 ]]', script)

    def test_spring_flags_never_receive_raw_numeric_switch(self):
        script = HARNESS.read_text(encoding="utf-8")
        for name in FLAGS:
            with self.subTest(property=name):
                self.assertEqual(script.count(f'--{name}="$CONSOLE_ENABLED"'), 1)
                self.assertNotIn(f'--{name}="$CONSOLE_MODE"', script)

    def test_live_console_direct_tls_diagnostics_fail_closed(self):
        script = HARNESS.read_text(encoding="utf-8")
        self.assertIn('direct_console_status() {', script)
        self.assertIn('direct_h1="$(direct_console_status --http1.1)"', script)
        self.assertIn('direct_h2="$(direct_console_status --http2)"', script)
        self.assertIn('direct_console_status() {', script)
        self.assertIn('edge_console_status() {', script)
        self.assertIn('edge_h1="$(edge_console_status --http1.1)"', script)
        self.assertIn('edge_h2="$(edge_console_status --http2)"', script)
        self.assertIn('[[ "$direct_h1" != 401 || "$direct_h2" != 401 || "$edge_h1" != 401 || "$edge_h2" != 401 ]]', script)
        self.assertLess(script.index('direct_h1="$(direct_console_status --http1.1)"'),
                        script.index('edge_h1="$(edge_console_status --http1.1)"'))
        self.assertLess(script.index('edge_h2="$(edge_console_status --http2)"'),
                        script.index('verify-synthetic-console-session.sh'))
        self.assertIn('H \'Host: console.lanveriq.internal:443\'', script)
        self.assertIn('header_up Host console.lanveriq.internal:443', script)
        self.assertEqual(script.count("-w '%{http_code} %{http_version}'"), 2)
        self.assertIn('"$direct_h2_protocol" == 2', script)
        self.assertIn('"$edge_h2_protocol" == 2', script)
        self.assertIn('"$direct_h1_protocol" == 1.1', script)
        self.assertIn('"$edge_h1_protocol" == 1.1', script)

        self.assertIn('TMP/pki/proxy.crt', script)
        self.assertIn('TMP/pki/backend-ca.crt', script)
        self.assertLess(script.index('direct_h1="$(direct_console_status --http1.1)"'),
                        script.index('verify-synthetic-console-session.sh'))
        self.assertNotIn('curl -k', script)
        self.assertNotIn('curl --insecure', script)

    def test_http2_requires_explicit_tls_only_loopback_acceptance_profile(self):
        fixture = (ROOT / "apps/server/src/main/resources/"
                   "application-agent-upstream-mtls-acceptance.yml").read_text(encoding="utf-8")
        production = (ROOT / "apps/server/src/main/resources/"
                      "application.yml").read_text(encoding="utf-8")
        self.assertIn("  address: 127.0.0.1", fixture)
        self.assertIn("  port: 8443", fixture)
        self.assertIn("  http2:\n    enabled: true", fixture)
        self.assertIn("  ssl:\n    enabled: true", fixture)
        self.assertIn("    client-auth: need", fixture)
        self.assertIn("    enabled-protocols: TLSv1.3", fixture)
        self.assertNotIn("  http2:\n    enabled: true", production)
        script = HARNESS.read_text(encoding="utf-8")
        self.assertIn("--spring.profiles.active=agent-upstream-mtls-acceptance", script)
        self.assertIn("== 1.1", script)
        self.assertIn("== 2", script)

    def test_manual_runtime_chain_diagnostics_are_private_and_fixed_boolean_only(self):
        script = HARNESS.read_text(encoding="utf-8")
        guard = (SPRING_SECURITY / "ConsoleRequestGuardFilter.java").read_text(
            encoding="utf-8")
        self.assertIn("LANVERIQ_CONSOLE_GUARD_RESULT", guard)
        self.assertIn("LOG.isDebugEnabled()", guard)
        self.assertIn("hostOk, originOk, fetchOk, allowed", guard)
        self.assertNotIn('LOG.debug("Host:', guard)
        self.assertNotIn('LOG.debug("Origin:', guard)
        self.assertIn("DefaultSecurityFilterChain=DEBUG", script)
        self.assertIn("ConsoleRequestGuardFilter=DEBUG", script)
        self.assertIn('python3 - "$TMP/spring.log"', script)
        self.assertIn('guard_seen=', script)
        self.assertIn('console_matcher_seen=', script)
        self.assertLess(script.index('guard_seen='),
                        script.index('verify-synthetic-console-session.sh'))
        self.assertNotIn('cat "$TMP/spring.log"', script)

    def test_private_diagnostic_python_is_syntactically_valid_and_bounded(self):
        script = HARNESS.read_text(encoding="utf-8")
        match = re.search(
            r"""python3 - "\$TMP/spring\.log" <<'PY'\n(.*?)\nPY""",
            script, re.DOTALL)
        self.assertIsNotNone(match)
        code = match.group(1)
        ast.parse(code)
        self.assertIn("LANVERIQ_CONSOLE_HOST_DETAIL", code)
        self.assertIn("SYNTHETIC CONSOLE FILTER DIAGNOSTIC:", code)
        self.assertIn("SYNTHETIC CONSOLE HOST DIAGNOSTIC:", code)
        # Valid HTTP/2 :authority need not carry an ordinary Host header.
        # The summary must not mistake that legal case for H1 Host loss.
        self.assertIn("http1_missing_or_multiple_host=", code)
        self.assertIn("http2_no_ordinary_host=", code)
        self.assertIn("http2_explicit_host_mismatch=", code)
        self.assertIn("http2_authority_mismatch=", code)
        self.assertIn("SYNTHETIC CONSOLE CHAIN DIAGNOSTIC:", code)
        self.assertNotIn("print(log)", code)
        self.assertNotIn("print(line)", code)

    def test_console_api_error_statuses_never_trigger_servlet_error_redispatch(self):
        # Tomcat can dispatch response.sendError(401) to /error; the
        # immutable deny-all chain then replaces 401 with a misleading 403.
        # Keep /error denied. Return intentional API status directly.
        for name in (
            "ConsoleSessionSecurityConfiguration.java",
            "DeviceDirectoryHttpSecurityConfiguration.java",
            "MonitoringOverviewHttpSecurityConfiguration.java",
            "ConsoleSessionRevalidationFilter.java",
            "ConsoleSessionEventHandlers.java",
            "ConsoleLoginRateLimitFilter.java",
        ):
            with self.subTest(source=name):
                source = (SPRING_SECURITY / name).read_text(encoding="utf-8")
                self.assertIn("response.setStatus(", source)
                self.assertNotIn("response.sendError(", source)
        manual = (ROOT / "apps/server/scripts/"
                  "verify-synthetic-console-session.sh").read_text(encoding="utf-8")
        self.assertIn("assert_direct_401() {", manual)
        self.assertEqual(manual.count("assert_direct_401"), 7)
        self.assertIn("cache-control:", manual)
        self.assertIn('if body:', manual)
        self.assertNotIn("curl --insecure", manual)

    def test_scoped_security_chains_remain_explicit_opt_in(self):
        for name in (
            "ConsoleSessionSecurityConfiguration.java",
            "DeviceDirectoryHttpSecurityConfiguration.java",
            "MonitoringOverviewHttpSecurityConfiguration.java",
        ):
            with self.subTest(class_name=name):
                source = (SPRING_SECURITY / name).read_text(encoding="utf-8")
                self.assertIn('havingValue = "true"', source)


if __name__ == "__main__":
    unittest.main()
