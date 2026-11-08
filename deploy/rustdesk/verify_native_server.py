#!/usr/bin/env python3
"""Real RustDesk OSS server control-plane test in a disposable loopback CI pilot.

Exercises the official hbbs loopback console, relay-mode readback, rejection of
a non-console host-forwarded request, and persistence of the server public key
across restart. Does NOT simulate a RustDesk peer or claim P2P/relay sessions.
Never run this script on a deployed LAN/WAN or production server.
"""
from __future__ import annotations

import json
import os
import re
import socket
import subprocess
import sys
import time
from pathlib import Path

from verify_pilot_config import PILOT_IMAGE, check_contract

ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy" / "rustdesk" / "compose.pilot.yml"
COMPOSE = ("docker", "compose", "-f", str(COMPOSE_FILE), "--profile", "pilot")
SERVICE = "rustdesk-pilot"
MODE_PATTERN = re.compile(r"^ALWAYS_USE_RELAY:\s*(true|false)\s*$", re.IGNORECASE)


class NativeServerError(RuntimeError):
    pass


def run(args: list[str], *, timeout: float = 20.0) -> str:
    command = subprocess.run(
        args, text=True, capture_output=True, check=False, timeout=timeout
    )
    if command.returncode:
        # Don't forward arbitrary container output: it might contain secrets.
        raise NativeServerError(f"RustDesk command failed (exit={command.returncode})")
    return command.stdout.strip()


def compose_exec(script: str, *, timeout: float = 20.0) -> str:
    return run([*COMPOSE, "exec", "-T", SERVICE, "sh", "-ec", script], timeout=timeout)


def parse_mode(response: str) -> bool:
    match = MODE_PATTERN.fullmatch(response.strip())
    if not match:
        raise NativeServerError("hbbs did not return an unambiguous relay mode")
    return match.group(1).lower() == "true"


def console(command: str) -> str:
    if command not in {"aur", "aur Y", "aur N"}:
        raise NativeServerError("Unsupported hbbs console request")
    # BusyBox nc is inside the container network namespace, so the peer IP
    # is real loopback for the server's built-in administrative console.
    return compose_exec(f"printf '%s' '{command}' | nc -w 2 127.0.0.1 21115")


def mode() -> bool:
    return parse_mode(console("aur"))


def await_mode(expected: bool, retries: int = 12) -> None:
    for _ in range(retries):
        try:
            if mode() is expected:
                return
        except NativeServerError:
            pass
        time.sleep(0.3)
    raise NativeServerError(f"hbbs mode did not reach expected state {expected}")


def public_key_hash() -> str:
    # Hash public bytes inside the container; private key is never read or
    # supplied as a command-line argument or logged.
    value = compose_exec("test -s /data/id_ed25519 && test -s /data/id_ed25519.pub && sha256sum /data/id_ed25519.pub")
    candidate = value.split(" ", 1)[0]
    if not re.fullmatch(r"[a-f0-9]{64}", candidate):
        raise NativeServerError("Invalid public key digest")
    return candidate


def assert_private_key_permissions() -> None:
    perm = compose_exec("stat -c '%a' /data/id_ed25519")
    if perm not in ("600", "400"):
        raise NativeServerError("Private key file is not owner-readable-only")


def host_forwarded_console_is_denied() -> None:
    # Host's 127.0.0.1:21115 maps through Docker bridge to container.
    # It must NOT gain the container's loopback-admin authority.
    # Never attempt this against any external host.
    with socket.create_connection(("127.0.0.1", 21115), timeout=2) as sock:
        sock.settimeout(1)
        sock.sendall(b"aur Y")
        try:
            sock.shutdown(socket.SHUT_WR)
        except OSError:
            pass
        try:
            sock.recv(100)
        except (OSError, TimeoutError):
            pass
    time.sleep(0.3)
    await_mode(False)


def check_isolated_ci_only() -> None:
    if not (os.environ.get("GITHUB_ACTIONS") == "true"
            and os.environ.get("LANVERIQ_RUSTDESK_CI_BEHAVIOR_TEST") == "1"):
        raise NativeServerError("Refusing to modify RustDesk state outside explicitly opted-in GitHub CI")
    rendered = json.loads(run([*COMPOSE, "config", "--format", "json"]))
    checked = check_contract(rendered, "127.0.0.1", "localhost")
    if (checked["bind_ip"] != "127.0.0.1"
            or checked["relay_host"] != "localhost"
            or rendered["services"][SERVICE]["image"] != PILOT_IMAGE):
        raise NativeServerError("Only the pinned, localhost-only test service may be used")


def main() -> int:
    before: str | None = None
    failures: list[str] = []
    try:
        check_isolated_ci_only()
        before = public_key_hash()
        assert_private_key_permissions()
        await_mode(False)
        host_forwarded_console_is_denied()
        console("aur Y")
        await_mode(True)
        console("aur N")
        await_mode(False)
    except (NativeServerError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        failures.append(str(exc))
    finally:
        if before is not None:
            # A mode toggle in upstream 1.1.16 can alter in-memory relay
            # server lists as a side effect, so always restart to return
            # to the pinned environment configuration, even on failure.
            try:
                run([*COMPOSE, "restart", SERVICE], timeout=50)
                for _ in range(30):
                    try:
                        after = public_key_hash()
                        await_mode(False, retries=1)
                        if after != before:
                            raise NativeServerError("RustDesk public key changed across restart")
                        assert_private_key_permissions()
                        break
                    except NativeServerError as exc:
                        if "changed across restart" in str(exc):
                            raise
                        time.sleep(1)
                else:
                    raise NativeServerError("RustDesk server did not recover after restart")
            except (NativeServerError, OSError, subprocess.TimeoutExpired) as exc:
                failures.append(f"restart/restore: {exc}")
    if failures:
        # Only static, bounded error strings; no private key data.
        print(json.dumps({"result": "FAIL_NATIVE_SERVER_CONTROL", "reasons": failures}), file=sys.stderr)
        return 2
    print(json.dumps({
        "result": "PASS_NATIVE_HBBS_CONTROL_AND_KEY_PERSISTENCE",
        "image_digest_pinned": True,
        "host_forwarded_admin_command_denied": True,
        "relay_mode_readback": ["false", "true", "false", "false_after_restart"],
        "public_key_unchanged_across_restart": True,
        "private_key_permissions_checked": True,
        "not_proven": ["RustDesk peer authentication", "remote screen/input",
                       "UDP/ICE/P2P", "actual relay session", "LAN/WAN/VPN reachability"],
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
