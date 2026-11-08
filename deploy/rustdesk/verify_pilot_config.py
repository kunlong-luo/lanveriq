#!/usr/bin/env python3
"""Fail-closed static guard for an approved, single-host RustDesk OSS pilot.

Input is rendered JSON from `docker compose -f compose.pilot.yml config --format json`.
This checker is offline/read-only: it never runs Docker, binds a port or
requests credentials. Passing does NOT prove that Docker enforces a firewall,
that a relay works or that any external endpoint is authorized.
"""
from __future__ import annotations

import argparse
import ipaddress
import json
import re
import sys


PILOT_IMAGE = (
    "ghcr.io/rustdesk/rustdesk-server-s6:1.1.16@"
    "sha256:5974cd791a5aca6fbf01a167b37e7427db1d136e95b2710c33a34fdc9fea724c"
)
EXPECTED_PORTS = {(21115, "tcp"), (21116, "tcp"), (21116, "udp"), (21117, "tcp")}
PILOT_ENTRYPOINT = """umask 077
if [ ! -e /data/id_ed25519 ] && [ ! -e /data/id_ed25519.pub ]; then
  /usr/bin/rustdesk-utils genkeypair | awk '
    /^Public Key:/ { pub=$3 }
    /^Secret Key:/ { priv=$3 }
    END {
      if (pub == "" || priv == "") exit 1
      print pub > "/data/id_ed25519.pub"
      print priv > "/data/id_ed25519"
    }
  '
fi
test -s /data/id_ed25519
test -s /data/id_ed25519.pub
chmod 0600 /data/id_ed25519 /data/id_ed25519.pub
exec /init
"""
SERVICE = "rustdesk-pilot"


class ConfigurationRejected(ValueError):
    pass


def approved_ip(raw: str) -> str:
    try:
        ip = ipaddress.ip_address(raw)
    except ValueError as exc:
        raise ConfigurationRejected("Bind must be a literal IPv4 address") from exc
    # RFC 6598 shared address space is used by some approved mesh VPNs
    # (e.g. 100.64.0.0/10). It is not publicly routable, but an operator
    # still must verify that it is an actual bound host interface with ACLs.
    overlay_address = ip in ipaddress.ip_network("100.64.0.0/10")
    if ip.version != 4 or not (ip.is_loopback or (ip.is_private and not ip.is_link_local) or overlay_address):
        raise ConfigurationRejected("Bind must be approved loopback/private/CGNAT-overlay IPv4, never public/wildcard")
    if ip.is_unspecified or ip.is_multicast or ip.is_reserved:
        raise ConfigurationRejected("Unsafe bind IP")
    return str(ip)


def approved_relay(raw: str, bind: str) -> str:
    if not isinstance(raw, str) or not raw or raw != raw.strip():
        raise ConfigurationRejected("Missing relay host")
    if raw == "localhost" and ipaddress.ip_address(bind).is_loopback:
        return raw
    if raw == "localhost":
        raise ConfigurationRejected("LAN/VPN pilot must advertise a real reachable DNS host")
    if (raw.lower().endswith((".example.com", ".example.org", ".example.net", ".invalid", ".test", ".internal"))
            or raw.lower() in {"example.com", "example.org", "example.net"}):
        raise ConfigurationRejected("Placeholder DNS names cannot be used for pilot launch")
    if raw.lower().startswith(("http://", "https://")) or "/" in raw or ":" in raw or raw.endswith("."):
        raise ConfigurationRejected("Relay must be a DNS hostname only, no scheme/port/path")
    labels = raw.split(".")
    if len(labels) < 2 or len(raw) > 253:
        raise ConfigurationRejected("Relay must be an owned FQDN")
    if not all(re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", part) for part in labels):
        raise ConfigurationRejected("Invalid relay hostname")
    return raw


def check_contract(document: dict, bind_ip: str, relay_host: str) -> dict:
    bind = approved_ip(bind_ip)
    relay = approved_relay(relay_host, bind)
    if not isinstance(document, dict):
        raise ConfigurationRejected("Expected Compose config JSON object")
    if document.get("name") != "lanveriq-rustdesk-pilot":
        raise ConfigurationRejected("Pilot Compose project name mismatch")
    services = document.get("services")
    if not isinstance(services, dict) or set(services) != {SERVICE}:
        raise ConfigurationRejected("Pilot must contain exactly one RustDesk service")
    service = services[SERVICE]
    if service.get("image") != PILOT_IMAGE:
        raise ConfigurationRejected("RustDesk must be digest-pinned to the reviewed OSS pilot image")
    entrypoint = service.get("entrypoint")
    # Compose may normalize a YAML literal's terminal newline. Only that
    # non-executable formatting difference is tolerated, not shell changes.
    if not (
        isinstance(entrypoint, list)
        and len(entrypoint) == 3
        and entrypoint[:2] == ["/bin/sh", "-ec"]
        and isinstance(entrypoint[2], str)
        and entrypoint[2].rstrip("\n") == PILOT_ENTRYPOINT.rstrip("\n")
    ):
        actual = entrypoint[2] if isinstance(entrypoint, list) and len(entrypoint) == 3 and isinstance(entrypoint[2], str) else ""
        first = next((i for i, (a, b) in enumerate(zip(actual, PILOT_ENTRYPOINT)) if a != b),
                     min(len(actual), len(PILOT_ENTRYPOINT)))
        # Entrypoint source contains NO passwords or device material, only static
        # reviewed shell text. Report a bounded discrepancy for CI debugging.
        raise ConfigurationRejected(
            f"Reviewed key bootstrap mismatch at offset={first}; "
            f"rendered length={len(actual)}, expected={len(PILOT_ENTRYPOINT)}; "
            f"actual={actual[max(0, first-8):first+18]!r}; "
            f"expected={PILOT_ENTRYPOINT[max(0, first-8):first+18]!r}"
        )
    if service.get("restart", "no") not in ("no", "none"):
        raise ConfigurationRejected("Pilot service cannot auto-restart on reboot")
    if set(service.get("profiles", [])) != {"pilot"}:
        raise ConfigurationRejected("Pilot must require explicit profile opt-in")
    if service.get("privileged") or service.get("network_mode") == "host":
        raise ConfigurationRejected("Host/privileged mode not permitted for isolated pilot")
    if service.get("cap_add") or service.get("devices"):
        raise ConfigurationRejected("Pilot may not request extra capabilities or host devices")
    environment = service.get("environment", {})
    if not isinstance(environment, dict):
        raise ConfigurationRejected("Expected rendered environment dictionary")
    expected_env = {
        "RELAY": relay,
        "ENCRYPTED_ONLY": "1",
        "ALWAYS_USE_RELAY": "N",
        "RUST_LOG": "info",
    }
    if environment != expected_env:
        raise ConfigurationRejected("Unexpected relay, encryption or process environment")
    entries = service.get("ports")
    if not isinstance(entries, list) or len(entries) != 4:
        raise ConfigurationRejected("Exactly four published native RustDesk ports are required")
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ConfigurationRejected("Port entries must be expanded Compose objects")
        try:
            target = int(entry.get("target"))
            published = int(entry.get("published"))
        except (TypeError, ValueError) as exc:
            raise ConfigurationRejected("Missing/invalid host or container port") from exc
        protocol = entry.get("protocol", "tcp")
        if published != target or (target, protocol) not in EXPECTED_PORTS:
            raise ConfigurationRejected("Unexpected/missing RustDesk port or remapped endpoint")
        if entry.get("host_ip") != bind:
            raise ConfigurationRejected("Published host IP is not the approved interface")
        seen.add((target, protocol))
    if seen != EXPECTED_PORTS:
        raise ConfigurationRejected("Missing native port or repeated/unsafe published port")
    mounts = service.get("volumes")
    if not isinstance(mounts, list) or len(mounts) != 1:
        raise ConfigurationRejected("Exactly one dedicated persistent data mount is permitted")
    mount = mounts[0]
    if (mount.get("type") != "volume" or mount.get("target") != "/data"
            or mount.get("read_only", False)):
        raise ConfigurationRejected("Only a private named /data volume is permitted")
    source = mount.get("source", "")
    if not source.endswith("rustdesk_pilot_state"):
        raise ConfigurationRejected("Unrecognized/shared RustDesk storage volume")
    net = service.get("networks")
    if not isinstance(net, dict) or set(net) != {"rustdesk_pilot_only"}:
        raise ConfigurationRejected("No shared application/DB network permitted")
    declared = document.get("networks", {})
    if not isinstance(declared, dict) or set(declared) != {"rustdesk_pilot_only"}:
        raise ConfigurationRejected("Unrecognized network declarations")
    if declared["rustdesk_pilot_only"].get("external"):
        raise ConfigurationRejected("Never attach pilot to an existing network")
    return {
        "result": "PASS_STATIC_CONFIG_ONLY",
        "bind_ip": bind,
        "relay_host": relay,
        "native_port_count": len(seen),
        "websocket_ports_exposed": False,
        "network_reachability_proven": False,
        "client_identity_and_relay_proven": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind-ip", required=True)
    parser.add_argument("--relay-host", required=True)
    args = parser.parse_args(argv)
    try:
        document = json.load(sys.stdin)
        print(json.dumps(check_contract(document, args.bind_ip, args.relay_host), indent=2))
        return 0
    except (ValueError, TypeError, json.JSONDecodeError) as exc:
        print(json.dumps({"result": "FAIL_STATIC_CONFIG", "reason": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
