#!/usr/bin/env python3
"""NXT-10: test the disposable Next.js listener without Caddy or credentials.

The random internal edge proof is read only as a presence/format condition,
never echoed or sent. All HTTP probes must lack valid edge authority.
"""
import http.client
import os
import re
import socket
import sys

PORT = 3100
PATHS = (
    "/synthetic-console-route/overview",
    "/synthetic-console-route/devices",
    "/synthetic-console-route/monitoring",
)


def require(condition, label):
    if not condition:
        raise RuntimeError(label)


def probe(path, headers):
    conn = http.client.HTTPConnection("127.0.0.1", PORT, timeout=6)
    try:
        conn.request("GET", path, headers={
            "Host": "console.lanveriq.internal", **headers,
        })
        response = conn.getresponse()
        status = response.status
        body = response.read(128 * 1024)
        # Explicitly exclude the candidate's authorized DOM from 404 text.
        require(b"data-route-state=" not in body,
                "direct_origin_must_not_render_authorized_component")
        return status
    finally:
        conn.close()


def nonloopback_address():
    # Connecting a UDP socket does not transmit or query an external service.
    # It determines the local outgoing interface address for an RFC5737
    # documentation destination without actually sending a packet.
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 9))
        address = sock.getsockname()[0]
        require(address not in ("127.0.0.1", "0.0.0.0", ""),
                "runner_nonloopback_address_required")
        return address
    finally:
        sock.close()


def main():
    require(os.environ.get("GITHUB_ACTIONS") == "true" and
            os.environ.get("LANVERIQ_SYNTHETIC_NEXTJS_PROBE") == "1",
            "disposable_ci_only")
    secret = os.environ.get("LANVERIQ_SYNTHETIC_EDGE_SECRET", "")
    require(re.fullmatch(r"[0-9a-f]{64}", secret) is not None,
            "random_edge_proof_missing")
    # Do not store or transmit secret or authorize direct access in this test.
    for path in PATHS:
        require(probe(path, {}) == 404, "direct_origin_anonymous_must_404")
        require(probe(path, {
            "Cookie": "__Host-lanveriq-session=synthetic-forged-session",
        }) == 404, "direct_origin_forged_cookie_must_404")
        require(probe(path, {
            "X-Lanveriq-Synthetic-Edge-Token": "0" * 64,
        }) == 404, "direct_origin_forged_edge_token_must_404")
        require(probe(path + "?__rsc=synthetic-direct", {
            "RSC": "1", "Next-Router-Prefetch": "1",
            "Cookie": "__Host-lanveriq-session=synthetic-forged-session",
        }) == 404, "direct_origin_rsc_prefetch_must_404")

    address = nonloopback_address()
    try:
        connection = socket.create_connection((address, PORT), timeout=3)
    except OSError:
        pass
    else:
        connection.close()
        raise RuntimeError("nextjs_nonloopback_tcp_must_be_unreachable")

    print("SYNTHETIC NEXTJS ORIGIN ISOLATION PASS: direct loopback HTML/"
          "RSC and forged Cookie/edge proof denied; nonloopback TCP closed.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Avoid printing raw network error details, environment or secrets.
        print("FAIL: disposable Next.js origin containment security gate",
              file=sys.stderr)
        raise SystemExit(1) from None
