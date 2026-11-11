#!/usr/bin/env python3
"""Fail-closed systemd unit policy check for Pop!_OS 24.04 Linux-first Agent.

Static policy only. No systemctl enable/start, no real endpoint, privileges,
secrets, enrollment, device monitoring or network connections.
"""
import configparser
import pathlib
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: verify-systemd-popos24.py UNIT")
unit = pathlib.Path(sys.argv[1])
config = configparser.ConfigParser(interpolation=None, strict=True, delimiters=("=",))
config.optionxform = str
with unit.open(encoding="utf-8") as handle:
    config.read_file(handle)
assert "Service" in config and "Unit" in config, "Missing core systemd unit sections"
service = config["Service"]
required = {
    "Type": "exec",
    "DynamicUser": "yes",
    "StateDirectory": "lanveriq-agent",
    "StateDirectoryMode": "0700",
    "RuntimeDirectory": "lanveriq-agent",
    "RuntimeDirectoryMode": "0700",
    "UMask": "0077",
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
for name, expected in required.items():
    actual = service.get(name)
    assert actual == expected, f"{name} must equal {expected!r}, got {actual!r}"
assert service.get("ExecStart") == (
    "/usr/local/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml"
), "Unexpected command or remote/autonomous service mode"
assert config["Unit"].get("ConditionPathExists") == "/etc/lanveriq-agent/agent.toml"
for bad in (
    "ExecStartPre",
    "ExecStartPost",
    "ExecReload",
    "Environment",
    "EnvironmentFile",
    "RootDirectory",
    "LoadCredential",
    "LoadCredentialEncrypted",
    "Sockets",
    "User",
    "SupplementaryGroups",
):
    assert bad not in service, f"Unexpected service-level or privileged override: {bad}"
assert config.get("Install", "WantedBy") == "multi-user.target"
assert "After" not in config["Unit"], "Idle mode must not need networking"
print("PASS: Pop!_OS 24.04 Linux-first systemd idle-only policy, no IP networking, private state and zero capabilities")
