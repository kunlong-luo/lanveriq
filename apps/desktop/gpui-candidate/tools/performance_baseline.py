#!/usr/bin/env python3
"""Same-runner debug/release baseline, synthetic X11 only, no Agent or install."""
import json
import os
import pathlib
import re
import statistics
import subprocess
import time

from package_audit import ROOT, NAME, check, digest
from window_smoke import main as smoke

PROFILES = ("debug", "release")
SAMPLES = 3
MAX_SMAPS = 65536

def parse_memory(raw):
    check(len(raw) <= MAX_SMAPS, "smaps_rollup bound")
    values = {}
    for line in raw.splitlines():
        match = re.fullmatch(r"(Rss|Pss):\s+(\d+)\s+kB", line)
        if match:
            key, value = match.groups()
            check(key not in values and 0 < int(value) < 2**32, "invalid memory sample")
            values[key] = int(value)
    check(set(values) == {"Rss", "Pss"} and values["Pss"] <= values["Rss"],
          "incomplete or inconsistent memory sample")
    return {"rss_kib": values["Rss"], "pss_kib": values["Pss"]}

def measure(process, binary, elapsed_ns):
    check(type(elapsed_ns) is int and 0 < elapsed_ns <= 120_000_000_000,
          "invalid startup observation")
    time.sleep(1.0)  # fixed delay after first positive pixel probe; not peak memory
    proc = pathlib.Path("/proc") / str(process.pid)
    expected = binary.resolve(strict=True)
    check(process.poll() is None and (proc / "exe").resolve(strict=True) == expected,
          "expected exact running synthetic child")
    with (proc / "smaps_rollup").open("r", encoding="ascii") as stream:
        raw = stream.read(MAX_SMAPS + 1)
    check(process.poll() is None and (proc / "exe").resolve(strict=True) == expected,
          "child changed during memory observation")
    return {"spawn_to_nonuniform_pixel_probe_ms": round(elapsed_ns / 1_000_000, 3),
            **parse_memory(raw)}

def summary(samples):
    check(len(samples) == SAMPLES, "missing performance samples")
    keys = ("spawn_to_nonuniform_pixel_probe_ms", "rss_kib", "pss_kib")
    return {key: {"median": statistics.median(s[key] for s in samples),
                  "min": min(s[key] for s in samples), "max": max(s[key] for s in samples)}
            for key in keys}

def main():
    check(os.getuid() != 0 and os.geteuid() != 0, "non-root CI only")
    rows = {}
    for profile in PROFILES:
        binary = ROOT / "target" / profile / NAME
        check(binary.is_file() and not binary.is_symlink(), "missing exact binary")
        dynamic = subprocess.check_output(["/usr/bin/readelf", "-d", str(binary)], text=True, timeout=20)
        needed = sorted(set(re.findall(r"\(NEEDED\).*\[([^\]]+)\]", dynamic)))
        allowed = {"libxcb.so.1", "libxkbcommon.so.0", "libxkbcommon-x11.so.0", "libgcc_s.so.1",
                   "libm.so.6", "libc.so.6", "ld-linux-x86-64.so.2"}
        check("libc.so.6" in needed and set(needed) <= allowed, "unexpected direct ELF dependency")
        check(not any(key in dynamic for key in ("(RPATH)", "(RUNPATH)")), "unreviewed runtime path")
        rows[profile] = {"elf_sha256": digest(binary), "elf_bytes": binary.stat().st_size,
                         "direct_needed": needed, "samples": []}
    for index in range(SAMPLES):
        # Alternate first profile to reduce ordering bias; no filesystem cache flush.
        for profile in (PROFILES if index % 2 == 0 else PROFILES[::-1]):
            binary = ROOT / "target" / profile / NAME
            sample = smoke(binary=binary, evidence=False, measure=measure)
            check(digest(binary) == rows[profile]["elf_sha256"], "benchmark ELF changed")
            rows[profile]["samples"].append(sample)
            print("PERF", profile, index + 1, json.dumps(sample, sort_keys=True), flush=True)
    for profile in PROFILES:
        rows[profile]["summary"] = summary(rows[profile]["samples"])
        print("PERF summary", profile, json.dumps(rows[profile]["summary"], sort_keys=True))
    report = {"schema": "lanveriq.gpui.untrusted-x11-performance-v1",
              "source_sha": os.environ["GITHUB_SHA"], "lock_sha256": digest(ROOT / "Cargo.lock"),
              "profile_settings": "Cargo dev debug=0 versus default Cargo release",
              "scope": "SAME_RUNNER_X11_SOFTWARE_RENDERED_SINGLE_PROCESS_WARM_CACHE_UNCONTROLLED",
              "startup_metric": "Popen to first positive nonuniform-window-pixel probe including polling overhead",
              "memory_metric": "child smaps_rollup RSS/PSS one second after pixel probe, not peak or process tree",
              "samples_per_profile": SAMPLES, "profiles": rows,
              "tauri_comparison": "NOT_RUN", "physical_cosmic": "NOT_RUN",
              "install_authorized": False, "release_approved": False}
    with (ROOT / "candidate-performance.json").open("x", encoding="utf-8") as out:
        json.dump(report, out, indent=2, sort_keys=True)
    print("PASS: six clean synthetic native window runs; not a Tauri comparison or physical performance proof")

if __name__ == "__main__":
    main()
