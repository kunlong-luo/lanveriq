#!/usr/bin/env python3
"""G05-LNX3 read-only installed idle Agent audit.

Requires a separately authorized lab installation to return PASS. On CI,
ordinary hosts without the package must return NOT_INSTALLED (exit 2).
No install/start/stop/enable/network/device discovery, no secrets or state read.
This is an audit *aid*, not Pop!_OS 24.04 physical L3 sign-off.
"""

import pathlib
import re
import stat
import subprocess
import sys

PACKAGE = "lanveriq-agent-offline-preview"
VERSION = "0.1.0~dev1"
UNIT_NAME = "lanveriq-agent.service"
UNIT_PATH = pathlib.Path("/usr/lib/systemd/system/lanveriq-agent.service")
BINARY_PATH = pathlib.Path("/usr/bin/lanveriq-agent")
SOURCE_UNIT = (
    pathlib.Path(__file__).resolve().parents[2]
    / "deploy/systemd/lanveriq-agent.service"
)

# systemctl show canonical D-Bus property values, not only unit-file text.
PROPERTIES = (
    "Id", "LoadState", "ActiveState", "UnitFileState", "NeedDaemonReload",
    "FragmentPath", "DropInPaths", "ExecStart", "DynamicUser",
    "PrivateNetwork", "IPAddressDeny", "IPAddressAllow", "SocketBindDeny",
    "RestrictAddressFamilies", "CapabilityBoundingSet", "AmbientCapabilities",
    "NoNewPrivileges", "ProtectSystem", "ProtectHome", "PrivateDevices",
    "StateDirectory", "StateDirectoryMode",
)
EXPECTED = {
    "Id": UNIT_NAME,
    "LoadState": "loaded",
    "ActiveState": "inactive",
    "UnitFileState": "disabled",
    "NeedDaemonReload": "no",
    "DropInPaths": "",
    "DynamicUser": "yes",
    "PrivateNetwork": "yes",
    "IPAddressDeny": "any",
    "IPAddressAllow": "",
    "SocketBindDeny": "any",
    "RestrictAddressFamilies": "AF_UNIX",
    "CapabilityBoundingSet": "",
    "AmbientCapabilities": "",
    "NoNewPrivileges": "yes",
    "ProtectSystem": "strict",
    "ProtectHome": "yes",
    "PrivateDevices": "yes",
    "StateDirectory": "lanveriq-agent",
}
EXEC_PATH = "/usr/bin/lanveriq-agent"
EXEC_ARGV = "/usr/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml"


class AuditFailure(ValueError):
    pass


def check(condition, reason):
    if not condition:
        raise AuditFailure(reason)


def parse_show(output):
    result = {}
    for line in output.splitlines():
        if "=" not in line:
            raise AuditFailure("unrecognized systemctl show line")
        key, value = line.split("=", 1)
        check(key in PROPERTIES, f"unexpected systemctl property: {key}")
        check(key not in result, f"duplicate systemctl property: {key}")
        result[key] = value
    check(set(result) == set(PROPERTIES), "missing systemctl properties")
    return result


def audit_properties(show_output):
    values = parse_show(show_output)
    for key, expected in EXPECTED.items():
        check(values[key] == expected, f"{key} not approved: {values[key]!r}")
    # Service path must be package-managed. /lib is a symlink on merged-/usr
    # distros but FragmentPath itself should be the canonical vendor path.
    check(values["FragmentPath"] == str(UNIT_PATH), "unexpected systemd FragmentPath")
    # systemd outputs a structured ExecStart record, not ExecStart unit syntax.
    # Do not accept a matching substring nested in an extra shell command.
    match = re.fullmatch(
        r"\{\s*path=([^;]+)\s*;\s*argv\[\]=([^;]+)\s*;"
        r".*\}", values["ExecStart"]
    )
    check(match is not None, "unrecognized systemctl ExecStart property")
    check(match.group(1).strip() == EXEC_PATH, "ExecStart path differs")
    check(match.group(2).strip() == EXEC_ARGV, "ExecStart arguments differ")
    mode = values["StateDirectoryMode"]
    check(bool(re.fullmatch(r"0?[0-7]{3,4}", mode)), "invalid StateDirectoryMode")
    check(int(mode, 8) == 0o700, "StateDirectoryMode not private")
    return values


def run_read_only(*command):
    result = subprocess.run(
        command, text=True, capture_output=True, check=False, timeout=12
    )
    if result.returncode:
        raise AuditFailure(f"read-only command unavailable or failed: {command[0]}")
    check(len(result.stdout) < 32 * 1024, "unexpected large command output")
    return result.stdout


def check_file(path, mode, expected_bytes=None, elf=False):
    check(not path.is_symlink(), f"package file is symlink: {path}")
    info = path.stat()
    check(stat.S_ISREG(info.st_mode), f"not a regular package file: {path}")
    check(info.st_uid == 0 and info.st_gid == 0, f"non-root package owner: {path}")
    check(stat.S_IMODE(info.st_mode) == mode, f"unexpected package mode: {path}")
    if expected_bytes is not None:
        check(path.read_bytes() == expected_bytes, f"unit source mismatch: {path}")
    if elf:
        with path.open("rb") as stream:
            check(stream.read(4) == b"\x7fELF", f"not an ELF executable: {path}")


def audit_installed():
    version = run_read_only(
        "dpkg-query", "-W", "-f", "${Status}\n${Version}\n", PACKAGE
    ).splitlines()
    check(version == ["install ok installed", VERSION], "package not installed or version drift")

    for path in (UNIT_PATH, BINARY_PATH):
        owned = run_read_only("dpkg-query", "-S", str(path)).strip()
        check(
            owned == f"{PACKAGE}: {path}",
            f"file not owned by expected package: {path}"
        )
    check_file(UNIT_PATH, 0o644, expected_bytes=SOURCE_UNIT.read_bytes())
    check_file(BINARY_PATH, 0o755, elf=True)

    properties = run_read_only(
        "systemctl", "show", UNIT_NAME, "--no-pager", "--all",
        *(f"--property={key}" for key in PROPERTIES),
    )
    audit_properties(properties)
    return (
        "PASS: read-only installed idle-agent inspection: exact package, "
        "unchanged unit/binary custody, no drop-ins, disabled/inactive, "
        "current systemd sandbox properties; NOT Pop!_OS L3 sign-off"
    )


def main():
    if len(sys.argv) != 1:
        print("FAIL: usage: python3 verify-installed-agent-idle.py", file=sys.stderr)
        return 2
    try:
        print(audit_installed())
        return 0
    except (AuditFailure, OSError, subprocess.TimeoutExpired) as exc:
        print(f"NOT_APPROVED: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
