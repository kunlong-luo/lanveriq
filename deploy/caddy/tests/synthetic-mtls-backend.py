#!/usr/bin/env python3
"""Synthetic-only TLS upstream for the manually dispatched Caddy ingress gate.

This is NOT a Spring replacement, Agent API, or authentication service. It
proves that Caddy presents the separately pinned upstream TLS client
certificate, strips hostile downstream headers, and forwards the SHA-256 of a
real synthetic Agent leaf over its HTTPS connection. Test data only.
"""
import hashlib
import http.server
import json
import os
import ssl


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        tls_peer = self.connection.getpeercert(binary_form=True)
        proxy_digest = hashlib.sha256(tls_peer or b"").hexdigest()
        identity_headers = self.headers.get_all(
            "X-Lanveriq-Verified-Client-Fingerprint", []
        )
        expected_path = f"/api/v1/devices/{os.environ['SYNTHETIC_DEVICE_ID']}/activate"
        verified = (
            proxy_digest == os.environ["SYNTHETIC_PROXY_SHA256"]
            and len(identity_headers) == 1
            and identity_headers[0] == os.environ["SYNTHETIC_AGENT_SHA256"]
            and self.headers.get("Host") == "agent.lanveriq.internal"
            and not self.headers.get_all("X-Forwarded-Client-Cert", [])
            and not self.headers.get_all("SSL-Client-Cert", [])
            and self.path == expected_path
        )
        body = json.dumps({"synthetic_upstream_verified": verified}).encode()
        self.send_response(200 if verified else 403)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Do not persist headers, certs, employee data or authentication input.
        pass


def main():
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_3
    context.verify_mode = ssl.CERT_REQUIRED
    context.load_cert_chain(
        os.environ["SYNTHETIC_BACKEND_CERT"], os.environ["SYNTHETIC_BACKEND_KEY"]
    )
    context.load_verify_locations(cafile=os.environ["SYNTHETIC_PROXY_CA"])
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 8443), Handler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
