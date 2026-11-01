"""Source-level regression for the Caddy container CLI argument prefix.

This does not run Docker or assert a successful TLS handshake. It prevents
the exact CLI error observed in manual Actions #41291586713: Docker treated
"adapt" as the executable instead of running "caddy adapt".
"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]


class DockerCaddyEntrypointTest(unittest.TestCase):
    def test_caddy_only_harness_always_invokes_explicit_binary(self):
        script = (ROOT / "deploy/caddy/tests/verify-reference.sh").read_text()
        self.assertRegex(script, r'"\$IMAGE"\s*\\?\s*caddy adapt\b')
        self.assertIn('"$IMAGE" caddy validate --config /etc/caddy/Caddyfile', script)
        self.assertIn('"$IMAGE" caddy run --config /etc/caddy/Caddyfile', script)
        self.assertNotRegex(script, r'"\$IMAGE"\s*\\?\s*(?:adapt|validate|run)\b')

    def test_real_spring_bridge_harness_always_invokes_explicit_binary(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        self.assertIn('"$IMAGE" caddy "$@"', script)
        self.assertIn('"$IMAGE" caddy run --config /etc/caddy/Caddyfile', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" adapt', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" validate', script)
        self.assertNotRegex(script, r'"\$IMAGE"\s*\\?\s*(?:adapt|validate|run)\b')


if __name__ == "__main__":
    unittest.main()
