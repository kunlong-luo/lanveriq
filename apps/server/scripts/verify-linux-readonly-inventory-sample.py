#!/usr/bin/env python3
"""CI-only corroboration of actual Linux OS, CPU and RAM from Rust's outbox.

Independent host facts: /etc/os-release, kernel uname, first /proc/cpuinfo
model name and /proc/meminfo MemTotal. No network or user/process/NIC/disk
enumeration, no upload, no arbitrary file path or disclosure of sampled values.
"""
import json
import os
import re
from pathlib import Path
import platform
import sys

class GateFailure(Exception):
    """Only literal source-authored gate labels; never host inventory values."""

def must(value: bool, name: str):
    if not value:
        raise GateFailure(name)

def first_value(path: str, key: str) -> str | None:
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if ":" in line and line.split(":", 1)[0].strip() == key:
                return line.split(":", 1)[1].strip()
    return None

def main() -> None:
    must(os.environ.get("GITHUB_ACTIONS") == "true", "CI-only")
    must(os.environ.get("RUNNER_OS") == "Linux", "Linux-only")
    must(os.environ.get("LANVERIQ_SYNTHETIC_INVENTORY_CHAIN") == "1",
         "inventory chain not enabled")
    must(os.environ.get("LANVERIQ_SYNTHETIC_LINUX_READONLY_SAMPLE") == "1",
         "explicit sampling consent missing")
    must(len(sys.argv) == 2, "expected private inventory path")
    path = Path(sys.argv[1])
    must(path.is_absolute() and str(path).startswith("/tmp/") and
         path.name == "synthetic-inventory-request.json" and path.is_file(),
         "sample must remain under a private disposable CI directory")
    must(path.stat().st_size <= 128 * 1024, "oversized sample")
    data = json.loads(path.read_text(encoding="utf-8"))
    must(data.get("schema_version") == 1 and
         data.get("device_id") == "00000000-0000-7000-8000-000000000001" and
         data.get("inventory_revision") == 1, "bound synthetic CI identity")
    must(set(data) == {"schema_version", "snapshot_id", "inventory_revision",
                       "device_id", "observed_at", "hardware", "os",
                       "interfaces", "volumes", "capabilities"},
         "unexpected transmitted field")
    must(data.get("interfaces") == [] and data.get("volumes") == [],
         "NIC or mount metadata must not be read or transmitted")
    hw = data["hardware"]
    cpu = hw.get("cpu")
    memory = hw.get("memory")
    must(hw.get("manufacturer") is None and hw.get("model") is None and
         isinstance(cpu, dict) and isinstance(memory, dict),
         "only CPU and memory whitelisted")
    must(isinstance(cpu.get("model"), str) and cpu.get("model") and
         isinstance(cpu.get("logical_cores"), int) and
         cpu["logical_cores"] > 0, "actual CPU details missing")
    must(isinstance(memory.get("total_bytes"), int) and
         memory["total_bytes"] > 0, "actual memory missing")
    actual_brand = first_value("/proc/cpuinfo", "model name")
    must(actual_brand is not None and cpu["model"].strip() == actual_brand,
         "Rust CPU brand differs from kernel CPU")
    memtotal = first_value("/proc/meminfo", "MemTotal")
    must(memtotal is not None and re.fullmatch(r"\d+ kB", memtotal) is not None,
         "kernel RAM reference missing")
    total = int(memtotal.split()[0]) * 1024
    must(abs(memory["total_bytes"] - total) <= max(32 * 1024 * 1024, total // 100),
         "sysinfo RAM inconsistent with kernel MemTotal")
    os_values = {}
    with open("/etc/os-release", encoding="utf-8") as source:
        for line in source:
            if line.startswith(("NAME=", "VERSION_ID=")):
                key, val = line.rstrip("\n").split("=", 1)
                os_values[key] = val.strip('"')
    observed_os = data["os"]
    must(observed_os.get("family") == "linux" and
         observed_os.get("name") == os_values.get("NAME") and
         observed_os.get("version") == os_values.get("VERSION_ID") and
         observed_os.get("kernel_release") == platform.uname().release and
         observed_os.get("arch") == platform.machine(),
         "Rust Linux OS differs from the actual CI host")
    caps = data["capabilities"]
    must(caps.get("hardware_inventory") == "available" and
         all(caps.get(k) == "not_collected"
             for k in ("network_interfaces", "mounted_volumes",
                       "desktop_session", "manufacturer_model")),
         "non-whitelist capabilities were read")
    must(observed_os.get("name") != "Synthetic Inventory Linux",
         "fixed fixture misrepresented as live host")
    print("PASS: independently corroborated Rust OS/CPU/RAM-only read-only Linux host sample against /etc/os-release, uname, /proc/cpuinfo and /proc/meminfo; no NIC/mount/user/process data.")

if __name__ == "__main__":
    try:
        main()
    except GateFailure as failure:
        # Every gate name above is a fixed source-authored literal. Never
        # expose sampled host values, private paths, JSON or error repr.
        print("FAIL: isolated Linux OS/CPU/RAM source corroboration: " +
              str(failure), file=sys.stderr)
        raise SystemExit(1) from None
    except Exception:
        print("FAIL: isolated Linux OS/CPU/RAM source runtime", file=sys.stderr)
        raise SystemExit(1) from None
