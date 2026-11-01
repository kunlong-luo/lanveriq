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

    def test_untrusted_assertion_stripped_before_proxy_injects_identity(self):
        config = (ROOT / "deploy/caddy/Caddyfile.reference").read_text()
        strip = "request_header -X-Lanveriq-Verified-Client-*"
        proxy = "reverse_proxy https://127.0.0.1:8443 {"
        inject = (
            "header_up X-Lanveriq-Verified-Client-Fingerprint "
            "{http.request.tls.client.fingerprint}"
        )
        self.assertIn(strip, config)
        self.assertIn(inject, config)
        self.assertLess(config.index(strip), config.index(proxy))
        self.assertLess(config.index(proxy), config.index(inject))
        # Caddy HeaderOps (v2.11.7) processes Set before Delete within a
        # single reverse_proxy. Removing the entire identity family there
        # would erase the verified fingerprint it just inserted.
        self.assertNotIn("header_up -X-Lanveriq-Verified-Client-*", config)
        self.assertIn("header_up -X-Forwarded-Client-Cert", config)
        self.assertIn("header_up -SSL-Client-Cert", config)
        self.assertIn("mode require_and_verify", config)

    def test_caddy_only_private_ca_stays_in_docker_storage(self):
        script = (ROOT / "deploy/caddy/tests/verify-reference.sh").read_text()
        self.assertIn('CADDY_DATA_VOLUME="$(docker volume create)"', script)
        self.assertIn('CADDY_CONFIG_VOLUME="$(docker volume create)"', script)
        self.assertIn('"$CADDY_DATA_VOLUME:/data"', script)
        self.assertIn('"$CADDY_CONFIG_VOLUME:/config"', script)
        self.assertIn('docker volume rm "$CADDY_DATA_VOLUME"', script)
        self.assertIn('docker volume rm "$CADDY_CONFIG_VOLUME"', script)
        self.assertIn(
            'docker cp "$CID:/data/caddy/pki/authorities/local/root.crt"', script
        )
        self.assertIn('CA_ROOT="$TMP/public-caddy-root.crt"', script)
        self.assertIn('--cacert "$CA_ROOT"', script)
        self.assertNotIn('CA_ROOT="$TMP/data/caddy/', script)
        self.assertNotIn('docker cp "$CID:/data/caddy/pki/authorities/local/root.key"', script)
        self.assertNotIn('chmod -R 777', script)

    def test_real_bridge_private_ca_stays_in_docker_storage(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        self.assertIn('CADDY_DATA_VOLUME="$(docker volume create)"', script)
        self.assertIn('CADDY_CONFIG_VOLUME="$(docker volume create)"', script)
        self.assertIn('"$CADDY_DATA_VOLUME:/data"', script)
        self.assertIn('"$CADDY_CONFIG_VOLUME:/config"', script)
        self.assertIn('docker volume rm "$CADDY_DATA_VOLUME"', script)
        self.assertIn('docker volume rm "$CADDY_CONFIG_VOLUME"', script)
        self.assertIn(
            'docker cp "$CADDY_CID:/data/caddy/pki/authorities/local/root.crt"', script
        )
        self.assertIn('ROOT_CA="$TMP/public-caddy-root.crt"', script)
        self.assertIn('--cacert "$ROOT_CA"', script)
        self.assertNotIn('ROOT_CA="$TMP/caddy-data/', script)
        self.assertNotIn('chmod -R 777', script)

    def test_real_tomcat_negative_probe_requires_live_trusted_tls13_peer(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        self.assertIn("assert_trusted_upstream_healthy() {", script)
        self.assertIn('kill -0 "$APP_PID"', script)
        self.assertIn("--tlsv1.3 --tls-max 1.3", script)
        self.assertIn("--cacert \"$TMP/pki/backend-ca.crt\"", script)
        self.assertIn('assert_trusted_upstream_healthy\n    echo "PASS: $label failed TLS handshake', script)
        self.assertIn('tls_denied "forced TLSv1.2 despite valid proxy certificate"', script)
        self.assertIn("--tlsv1.2 --tls-max 1.2", script)
        self.assertIn("assert_trusted_upstream_healthy\necho \"PASS: backend certificate DNS/SNI", script)
        self.assertNotIn("--insecure", script)
        self.assertNotIn("--no-check-certificate", script)

    def test_real_spring_bridge_harness_always_invokes_explicit_binary(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        self.assertIn('"$IMAGE" caddy "$@"', script)
        self.assertIn('"$IMAGE" caddy run --config /etc/caddy/Caddyfile', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" adapt', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" validate', script)
        self.assertNotRegex(script, r'"\$IMAGE"\s*\\?\s*(?:adapt|validate|run)\b')


if __name__ == "__main__":
    unittest.main()
