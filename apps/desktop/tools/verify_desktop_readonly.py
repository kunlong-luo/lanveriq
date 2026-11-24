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
    # A pinned dependency list does not activate bundling or grant install rights.
    # The reviewed ABI baseline must not drift without a new native audit.
    from importlib.util import spec_from_file_location, module_from_spec
    dep_module = DESKTOP / "tools" / "verify_desktop_deb_shlibdeps_audit.py"
    require(dep_module.is_file(), "missing reviewed ABI dependency audit")
    reviewed = ["libc6 (>= 2.39)","libcairo2 (>= 1.10.0)","libdbus-1-3 (>= 1.9.14)","libgcc-s1 (>= 4.2)","libgdk-pixbuf-2.0-0 (>= 2.36.9)","libglib2.0-0t64 (>= 2.66.0)","libgtk-3-0t64 (>= 3.21.5)","libjavascriptcoregtk-4.1-0","libsoup-3.0-0 (>= 3.0.3)","libwebkit2gtk-4.1-0 (>= 2.41.90)"]
    require(tauri["bundle"].get("linux", {}).get("deb", {}).get("depends") == reviewed,
            "Ubuntu24 native GUI-only ABI dependency baseline drift")
    require(not any(tauri["bundle"]["linux"]["deb"].get(key) for key in
                    ("files", "preInstallScript", "postInstallScript",
                     "preRemoveScript", "postRemoveScript")),
            "GUI-only bundle must not contain installer hooks or added files")
    require(tauri["bundle"].get("icon") == ["icons/icon.png"], "desktop bundle icon must be the reviewed native PNG")
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
        native_code.count("#[tauri::command]") == 2
        and re.search(r"#\[tauri::command\]\s*fn inspect_host\(\)", native_code)
        and re.search(r"#\[tauri::command\]\s*async fn inspect_popos_preflight\(\)", native_code),
        "only inspect_host and inspect_popos_preflight are permitted",
    )
    require(
        re.search(r"tauri::generate_handler!\[\s*inspect_host,\s*inspect_popos_preflight\s*\]", native_code) is not None,
        "unexpected handler dispatch",
    )
    require(
        'include_str!("../../tools/verify_desktop_popos_local_preflight.py")' in native_code
        and 'Command::new("/usr/bin/python3")' in native_code
        and '"-I", "-c", BUNDLED_PREFLIGHT, "inspect-json"' in native_code
        and ".env_clear()" in native_code
        and "spawn_blocking(collect_popos_preflight)" in native_code,
        "bundled read-only PopOS preflight protocol drift",
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
        'invoke<HostSnapshot>("inspect_host")' in react_code
        and 'invoke<string>("inspect_popos_preflight")' in react_code
        and "parseLocalPreflight(raw)" in react_code,
        "desktop must invoke only read-only host and PopOS compatibility inspection",
    )
    require(react_code.count("invoke<") == 2, "extra IPC commands need review")
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
