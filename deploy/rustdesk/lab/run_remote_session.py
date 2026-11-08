#!/usr/bin/env python3
"""R2-02: actual synthetic RustDesk screen AND input over an authorized session.

Requires TWO independent pinned upstream RustDesk 1.5.1 processes and official
hbbs/hbbr 1.1.16. The receiving synthetic user explicitly clicks the upstream
RustDesk GUI approval. The controlling peer must then see remote-only RGB
tiles, and a controller-side X11 click must create a remote-side input marker.

This is not an Internet/WAN test, not unattended access, and not a substitute
for Lanveriq auth/RBAC. No images, credentials, IDs or private keys are logged.
"""
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sqlite3
import sys
import tempfile
import time
from typing import Any

import run as lab
from verify import EvidenceRejected


BASE = pathlib.Path(__file__).resolve().parent
SCENE = BASE / "synthetic_scene.py"
VERIFY = BASE / "verify_remote_frame.py"


def peer_cmd(peer: str, script: str, *, timeout: int = 12) -> str:
    if peer not in ("peer-a", "peer-b"):
        raise EvidenceRejected("Unknown synthetic client role")
    return lab.compose("exec", "-T", peer, "sh", "-ec", script, timeout=timeout)


def background(peer: str, script: str) -> None:
    if peer not in ("peer-a", "peer-b"):
        raise EvidenceRejected("Unknown synthetic client role")
    lab.compose("exec", "-T", "-d", peer, "sh", "-ec", script)


def wait_for_id(peer: str) -> str:
    for _ in range(30):
        try:
            output = lab.compose("exec", "-T", peer, "rustdesk", "--get-id", timeout=8)
            if re.fullmatch(r"[0-9]{6,20}", output):
                return output
        except EvidenceRejected:
            pass
        time.sleep(1)
    raise EvidenceRejected(f"{peer} upstream RustDesk --get-id failed")


def registered_ids(directory: pathlib.Path) -> set[str]:
    file = directory / "r2-registered.sqlite3"
    lab.registration_snapshot(file)
    if lab.check_registered_db(file) != 2:
        raise EvidenceRejected("Neither of two real peer registrations could be verified")
    with sqlite3.connect(f"file:{file.resolve()}?mode=ro", uri=True) as conn:
        ids = {row[0] for row in conn.execute("SELECT id FROM peer")}
    if len(ids) != 2 or any(not re.fullmatch(r"[0-9]{6,20}", x) for x in ids):
        raise EvidenceRejected("Invalid RustDesk native peer IDs in server SQLite")
    return ids


def start_synthetic_scene() -> None:
    lab.compose("cp", str(SCENE), "peer-b:/tmp/r2-synthetic-scene.py")
    lab.compose("cp", str(VERIFY), "peer-a:/tmp/r2-verify-remote-frame.py")
    for peer in ("peer-a", "peer-b"):
        background(peer, "exec openbox >/tmp/r2-wm.log 2>&1")
    background("peer-b", "exec python3 /tmp/r2-synthetic-scene.py >/tmp/r2-scene.log 2>&1")
    for _ in range(20):
        try:
            visible = peer_cmd("peer-b",
                               "xdotool search --onlyvisible --name '^LANVERIQ_R2_REMOTE_ONLY_CANVAS$' "
                               "2>/dev/null | head -n 1", timeout=5)
            if re.fullmatch(r"[0-9]+", visible):
                # No local/automation mouse click has been sent to peer-b.
                peer_cmd("peer-b", "test ! -e /tmp/r2-remote-input-ok")
                # Headless Docker lacks an interactive login session that
                # normally starts RustDesk's native Connection Manager.
                # Launch the *official* upstream CM explicitly, but do not
                # approve any connection until its real Accept button appears.
                background("peer-b", "exec rustdesk --cm >/tmp/r2-cm.log 2>&1")
                return
        except EvidenceRejected:
            pass
        time.sleep(0.5)
    raise EvidenceRejected("Remote-only synthetic X11 scene not visible on controlled peer")


def launch_connect(target_id: str) -> None:
    if not re.fullmatch(r"[0-9]{6,20}", target_id):
        raise EvidenceRejected("Untrusted target peer ID")
    # In RustDesk 1.5.1 --connect invokes the real upstream Flutter session.
    # Never pass password/keys in CLI args.
    background("peer-a", "exec rustdesk >/tmp/r2-controller-gui.log 2>&1")
    time.sleep(3)
    background("peer-a", f"exec rustdesk --connect {target_id} >/tmp/r2-connect.log 2>&1")


def try_operator_accept() -> bool:
    # Synthetic peer-b is the ONLY operator and has an explicit visible
    # upstream RustDesk CM window. The test does not supply any password.
    # Exclude the known test-scene window before clicking any GUI surface.
    click = r"""
ids="$(xdotool search --onlyvisible --class '([Rr]ust[Dd]esk|flutter)' 2>/dev/null || true)"
if [ -z "$ids" ]; then
  ids="$(xdotool search --onlyvisible --name '.*' 2>/dev/null || true)"
fi
for id in $ids; do
  title="$(xdotool getwindowname "$id" 2>/dev/null || true)"
  case "$title" in *LANVERIQ_R2_REMOTE_ONLY_CANVAS*) continue ;; esac
  geometry="$(xdotool getwindowgeometry --shell "$id" 2>/dev/null || true)"
  case "$geometry" in *WIDTH=*"HEIGHT="*) ;; *) continue ;; esac
  eval "$geometry"
  # Fail closed on tiny tray/popover windows; the real CM is a large
  # desktop Flutter window. No other non-test GUI runs in peer-b.
  if [ "$WIDTH" -ge 300 ] && [ "$HEIGHT" -ge 220 ] &&
     [ "$WIDTH" -le 1500 ] && [ "$HEIGHT" -le 1300 ]; then
    x="$((WIDTH * 27 / 100))"
    y="$((HEIGHT - 48))"
    # Never wait indefinitely for a Flutter/X11 window focus transition.
    timeout 2s xdotool windowactivate "$id" >/dev/null 2>&1 || continue
    timeout 2s xdotool mousemove --window "$id" "$x" "$y" click 1 >/dev/null 2>&1 || continue
    echo CLICKED_UPSTREAM_SYNTHETIC_ACCEPT_BUTTON
    exit 0
  fi
done
echo NO_UPSTREAM_ACCEPT_WINDOW
"""
    output = peer_cmd("peer-b", click)
    return output == "CLICKED_UPSTREAM_SYNTHETIC_ACCEPT_BUTTON"


def observe_remote_tiles() -> dict[str, Any] | None:
    try:
        stdout = lab.compose("exec", "-T", "peer-a", "python3",
                             "/tmp/r2-verify-remote-frame.py", timeout=20)
        parsed = json.loads(stdout)
        if parsed.get("visual_proof") != "PASS_REAL_CONTROLLER_FRAMEBUFFER_SYNTHETIC_TILES":
            return None
        return parsed
    except (EvidenceRejected, ValueError):
        return None


def input_arrived() -> bool:
    try:
        peer_cmd("peer-b",
                 "test -s /tmp/r2-remote-input-ok && "
                 "grep -Fxq R2_SYNTHETIC_REMOTE_CLICK_CONFIRMED /tmp/r2-remote-input-ok",
                 timeout=5)
        return True
    except EvidenceRejected:
        return False


def diagnosis(peer: str) -> str:
    # Whitelist of boolean process/window states only, NEVER raw logs,
    # screenshots, user files, peer IDs or private server key.
    script = r"""
printf 'x11='
if xdpyinfo >/dev/null 2>&1; then echo ready; else echo unavailable; fi
printf 'rustdesk_windows='
xdotool search --onlyvisible --class '([Rr]ust[Dd]esk|flutter)' 2>/dev/null | wc -l
printf 'any_gui_windows='
xdotool search --onlyvisible --name '.*' 2>/dev/null | wc -l
printf 'cm_executable='
if grep -Fq 'cm' /tmp/r2-cm.log 2>/dev/null; then echo logging; elif test -f /tmp/r2-cm.log; then echo started; else echo absent; fi
printf 'rustdesk_processes='
ps -e -o comm= | grep -cx rustdesk || true
printf 'scene='
if xdotool search --onlyvisible --name '^LANVERIQ_R2_REMOTE_ONLY_CANVAS$' >/dev/null 2>&1; then echo shown; else echo hidden; fi
printf 'login_error='
if grep -Eqi 'login error|authorization denied|no password access' /tmp/r2-connect.log 2>/dev/null; then echo yes; else echo no; fi
printf 'fatal='
if grep -Eqi 'error while loading shared libraries|segmentation fault|panic' /tmp/r2-connect.log /tmp/r2-controller-gui.log /tmp/r2-scene.log /tmp/r2-cm.log 2>/dev/null; then echo yes; else echo no; fi
"""
    try:
        return peer_cmd(peer, script).replace("\n", ",")
    except EvidenceRejected:
        return "unavailable"


def run_session() -> dict[str, Any]:
    lab.verify_environment()
    try:
        lab.compose("up", "-d", "--pull", "missing", timeout=140)
        pub_key = lab.wait_for_public_key()
        lab.start_peer("peer-a", pub_key)
        lab.start_peer("peer-b", pub_key)  # default upstream Both mode still requires GUI consent for this test
        with tempfile.TemporaryDirectory(prefix="lvq-r2-remote-") as path:
            count = lab.await_two_registrations(pathlib.Path(path))
            actual_server_ids = registered_ids(pathlib.Path(path))
        first_id = wait_for_id("peer-a")
        second_id = wait_for_id("peer-b")
        if first_id == second_id or {first_id, second_id} != actual_server_ids:
            raise EvidenceRejected("Client-local IDs not equal to two distinct server registrations")
        start_synthetic_scene()
        launch_connect(second_id)
        approval_clicked = False
        frame: dict[str, Any] | None = None
        for step in range(35):
            if step > 1 and step % 3 == 0:
                # The CM can appear before a pending request. Retry the real
                # visible accept surface periodically, rather than treating
                # an early click on an idle CM window as final consent.
                approval_clicked = try_operator_accept() or approval_clicked
            frame = observe_remote_tiles()
            if frame is not None:
                break
            time.sleep(1.3)
        if frame is None or not approval_clicked:
            raise EvidenceRejected(
                "Synthetic RustDesk full-session remote tiles/GUI consent not proven; "
                f"approve={approval_clicked},"
                f"a={diagnosis('peer-a')},b={diagnosis('peer-b')}"
            )
        if input_arrived():
            # A passing input marker before controller-side click would be a
            # false positive; never accept it.
            raise EvidenceRejected("Remote input marker already present before controller click")
        x, y = int(frame["input_target_x"]), int(frame["input_target_y"])
        if not (10 <= x <= 3840 and 10 <= y <= 2160):
            raise EvidenceRejected("Untrusted or out of range framebuffer input target")
        peer_cmd("peer-a", f"xdotool mousemove {x} {y} click 1", timeout=10)
        for _ in range(15):
            if input_arrived():
                return {
                    "verdict": "PASS_R2_02_REAL_REMOTE_FRAME_AND_SYNTHETIC_INPUT",
                    "official_peer_client": "RustDesk 1.5.1",
                    "official_id_relay_server": "RustDesk OSS 1.1.16",
                    "independent_client_containers": 2,
                    "server_db_registered_distinct_peers": count,
                    "native_gui_operator_approved": approval_clicked,
                    "remote_only_frame_observed_on_controller": True,
                    "remote_application_received_controller_input": True,
                    "synthetic_only": True,
                    "images_uploaded": False,
                    "actual_wan_test": False,
                    "not_proven": ["two physical LAN machines", "independent WAN/VPN",
                                   "Relay data plane", "NAT/P2P beyond this Docker bridge",
                                   "Windows GUI", "Lanveriq RBAC integration"],
                }
            time.sleep(1)
        raise EvidenceRejected("Remote tiles visible, but synthetic control-input proof did not arrive")
    finally:
        # The run's unique, non-production isolated Docker project only.
        lab.compose("down", "--volumes", "--remove-orphans", timeout=60)


def main() -> int:
    try:
        print(json.dumps(run_session(), sort_keys=True))
        return 0
    except (EvidenceRejected, OSError, subprocess.TimeoutExpired, ValueError) as exc:
        print(json.dumps({"verdict": "FAIL_R2_02", "reason": str(exc)}),
              file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
