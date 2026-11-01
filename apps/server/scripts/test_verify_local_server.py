"""Offline tests for the manual, read-only localhost Spring Boot probe.

Uses ONLY a temporary, in-process synthetic HTTP server bound to 127.0.0.1.
No Docker, PostgreSQL, production endpoint, device data or credentials.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
import subprocess
import threading
import unittest


SCRIPT = Path(__file__).with_name("verify-local-server.sh")


class LocalProbeTests(unittest.TestCase):
    def invoke(self, *, health_code=200, health_body=b'{"status":"UP"}',
               devices_code=403):
        class FakeBackend(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/actuator/health":
                    status, body = health_code, health_body
                elif self.path == "/api/v1/devices":
                    status, body = devices_code, b"denied"
                else:
                    status, body = 404, b"not found"
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeBackend)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            result = subprocess.run(
                ["bash", str(SCRIPT), str(server.server_port)],
                capture_output=True, text=True, timeout=15, check=False,
            )
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)
        return result

    @unittest.skipUnless(shutil.which("curl") and shutil.which("python3"),
                         "curl and python3 are required for this check")
    def test_only_healthy_and_default_denied_can_pass(self):
        result = self.invoke()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS: health HTTP 200/UP", result.stdout)

    def test_down_health_fails(self):
        result = self.invoke(health_body=b'{"status":"DOWN"}')
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Health response was not", result.stderr)

    def test_malformed_health_fails(self):
        result = self.invoke(health_body=b"<html>up</html>")
        self.assertNotEqual(0, result.returncode)

    def test_open_business_endpoint_fails(self):
        result = self.invoke(devices_code=200)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("expected 403", result.stderr)

    def test_redirect_does_not_count_as_denied(self):
        result = self.invoke(devices_code=302)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("HTTP 302", result.stderr)

    def test_non_loopback_and_invalid_ports_cannot_be_used(self):
        for arg in ("http://example.com", "0", "80", "65536", "1; echo unsafe"):
            with self.subTest(port=arg):
                result = subprocess.run(
                    ["bash", str(SCRIPT), arg], text=True,
                    capture_output=True, timeout=5, check=False,
                )
                self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
