#!/usr/bin/env python3
"""Run TWO genuine RustDesk 1.5.1 Linux processes against an OSS hbbs server.

Explicitly GitHub-CI-only, entirely synthetic, Docker network internal:true.
No public ports or Internet egress, no real desktop, input, files or passwords.
A successful run proves only two DISTINCT PEER REGISTRATIONS IN SERVER SQLITE,
not P2P, Relay or remote pixels. Always destroys its OWN ephemeral R2 project.
"""
from __future__ import annotations

import json
import os
import pathlib
import re
import sqlite3
import subprocess
import sys
import tempfile
import time

from verify import EvidenceRejected, check_registered_db, verify_topology


ROOT = pathlib.Path(__file__).resolve().parents[3]
FILE = ROOT / "deploy" / "rustdesk" / "lab" / "compose.yml"
PROJECT = f"lanveriq-r2-{os.environ.get('GITHUB_RUN_ID', 'none')}"
CMD = ["docker", "compose", "--project-name", PROJECT, "-f", str(FILE)]


def invoke(args: list[str], *, input_text: str | None = None, timeout: int = 30,
           accepted: tuple[int, ...] = (0,)) -> str:
    run = subprocess.run(args, input=input_text, capture_output=True, text=True,
                         check=False, timeout=timeout)
    if run.returncode not in accepted:
        # Never reveal service logs which may include transient IDs/keys.
        raise EvidenceRejected(f"Container lab subprocess failed (exit={run.returncode}, command_family={args[0]})")
    return run.stdout.strip()


def compose(*args: str, input_text: str | None = None, timeout: int = 30) -> str:
    return invoke([*CMD, *args], input_text=input_text, timeout=timeout)


def verify_environment() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("LANVERIQ_R2_SYNTHETIC") != "1":
        raise EvidenceRejected("Refuse to create RustDesk peers outside opted-in disposable CI")
    if not re.fullmatch(r"[0-9]{5,20}", os.environ.get("GITHUB_RUN_ID", "")):
        raise EvidenceRejected("No numeric unique GitHub run ID")
    topology = json.loads(compose("config", "--format", "json"))
    verify_topology(topology)


def wait_for_public_key() -> str:
    for _ in range(40):
        try:
            result = compose("exec", "-T", "rd-server", "sh", "-ec",
                             "test -s /data/id_ed25519.pub && cat /data/id_ed25519.pub",
                             timeout=5).strip()
            if re.fullmatch(r"[A-Za-z0-9+/=_-]{32,128}", result):
                return result
        except EvidenceRejected:
            pass
        time.sleep(0.5)
    raise EvidenceRejected("Official OSS server did not prepare public key")


def config_for_peer(key: str) -> str:
    return f"""# Synthetic isolated R2; no public RustDesk rendezvous server.
[options]
custom-rendezvous-server = "rd-server:21116"
relay-server = "rd-server:21117"
key = {json.dumps(key)}
allow-always-software-render = "Y"
"""


def start_peer(name: str, pub_key: str) -> None:
    cfg = config_for_peer(pub_key)
    compose("exec", "-T", name, "sh", "-ec",
            "umask 077; mkdir -p \"$HOME/.config/rustdesk\"; "
            "cat > \"$HOME/.config/rustdesk/RustDesk2.toml\"",
            input_text=cfg)
    # Enforce that configured ID server hostname is ONLY the isolated Docker
    # service, and no default/public relay DNS exists on the internal network.
    compose("exec", "-T", name, "sh", "-ec",
            "test -s \"$HOME/.config/rustdesk/RustDesk2.toml\"")
    # Two independent container network namespaces and synthetic X11 servers.
    compose("exec", "-T", "-d", name, "sh", "-ec",
            "Xvfb :99 -screen 0 1280x800x24 -nolisten tcp >/tmp/xvfb.log 2>&1 & "
            "sleep 2; exec /usr/local/bin/rustdesk --server >/tmp/rustdesk.log 2>&1")


def registration_snapshot(destination: pathlib.Path) -> None:
    # Copy only the synthetic registration SQLite and its WAL (if any); never
    # copy /data/id_ed25519 private key or application/real customer data.
    for suffix in ("", "-wal", "-shm"):
        src = f"rd-server:/data/db_v2.sqlite3{suffix}"
        dst = str(destination) + suffix
        run = subprocess.run([*CMD, "cp", "-q", src, dst],
                             capture_output=True, text=True, timeout=15)
        if suffix == "" and run.returncode:
            raise EvidenceRejected("RustDesk registration SQLite does not exist")
        if suffix != "" and run.returncode and pathlib.Path(dst).exists():
            pathlib.Path(dst).unlink()


def peer_diagnostic(name: str) -> str:
    # Return only boolean categories, no raw client logs/IDs/public keys.
    test = (
        "printf 'dns='; "
        "if getent hosts rd-server >/dev/null 2>&1; then echo resolved; else echo failure; fi; "
        "printf 'cfg='; "
        "if grep -q 'custom-rendezvous-server = \\"rd-server:21116\\"' "
        '\\"$HOME/.config/rustdesk/RustDesk2.toml\\" 2>/dev/null; '
        "then echo loaded_file; else echo missing_or_invalid; fi; "
        "printf 'client_register_log='; "
        "if grep -Eiq 'register.*(id|peer|pk)|rendezvous.*(server|connect)' /tmp/rustdesk.log 2>/dev/null; "
        "then echo seen; else echo absent; fi; "
        "printf 'client_network_error='; "
        "if grep -Eiq 'failed to resolve|name or service not known|network is unreachable|connection refused|server unreachable' /tmp/rustdesk.log 2>/dev/null; "
        "then echo seen; else echo absent; fi; "
        "printf 'client_panic='; "
        "if grep -Eiq 'panic|fatal error|segmentation fault' /tmp/rustdesk.log 2>/dev/null; "
        "then echo seen; else echo absent; fi; "
        "printf 'rustdesk='; "
        "if ps -e -o comm= | grep -qx rustdesk; then echo running; else echo absent; fi; "
        "printf 'xvfb='; "
        "if ps -e -o comm= | grep -qx Xvfb; then echo running; else echo absent; fi; "
        "printf 'log='; "
        "if test -s /tmp/rustdesk.log; then echo nonempty; else echo empty; fi; "
        "printf 'missing_lib='; "
        "if grep -qi 'error while loading shared libraries' /tmp/rustdesk.log 2>/dev/null; "
        "then echo yes; else echo no; fi; "
        "printf 'display_error='; "
        "if grep -Eqi 'cannot open display|Unable to open display|XOpenDisplay|Gtk-WARNING' "
        "/tmp/rustdesk.log 2>/dev/null; then echo yes; else echo no; fi"
    )
    try:
        result = compose("exec", "-T", name, "sh", "-ec", test, timeout=8)
        return result.replace("\n", ",")
    except EvidenceRejected:
        return "unavailable"


def db_diagnostic(file: pathlib.Path) -> str:
    if not file.is_file():
        return "missing"
    try:
        with sqlite3.connect(f"file:{file.resolve()}?mode=ro", uri=True, timeout=1) as db:
            tables = {x[0] for x in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if "peer" not in tables:
                return "no_peer_table"
            total, distinct = db.execute("SELECT COUNT(*), COUNT(DISTINCT id) FROM peer").fetchone()
            return f"rows={total},distinct={distinct}"
    except sqlite3.Error:
        return "unreadable"


def await_two_registrations(folder: pathlib.Path) -> int:
    db = folder / "registered.sqlite3"
    for _ in range(20):
        try:
            registration_snapshot(db)
            return check_registered_db(db)
        except (EvidenceRejected, sqlite3.Error):
            time.sleep(2)
    diagnostics = [f"{name}:{peer_diagnostic(name)}" for name in ("peer-a", "peer-b")]
    try:
        db_ok = compose("exec", "-T", "rd-server", "sh", "-ec",
                        "if test -s /data/db_v2.sqlite3; then echo present; else echo absent; fi", timeout=8)
    except EvidenceRejected:
        db_ok = "unavailable"
    raise EvidenceRejected("No evidence of two unique registered RustDesk peers; "
                           + " | ".join(diagnostics)
                           + f" | hbbs_sqlite={db_ok},snapshot={db_diagnostic(db)}")


def run_lab() -> dict:
    verify_environment()
    # Create only this run's unique container project, never Spark DB/Redis.
    try:
        compose("up", "-d", "--pull", "missing", timeout=140)
        key = wait_for_public_key()
        start_peer("peer-a", key)
        start_peer("peer-b", key)
        with tempfile.TemporaryDirectory(prefix="lvq-r2-") as path:
            count = await_two_registrations(pathlib.Path(path))
        return {
            "verdict": "PASS_R2_01_TWO_REAL_CLIENT_REGISTRATIONS_ON_ISOLATED_DOCKER",
            "real_rustdesk_client_version": "1.5.1",
            "real_oss_hbbs_version": "1.1.16",
            "unique_server_registrations": count,
            "internet_egress_allowed": False,
            "host_ports_published": 0,
            "not_proven": ["real remote framebuffer or keyboard events",
                           "direct P2P/ICE/UDP", "real Relay session",
                           "separate physical LAN hosts", "WAN/VPN access",
                           "Windows runtime", "Lanveriq authorization", "production readiness"],
        }
    finally:
        # This project name is derived from the numeric GitHub run ID. Its
        # isolated volume holds synthetic-only data and must not persist.
        compose("down", "--volumes", "--remove-orphans", timeout=60)


def main() -> int:
    try:
        result = run_lab()
        print(json.dumps(result, sort_keys=True))
        return 0
    except (EvidenceRejected, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        # Only bounded diagnostic strings are shown; never print peer configs,
        # public/private keys or captured desktop contents.
        print(json.dumps({"verdict": "FAIL_R2_01", "reason": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
