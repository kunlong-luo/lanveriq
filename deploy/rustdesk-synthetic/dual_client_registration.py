#!/usr/bin/env python3
"""Two REAL RustDesk 1.5.1 clients against the genuine RustDesk 1.1.16 hbbs.

Run on a GitHub-hosted runner with Docker. Each upstream client is a separate
non-root Docker process/PID/IPC/mount namespace; both join only the isolated
server's Docker --network none namespace. No real endpoint or internet route.

This proves parallel rendezvous registration and independent lifecycles, NOT
desktop streaming, pairing authorization, NAT, encryption or Lanveriq RBAC.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import secrets
import signal
import subprocess
import sys
import time

BASE = ("python:3.13-slim@sha256:"
        "e48f59cdba69a3cbd9ed2cf0f348d90d45a8d350d98134cf1070bc106ee9ed90")
SERVER_TIMEOUT_S = 18
CLIENT_TIMEOUT_S = 60
PUBLIC_KEY = re.compile(r"^[a-zA-Z0-9+/=]{32,128}$")


def docker(*args: str, timeout: int = 25, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["docker", *args],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, timeout=timeout, check=check,
    )


def is_running(name: str) -> bool:
    try:
        result = docker("inspect", "--format", "{{.State.Running}}", name, timeout=8)
        return result.stdout.strip() == "true"
    except subprocess.CalledProcessError:
        return False


def rows(server: str) -> list[tuple[str, int, int]]:
    statement = (
        "import sqlite3,json,os; p='/tmp/db_v2.sqlite3'; "
        "c=sqlite3.connect('file:'+p+'?mode=ro',uri=True) if os.path.isfile(p) else None; "
        "print(json.dumps(list(c.execute("
        "'SELECT id,length(uuid),length(pk) FROM peer ORDER BY id')) if c else []))"
    )
    try:
        value = docker("exec", server, "python", "-c", statement, timeout=7)
        return [tuple(x) for x in json.loads(value.stdout)]
    except (subprocess.CalledProcessError, ValueError):
        # Initial tables may be migrating; never silently pass the final check.
        return []


def wait_rows(server: str, clients: list[str], expected: int, seconds: int) -> list[tuple[str, int, int]]:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        for name in [server, *clients]:
            if not is_running(name):
                raise AssertionError(f"isolated upstream container exited: {name.split('-')[-1]}")
        result = rows(server)
        if len(result) == expected:
            if len({record[0] for record in result}) != expected:
                raise AssertionError("duplicate peer IDs")
            if not all(len(record[0]) >= 6 and record[1] >= 16 and record[2] == 32
                       for record in result):
                raise AssertionError("invalid real client UUID/public-key metadata")
            return result
        if len(result) > expected:
            raise AssertionError("unexpected extra registered RustDesk peer")
        time.sleep(0.6)
    raise AssertionError(f"real RustDesk registration count did not reach {expected}")


def node() -> None:
    from pathlib import Path
    import subprocess

    pub = os.environ.get("RD_TEST_PUBLIC_KEY", "")
    if not PUBLIC_KEY.fullmatch(pub):
        raise AssertionError("missing ephemeral synthetic server public key")
    home = Path("/tmp/rd-client-home")
    runtime = Path("/tmp/rd-client-runtime")
    runtime.mkdir(mode=0o700, parents=True, exist_ok=True)
    env = {
        **os.environ, "HOME": str(home),
        "XDG_CONFIG_HOME": str(home / ".config"),
        "XDG_CACHE_HOME": str(home / ".cache"),
        "XDG_RUNTIME_DIR": str(runtime),
        "DISPLAY": ":91", "NO_AT_BRIDGE": "1",
    }
    text = (
        '[options]\n'
        'custom-rendezvous-server = "127.0.0.1:21116"\n'
        'relay-server = "127.0.0.1:21117"\n'
        f'key = "{pub}"\n'
    )
    for dirname in ("RustDesk", "rustdesk"):
        directory = home / ".config" / dirname
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        cfg = directory / "RustDesk2.toml"
        cfg.write_text(text, encoding="utf-8")
        cfg.chmod(0o600)

    binary = "/tmp/squashfs-root/AppRun"
    appimage = "/opt/client/rustdesk.AppImage"
    subprocess.run([appimage, "--appimage-extract"], cwd="/tmp",
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   timeout=45, check=True)
    if not Path(binary).is_file():
        raise AssertionError("official RustDesk AppRun extraction failed")
    xvfb = subprocess.Popen(
        ["Xvfb", ":91", "-screen", "0", "1024x768x24", "-nolisten", "tcp", "-ac"],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    try:
        deadline = time.monotonic() + 10
        while not Path("/tmp/.X11-unix/X91").exists() and time.monotonic() < deadline:
            if xvfb.poll() is not None:
                raise AssertionError("Xvfb died before real client started")
            time.sleep(0.2)
        if not Path("/tmp/.X11-unix/X91").exists():
            raise AssertionError("virtual display socket not created")
        with open("/tmp/official-client.log", "wb") as output:
            peer = subprocess.Popen(
                [binary, "--server"], env=env, cwd="/tmp",
                stdout=output, stderr=subprocess.STDOUT,
                start_new_session=True,
            )
            try:
                for _ in range(90):
                    if peer.poll() is not None:
                        raise AssertionError(
                            f"official upstream --server exited early ({peer.returncode})"
                        )
                    time.sleep(1)
            finally:
                if peer.poll() is None:
                    os.killpg(peer.pid, signal.SIGTERM)
                    try:
                        peer.wait(timeout=4)
                    except subprocess.TimeoutExpired:
                        os.killpg(peer.pid, signal.SIGKILL)
                        peer.wait(timeout=4)
    finally:
        if xvfb.poll() is None:
            os.killpg(xvfb.pid, signal.SIGTERM)
            xvfb.wait(timeout=4)


def orchestrate() -> None:
    runner = Path(os.environ["RUNNER_TEMP"])
    workspace = Path(os.environ["GITHUB_WORKSPACE"])
    client_image = f"lanveriq-rd-cli:{os.environ['GITHUB_RUN_ID']}"
    suffix = secrets.token_hex(5)
    server = "lanveriq-rd-srv-" + suffix
    first = "lanveriq-rd-a-" + suffix
    second = "lanveriq-rd-b-" + suffix
    active: list[str] = []
    script = workspace / "deploy" / "rustdesk-synthetic" / "dual_client_registration.py"
    binaries = runner / "rustdesk-server-bin"
    appimage = runner / "rustdesk-official-client"

    def launch(name: str, args: list[str]) -> None:
        docker("run", "-d", "--name", name, *args, timeout=25)
        active.append(name)

    try:
        launch(server, [
            "--network", "none", "--read-only", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges", "--pids-limit", "64",
            "--memory", "256m", "--cpus", "1", "--user", "65534:65534",
            "--tmpfs", "/tmp:rw,nosuid,nodev,size=96m",
            "--volume", f"{binaries}:/opt/rustdesk:ro",
            "--workdir", "/tmp", BASE,
            "sh", "-ec",
            "/opt/rustdesk/hbbs -k - -r 127.0.0.1:21117 >/tmp/hbbs.log 2>&1 & "
            "for i in $(seq 1 50); do [ -s /tmp/id_ed25519.pub ] && break; sleep .1; done; "
            "test -s /tmp/id_ed25519.pub; "
            "/opt/rustdesk/hbbr -k - >/tmp/hbbr.log 2>&1 & wait",
        ])
        deadline = time.monotonic() + SERVER_TIMEOUT_S
        key = ""
        while time.monotonic() < deadline:
            if not is_running(server):
                raise AssertionError("real upstream hbbs/hbbr container exited")
            try:
                result = docker("exec", server, "cat", "/tmp/id_ed25519.pub", timeout=5)
                key = result.stdout.strip()
                if PUBLIC_KEY.fullmatch(key):
                    break
            except subprocess.CalledProcessError:
                pass
            time.sleep(0.2)
        if not PUBLIC_KEY.fullmatch(key):
            raise AssertionError("hbbs did not generate ephemeral server key")

        # Every client has its OWN mount, IPC and PID namespace. Only the
        # server's disconnected network namespace is deliberately shared.
        def launch_client(name: str) -> None:
            launch(name, [
                "--network", f"container:{server}",
                "--read-only", "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges", "--pids-limit", "128",
                "--memory", "1g", "--cpus", "2", "--user", "65534:65534",
                "--tmpfs", "/tmp:rw,exec,nosuid,nodev,size=512m",
                "--volume", f"{appimage}:/opt/client:ro",
                "--volume", f"{script}:/workspace/dual_client_registration.py:ro",
                "--env", "RD_TEST_PUBLIC_KEY=" + key,
                "--workdir", "/tmp", client_image,
                "python", "/workspace/dual_client_registration.py", "--node",
            ])

        if rows(server):
            raise AssertionError("new hbbs must have zero peers")
        launch_client(first)
        initial = wait_rows(server, [first], 1, CLIENT_TIMEOUT_S)
        launch_client(second)
        current = wait_rows(server, [first, second], 2, CLIENT_TIMEOUT_S)
        if initial[0][0] not in {x[0] for x in current}:
            raise AssertionError("first client's registered identity disappeared")
        print("PASS: two unmodified RustDesk 1.5.1 clients registered distinct IDs")
        print("PASS: genuine RustDesk Server 1.1.16 SQLite has two valid UUID/PK rows")
        print("PASS: each official client has its own PID/IPC/filesystem namespace")
        docker("rm", "-f", second, timeout=10)
        active.remove(second)
        if not is_running(first) or not is_running(server):
            raise AssertionError("removing one client terminated the other real client")
        if len(rows(server)) != 2:
            raise AssertionError("hbbs unexpectedly lost persisted identities after disconnect")
        print("PASS: disconnecting one client does not stop another and DB rows persist")
        print("SCOPE: registration/lifecycle only; NOT real remote desktop E2E or RBAC")
    finally:
        for name in reversed(active):
            docker("rm", "-f", name, check=False, timeout=15)


if __name__ == "__main__":
    if sys.argv[1:] == ["--node"]:
        node()
    elif not sys.argv[1:]:
        orchestrate()
    else:
        raise SystemExit("usage: dual_client_registration.py [--node]")
