#!/usr/bin/env python3
"""Fail-closed static audit of the offline Pop!_OS 24.04 systemd unit TEMPLATE.

This is an L1 source policy check, NOT a check of installed units, systemd
drop-ins or live hosts. The validation must work under python -O/-OO:
never put a security condition in an 'assert' statement.
No systemctl, real endpoint, network, enrollment or collection.
"""

import configparser
import pathlib
import sys


REQUIRED_SERVICE = {
    "Type": "exec",
    "DynamicUser": "yes",
    "StateDirectory": "lanveriq-agent",
    "StateDirectoryMode": "0700",
    "RuntimeDirectory": "lanveriq-agent",
    "RuntimeDirectoryMode": "0700",
    "UMask": "0077",
    "ExecStart": "/usr/local/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml",
    "Restart": "on-failure",
    "RestartSec": "10s",
    "TimeoutStopSec": "15s",
    "NoNewPrivileges": "yes",
    "CapabilityBoundingSet": "",
    "AmbientCapabilities": "",
    "ProtectSystem": "strict",
    "ProtectHome": "yes",
    "PrivateTmp": "yes",
    "PrivateDevices": "yes",
    "PrivateNetwork": "yes",
    "IPAddressDeny": "any",
    "SocketBindDeny": "any",
    "RestrictAddressFamilies": "AF_UNIX",
    "ProtectKernelTunables": "yes",
    "ProtectKernelModules": "yes",
    "ProtectKernelLogs": "yes",
    "ProtectControlGroups": "yes",
    "ProtectClock": "yes",
    "ProtectHostname": "yes",
    "LockPersonality": "yes",
    "RestrictSUIDSGID": "yes",
    "RestrictRealtime": "yes",
    "RestrictNamespaces": "yes",
    "SystemCallArchitectures": "native",
    "MemoryDenyWriteExecute": "yes",
}
REQUIRED_UNIT = {
    "Description": "Lanveriq Agent (idle, offline, synthetic-safe)",
    "Documentation": "https://github.com/kunlong-luo/lanveriq",
    "ConditionPathExists": "/etc/lanveriq-agent/agent.toml",
}
REQUIRED_INSTALL = {"WantedBy": "multi-user.target"}
REQUIRED_SECTIONS = {
    "Unit": REQUIRED_UNIT,
    "Service": REQUIRED_SERVICE,
    "Install": REQUIRED_INSTALL,
}


def fail(message):
    raise SystemExit(f"FAIL: Pop!_OS systemd idle-only policy: {message}")


def check_unit(path):
    config = configparser.ConfigParser(
        interpolation=None, strict=True, delimiters=("=",)
    )
    config.optionxform = str
    try:
        with path.open(encoding="utf-8") as handle:
            config.read_file(handle)
    except (OSError, UnicodeError, configparser.Error) as exc:
        fail(f"unreadable or malformed systemd template: {exc}")

    if config.defaults():
        fail("unexpected [DEFAULT] directives can silently alter service fields")
    if set(config.sections()) != set(REQUIRED_SECTIONS):
        fail(
            f"unexpected/missing sections: {sorted(set(config.sections()) ^ set(REQUIRED_SECTIONS))}"
        )

    # Exact allowlist is deliberate: an extra directive such as
    # IPAddressAllow=, ExecStartPre=, EnvironmentFile=, JoinsNamespaceOf=,
    # RootDirectory= or User= must never escape review merely because the
    # existing security directives still appear in the file.
    for section, expected in REQUIRED_SECTIONS.items():
        actual = dict(config.items(section, raw=True))
        if set(actual) != set(expected):
            missing = sorted(set(expected) - set(actual))
            extra = sorted(set(actual) - set(expected))
            fail(f"[{section}] directive mismatch: missing={missing} unexpected={extra}")
        for key, required in expected.items():
            if actual[key] != required:
                fail(f"[{section}] {key} must be {required!r}, got {actual[key]!r}")


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    if len(argv) != 1:
        fail("usage: verify-systemd-popos24.py UNIT")
    check_unit(pathlib.Path(argv[0]))
    print(
        "PASS: static idle-only Pop!_OS systemd TEMPLATE; exact directives, "
        "private state, zero capabilities and all IP networking denied "
        "(not an installed-unit/drop-in or Pop!_OS L3 test)"
    )


if __name__ == "__main__":
    main()
