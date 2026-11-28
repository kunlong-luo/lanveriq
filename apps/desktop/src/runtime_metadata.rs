//! Operator-requested, bounded local queries. No command or path supplied by UI.
use crate::host_identity::{HostIdentity, inspect_host_identity};
use crate::graphics::{GraphicsMetadata, SessionClaim, DIRECT_PACKAGES, COSMIC_PACKAGES, installed_set, session_claim};

const UNKNOWN: &str = "unverified";
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct RuntimeMetadata {
    attempted: bool,
    package: &'static str,
    service: &'static str,
    unit: &'static str,
    dropins: &'static str,
    reload: &'static str,
    legacy: &'static str,
    graphics: GraphicsMetadata,
}
impl Default for RuntimeMetadata {
    fn default() -> Self {
        Self { attempted: false, package: UNKNOWN, service: UNKNOWN,
            unit: UNKNOWN, dropins: UNKNOWN, reload: UNKNOWN, legacy: UNKNOWN, graphics: GraphicsMetadata::default() }
    }
}
impl RuntimeMetadata {
    pub fn graphics(self) -> GraphicsMetadata { self.graphics }
    pub fn attempted(self) -> bool { self.attempted }
    pub fn package_clean(self) -> bool { self.package == "not_installed" }
    pub fn service_clean(self) -> bool {
        self.service == "inactive" && self.unit == "disabled" &&
            self.dropins == "none_reported" && self.reload == "not_required" &&
            self.legacy == "not_detected"
    }
    pub fn rows(self) -> [(&'static str, &'static str); 6] {
        [("agent_package", self.package), ("systemd_service", self.service),
         ("systemd_unit", self.unit), ("systemd_dropins", self.dropins),
         ("systemd_reload", self.reload), ("legacy_binary", self.legacy)]
    }
    pub fn diagnostic_rows(self) -> [(&'static str, &'static str); 6] {
        let mut rows = self.rows();
        rows[0].1 = match self.package { "preview_candidate" => "unapproved_preview_candidate",
            "unrecognized_version" => "unknown_version", other => other };
        rows[1].1 = if self.service == "active" { "running_unapproved" } else { self.service };
        rows[2].1 = if self.unit == "enabled" { "enabled_unapproved" } else { self.unit };
        rows[3].1 = if self.dropins == "none_reported" { "none_reported_not_verified" } else { self.dropins };
        rows[5].1 = if self.legacy == "not_detected" { "not_detected_not_proven" } else { self.legacy };
        rows
    }
}

// Adapt old Tauri allowlists, but reject malformed package/duplicate property output.
fn package(raw: &str) -> &'static str {
    let Some((status, version)) = raw.trim_end_matches('\n').split_once('|') else { return UNKNOWN };
    if version.len() > 180 || !version.bytes().all(|b| b.is_ascii_alphanumeric() || b".+~:-".contains(&b)) { return UNKNOWN; }
    match status {
        "install ok installed" if !version.is_empty() => {
            if version == "0.1.0~dev1" { "preview_candidate" } else { "unrecognized_version" }
        }
        "deinstall ok config-files" if !version.is_empty() => "not_installed",
        "unknown ok not-installed" | "purge ok not-installed" if version.is_empty() => "not_installed",
        _ => UNKNOWN,
    }
}
fn unit(raw: &str) -> [&'static str; 4] {
    let mut properties = [None; 4];
    for line in raw.lines() {
        let Some((key, value)) = line.split_once('=') else { return [UNKNOWN; 4] };
        let index = match key {
            "ActiveState" => 0, "UnitFileState" => 1, "DropInPaths" => 2, "NeedDaemonReload" => 3,
            _ => return [UNKNOWN; 4],
        };
        if properties[index].replace(value).is_some() { return [UNKNOWN; 4]; }
    }
    [
        match properties[0] { Some("active") => "active", Some("inactive") => "inactive", Some("failed") => "failed", _ => UNKNOWN },
        match properties[1] { Some("enabled") => "enabled", Some("disabled") => "disabled", Some("masked") => "masked", _ => UNKNOWN },
        match properties[2] { Some("") => "none_reported", Some(_) => "present_unreviewed", _ => UNKNOWN },
        match properties[3] { Some("yes") => "reload_required", Some("no") => "not_required", _ => UNKNOWN },
    ]
}

#[cfg(target_os = "linux")]
enum Query { Package, Unit, DirectLibraries, CosmicComponents }

#[cfg(target_os = "linux")]
fn query(which: Query) -> Option<String> {
    // Production callers can only choose the fixed enum above; no UI-supplied arguments.
    const PACKAGE_FORMAT: &str = concat!("$", "{Package}", "|", "$", "{Architecture}", "|", "$", "{Status}", "\\n");
    let args: &[&str] = match which {
        Query::DirectLibraries => &["/usr/bin/dpkg-query", "-W", "-f", PACKAGE_FORMAT,
            "libc6:amd64", "libgcc-s1:amd64", "libxcb1:amd64", "libxkbcommon0:amd64", "libxkbcommon-x11-0:amd64"],
        Query::CosmicComponents => &["/usr/bin/dpkg-query", "-W", "-f", PACKAGE_FORMAT,
            "cosmic-session", "cosmic-comp", "xdg-desktop-portal-cosmic"],
        Query::Package => &["/usr/bin/dpkg-query", "-W", "-f",
            concat!("$", "{Status}", "|", "$", "{Version}"), "lanveriq-agent-offline-preview"],
        Query::Unit => &["/usr/bin/systemctl", "--system", "--no-pager", "--no-ask-password", "--all", "show",
            "--property=ActiveState", "--property=UnitFileState", "--property=DropInPaths",
            "--property=NeedDaemonReload", "--", "lanveriq-agent.service"],
    };
    bounded_query(args)
}

// Private execution boundary; production arguments originate only from Query.
// Coreutils timeout handles TERM/KILL escalation. Test-only callers exercise it
// with disposable fixture programs; no generic command interface is exposed.
#[cfg(target_os = "linux")]
fn bounded_query(args: &[&str]) -> Option<String> {
    use std::io::Read;
    use std::process::{Command, Stdio};
    let mut child = Command::new("/usr/bin/timeout")
        .args(["--kill-after=1s", "3s"]).args(args)
        .env_clear().env("LANG", "C").env("PATH", "/usr/bin:/bin")
        .current_dir("/").stdin(Stdio::null()).stderr(Stdio::null()).stdout(Stdio::piped())
        .spawn().ok()?;
    let mut bytes = Vec::new();
    let read = child.stdout.take().map(|out| out.take(4097).read_to_end(&mut bytes));
    // Always reap the timeout process, even if reading fails or exceeds the cap.
    let status = child.wait().ok()?;
    if !status.success() || !matches!(read, Some(Ok(_))) || bytes.len() > 4096 { return None; }
    String::from_utf8(bytes).ok()
}

#[cfg(target_os = "linux")]
fn legacy() -> &'static str {
    match std::fs::symlink_metadata("/usr/local/bin/lanveriq-agent") {
        Ok(_) => "present_unreviewed",
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => "not_detected",
        Err(_) => UNKNOWN,
    }
}

/// Fresh identity is checked on every request; root/failed identity skips all queries.
pub fn inspect_runtime() -> (HostIdentity, RuntimeMetadata) {
    let identity = inspect_host_identity();
    let mut result = RuntimeMetadata::default();
    #[cfg(target_os = "linux")]
    if matches!(identity, HostIdentity::PopOs24Amd64 | HostIdentity::Other) {
        result.attempted = true;
        result.package = query(Query::Package).as_deref().map(package).unwrap_or(UNKNOWN);
        let [service, unit_state, dropins, reload] = query(Query::Unit).as_deref().map(unit).unwrap_or([UNKNOWN; 4]);
        result.service = service;
        result.unit = unit_state;
        result.dropins = dropins;
        result.reload = reload;
        result.legacy = legacy();
        let direct = query(Query::DirectLibraries).is_some_and(|raw| installed_set(&raw, &DIRECT_PACKAGES, false));
        let (session, cosmic) = if identity == HostIdentity::PopOs24Amd64 {
            let desktop = std::env::var("XDG_CURRENT_DESKTOP").unwrap_or_default();
            let kind = std::env::var("XDG_SESSION_TYPE").unwrap_or_default();
            let display = std::env::var("WAYLAND_DISPLAY").unwrap_or_default();
            let session = session_claim(&desktop, &kind, &display);
            let cosmic = session == SessionClaim::CosmicWayland &&
                query(Query::CosmicComponents).is_some_and(|raw| installed_set(&raw, &COSMIC_PACKAGES, true));
            (session, cosmic)
        } else { (SessionClaim::Unknown, false) };
        result.graphics = GraphicsMetadata::from_observations(identity, session, direct, cosmic);
    }
    (identity, result)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn package_allowlist_rejects_ambiguous_state() {
        assert_eq!(package("install ok installed|0.1.0~dev1"), "preview_candidate");
        assert_eq!(package("install ok installed|9.9"), "unrecognized_version");
        assert_eq!(package("deinstall ok config-files|0.1.0~dev1"), "not_installed");
        for raw in ["", "install reinstreq half-installed|1", "install ok installed|", "garbage|1",
            "install ok installed|1\ninstall ok installed|2", "install ok installed|1|2"] {
            assert_eq!(package(raw), UNKNOWN);
        }
    }
    #[test]
    fn unit_metadata_never_exposes_dropin_paths_and_rejects_duplicates() {
        assert_eq!(unit("ActiveState=inactive\nUnitFileState=disabled\nDropInPaths=\nNeedDaemonReload=no\n"),
            ["inactive", "disabled", "none_reported", "not_required"]);
        assert_eq!(unit("ActiveState=active\nUnitFileState=enabled\nDropInPaths=/home/private/a.conf\nNeedDaemonReload=yes"),
            ["active", "enabled", "present_unreviewed", "reload_required"]);
        assert_eq!(unit("ActiveState=inactive\nActiveState=active"), [UNKNOWN; 4]);
        assert_eq!(unit(""), [UNKNOWN; 4]);
    }
    #[test]
    fn default_metadata_cannot_support_clean_review() {
        let m = RuntimeMetadata::default();
        assert!(!m.attempted() && !m.package_clean() && !m.service_clean());
        assert!(m.rows().iter().all(|(_, v)| *v == UNKNOWN));
    }
    #[test]
    fn runtime_projection_never_grants_installation_and_clears_stale_metadata() {
        use crate::review::{GateStatus, runtime_diagnostic, runtime_review_gates};
        use crate::Workspace;
        let clean = RuntimeMetadata { attempted: true, package: "not_installed",
            service: "inactive", unit: "disabled", dropins: "none_reported",
            reload: "not_required", legacy: "not_detected", graphics: GraphicsMetadata::default() };
        let gates = runtime_review_gates(HostIdentity::PopOs24Amd64, clean);
        assert_eq!(gates[1].status, GateStatus::MetadataOnly);
        assert_eq!(gates[2].status, GateStatus::MetadataOnly);
        assert_eq!(gates[3].status, GateStatus::NotChecked);
        assert!(gates[4..].iter().all(|g| g.status == GateStatus::NotApproved));
        let dirty = RuntimeMetadata { service: "active", unit: "enabled", dropins: "present_unreviewed", ..clean };
        assert_eq!(runtime_review_gates(HostIdentity::PopOs24Amd64, dirty)[2].status, GateStatus::Blocked);
        let report = runtime_diagnostic(HostIdentity::PopOs24Amd64, dirty);
        for line in ["systemd_service=running_unapproved", "systemd_unit=enabled_unapproved",
            "systemd_dropins=present_unreviewed", "install_authorized=false",
            "agent_start_authorized=false", "cosmic_preflight=not_verified"] {
            assert!(report.lines().any(|actual| actual == line));
        }
        let mut workspace = Workspace::default();
        workspace.replace_runtime_metadata(HostIdentity::PopOs24Amd64, clean);
        workspace.replace_runtime_metadata(HostIdentity::Blocked, dirty);
        assert!(!workspace.runtime_metadata().attempted());
        assert!(workspace.runtime_metadata().rows().iter().all(|(_, value)| *value == UNKNOWN));
    }

    #[test]
    fn graphics_projection_is_advisory_and_cleared_on_failure() {
        use crate::review::{GateStatus, runtime_review_gates, runtime_diagnostic};
        use crate::Workspace;
        let graphics = GraphicsMetadata::from_observations(HostIdentity::PopOs24Amd64, SessionClaim::CosmicWayland, true, true);
        let m = RuntimeMetadata { attempted: true, graphics, ..RuntimeMetadata::default() };
        let gates = runtime_review_gates(HostIdentity::PopOs24Amd64, m);
        assert_eq!(gates[3].status, GateStatus::MetadataOnly);
        assert!(gates[4..].iter().all(|g| g.status == GateStatus::NotApproved));
        let report = runtime_diagnostic(HostIdentity::PopOs24Amd64, m);
        assert!(report.contains("cosmic_preflight=metadata_checked_not_l3"));
        let kde = RuntimeMetadata { graphics: GraphicsMetadata::from_observations(
            HostIdentity::PopOs24Amd64, SessionClaim::KdeX11, true, false), ..m };
        let kde_gates = runtime_review_gates(HostIdentity::PopOs24Amd64, kde);
        assert_eq!(kde_gates[3].status, GateStatus::MetadataOnly);
        assert!(kde_gates[4..].iter().all(|g| g.status == GateStatus::NotApproved));
        let kde_report = runtime_diagnostic(HostIdentity::PopOs24Amd64, kde);
        for line in ["desktop_session_claim=kde_x11_claim", "desktop_preflight=metadata_checked_not_l3",
                     "cosmic_preflight=not_verified", "install_authorized=false", "agent_start_authorized=false"] {
            assert!(kde_report.lines().any(|actual| actual == line));
        }
        assert!(report.contains("install_authorized=false"));
        let mut workspace = Workspace::default();
        workspace.replace_runtime_metadata(HostIdentity::PopOs24Amd64, m);
        workspace.replace_host_identity(HostIdentity::NotChecked);
        assert!(!workspace.runtime_metadata().graphics().attempted());
    }

    #[cfg(target_os = "linux")]
    #[test]
    #[ignore = "Explicit disposable non-root Ubuntu process boundary smoke"]
    fn live_query_boundary_rejects_failed_and_oversize_output() {
        assert_eq!(bounded_query(&["/usr/bin/printf", "%s", "fixture"]), Some("fixture".into()));
        assert_eq!(bounded_query(&["/usr/bin/false"]), None);
        assert_eq!(bounded_query(&["/usr/bin/printf", "\\377"]), None);
        let exact = "x".repeat(4096);
        assert_eq!(bounded_query(&["/usr/bin/printf", "%s", &exact]), Some(exact.clone()));
        let excess = "x".repeat(4097);
        assert_eq!(bounded_query(&["/usr/bin/printf", "%s", &excess]), None);
        assert_eq!(bounded_query(&["/usr/bin/yes", "fixture"]), None);
    }

    #[cfg(target_os = "linux")]
    #[test]
    #[ignore = "Explicit disposable non-root Ubuntu process boundary smoke"]
    fn live_query_boundary_times_out_and_escalates() {
        use std::time::{Duration, Instant};
        let started = Instant::now();
        assert_eq!(bounded_query(&["/usr/bin/sleep", "30"]), None);
        assert!(started.elapsed() >= Duration::from_secs(3));
        assert!(started.elapsed() < Duration::from_secs(10));
        // Fixed test fixture only: ignore TERM and hold stdout open. Coreutils
        // must escalate to KILL, and the reader/wait must return no metadata.
        let started = Instant::now();
        assert_eq!(bounded_query(&["/usr/bin/python3", "-I", "-c",
            "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); print('fixture', flush=True); time.sleep(30)"]), None);
        assert!(started.elapsed() >= Duration::from_secs(4));
        assert!(started.elapsed() < Duration::from_secs(12));
    }

    #[cfg(target_os = "linux")]
    #[test]
    #[ignore = "Explicit disposable non-root Ubuntu smoke"]
    fn live_ubuntu_runtime_readonly() {
        let (identity, metadata) = inspect_runtime();
        assert_eq!(identity, HostIdentity::Other);
        assert!(metadata.attempted());
        // This runner need not have a running systemd or an installed Agent.
        assert!(matches!(metadata.rows()[0].1, "unverified" | "not_installed" | "preview_candidate" | "unrecognized_version"));
        assert!(matches!(metadata.rows()[1].1, "unverified" | "active" | "inactive" | "failed"));
        assert_eq!(metadata.rows()[5].1, legacy());
        assert!(metadata.graphics().attempted());
        assert!(!metadata.graphics().metadata_complete());
        assert_eq!(metadata.graphics().rows()[1].1, "软件包元数据齐全");
    }
}
