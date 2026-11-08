#!/usr/bin/env python3
"""Isolated RustDesk Server OSS startup smoke. Never connects to a real endpoint.

This is deliberately not a remote-desktop session, protocol-authentication,
LAN/WAN traversal, or production-authorization acceptance test.
Run inside a Docker container with --network none, non-root and read-only rootfs.
"""
from __future__ import annotations

import os
import signal
import socket
import subprocess
import tempfile
import time
from pathlib import Path

HBBS = Path(os.environ.get("LANVERIQ_HBBS", "/opt/rustdesk/hbbs"))
HBBR = Path(os.environ.get("LANVERIQ_HBBR", "/opt/rustdesk/hbbr"))
TIMEOUT_S = 25.0


def require_tcp_port(port: int, process: subprocess.Popen[bytes]) -> None:
    deadline = time.monotonic() + TIMEOUT_S
    while time.monotonic() < deadline:
        returncode = process.poll()
        if returncode is not None:
            raise AssertionError(
                f"service exited before port {port} became available: exit={returncode}"
            )
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.4):
                return
        except OSError:
            time.sleep(0.2)
    raise AssertionError(f"service did not listen on loopback TCP {port}")


def require_udp_port(port: int) -> None:
    # UDP does not have a connect handshake. Inspect the kernel's socket table
    # rather than falsely claiming that a UDP send proves a bound listener.
    target = f"{port:04X}"
    deadline = time.monotonic() + TIMEOUT_S
    while time.monotonic() < deadline:
        for filename in ("/proc/net/udp", "/proc/net/udp6"):
            rows = Path(filename).read_text(encoding="ascii").splitlines()[1:]
            for row in rows:
                columns = row.split()
                if len(columns) > 1 and columns[1].split(":")[-1].upper() == target:
                    return
        time.sleep(0.2)
    raise AssertionError(f"no UDP listener on {port} inside isolated namespace")


def run() -> None:
    for executable in (HBBS, HBBR):
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise AssertionError(f"missing executable: {executable.name}")

    with tempfile.TemporaryDirectory(prefix="lanveriq-rustdesk-") as directory:
        root = Path(directory)
        children: list[subprocess.Popen[bytes]] = []

        def spawn(executable: Path, *args: str) -> subprocess.Popen[bytes]:
            child = subprocess.Popen(
                [str(executable), *args],
                cwd=root,
                env={**os.environ, "RUST_LOG": "warn", "TEST_HBBS": "no"},
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True,
            )
            children.append(child)
            return child

        def stop(child: subprocess.Popen[bytes]) -> None:
            if child.poll() is None:
                os.killpg(child.pid, signal.SIGTERM)
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(child.pid, signal.SIGKILL)
                    child.wait(timeout=5)

        try:
            # In 1.1.16, '-k -' loads/generates a local Ed25519 keypair.
            # Explicitly do NOT use hbbr's empty-key default, which bypasses
            # relay key validation per upstream documentation.
            hbbs = spawn(HBBS, "-k", "-", "-r", "127.0.0.1:21117")
            require_tcp_port(21116, hbbs)
            require_tcp_port(21115, hbbs)
            require_udp_port(21116)

            key = root / "id_ed25519"
            public = root / "id_ed25519.pub"
            if not key.is_file() or not public.is_file():
                raise AssertionError("hbbs did not create its expected test keypair")
            if key.stat().st_size == 0 or public.stat().st_size == 0:
                raise AssertionError("hbbs generated empty key material")

            hbbr = spawn(HBBR, "-k", "-")
            require_tcp_port(21117, hbbr)
            if hbbs.poll() is not None or hbbr.poll() is not None:
                raise AssertionError("one RustDesk service exited unexpectedly")

            # An explicit process restart proves the relay port can recover.
            stop(hbbr)
            hbbr = spawn(HBBR, "-k", "-")
            require_tcp_port(21117, hbbr)
            if hbbr.poll() is not None:
                raise AssertionError("hbbr exited immediately after restart")

            # OSS does not provide the Pro-only HTTP API on 21114.
            try:
                with socket.create_connection(("127.0.0.1", 21114), timeout=0.4):
                    raise AssertionError("unexpected listener on Pro-only TCP 21114")
            except OSError:
                pass

            print("PASS: pinned RustDesk OSS hbbs/hbbr start with a shared local keypair")
            print("PASS: loopback-only TCP 21115/21116/21117 and UDP 21116 listeners")
            print("PASS: hbbr restart, no listener on Pro API port 21114")
            print("SCOPE: startup/port smoke ONLY; no client auth, NAT, relay data or UI test")
        finally:
            for child in reversed(children):
                stop(child)


if __name__ == "__main__":
    run()
