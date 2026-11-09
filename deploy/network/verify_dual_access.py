#!/usr/bin/env python3
"""Read-only LAN/WAN HTTPS and optional RustDesk TCP reachability probe.

This deliberately never presents credentials, disables TLS verification or
mutates DNS/firewalls. A passing result is a single vantage-point network
observation, NOT remote access, endpoint identity, RBAC or production approval.
"""
from __future__ import annotations

import argparse
import http.client
import ipaddress
import json
import socket
import ssl
import sys
from datetime import datetime, timezone
from urllib.parse import urljoin, urlsplit


class ProbeError(RuntimeError):
    """A network or security acceptance check failed."""


def validate_origin(raw: str):
    value = urlsplit(raw)
    if (
        value.scheme != "https"
        or not value.hostname
        or value.username is not None
        or value.password is not None
        or value.path not in ("", "/")
        or value.query
        or value.fragment
    ):
        raise ProbeError("Console origin must be an HTTPS host with no credentials, path or query")
    try:
        _ = value.port
    except ValueError as exc:
        raise ProbeError("Invalid HTTPS port") from exc
    return value


def validate_redirect(origin: str, location: str | None) -> None:
    if not location:
        raise ProbeError("Redirect missing Location")
    dest = urlsplit(urljoin(origin.rstrip("/") + "/", location))
    start = urlsplit(origin)
    if dest.scheme != "https" or dest.netloc.casefold() != start.netloc.casefold():
        raise ProbeError("Redirect leaves the original HTTPS Console origin")


def validate_peer(peer: str, cidrs: list[str]) -> None:
    if not cidrs:
        return
    address = ipaddress.ip_address(peer)
    if not any(address in ipaddress.ip_network(item, strict=False) for item in cidrs):
        raise ProbeError("Connected peer IP is outside the explicitly approved network ranges")


def validate_status(path: str, status: int) -> None:
    if path == "/api/v1/devices":
        # This is intentionally anonymous: a positive response is an AUTH FAILURE.
        if status not in (401, 403):
            raise ProbeError("Anonymous API must be denied with 401/403; got HTTP %s" % status)
    elif status not in (200, 301, 302, 303, 307, 308, 401, 403):
        raise ProbeError("Console HTML is not reachable or explicitly protected; got HTTP %s" % status)


def probe_https(origin: str, path: str, timeout: float, cidrs: list[str]):
    parsed = validate_origin(origin)
    host = parsed.hostname
    assert host is not None
    conn = http.client.HTTPSConnection(
        host=host,
        port=parsed.port or 443,
        timeout=timeout,
        context=ssl.create_default_context(),
    )
    try:
        # HTTPSConnection validates BOTH the CA trust chain and SNI hostname.
        conn.connect()
        if conn.sock is None:
            raise ProbeError("No verified TLS connection established")
        peer = conn.sock.getpeername()[0]
        validate_peer(peer, cidrs)
        conn.request("GET", path, headers={"Accept": "text/html,application/json", "Connection": "close"})
        response = conn.getresponse()
        status = response.status
        if status in (301, 302, 303, 307, 308):
            validate_redirect(origin, response.getheader("Location"))
        validate_status(path, status)
        return {"path": path, "http_status": status, "tls_verified": True, "connected_peer": peer}
    except (OSError, ssl.SSLError, http.client.HTTPException) as exc:
        raise ProbeError("TLS/HTTPS failed for %s: %s" % (path, type(exc).__name__)) from exc
    finally:
        conn.close()


def probe_tcp(host: str, port: int, timeout: float):
    # Reachability only; no RustDesk handshake, registration or peer access.
    try:
        with socket.create_connection((host, port), timeout=timeout) as sock:
            peer = sock.getpeername()[0]
            return {"port": port, "tcp_reachable": True, "connected_peer": peer}
    except OSError as exc:
        raise ProbeError("RustDesk TCP %s is unreachable: %s" % (port, type(exc).__name__)) from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--console-origin", required=True, help="Owned HTTPS origin, e.g. https://console.example.org")
    parser.add_argument("--vantage", choices=("lan", "wan"), required=True, help="Operator-declared physical network vantage")
    parser.add_argument("--expect-peer-cidr", action="append", default=[], help="Approved peer range; repeat for IPv4/IPv6")
    parser.add_argument("--rustdesk-host", help="Optional separate RustDesk rendezvous/relay host")
    parser.add_argument("--timeout", type=float, default=5.0)
    args = parser.parse_args(argv)
    if args.timeout <= 0 or args.timeout > 30:
        parser.error("--timeout must be >0 and <=30 seconds")
    try:
        validate_origin(args.console_origin)
        for cidr in args.expect_peer_cidr:
            ipaddress.ip_network(cidr, strict=False)
        result = {
            "checked_at_utc": datetime.now(timezone.utc).isoformat(),
            "vantage_declared_by_operator": args.vantage,
            "vantage_independently_verified": False,
            "console_origin": args.console_origin,
            "checks": [
                probe_https(args.console_origin, "/login", args.timeout, args.expect_peer_cidr),
                probe_https(args.console_origin, "/api/v1/devices", args.timeout, args.expect_peer_cidr),
            ],
        }
        if args.rustdesk_host:
            if "://" in args.rustdesk_host or "/" in args.rustdesk_host:
                raise ProbeError("--rustdesk-host must be a plain hostname, not a URL")
            result["rustdesk_tcp_checks"] = [
                probe_tcp(args.rustdesk_host, 21116, args.timeout),
                probe_tcp(args.rustdesk_host, 21117, args.timeout),
            ]
        result["verdict"] = "PASS_NETWORK_ONLY"
        result["not_proven"] = [
            "true external vantage", "UDP/ICE/P2P", "Relay functionality", "RustDesk user authorization",
            "Agent client-mTLS", "real admin login/RBAC", "device privacy authorization", "production readiness",
        ]
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (ProbeError, ValueError) as exc:
        print(json.dumps({"verdict": "FAIL", "vantage_declared_by_operator": args.vantage, "reason": str(exc)},
                         ensure_ascii=False), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
