#!/usr/bin/env python3
"""Synthetic-only TLS upstream for the manually dispatched Caddy ingress gate.

No real Agent API or secrets. Fail closed on every forwarded identity mismatch.
On failure, expose only the NAMES of failed synthetic checks, never certificate
bytes, fingerprints, header values, private keys or request bodies. The names
let a manual GitHub run distinguish a proxy/header failure from a TLS failure.
"""
import hashlib
import http.server
import json
import os
import ssl


def failed_synthetic_checks(tls_peer, headers, path):
    """Return stable boolean check names, with no secret or identity values."""
    expected_path = f"/api/v1/devices/{os.environ['SYNTHETIC_DEVICE_ID']}/activate"
    expected_fingerprint = os.environ["SYNTHETIC_AGENT_SHA256"]
    expected_proxy = os.environ["SYNTHETIC_PROXY_SHA256"]
    field = "X-Lanveriq-Verified-Client-Fingerprint"
    identities = headers.get_all(field, [])
    # Catch other identity assertions as well, including case variants.
    # A successful proxy must remove the whole incoming header family.
    unexpected_family = [
        name for name in headers.keys()
        if name.lower().startswith("x-lanveriq-verified-client-")
        and name.lower() != field.lower()
    ]
    checks = {
        "proxy_certificate": bool(tls_peer) and
            hashlib.sha256(tls_peer).hexdigest() == expected_proxy,
        "fingerprint_count": len(identities) == 1,
        "fingerprint_value": len(identities) == 1 and
            identities[0] == expected_fingerprint,
        "unexpected_identity_header": not unexpected_family,
        "upstream_host": headers.get("Host") == "agent.lanveriq.internal",
        "forwarded_cert_header": not headers.get_all("X-Forwarded-Client-Cert", []),
        "ssl_client_cert_header": not headers.get_all("SSL-Client-Cert", []),
        "request_path": path == expected_path,
    }
    return [name for name, accepted in checks.items() if not accepted]


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        failures = failed_synthetic_checks(
            self.connection.getpeercert(binary_form=True),
            self.headers,
            self.path,
        )
        verified = not failures
        payload = {"synthetic_upstream_verified": verified}
        if failures:
            # Only machine-chosen check labels; not raw request data.
            payload["failed_checks"] = failures
        body = json.dumps(payload).encode()
        self.send_response(200 if verified else 403)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # No certificate/header values or user/employee information in logs.
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
