//! Read-only, offline, nonprivileged host inspection for the desktop preview.
//! No install/update/uninstall/start/enable, shell, network, capture or agent IPC.
//! This information is advisory and must not be treated as Pop!_OS L3 approval.

use serde::Serialize;

const PACKAGE: &str = "lanveriq-agent-offline-preview";
const EXPECTED_VERSION: &str = "0.1.0~dev1";
const UNIT: &str = "lanveriq-agent.service";

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct HostSnapshot {
    os_name: String,
    architecture: String,
    target_supported: bool,
    package_state: String,
    service_state: String,
    unit_file_state: String,
    override_state: String,
    manager_reload_state: String,
    installation_allowed: bool,
    runtime_activation_allowed: bool,
}

fn parse_os_release(contents: &str) -> (String, bool) {
    let mut id = String::new();
    let mut version = String::new();
    let mut name = String::from("无法核验");
    for line in contents.lines().take(80) {
        if let Some((key, value)) = line.split_once('=') {
            let value = value.trim().trim_matches('"');
            match key {
                "ID" => id = value.to_string(),
                "VERSION_ID" => version = value.to_string(),
                "PRETTY_NAME" if value.len() <= 120 && value.chars().all(|ch| !ch.is_control()) => {
                    name = value.to_string();
                }
                _ => {}
            }
        }
    }
    (name, id == "pop" && version == "24.04")
}

fn parse_package(raw: &str) -> &str {
    let fields: Vec<&str> = raw.trim().split('|').collect();
    if fields.len() != 2 {
        return "unverified";
    }
    if fields[0] != "install ok installed" {
        return "not_installed";
    }
    if fields[1] == EXPECTED_VERSION {
        "preview_candidate"
    } else {
        "unrecognized_version"
    }
}

fn parse_unit_properties(raw: &str) -> (&str, &str, &str, &str) {
    let mut active = None;
    let mut unit_file = None;
    let mut drop_ins = None;
    let mut reload = None;
    for line in raw.lines().take(24) {
        if let Some((key, value)) = line.split_once('=') {
            match key {
                "ActiveState" if active.is_none() => active = Some(value),
                "UnitFileState" if unit_file.is_none() => unit_file = Some(value),
                "DropInPaths" if drop_ins.is_none() => drop_ins = Some(value),
                "NeedDaemonReload" if reload.is_none() => reload = Some(value),
                _ => {}
            }
        }
    }
    let state = match active {
        Some("active") => "active",
        Some("inactive") => "inactive",
        Some("failed") => "failed",
        _ => "unverified",
    };
    let enabled = match unit_file {
        Some("enabled") => "enabled",
        Some("disabled") => "disabled",
        Some("masked") => "masked",
        _ => "unverified",
    };
    // An unreviewed drop-in must not be mistaken for the source unit.
    let overrides = match drop_ins {
        Some("") => "none_reported",
        Some(_) => "present_unreviewed",
        _ => "unverified",
    };
    let reloaded = match reload {
        Some("yes") => "reload_required",
        Some("no") => "not_required",
        _ => "unverified",
    };
    (state, enabled, overrides, reloaded)
}

#[cfg(target_os = "linux")]
fn command_read_only(binary: &str, args: &[&str]) -> Option<String> {
    use std::process::Command;
    let output = Command::new(binary).args(args).output().ok()?;
    if !output.status.success() || output.stdout.len() > 4096 {
        return None;
    }
    String::from_utf8(output.stdout).ok()
}

#[tauri::command]
fn inspect_host() -> HostSnapshot {
    let architecture = std::env::consts::ARCH.to_string();
    #[cfg(target_os = "linux")]
    {
        let os_release = std::fs::read_to_string("/etc/os-release").unwrap_or_default();
        let (os_name, os_matches) = parse_os_release(&os_release);
        // Fixed query names and paths; never execute shell commands or caller input.
        const FORMAT: &str = concat!("$", "{Status}", "|", "$", "{Version}");
        let package_state =
            command_read_only("/usr/bin/dpkg-query", &["-W", "-f", FORMAT, PACKAGE])
                .map(|raw| parse_package(&raw).to_string())
                .unwrap_or_else(|| "unverified".to_string());
        let service = command_read_only(
            "/usr/bin/systemctl",
            &[
                "show",
                UNIT,
                "--no-pager",
                "--property=ActiveState",
                "--property=UnitFileState",
                "--property=DropInPaths",
                "--property=NeedDaemonReload",
            ],
        );
        let (service_state, unit_file_state, override_state, manager_reload_state) = service
            .as_deref()
            .map(parse_unit_properties)
            .unwrap_or(("unverified", "unverified", "unverified", "unverified"));
        HostSnapshot {
            os_name,
            architecture: architecture.clone(),
            target_supported: os_matches && architecture == "x86_64",
            package_state,
            service_state: service_state.to_string(),
            unit_file_state: unit_file_state.to_string(),
            override_state: override_state.to_string(),
            manager_reload_state: manager_reload_state.to_string(),
            installation_allowed: false,
            runtime_activation_allowed: false,
        }
    }
    #[cfg(not(target_os = "linux"))]
    HostSnapshot {
        os_name: "非 Linux 目标环境".into(),
        architecture,
        target_supported: false,
        package_state: "unverified".into(),
        service_state: "unverified".into(),
        unit_file_state: "unverified".into(),
        override_state: "unverified".into(),
        manager_reload_state: "unverified".into(),
        installation_allowed: false,
        runtime_activation_allowed: false,
    }
}

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![inspect_host])
        .run(tauri::generate_context!())
        .expect("failed to run offline Lanveriq desktop preview");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn popos_target_requires_exact_distribution_and_version() {
        let (name, allowed) =
            parse_os_release("ID=pop\nVERSION_ID=\"24.04\"\nPRETTY_NAME=\"Pop!_OS 24.04\"\n");
        assert_eq!(name, "Pop!_OS 24.04");
        assert!(allowed);
        for raw in [
            "ID=ubuntu\nVERSION_ID=24.04\n",
            "ID=pop\nVERSION_ID=22.04\n",
            "VERSION_ID=24.04\n",
            "ID=pop\n",
        ] {
            assert!(!parse_os_release(raw).1);
        }
    }

    #[test]
    fn only_candidate_package_version_is_recognized() {
        assert_eq!(
            parse_package("install ok installed|0.1.0~dev1\n"),
            "preview_candidate"
        );
        assert_eq!(
            parse_package("install ok installed|9.9.9\n"),
            "unrecognized_version"
        );
        assert_eq!(
            parse_package("deinstall ok config-files|0.1.0~dev1"),
            "not_installed"
        );
        assert_eq!(parse_package("unexpected output"), "unverified");
    }

    #[test]
    fn service_status_is_only_advisory() {
        let clean =
            "ActiveState=inactive\nUnitFileState=disabled\nDropInPaths=\nNeedDaemonReload=no\n";
        assert_eq!(
            parse_unit_properties(clean),
            ("inactive", "disabled", "none_reported", "not_required")
        );
        let unsafe_values = "ActiveState=active\nUnitFileState=enabled\nDropInPaths=/etc/systemd/system/a.conf\nNeedDaemonReload=yes\n";
        assert_eq!(
            parse_unit_properties(unsafe_values),
            ("active", "enabled", "present_unreviewed", "reload_required")
        );
        assert_eq!(
            parse_unit_properties(""),
            ("unverified", "unverified", "unverified", "unverified")
        );
    }

    #[test]
    fn no_install_or_activation_authority_is_exposed() {
        let host = inspect_host();
        assert!(!host.installation_allowed);
        assert!(!host.runtime_activation_allowed);
    }
}
