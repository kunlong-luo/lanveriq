#!/usr/bin/env python3
"""Paired Release X11 comparison, disposable non-root CI only.
No GUI/product package install, network endpoint, Agent action or screenshot archive.
"""
import json
import os
import pathlib
import signal
import statistics
import subprocess
import sys
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "gpui-candidate/tools"))
from window_smoke import command, wait_for, stop
from performance_baseline import parse_memory, MAX_SMAPS
from package_audit import digest, check

TARGETS = {
    "gpui": (ROOT / "gpui-candidate/target/release/lanveriq-desktop-gpui-candidate",
             "^Lanveriq Desktop - Offline Candidate$"),
    "tauri": (ROOT / "src-tauri/target/release/lanveriq-desktop",
              "^Lanveriq Desktop · Offline Preview$"),
}
SAMPLES = 3
GEOMETRY = (1000, 700)

def members(rows, root):
    """Same session plus descendants, excluding zombies; no unrelated process names."""
    selected = {pid for pid, parent, sid, state in rows if sid == root or pid == root}
    while True:
        new = selected | {pid for pid, parent, sid, state in rows if parent in selected}
        if new == selected:
            break
        selected = new
    return {pid for pid, _, _, state in rows if pid in selected and not state.startswith("Z")}

def process_rows():
    raw = subprocess.check_output(["/usr/bin/ps", "-eo", "pid=,ppid=,sid=,stat="],
                                  text=True, timeout=5)
    check(len(raw) < 2_000_000, "process table exceeds bound")
    return [(int(p), int(pp), int(s), state) for p, pp, s, state in
            (line.split() for line in raw.splitlines())]

def sample_tree(app, binary):
    root = app.pid
    proc = pathlib.Path("/proc")
    check(app.poll() is None and (proc / str(root) / "exe").resolve() == binary.resolve(),
          "root executable changed")
    for _ in range(4):
        before = members(process_rows(), root)
        check(root in before and len(before) <= 64, "unexpected process tree")
        readings = []
        webkit = 0
        try:
            for pid in sorted(before):
                p = proc / str(pid)
                exe = (p / "exe").resolve(strict=True).name
                webkit += int(exe in ("WebKitWebProcess", "WebKitNetworkProcess", "WebKitGPUProcess"))
                with (p / "smaps_rollup").open(encoding="ascii") as f:
                    readings.append(parse_memory(f.read(MAX_SMAPS + 1)))
        except (FileNotFoundError, ProcessLookupError):
            continue
        if before == members(process_rows(), root) and app.poll() is None:
            return {"process_count": len(before), "webkit_process_count": webkit,
                    "root_rss_kib": readings[sorted(before).index(root)]["rss_kib"],
                    "root_pss_kib": readings[sorted(before).index(root)]["pss_kib"],
                    "tree_rss_kib": sum(r["rss_kib"] for r in readings),
                    "tree_pss_kib": sum(r["pss_kib"] for r in readings)}
    raise RuntimeError("unstable process tree")

def run_one(name, binary, title, env_base):
    with tempfile.TemporaryDirectory(prefix="lanveriq-paired-") as temp:
        home = pathlib.Path(temp)
        runtime = home / "runtime"
        runtime.mkdir(mode=0o700)
        env = {**env_base, "HOME": str(home), "XDG_RUNTIME_DIR": str(runtime),
               "XDG_CONFIG_HOME": str(home / "config"), "XDG_CACHE_HOME": str(home / "cache"),
               "XDG_DATA_HOME": str(home / "data"), "GDK_BACKEND": "x11"}
        app = None
        stage = "launch"
        with (home / "app.log").open("w+b") as log:
            try:
                started = time.monotonic_ns()
                app = subprocess.Popen([str(binary)], cwd=home, env=env, start_new_session=True,
                                       stdout=log, stderr=subprocess.STDOUT)
                def mapped():
                    if app.poll() is not None:
                        raise RuntimeError("application exited before window")
                    ids = command(["/usr/bin/xdotool", "search", "--onlyvisible", "--name", title], env).splitlines()
                    if len(ids) != 1:
                        return None
                    info = command(["/usr/bin/xwininfo", "-id", ids[0]], env)
                    return ids[0] if "Map State: IsViewable" in info else None
                stage = "map"
                wid = wait_for(mapped, 30)
                def pixels():
                    return int(command(["/usr/bin/import", "-window", wid, "-resize", "64x64!",
                                        "-format", "%k", "info:"], env)) > 8
                stage = "pixels"
                wait_for(pixels, 30)
                latency = round((time.monotonic_ns()-started)/1_000_000, 3)
                def geometry():
                    fields = dict(line.split("=", 1) for line in command(
                        ["/usr/bin/xdotool", "getwindowgeometry", "--shell", wid], env).splitlines() if "=" in line)
                    return int(fields["WIDTH"]), int(fields["HEIGHT"])
                initial = geometry()
                stage = "resize"
                command(["/usr/bin/xdotool", "windowsize", wid, *map(str, GEOMETRY)], env)
                wait_for(lambda: geometry() == GEOMETRY)
                wait_for(pixels)
                time.sleep(1)
                stage = "memory"
                memory = sample_tree(app, binary)
                if name == "tauri":
                    check(memory["webkit_process_count"] > 0, "WebKit process not observed")
                stage = "wm_close"
                command(["/usr/bin/xdotool", "windowactivate", "--sync", wid], env)
                command(["/usr/bin/xdotool", "key", "--clearmodifiers", "alt+F4"], env)
                check(app.wait(timeout=15) == 0, "window manager close nonzero")
                wait_for(lambda: not members(process_rows(), app.pid), 10)
                return {"status": "PASS", "startup_ms": latency, "initial_geometry": list(initial),
                        "measured_geometry": list(GEOMETRY), "wm_close": "PASS", **memory}
            except (Exception,) as exc:
                # Keep only bounded synthetic diagnostics; never upload log contents.
                log.seek(0, os.SEEK_END)
                log.seek(max(0, log.tell()-8192))
                tail = log.read().decode("utf-8", "replace")
                return {"status": "FAIL", "stage": stage, "error_type": type(exc).__name__,
                        "exit_code": app.poll() if app else None,
                        "gdk_pixbuf_length_panic": "data.len() must fit the width, height, and row_stride" in tail}
            finally:
                if app:
                    if members(process_rows(), app.pid):
                        try:
                            os.killpg(app.pid, signal.SIGTERM)
                        except ProcessLookupError:
                            pass
                    stop(app)
                    # Refuse continuation if any same-session process survived.
                    if members(process_rows(), app.pid):
                        try:
                            os.killpg(app.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        wait_for(lambda: not members(process_rows(), app.pid), 5)

def summarize(samples):
    check(len(samples) == SAMPLES and all(x["status"] == "PASS" for x in samples), "incomplete samples")
    return {k: {"min": min(x[k] for x in samples), "median": statistics.median(x[k] for x in samples),
                "max": max(x[k] for x in samples)}
            for k in ("startup_ms", "root_rss_kib", "root_pss_kib", "tree_rss_kib", "tree_pss_kib", "process_count")}

def main():
    check(os.getuid() != 0 and os.geteuid() != 0, "non-root CI only")
    out = pathlib.Path(sys.argv[1])
    rows = {}
    for name, (binary, title) in TARGETS.items():
        check(binary.is_file() and not binary.is_symlink(), "missing exact release executable")
        rows[name] = {"elf_bytes": binary.stat().st_size, "elf_sha256": digest(binary), "samples": []}
    report = {"schema": "lanveriq.desktop.paired-release-x11-v1", "source_sha": os.environ["GITHUB_SHA"],
              "gpui_lock_sha256": digest(ROOT / "gpui-candidate/Cargo.lock"),
              "tauri_lock_sha256": digest(ROOT / "src-tauri/Cargo.lock"),
              "pnpm_lock_sha256": digest(ROOT / "pnpm-lock.yaml"),
              "scope": "SAME_RUNNER_RELEASE_X11_SOFTWARE_RENDERED_DEFAULT_STARTUP_FIXED_RESIZE",
              "memory_scope": "stable live descendant and same-session RSS sum/PSS sum one second after resize; external services excluded",
              "startup_scope": "Popen to nonuniform client pixels at each app default window size; uncontrolled cache",
              "samples_per_app": SAMPLES, "apps": rows, "physical_cosmic": "NOT_RUN",
              "install_authorized": False, "release_approved": False}
    with tempfile.TemporaryDirectory(prefix="lanveriq-wm-") as temp:
        home = pathlib.Path(temp)
        runtime = home / "runtime"
        runtime.mkdir(mode=0o700)
        env = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "HOME": str(home),
               "XDG_RUNTIME_DIR": str(runtime), "XDG_SESSION_TYPE": "x11", "XDG_CURRENT_DESKTOP": "OPENBOX",
               "DISPLAY": os.environ["DISPLAY"], "XAUTHORITY": os.environ["XAUTHORITY"],
               "DBUS_SESSION_BUS_ADDRESS": os.environ["DBUS_SESSION_BUS_ADDRESS"],
               "LIBGL_ALWAYS_SOFTWARE": "1", "RUST_BACKTRACE": "1"}
        wm = subprocess.Popen(["/usr/bin/openbox"], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            wait_for(lambda: "window id #" in command(["/usr/bin/xprop", "-root", "_NET_SUPPORTING_WM_CHECK"], env))
            for i in range(SAMPLES):
                for name in (("gpui", "tauri") if i % 2 == 0 else ("tauri", "gpui")):
                    binary, title = TARGETS[name]
                    sample = run_one(name, binary, title, env)
                    check(digest(binary) == rows[name]["elf_sha256"], "executable changed")
                    rows[name]["samples"].append(sample)
                    print(name, i+1, json.dumps(sample, sort_keys=True), flush=True)
            for name, row in rows.items():
                if all(s["status"] == "PASS" for s in row["samples"]):
                    row["summary"] = summarize(row["samples"])
        finally:
            stop(wm)
            out.write_text(json.dumps(report, indent=2, sort_keys=True)+"\n")
    check(all("summary" in row for row in rows.values()), "paired comparison incomplete")

if __name__ == "__main__":
    main()
