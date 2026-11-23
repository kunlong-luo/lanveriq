#!/usr/bin/env python3
"""L2B-5: inspect read-only GUI ELF direct ABI with GNU readelf, never execute it.

All evidence is local, UNSIGNED and NOT an approved release or runtime license audit.
This does not evaluate dlopen(), transitive packages, loader config or Pop!_OS 24.
"""
import argparse
import importlib.util
import json
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "desktop_payload_authority", HERE / "verify_desktop_payload_evidence.py"
)
payload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(payload)

require = payload.require
GUI = "usr/bin/lanveriq-desktop"
# Reviewed for the *synthetic/native GUI-only candidate*, not an ABI guarantee.
# Any new direct SONAME must be independently investigated before admission.
APPROVED_DIRECT_SONAMES = frozenset((
    "libc.so.6", "libm.so.6", "libgcc_s.so.1", "libstdc++.so.6",
    "libpthread.so.0", "libdl.so.2", "librt.so.1", "libresolv.so.2",
    "libwebkit2gtk-4.1.so.0", "libjavascriptcoregtk-4.1.so.0",
    "libgtk-3.so.0", "libgdk-3.so.0", "libgdk_pixbuf-2.0.so.0",
    "libgio-2.0.so.0", "libglib-2.0.so.0", "libgobject-2.0.so.0",
    "libgthread-2.0.so.0", "libpango-1.0.so.0", "libpangocairo-1.0.so.0",
    "libharfbuzz.so.0", "libatk-1.0.so.0", "libatspi.so.0",
    "libcairo.so.2", "libcairo-gobject.so.2", "libfontconfig.so.1",
    "libfreetype.so.6", "libfribidi.so.0", "libepoxy.so.0",
    "libayatana-appindicator3.so.1", "libappindicator3.so.1",
    "libdbusmenu-gtk3.so.4", "librsvg-2.so.2", "libnotify.so.4",
    "libdbus-1.so.3", "libsoup-3.0.so.0", "libssl.so.3",
    "libcrypto.so.3", "libz.so.1", "libX11.so.6", "libXext.so.6",
    "libXrandr.so.2", "libXrender.so.1", "libXcursor.so.1",
    "libXfixes.so.3", "libXi.so.6", "libXcomposite.so.1",
    "libXdamage.so.1", "libXinerama.so.1", "libXss.so.1",
    "libxcb.so.1", "libXau.so.6", "libpixman-1.so.0",
))
APPROVED_INTERPRETERS = frozenset((
    "/lib64/ld-linux-x86-64.so.2",
    "/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2",
))
FORBIDDEN_DYNAMIC_TAGS = frozenset((
    "RPATH", "RUNPATH", "AUDIT", "DEPAUDIT", "FILTER",
    "AUXILIARY", "TEXTREL",
))
DYNAMIC_ENTRY = re.compile(r"^\s*0x[0-9a-fA-F]+\s+\(([^)]+)\)\s*(.*)$")
SONAME_FIELD = re.compile(r"^Shared library: \[([^\]\r\n]+)\]$")
INTERPRETER_FIELD = re.compile(r"\[Requesting program interpreter: ([^\]\r\n]+)\]")
SONAME_SHAPE = re.compile(r"lib[A-Za-z0-9_.+-]+\.so(?:\.[0-9]+)*\Z")


def inspect_readelf_output(raw):
    """Parse text returned by the standard GNU readelf, no custom ELF binary decoder."""
    require(isinstance(raw, bytes) and 0 < len(raw) < 1024 * 1024,
            "missing or oversized ELF inspection")
    try:
        report = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError("non-UTF8 readelf output") from exc
    require(re.search(r"^\s*Class:\s*ELF64\s*$", report, flags=re.M) is not None,
            "not ELF64")
    require(re.search(r"^\s*Data:\s*2's complement, little endian\s*$",
                      report, flags=re.M) is not None, "wrong ELF endianness")
    require(re.search(r"^\s*Machine:\s*Advanced Micro Devices X86-64\s*$",
                      report, flags=re.M) is not None, "wrong ELF architecture")
    require(re.search(r"^\s*Type:\s*DYN\s", report, flags=re.M) is not None,
            "GUI must be a PIE dynamic ELF")
    interp = INTERPRETER_FIELD.findall(report)
    require(len(interp) == 1 and interp[0] in APPROVED_INTERPRETERS,
            "unapproved ELF program interpreter")
    stack = [line.strip().split() for line in report.splitlines()
             if line.lstrip().startswith("GNU_STACK ")]
    require(len(stack) == 1 and len(stack[0]) >= 8
            and stack[0][-2] in ("R", "RW"), "executable or missing GNU_STACK")
    require("Dynamic section at offset" in report, "missing ELF dynamic section")
    needed = []
    forbidden = []
    for line in report.splitlines():
        match = DYNAMIC_ENTRY.fullmatch(line.rstrip())
        if match is None:
            continue
        tag, value = match.groups()
        if tag == "NEEDED":
            lib = SONAME_FIELD.fullmatch(value.strip())
            require(lib is not None, "malformed direct shared-library dependency")
            soname = lib.group(1)
            require(SONAME_SHAPE.fullmatch(soname) is not None
                    and ".." not in soname and "/" not in soname,
                    "unsafe shared library path/name")
            require(soname not in needed, "duplicate ELF NEEDED dependency")
            needed.append(soname)
        elif tag in FORBIDDEN_DYNAMIC_TAGS or (
            tag.startswith("FLAGS") and "TEXTREL" in value
        ):
            forbidden.append(tag)
    require(not forbidden, "forbidden dynamic loader metadata: " + ",".join(forbidden))
    require(needed and "libc.so.6" in needed, "missing expected libc dependency")
    unknown = sorted(set(needed) - APPROVED_DIRECT_SONAMES)
    require(not unknown, "unreviewed direct SONAME(s): " + ", ".join(unknown))
    return {"elf_class": "ELF64", "machine": "x86_64", "elf_type": "DYN_PIE",
            "interpreter": interp[0], "gnu_stack": stack[0][-2],
            "direct_needed_sonames": sorted(needed)}


def inspect_binary(binary):
    binary = pathlib.Path(binary)
    require(binary.is_file() and not binary.is_symlink(), "missing or linked GUI binary")
    require(1024 <= binary.stat().st_size < 120 * 1024 * 1024,
            "invalid native binary length")
    try:
        result = subprocess.run(
            ["readelf", "--wide", "--file-header", "--program-headers",
             "--dynamic", str(binary)],
            shell=False, check=False, capture_output=True, timeout=15,
            env={"LC_ALL": "C", "LANG": "C", "PATH": "/usr/bin:/bin"},
        )
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise ValueError("GNU readelf unavailable or timed out") from exc
    require(result.returncode == 0, "GNU readelf refused/failed")
    return inspect_readelf_output(result.stdout)


def declared_debian_dependencies(package):
    metadata = payload.deb.exact_control(
        payload.deb.run("dpkg-deb", "--field", str(package))
    )
    raw = metadata.get("Depends", "")
    deps = []
    for field in raw.split(","):
        if not field.strip():
            continue
        match = payload.deb.DEPENDENCY.fullmatch(field.strip())
        require(match is not None, "unreviewed Debian Depends")
        deps.append(match.group(1))
    return sorted(set(deps))


def evidence(package, extracted, payload_spdx, source_receipt, source_spdx,
             inputs, source_sha, run_id, attempt, payload_evidence):
    package = pathlib.Path(package)
    root = pathlib.Path(extracted)
    prior = payload.verify(
        payload_evidence, package, root, payload_spdx, source_receipt,
        source_spdx, inputs, source_sha, run_id, attempt,
    )
    require(prior.startswith("PASS:"), "invalid payload evidence chain")
    elf = root / GUI
    report = inspect_binary(elf)
    # Prior payload verifier already checked the ELF byte-for-byte against .deb.
    return {
        "schema": "lanveriq.desktop.untrusted-runtime-needs.v1",
        "repository": "kunlong-luo/lanveriq",
        "source_sha": source_sha, "run_id": run_id, "attempt": attempt,
        "deb_sha256": payload.base.digest(package),
        "payload_evidence_sha256": payload.base.digest(payload_evidence),
        "elf_sha256": payload.base.digest(elf),
        "elf": report,
        "declared_debian_dependencies": declared_debian_dependencies(package),
        "transitive_runtime_resolution": "NOT_VERIFIED",
        "pop_os_runtime_versions": "NOT_TESTED",
        "shared_library_license_review": "OPEN",
        "scope": "direct_ELF_NEEDED_and_deb_control_only_not_complete_runtime",
        "signature": "UNSIGNED_UNTRUSTED",
        "release_approved": False, "install_authorized": False,
    }


def verify(output, *args):
    actual = payload.base.json_read(output, 16384)
    require(actual == evidence(*args), "runtime evidence mismatch or forged authority")
    return "PASS: ELF direct ABI evidence only; LICENSE/RELEASE/INSTALL NOT APPROVED"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("create", "verify"))
    for name in ("package", "extracted", "payload-spdx", "source-receipt",
                 "source-spdx", "payload-evidence", "cargo-lock", "pnpm-lock",
                 "tauri-config", "output"):
        p.add_argument("--" + name, required=True, type=pathlib.Path)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--run-id", required=True, type=int)
    p.add_argument("--attempt", required=True, type=int)
    x = p.parse_args()
    inputs = {"Cargo.lock": x.cargo_lock,
              "pnpm-lock.yaml": x.pnpm_lock, "tauri.conf.json": x.tauri_config}
    args = (x.package, x.extracted, x.payload_spdx, x.source_receipt,
            x.source_spdx, inputs, x.source_sha, x.run_id, x.attempt,
            x.payload_evidence)
    if x.mode == "create":
        require(not x.output.exists(), "refuse to overwrite runtime evidence")
        obj = evidence(*args)
        x.output.write_text(json.dumps(obj, sort_keys=True, indent=2) + "\n")
        print("PASS: untrusted runtime NEEDED/Depends report; NO LICENSE OR RELEASE")
    else:
        print(verify(x.output, *args))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        sys.exit(2)
