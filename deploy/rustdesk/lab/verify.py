#!/usr/bin/env python3
"""Fail-closed structural and SQLite proof checks for R2-01 synthetic peers.

This module NEVER creates containers, reaches the Internet or modifies DBs.
It verifies the actual rendered Compose topology and the hbbs registration
rows extracted from an ephemeral all-synthetic server, not packet transport,
credentials, P2P, screen pixels or any WAN connectivity.
"""
from __future__ import annotations

import json
import pathlib
import re
import sqlite3
import sys


SERVER_IMAGE = (
    "ghcr.io/rustdesk/rustdesk-server-s6:1.1.16@"
    "sha256:5974cd791a5aca6fbf01a167b37e7427db1d136e95b2710c33a34fdc9fea724c"
)
CLIENT_IMAGE = "lanveriq-rustdesk-client-1.5.1:r2-local-only"
SERVICES = {"rd-server", "peer-a", "peer-b"}
NETWORK = "r2_internal"


class EvidenceRejected(RuntimeError):
    pass


def verify_topology(compose: dict) -> dict:
    if not isinstance(compose, dict):
        raise EvidenceRejected("Expected Docker Compose rendered JSON")
    services = compose.get("services")
    if not isinstance(services, dict) or set(services) != SERVICES:
        raise EvidenceRejected("Require one official RustDesk OSS server and exactly two clients")
    networks = compose.get("networks")
    if not isinstance(networks, dict) or set(networks) != {NETWORK}:
        raise EvidenceRejected("Unexpected or shared Docker networks")
    if networks[NETWORK].get("internal") is not True or networks[NETWORK].get("external"):
        raise EvidenceRejected("R2 peers require an isolated network without Internet egress")
    for name, service in services.items():
        if service.get("ports") or service.get("network_mode") or service.get("privileged"):
            raise EvidenceRejected("No external published ports/host network/privileged containers")
        if service.get("devices") or service.get("cap_add"):
            raise EvidenceRejected("No host devices/extra Linux capabilities")
        joined = service.get("networks")
        if isinstance(joined, dict):
            keys = set(joined)
        elif isinstance(joined, list):
            keys = set(joined)
        else:
            raise EvidenceRejected("Invalid Docker networks shape")
        if keys != {NETWORK}:
            raise EvidenceRejected("Each service must live solely on isolated network")
        if service.get("restart", "no") not in ("no", "none"):
            raise EvidenceRejected("Synthetic R2 containers must not survive host reboot")
    server = services["rd-server"]
    if server.get("image") != SERVER_IMAGE:
        raise EvidenceRejected("RustDesk OSS server digest must be pinned")
    env = server.get("environment")
    if not isinstance(env, dict) or env != {
        "RELAY": "rd-server:21117",
        "ENCRYPTED_ONLY": "1",
        "ALWAYS_USE_RELAY": "N",
        "RUST_LOG": "info",
    }:
        raise EvidenceRejected("R2 server relay host and encryption must be fixed")
    mounts = server.get("volumes")
    if not isinstance(mounts, list) or len(mounts) != 1:
        raise EvidenceRejected("Use one private named server data volume")
    m = mounts[0]
    if m.get("type") != "volume" or m.get("target") != "/data":
        raise EvidenceRejected("No host path mounts or shared Lanveriq database volume")
    for name in ("peer-a", "peer-b"):
        client = services[name]
        if client.get("image") != CLIENT_IMAGE or client.get("pull_policy") != "never":
            raise EvidenceRejected("Only the verified locally built stable RustDesk client can run")
        if client.get("cap_drop") != ["ALL"]:
            raise EvidenceRejected("Client must drop all optional capabilities")
        if "no-new-privileges:true" not in client.get("security_opt", []):
            raise EvidenceRejected("Client must deny privilege escalation")
        if client.get("volumes"):
            raise EvidenceRejected("No host or shared secret mounts in clients")
    return {"topology": "PASS_NO_EGRESS_NO_HOST_PORTS", "clients": 2,
            "server_digest_pinned": True, "internet_reachable": False}


def check_registered_db(file: pathlib.Path) -> int:
    if not file.is_file() or file.is_symlink():
        raise EvidenceRejected("Missing or untrusted SQLite snapshot")
    try:
        with sqlite3.connect(f"file:{file.resolve()}?mode=ro", uri=True, timeout=1) as db:
            rows = db.execute("SELECT id FROM peer WHERE id IS NOT NULL").fetchall()
    except sqlite3.Error as exc:
        raise EvidenceRejected("Invalid or unreadable hbbs registration table") from exc
    ids = {row[0] for row in rows if isinstance(row[0], str) and row[0]}
    if len(ids) != 2 or len(rows) != 2:
        raise EvidenceRejected("R2-01 requires exactly two distinct server-side registered peer IDs")
    return 2


if __name__ == "__main__":
    print("This is a library of read-only R2 proof checks; use lab/run.py in the isolated CI only.", file=sys.stderr)
    raise SystemExit(2)
