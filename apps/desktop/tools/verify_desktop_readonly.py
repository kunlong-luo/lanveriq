#!/usr/bin/env python3
"""Offline Tauri preview source authority: no privilege, installer, tray or network.

This is a source gate, NOT a sandbox for hostile native code or a signed
software-supply-chain proof. Fail closed on unexpected Tauri commands/plugins.
"""
import json
import pathlib
import re
import sys
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[3]
DESKTOP = ROOT / "apps/desktop"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(root=DESKTOP):
    native = root / "src-tauri"
    tauri = json.loads((native / "tauri.conf.json").read_text(encoding="utf-8"))
    permissions = json.loads(
        (native / "capabilities/default.json").read_text(encoding="utf-8")
    )
    manifest = tomllib.loads(
        (native / "Cargo.toml").read_text(encoding="utf-8")
    )
    icon = native / "icons/icon.png"
    require(
        icon.is_file() and icon.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"),
        "missing or invalid native launcher icon (not a resident tray)",
    )
    native_code = (native / "src/lib.rs").read_text(encoding="utf-8")
    react_code = (root / "src/DesktopApp.tsx").read_text(encoding="utf-8")
    safety = (root / "src/safety.ts").read_text(encoding="utf-8")

    require(tauri["productName"] == "lanveriq-desktop", "desktop package identity drift")
    require(tauri["bundle"]["active"] is False, "bundle must stay OFF")
    require(tauri["bundle"]["targets"] == [], "no installer bundle targets yet")
    require(tauri["build"]["frontendDist"] == "../dist", "unexpected asset target")
    require(tauri["app"].get("trayIcon") is None, "resident tray forbidden")
    require(not tauri.get("plugins"), "Tauri plugins need separate review")
    require(len(tauri["app"]["windows"]) == 1, "only one window allowed")
    require(tauri["app"]["windows"][0]["label"] == "main", "unknown desktop window")
    require(
        permissions["permissions"] == ["core:default"]
        and permissions["windows"] == ["main"],
        "unapproved desktop capabilities",
    )

    requires = set(manifest["dependencies"]) - {"tauri", "serde"}
    require(not requires, f"unapproved Rust dependencies: {sorted(requires)}")
    require(
        native_code.count("#[tauri::command]") == 1
        and re.search(r"#\[tauri::command\]\s*fn inspect_host\(\)", native_code),
        "only inspect_host native command is permitted",
    )
    require(
        "tauri::generate_handler![inspect_host]" in native_code,
        "unexpected handler dispatch",
    )
    for forbidden in (
        "std::fs::write", "std::fs::remove", "std::fs::rename",
        "std::fs::create", "std::fs::set_permissions",
        ".spawn(", "Command::new(\"/bin/sh\")", "Command::new(\"/bin/bash\")",
        "Command::new(\"/usr/bin/sudo\")", "Command::new(\"/usr/bin/pkexec\")",
        "Command::new(\"/usr/bin/dpkg\")", "Command::new(\"/usr/bin/apt\")",
        "Command::new(\"/usr/bin/systemctl\")",
        "tauri_plugin_shell", "tauri_plugin_fs", "tauri_plugin_http",
        "tauri_plugin_updater", "tauri_plugin_autostart",
    ):
        require(forbidden not in native_code, f"unapproved runtime operation: {forbidden}")
    require(
        '"/usr/bin/dpkg-query"' in native_code
        and '"/usr/bin/systemctl"' in native_code,
        "missing fixed read-only system inspector",
    )
    require(
        "invoke<HostSnapshot>(\"inspect_host\")" in react_code,
        "desktop must invoke only read-only native inspection",
    )
    require(react_code.count("invoke<") == 1, "extra IPC commands need review")
    require("安装 Agent（尚未开放）" in react_code, "install must be explicitly disabled")
    for field in (
        "installEnabled", "serviceStartEnabled", "bootEnableEnabled",
        "collectionEnabled", "networkEnabled", "remoteControlEnabled",
        "certificateIssuanceEnabled",
    ):
        require(
            re.search(r"\b" + field + r":\s*false\b", safety) is not None,
            f"release lock lifted: {field}",
        )
    require("RELEASE_GATE.installEnabled" in react_code, "install UI must bind lock")
    package = json.loads((root / "package.json").read_text(encoding="utf-8"))
    require(
        not any("tauri-apps/plugin-" in name for name in package["dependencies"]),
        "unexpected privileged JS plugin",
    )
    return "PASS: isolated readonly desktop preview: no bundle, install privilege, autostart, shell or network"


if __name__ == "__main__":
    try:
        print(validate())
    except (ValueError, KeyError, FileNotFoundError, tomllib.TOMLDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
