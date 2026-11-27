#!/usr/bin/env python3
"""Disposable CI only: launch the exact compiled GPUI candidate on private X11.
No Agent, install, network endpoint, real user data or product evidence.
"""
import os
import pathlib
import subprocess
import tempfile
import time

TITLE = "^Lanveriq Desktop - Offline Candidate$"
ROOT = pathlib.Path(__file__).resolve().parent.parent
BINARY = ROOT / "target/debug/lanveriq-desktop-gpui-candidate"

def command(args, env, timeout=5):
    return subprocess.run(args, env=env, check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                          timeout=timeout).stdout.strip()

def wait_for(fn, seconds=20):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = fn()
            if result:
                return result
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
        time.sleep(0.2)
    raise RuntimeError("timed out waiting for native X11 evidence")

def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)

def main():
    assert os.getuid() != 0 and os.geteuid() != 0
    assert BINARY.is_file()
    with tempfile.TemporaryDirectory(prefix="lanveriq-window-smoke-") as temp:
        home = pathlib.Path(temp)
        runtime = home / "runtime"
        runtime.mkdir(mode=0o700)
        env = {
            "PATH": "/usr/bin:/bin", "LANG": "C.UTF-8",
            "HOME": str(home), "XDG_RUNTIME_DIR": str(runtime),
            "XDG_SESSION_TYPE": "x11", "XDG_CURRENT_DESKTOP": "OPENBOX",
            "DISPLAY": os.environ["DISPLAY"],
            "XAUTHORITY": os.environ["XAUTHORITY"],
            "DBUS_SESSION_BUS_ADDRESS": os.environ["DBUS_SESSION_BUS_ADDRESS"],
            "LIBGL_ALWAYS_SOFTWARE": "1", "RUST_BACKTRACE": "1",
        }
        app = wm = None
        with (home / "candidate.log").open("w+b") as log:
            try:
                wm = subprocess.Popen(["/usr/bin/openbox"], env=env,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                wait_for(lambda: "window id #" in command(
                    ["/usr/bin/xprop", "-root", "_NET_SUPPORTING_WM_CHECK"], env))
                app = subprocess.Popen([str(BINARY)], cwd=home, env=env,
                                       stdout=log, stderr=subprocess.STDOUT)
                def mapped():
                    if app.poll() is not None:
                        raise RuntimeError(f"candidate exited before window: {app.returncode}")
                    ids = command(["/usr/bin/xdotool", "search", "--onlyvisible", "--name", TITLE], env).splitlines()
                    if len(ids) != 1:
                        return None
                    wid = ids[0]
                    info = command(["/usr/bin/xwininfo", "-id", wid], env)
                    return wid if "Map State: IsViewable" in info else None
                wid = wait_for(mapped)
                print("PASS: exact-title native X11 window mapped", flush=True)
                def colors():
                    # Read only synthetic candidate-window pixels, not the desktop.
                    count = command(["/usr/bin/import", "-window", wid, "-resize", "64x64!", "-format", "%k", "info:"], env)
                    return int(count) > 8
                wait_for(colors)
                print("PASS: nonuniform native window pixels observed", flush=True)
                def geometry():
                    data = command(["/usr/bin/xdotool", "getwindowgeometry", "--shell", wid], env)
                    fields = dict(line.split("=", 1) for line in data.splitlines() if "=" in line)
                    return int(fields["WIDTH"]), int(fields["HEIGHT"])
                initial = geometry()
                command(["/usr/bin/xdotool", "windowsize", wid, "760", "540"], env)
                wait_for(lambda: geometry() == (760, 540) and geometry() != initial)
                wait_for(colors)
                assert app.poll() is None
                print("PASS: native window resized and nonuniform pixels observed", flush=True)
                command(["/usr/bin/xdotool", "windowactivate", "--sync", wid], env)
                command(["/usr/bin/xdotool", "key", "--clearmodifiers", "ctrl+q"], env)
                assert app.wait(timeout=10) == 0, "candidate did not exit cleanly"
                print("PASS: Ctrl+Q native action exited with code 0", flush=True)
            except BaseException:
                stop(app)
                log.seek(0, os.SEEK_END)
                log.seek(max(0, log.tell() - 8192))
                print("Synthetic candidate log tail:", log.read().decode("utf-8", "replace"), flush=True)
                raise
            finally:
                stop(app)
                stop(wm)
    print("PASS: disposable X11 smoke only; COSMIC/Wayland, IME and production release NOT APPROVED", flush=True)

if __name__ == "__main__":
    main()
