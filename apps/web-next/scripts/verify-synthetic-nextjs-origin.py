#!/usr/bin/env python3
"""NXT-10: test the disposable Next.js listener without Caddy or credentials.

The random internal edge proof is read only as a presence/format condition,
never echoed or sent. All HTTP probes must lack valid edge authority.
"""
import hashlib
import http.client
import os
from pathlib import Path
import re
import socket
import sys

PORT = 3100
PATHS = (
    "/synthetic-console-route/overview",
    "/synthetic-console-route/devices",
    "/synthetic-console-route/monitoring",
)


class OriginGateFailure(Exception):
    """Fixed non-secret label only; never include HTTP headers or bodies."""


def require(condition, label):
    if not condition:
        raise OriginGateFailure(label)


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


def select_public_chunk(static_root):
    """Select an actual small immutable JS asset, not an HTML/RSC response."""
    require(static_root.is_dir(), "nextjs_static_directory_missing")
    candidates = sorted(
        p for p in static_root.rglob("*.js")
        if p.is_file() and not p.is_symlink()
        and 0 < p.stat().st_size <= 2 * 1024 * 1024
    )
    require(bool(candidates), "nextjs_public_js_chunk_missing")
    return candidates[0]


def request_public_chunk(path, headers):
    """Fetch a public immutable asset; never echo any response content."""
    conn = http.client.HTTPConnection("127.0.0.1", PORT, timeout=6)
    try:
        conn.request("GET", path, headers={
            "Host": "console.lanveriq.internal",
            "Accept-Encoding": "identity",
            **headers,
        })
        response = conn.getresponse()
        body = response.read(2 * 1024 * 1024 + 1)
        require(len(body) <= 2 * 1024 * 1024,
                "public_js_chunk_exceeds_probe_limit")
        return (
            response.status,
            response.getheader("Content-Type", "").lower(),
            response.getheader("Cache-Control", "").lower(),
            response.getheader("X-Content-Type-Options", "").lower(),
            hashlib.sha256(body).digest(),
        )
    finally:
        conn.close()


def verify_public_static_boundary(secret):
    # Unlike session-gated HTML/RSC, content-addressed Next.js JS/CSS must
    # remain public to render the disabled login page. They must never
    # embed the internal per-run edge proof or vary with a forged identity.
    static_root = Path(__file__).resolve().parents[1] / ".next/static"
    chunk = select_public_chunk(static_root)
    secret_bytes = secret.encode("ascii")
    for asset in static_root.rglob("*"):
        if asset.is_file():
            require(not asset.is_symlink(),
                    "nextjs_static_symlink_forbidden")
            # Inspect the built public artifact, not server-only .next files.
            require(secret_bytes not in asset.read_bytes(),
                    "public_static_contains_private_edge_proof")
    url = "/_next/static/" + chunk.relative_to(static_root).as_posix()
    expected_hash = hashlib.sha256(chunk.read_bytes()).digest()
    anonymous = request_public_chunk(url, {})
    forged = request_public_chunk(url, {
        "Cookie": "__Host-lanveriq-session=synthetic-forged-session",
        "X-Lanveriq-Synthetic-Edge-Token": "0" * 64,
    })
    require(anonymous[0] == 200 and forged[0] == 200,
            "public_static_must_be_fetchable_without_session")
    require("javascript" in anonymous[1],
            "public_static_must_have_javascript_mime")
    require("public" in anonymous[2] and "immutable" in anonymous[2],
            "public_static_must_have_immutable_cache")
    require(anonymous[3] == "nosniff",
            "public_static_must_disable_content_sniffing")
    require(anonymous[4] == expected_hash and forged[4] == expected_hash,
            "public_static_must_not_vary_by_claimed_identity")
    require(anonymous[:4] == forged[:4],
            "public_static_headers_must_not_vary_by_claimed_identity")


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

    verify_public_static_boundary(secret)

    address = nonloopback_address()
    try:
        connection = socket.create_connection((address, PORT), timeout=3)
    except OSError:
        pass
    else:
        connection.close()
        raise OriginGateFailure("nextjs_nonloopback_tcp_must_be_unreachable")

    print("SYNTHETIC NEXTJS ORIGIN ISOLATION PASS: direct loopback HTML/"
          "RSC and forged Cookie/edge proof denied; nonloopback TCP closed.")


if __name__ == "__main__":
    try:
        main()
    except OriginGateFailure as exc:
        # require() only accepts fixed labels authored in this file.
        # Never expose the upstream exception, response body or secret.
        print("FAIL: disposable Next.js origin containment: " + str(exc),
              file=sys.stderr)
        raise SystemExit(1) from None
    except Exception:
        print("FAIL: disposable Next.js origin runtime/connectivity",
              file=sys.stderr)
        raise SystemExit(1) from None
