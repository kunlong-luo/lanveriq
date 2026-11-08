#!/usr/bin/env python3
"""RD-1 real, unmodified RustDesk 1.5.1 client registration against hbbs 1.1.16.

Launches the upstream client --server with an ephemeral virtual X display and
ONLY a locally configured upstream hbbs/hbbr (Docker --network none).
Success requires a new durable peer row in the REAL hbbs SQLite database.

This is NOT remote desktop E2E, consent, OS keyboard permission, P2P, E2EE, or
Lanveriq authorization acceptance. No synthetic RegisterPeer/PK is sent.
"""
from __future__ import annotations

import os
import re
import signal
import socket
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path("/tmp/lanveriq-rustdesk-client-registration")
HBBS = Path("/opt/rustdesk/hbbs")
HBBR = Path("/opt/rustdesk/hbbr")
CLIENT = Path("/tmp/squashfs-root/AppRun")
MAX_WAIT_S = 50.0
PROBE_INTERVAL_S = 0.4


def safe_file(path: Path) -> None:
    if not path.is_file() or not os.access(path, os.X_OK):
        raise AssertionError(f"missing executable: {path.name}")


def write_client_config(home: Path, server_key: str) -> None:
    # The upstream code uses directories_next::ProjectDirs(APP_NAME=RustDesk)
    # and loads the separate Config2 options map. Include capitalization
    # variants to handle Linux config-dir conventions without using --option
    # (which requires root/installed service and could touch host settings).
    # Both locations are inside this one disposable test container.
    cfg = (
        '[options]\n'
        'custom-rendezvous-server = "127.0.0.1:21116"\n'
        'relay-server = "127.0.0.1:21117"\n'
        f'key = "{server_key}"\n'
    )
    for directory in ("RustDesk", "rustdesk"):
        target = home / ".config" / directory
        target.mkdir(parents=True, exist_ok=True)
        path = target / "RustDesk2.toml"
        path.write_text(cfg, encoding="utf-8")
        path.chmod(0o600)


def get_peers(db_path: Path) -> list[tuple[str, int, int]]:
    if not db_path.is_file():
        return []
    try:
        with sqlite3.connect(f"file:{db_path}?mode=ro", uri=True, timeout=0.3) as conn:
            return list(conn.execute(
                "SELECT id, length(uuid), length(pk) FROM peer ORDER BY id LIMIT 3"
            ))
    except sqlite3.OperationalError:
        # Race with upstream SQLite initialization is expected.
        return []


def wait_listening(port: int, proc: subprocess.Popen[bytes]) -> None:
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise AssertionError(f"hbbs exited during startup ({proc.returncode})")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.3):
                return
        except OSError:
            time.sleep(0.2)
    raise AssertionError(f"upstream hbbs did not listen on {port}")


def read_safe_diagnostics(path: Path) -> str:
    if not path.is_file():
        return "log unavailable"
    # Avoid dumping upstream generated keys, full peer data, or random IDs.
    output = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if any(marker in line.lower() for marker in (
            "error while loading", "cannot open", "no such file",
            "permission denied", "address already in use", "failed to open display",
        )):
            output.append(re.sub(r"(?i)(key|token|password)=[^\s]+", r"\1=REDACTED", line[:300]))
    return " | ".join(output[-5:]) or "no known dependency error in program log"


def main() -> None:
    for path in (HBBS, HBBR, CLIENT):
        safe_file(path)
    if not Path("/usr/bin/Xvfb").is_file():
        raise AssertionError("Xvfb must be available in isolated test image")
    ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    home = ROOT / "home"
    home.mkdir(mode=0o700, exist_ok=True)
    runtime = ROOT / "runtime"
    runtime.mkdir(mode=0o700, exist_ok=True)
    env = {
        **os.environ,
        "HOME": str(home),
        "XDG_CONFIG_HOME": str(home / ".config"),
        "XDG_CACHE_HOME": str(home / ".cache"),
        "XDG_RUNTIME_DIR": str(runtime),
        "DISPLAY": ":91",
        "RUST_LOG": "warn",
        "TEST_HBBS": "no",
        "NO_AT_BRIDGE": "1",
    }
    processes: list[subprocess.Popen[bytes]] = []
    logs = []

    def start(command: list[str], name: str,
              custom_env: dict[str, str] | None = None) -> subprocess.Popen[bytes]:
        log_file = (ROOT / f"{name}.log").open("wb")
        logs.append(log_file)
        p = subprocess.Popen(
            command, cwd=ROOT, env=custom_env or env, stdin=subprocess.DEVNULL,
            stdout=log_file, stderr=subprocess.STDOUT, start_new_session=True,
        )
        processes.append(p)
        return p

    try:
        xvfb = start(["Xvfb", ":91", "-screen", "0", "1024x768x24",
                      "-nolisten", "tcp", "-ac"], "xvfb")
        deadline = time.monotonic() + 10
        while not Path("/tmp/.X11-unix/X91").exists() and time.monotonic() < deadline:
            if xvfb.poll() is not None:
                raise AssertionError("synthetic Xvfb exited")
            time.sleep(0.15)
        if not Path("/tmp/.X11-unix/X91").exists():
            raise AssertionError("virtual display UNIX socket not ready")

        hbbs = start([str(HBBS), "-k", "-", "-r", "127.0.0.1:21117"], "hbbs")
        wait_listening(21116, hbbs)
        public_key_file = ROOT / "id_ed25519.pub"
        until = time.monotonic() + 10
        while not public_key_file.is_file() and time.monotonic() < until:
            time.sleep(0.1)
        if not public_key_file.is_file():
            raise AssertionError("hbbs did not generate an ephemeral public key")
        server_key = public_key_file.read_text(encoding="ascii").strip()
        if len(server_key) < 32 or not re.fullmatch(r"[a-zA-Z0-9+/=]+", server_key):
            raise AssertionError("invalid synthetic server public key")
        hbbr = start([str(HBBR), "-k", "-"], "hbbr")
        wait_listening(21117, hbbr)
        write_client_config(home, server_key)

        db = ROOT / "db_v2.sqlite3"
        if get_peers(db):
            raise AssertionError("server database must be empty before real client starts")
        client = start([str(CLIENT), "--server"], "official-client")

        deadline = time.monotonic() + MAX_WAIT_S
        found = []
        while time.monotonic() < deadline:
            if hbbs.poll() is not None:
                raise AssertionError("hbbs crashed while real client registered")
            if hbbr.poll() is not None:
                raise AssertionError("hbbr crashed while real client registered")
            if client.poll() is not None:
                raise AssertionError(
                    f"official RustDesk --server exited before registration: "
                    f"exit={client.returncode}; "
                    f"{read_safe_diagnostics(ROOT / 'official-client.log')}"
                )
            found = get_peers(db)
            if found:
                break
            time.sleep(PROBE_INTERVAL_S)

        if len(found) != 1:
            raise AssertionError(
                f"expected exactly one real RustDesk peer in fresh hbbs database, got {len(found)}; "
                f"{read_safe_diagnostics(ROOT / 'official-client.log')}"
            )
        peer_id, uuid_size, pk_size = found[0]
        if len(peer_id) < 6 or uuid_size < 16 or pk_size != 32:
            raise AssertionError("registered RustDesk peer has invalid ID/UUID/public key")
        if client.poll() is not None:
            raise AssertionError("official client exited immediately after registration")
        # A second REAL upstream client must not inherit the first client's
        # logical ID simply because the same CI container hosts both. Each
        # process has a dedicated XDG config/runtime and virtual X11 display.
        home2 = ROOT / "home-secondary"
        home2.mkdir(mode=0o700, exist_ok=True)
        runtime2 = ROOT / "runtime-secondary"
        runtime2.mkdir(mode=0o700, exist_ok=True)
        env2 = {
            **env,
            "HOME": str(home2),
            "XDG_CONFIG_HOME": str(home2 / ".config"),
            "XDG_CACHE_HOME": str(home2 / ".cache"),
            "XDG_RUNTIME_DIR": str(runtime2),
            "DISPLAY": ":92",
        }
        xvfb2 = start(["Xvfb", ":92", "-screen", "0", "1024x768x24",
                       "-nolisten", "tcp", "-ac"], "xvfb-secondary", custom_env=env2)
        deadline = time.monotonic() + 10
        while not Path("/tmp/.X11-unix/X92").exists() and time.monotonic() < deadline:
            if xvfb2.poll() is not None:
                raise AssertionError("secondary synthetic Xvfb exited")
            time.sleep(0.15)
        if not Path("/tmp/.X11-unix/X92").exists():
            raise AssertionError("secondary virtual display socket not ready")
        write_client_config(home2, server_key)
        client2 = start([str(CLIENT), "--server"], "official-client-secondary", custom_env=env2)
        original_peer_id = peer_id
        deadline = time.monotonic() + MAX_WAIT_S
        while time.monotonic() < deadline:
            if client2.poll() is not None:
                raise AssertionError(
                    "secondary upstream client exited before identity-isolation check: "
                    + read_safe_diagnostics(ROOT / "official-client-secondary.log")
                )
            if client.poll() is not None or hbbs.poll() is not None or hbbr.poll() is not None:
                raise AssertionError("upstream first client or servers exited during two-peer check")
            peers = get_peers(db)
            if len(peers) == 2:
                second_ids = {item[0] for item in peers}
                if len(second_ids) == 2 and original_peer_id in second_ids:
                    if not all(6 <= len(item[0]) and item[1] >= 16 and item[2] == 32
                               for item in peers):
                        raise AssertionError("second client registered without valid UUID/PK metadata")
                    break
            if len(peers) > 2:
                raise AssertionError("unexpected extra rendezvous peer in isolated test")
            time.sleep(PROBE_INTERVAL_S)
        else:
            raise AssertionError(
                "two independent official RustDesk clients did not register "
                "with distinct IDs in the same isolated hbbs: "
                + read_safe_diagnostics(ROOT / "official-client-secondary.log")
            )
        print("PASS: two simultaneous genuine RustDesk 1.5.1 clients have distinct peer IDs")
        print("PASS: both genuine clients maintain valid UUID/public-key metadata in hbbs")
        print("PASS: official RustDesk 1.5.1 --server registered its real key and UUID")
        print("PASS: upstream hbbs 1.1.16 durably stored the peer in a fresh SQLite DB")
        print("PASS: no external routing, real host device, synthetic peer injection, or LAN ports")
        print("SCOPE: registration only; NOT peer authentication, E2EE desktop or RBAC approval")
    finally:
        for p in reversed(processes):
            if p.poll() is None:
                try:
                    os.killpg(p.pid, signal.SIGTERM)
                    p.wait(timeout=3)
                except (ProcessLookupError, subprocess.TimeoutExpired):
                    try:
                        os.killpg(p.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    p.wait(timeout=3)
        for log in logs:
            log.close()


if __name__ == "__main__":
    main()
