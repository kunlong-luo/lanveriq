#!/usr/bin/env python3
"""Synthetic nested Weston only; no real desktop, Agent, or installation."""
import json
import os
import pathlib
import re
import stat
import subprocess
import tempfile
import time

from package_audit import ROOT, NAME, check, digest
from window_smoke import stop

TITLE = "Lanveriq Desktop - Offline Candidate"
MAX_TRACE = 2 * 1024 * 1024
SOCKET = "wayland-lanveriq-ci"
REPORT = ROOT / "candidate-wayland-smoke.json"

# Recognize protocol objects (not arbitrary messages or a title string).
# libwayland and the Rust backend can differ in timestamps/new-id formatting.
MESSAGE = re.compile(r"^\s*(?:\[[^\]\n]{1,80}\]\s*)?(?P<out>->\s*)?"
                     r"(?P<interface>\w+)@(?P<id>\d+)\.(?P<method>\w+)\((?P<args>.*)\)\s*$")

def protocol_evidence(raw):
    check(len(raw) <= MAX_TRACE, "Wayland trace exceeded bounded synthetic input")
    surfaces, tops, titles, configured, acked, attached = {}, {}, set(), {}, set(), set()
    for line in raw.splitlines():
        m = MESSAGE.fullmatch(line)
        if not m:
            continue
        interface, obj, method, args = (m[k] for k in ("interface", "id", "method", "args"))
        outgoing = m["out"] is not None
        if interface == "xdg_wm_base" and method == "get_xdg_surface" and outgoing:
            pair = re.fullmatch(r"(?:new id )?xdg_surface@(\d+),\s*wl_surface@(\d+)", args)
            if pair:
                surfaces[pair[1]] = pair[2]
        elif interface == "xdg_surface" and method == "get_toplevel" and outgoing and obj in surfaces:
            top = re.fullmatch(r"(?:new id )?xdg_toplevel@(\d+)", args)
            if top:
                tops[top[1]] = obj
        elif interface == "xdg_toplevel" and method == "set_title" and outgoing and obj in tops:
            if args == '"' + TITLE + '"':
                titles.add(tops[obj])
            else:
                titles.discard(tops[obj])
        elif interface == "xdg_surface" and method == "configure" and not outgoing and obj in surfaces:
            if re.fullmatch(r"\d+", args):
                configured[obj] = args
                acked.discard(obj)
                attached.discard(obj)
        elif interface == "xdg_surface" and method == "ack_configure" and outgoing:
            if obj in configured and configured[obj] == args:
                acked.add(obj)
            else:
                acked.discard(obj)
                attached.discard(obj)
        elif interface == "wl_surface" and method == "attach" and outgoing:
            if re.fullmatch(r"wl_buffer@\d+,\s*-?\d+,\s*-?\d+", args):
                attached.update(x for x in acked if surfaces[x] == obj)
            else:
                attached.difference_update(x for x in acked if surfaces[x] == obj)
        elif interface == "wl_surface" and method == "commit" and outgoing:
            if any(x in titles and surfaces[x] == obj for x in attached):
                return True
        elif method == "destroy" and outgoing:
            # Reject stale object reuse rather than combining unrelated surfaces.
            if interface == "xdg_surface" and obj in surfaces:
                surfaces.pop(obj)
                titles.discard(obj)
                configured.pop(obj, None)
                acked.discard(obj)
                attached.discard(obj)
                tops = {k: v for k, v in tops.items() if v != obj}
            elif interface == "wl_surface":
                for x in [x for x, v in surfaces.items() if v == obj]:
                    attached.discard(x)
                    acked.discard(x)
            elif interface == "xdg_toplevel" and obj in tops:
                x = tops.pop(obj)
                titles.discard(x)
                attached.discard(x)
                acked.discard(x)
    return False

def registry_evidence(raw):
    check(len(raw) <= 131072, "oversized compositor registry output")
    return {name: bool(re.search(r"(?m)^\s*interface: '" + name + r"',", raw))
            for name in ("wl_seat", "xdg_wm_base", "wl_compositor")}

def failure_summary(raw):
    lower = raw.lower()
    return {key: any(token in lower for token in tokens) for key, tokens in {
        "rust_panic": ("panicked at",),
        "seat_reference": ("wl_seat", "no seat"),
        "option_unwrap": ("option::unwrap", "option::expect", "none value"),
        "vulkan_or_egl": ("vulkan", "egl", "vk_error", "no suitable gpu"),
        "protocol_error": ("protocol error", "error in client communication"),
        "unsupported_global": ("missing global", "global not found", "unsupported version"),
        "wayland_connect": ("failed to connect", "no wayland display"),
        "permission_denied": ("permission denied",),
    }.items()}

def read_trace(path):
    with path.open("rb") as stream:
        raw = stream.read(MAX_TRACE + 1)
    check(len(raw) <= MAX_TRACE, "Wayland trace exceeded bounded synthetic input")
    return raw.decode("utf-8", "replace")

def wait_for(fn, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if fn():
            return
        time.sleep(0.2)
    raise ValueError("timed out waiting for synthetic Wayland evidence")

def main():
    check(os.getuid() != 0 and os.geteuid() != 0, "non-root CI only")
    binary = ROOT / "target/release" / NAME
    check(binary.is_file() and not binary.is_symlink(), "missing optimized GUI")
    elf_sha = digest(binary)
    compositor = app = None
    with tempfile.TemporaryDirectory(prefix="lanveriq-weston-") as tmp:
        home = pathlib.Path(tmp)
        runtime = home / "runtime"
        runtime.mkdir(mode=0o700)
        env = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "HOME": str(home),
               "XDG_RUNTIME_DIR": str(runtime), "XDG_SESSION_TYPE": "wayland",
               "XDG_CURRENT_DESKTOP": "WESTON", "WAYLAND_DISPLAY": SOCKET,
               "DBUS_SESSION_BUS_ADDRESS": os.environ["DBUS_SESSION_BUS_ADDRESS"],
               "LIBGL_ALWAYS_SOFTWARE": "1", "RUST_BACKTRACE": "1"}
        # Only Weston receives the private Xvfb display. The GUI gets no X11
        # variables, inherited fd or real desktop environment.
        server_env = {**env, "DISPLAY": os.environ["DISPLAY"], "XAUTHORITY": os.environ["XAUTHORITY"]}
        server_env.pop("WAYLAND_DISPLAY")
        trace = home / "client.trace"
        with (home / "weston.log").open("wb") as server_log, trace.open("wb") as client_log:
            try:
                compositor = subprocess.Popen(
                    ["/usr/bin/weston", "--backend=x11-backend.so",
                     "--no-config", "--socket=" + SOCKET, "--idle-time=0",
                     "--width=1280", "--height=800"],
                    cwd=home, env=server_env, stdout=server_log, stderr=subprocess.STDOUT)
                def socket_ready():
                    check(compositor.poll() is None, "synthetic Weston exited")
                    p = runtime / SOCKET
                    return p.exists() and stat.S_ISSOCK(p.lstat().st_mode)
                wait_for(socket_ready)
                registry = subprocess.run(["/usr/bin/wayland-info"], cwd=home, env=env,
                                          capture_output=True, check=True, timeout=10)
                globals_seen = registry_evidence(registry.stdout.decode("utf-8"))
                print("Weston registry globals:", globals_seen, flush=True)
                check(all(globals_seen.values()), "required synthetic compositor globals missing")
                app = subprocess.Popen([str(binary)], cwd=home,
                                       env=env | {"WAYLAND_DEBUG": "client"},
                                       stdout=client_log, stderr=subprocess.STDOUT)
                def committed():
                    check(compositor.poll() is None and app.poll() is None,
                          "synthetic compositor/client exited before protocol evidence")
                    return protocol_evidence(read_trace(trace))
                wait_for(committed)
                check((pathlib.Path("/proc") / str(app.pid) / "exe").resolve(strict=True)
                      == binary.resolve(strict=True), "unexpected synthetic client executable")
                time.sleep(1)
                check(app.poll() is None and compositor.poll() is None,
                      "client/compositor exited immediately after commit")
                check(digest(binary) == elf_sha, "optimized ELF changed")
                print("PASS: exact-title xdg-shell toplevel configured, acknowledged and non-null buffer committed")
                # No physical seat or normal-close proof; stop test children.
                # Do NOT claim application Ctrl+Q or compositor close from SIGTERM.
                stop(app)
                report = {"schema": "lanveriq.gpui.untrusted-wayland-smoke-v1",
                          "source_sha": os.environ["GITHUB_SHA"], "elf_sha256": elf_sha,
                          "lock_sha256": digest(ROOT / "Cargo.lock"),
                          "scope": "SYNTHETIC_XVFB_NESTED_WESTON_SOFTWARE_RENDERED",
                          "compositor_registry": globals_seen,
                          "x11_environment_removed": True, "xdg_shell_commit": True,
                          "application_exit": "HARNESS_TERMINATED_NOT_NORMAL_CLOSE",
                          "client_returncode": app.returncode,
                          "pixel_readback": "NOT_TESTED", "physical_cosmic": "NOT_TESTED",
                          "ime": "NOT_TESTED", "release_approved": False, "install_authorized": False}
                with REPORT.open("x", encoding="utf-8") as out:
                    json.dump(report, out, indent=2, sort_keys=True)
            except BaseException:
                # Only synthetic child diagnostics; never archive a raw Wayland trace.
                print("NOT_APPROVED: synthetic Wayland smoke failed; no physical claim", flush=True)
                print("Weston returncode:", compositor.poll() if compositor else None, flush=True)
                print("candidate returncode:", app.poll() if app else None, flush=True)
                for label, path in (("Weston", home / "weston.log"), ("candidate", trace)):
                    with path.open("rb") as stream:
                        stream.seek(0, os.SEEK_END)
                        size = stream.tell()
                        stream.seek(max(0, size - 65536))
                        tail = stream.read(65536).decode("utf-8", "replace")
                        print(label + " synthetic log bytes:", size, flush=True)
                        print(label + " diagnostic categories:", failure_summary(tail), flush=True)
                        # Only bounded error excerpts from the disposable synthetic session.
                        errors = 0
                        for line in tail.splitlines():
                            if MESSAGE.fullmatch(line):
                                continue
                            if re.search(r"panicked at|called .*unwrap|called .*expect|fatal:|Error:|VK_ERROR", line):
                                safe = line.replace(str(home), "<synthetic-home>").replace(str(ROOT), "<candidate-source>")
                                safe = re.sub(r"/(?:home|tmp|run)/[^\s,;\]\)]+", "<path>", safe)
                                print(label + " synthetic error:", safe[:400], flush=True)
                                errors += 1
                                if errors >= 12:
                                    break
                raise
            finally:
                stop(app)
                stop(compositor)
    print("PASS: private Weston and client stopped; no COSMIC, GPU, IME, normal-close or release approval")

if __name__ == "__main__":
    main()
