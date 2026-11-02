"""Source-only regression guard for opt-in synthetic Spring Console flags.

The manual Caddy/Tomcat/PostgreSQL test harness uses a 0/1 shell switch.
Spring @ConditionalOnProperty(havingValue="true") requires literal "true".
This test never runs a server, container, agent, or network request.
"""

from pathlib import Path
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
