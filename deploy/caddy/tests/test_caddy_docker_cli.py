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
        self.assertNotIn("curl --insecure", script)
        self.assertNotIn("--no-check-certificate", script)

    def test_bridge_has_positive_caddy_to_real_tomcat_health_proof(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        reference = (ROOT / "deploy/caddy/Caddyfile.reference").read_text()
        # Never modify the deploy reference to enable Tomcat's health route
        # on the Agent host; inject it ONLY into the synthetic throwaway copy.
        self.assertNotIn("@agentRoutes path /actuator/health", reference)
        self.assertIn("s|@agentRoutes path |@agentRoutes path /actuator/health |", script)
        self.assertIn("'@agentRoutes path /actuator/health '", script)
        self.assertIn('bridge_health_request() {', script)
        self.assertIn('assert_bridge_up() {', script)
        self.assertIn('echo "PASS: Agent mTLS -> Caddy -> real Spring/Tomcat mTLS -> PG18 health 200/UP."', script)
        self.assertIn("--cert \"$TMP/pki/agent.crt\"", script)
        self.assertIn("--cacert \"$ROOT_CA\"", script)
        self.assertIn('"https://agent.lanveriq.internal/actuator/health"', script)
        self.assertIn('[[ "$code" == 200 ]]', script)
        self.assertIn("""grep -Fq '"status":"UP"'""", script)
        self.assertIn('[[ "$status" == 403 ]]', script)
        self.assertIn('[[ "$status" == 502 ]]', script)
        self.assertIn("assert_trusted_upstream_healthy", script)
        self.assertIn('start_caddy "$CADDY_REFERENCE"', script)
        self.assertIn("assert_bridge_up", script)
        self.assertNotIn("curl -k", script)
        self.assertNotIn("curl --insecure", script)

    def test_console_session_probe_cannot_run_without_isolated_manual_bridge(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        workflow = (ROOT / ".github/workflows/console-https-synthetic.yml").read_text()
        standalone = (ROOT / "apps/server/scripts/verify-synthetic-console-session.sh").read_text()
        reference = (ROOT / "deploy/caddy/Caddyfile.reference").read_text()
        self.assertIn('CONSOLE_MODE="${LANVERIQ_SYNTHETIC_CONSOLE_SESSION:-0}"', script)
        self.assertIn('[[ "$CONSOLE_MODE" == 0 || "$BRIDGE_MODE" == 1 ]]', script)
        self.assertIn('if [[ "$CONSOLE_MODE" == 1 ]]; then', script)
        self.assertIn('spring.main.web-application-type=none', script)
        self.assertIn('script --quiet --return --echo never --command', script)
        self.assertIn('console-offline-context.log', script)
        self.assertIn('--lanveriq.bootstrap.enabled=false', script)
        self.assertIn('PASS: actual Spring non-web context and isolated PostgreSQL startup.', script)
        self.assertIn('timeout --signal=TERM --kill-after=5s 75s', script)
        self.assertIn('diagnose-synthetic-bootstrap.py', script)
        self.assertIn('Initial administrator created.', script)
        self.assertNotIn('tail -n 80 "$TMP/console-bootstrap.log"', script)
        self.assertIn('--lanveriq.console.auth.enabled="$CONSOLE_MODE"', script)
        self.assertIn('TEST-ONLY canonical console host', script)
        self.assertIn('LANVERIQ_SYNTHETIC_CONSOLE_SESSION', workflow)
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("push:", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertIn('respond "Console not enabled in this reference configuration" 503', reference)
        self.assertNotIn('TEST-ONLY canonical console host', reference)
        self.assertIn('expect_status "missing CSRF login rejected" 403', standalone)
        self.assertIn('expect_status "cross-origin login rejected" 403', standalone)
        self.assertIn('expect_status "incorrect password denied" 401', standalone)
        self.assertIn('expect_status "scoped empty device page" 200', standalone)
        self.assertIn('expect_status "valid CSRF logout" 204', standalone)
        # Synthetic password and CSRF are never process argv values.
        self.assertIn('3<<< "$SYNTHETIC_PASSWORD"', script)
        self.assertNotIn('"$SYNTHETIC_LOGIN" "$SYNTHETIC_PASSWORD"', script)
        self.assertIn('IFS= read -r PASSWORD <&3', standalone)
        self.assertIn('token = sys.stdin.read()', standalone)
        self.assertNotIn('PASSWORD="$5"', standalone)
        self.assertNotIn('python3 - "$TMP/console-csrf.curlrc" "$token"', standalone)
        self.assertIn('expect_status "revoked synthetic session denied" 401', standalone)
        self.assertIn('assert_cookie_flags', standalone)
        self.assertIn("session_version = session_version + 1", standalone)
        self.assertIn('--cacert "$ROOT_CA"', standalone)
        self.assertNotIn("curl --insecure", standalone)
        self.assertNotIn("curl -k", standalone)

    def test_real_spring_bridge_harness_always_invokes_explicit_binary(self):
        script = (ROOT / "apps/server/scripts/verify-agent-upstream-tls.sh").read_text()
        self.assertIn('"$IMAGE" caddy "$@"', script)
        self.assertIn('"$IMAGE" caddy run --config /etc/caddy/Caddyfile', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" adapt', script)
        self.assertIn('run_caddy_command "$CADDY_REFERENCE" validate', script)
        self.assertNotRegex(script, r'"\$IMAGE"\s*\\?\s*(?:adapt|validate|run)\b')


if __name__ == "__main__":
    unittest.main()
