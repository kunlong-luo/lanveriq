//! GPUI-specific advisory metadata, never a runtime ABI or physical L3 proof.
use crate::host_identity::HostIdentity;

// Mapped from the reviewed seven direct ELF DT_NEEDED entries (libc6
// supplies libc, libm and ld-linux); no Tauri WebKitGTK assumptions.
pub(crate) const DIRECT_PACKAGES: [&str; 5] =
    ["libc6", "libgcc-s1", "libxcb1", "libxkbcommon0", "libxkbcommon-x11-0"];
pub(crate) const COSMIC_PACKAGES: [&str; 3] =
    ["cosmic-session", "cosmic-comp", "xdg-desktop-portal-cosmic"];

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct GraphicsMetadata {
    attempted: bool,
    session_claim: bool,
    direct_packages: bool,
    cosmic_packages: bool,
}

impl GraphicsMetadata {
    pub(crate) fn from_observations(identity: HostIdentity, session: bool, direct: bool, cosmic: bool) -> Self {
        match identity {
            HostIdentity::NotChecked | HostIdentity::Blocked => Self::default(),
            HostIdentity::Other => Self { attempted: true, direct_packages: direct, ..Self::default() },
            HostIdentity::PopOs24Amd64 => Self { attempted: true, session_claim: session,
                direct_packages: direct, cosmic_packages: cosmic },
        }
    }

    pub fn attempted(self) -> bool { self.attempted }

    pub fn metadata_complete(self) -> bool {
        self.attempted && self.session_claim && self.direct_packages && self.cosmic_packages
    }

    pub fn rows(self) -> [(&'static str, &'static str); 3] {
        [
            ("COSMIC / Wayland 会话声明", if self.session_claim { "仅有声明" } else { "未核验" }),
            ("GPUI 直接 ELF 依赖包", if self.direct_packages { "软件包元数据齐全" } else { "未核验" }),
            ("COSMIC 组件包", if self.cosmic_packages { "软件包元数据齐全" } else { "未核验" }),
        ]
    }

    pub fn diagnostic(self) -> &'static str {
        if self.metadata_complete() { "metadata_checked_not_l3" }
        else if self.attempted { "blocked_not_verified" }
        else { "not_verified" }
    }
}

// Compare only the old reviewed session claim semantics; never retain values,
// open a display socket, or claim the compositor is running.
pub(crate) fn session_claim(desktop: &str, kind: &str, display: &str) -> bool {
    desktop.len() <= 256 && desktop.is_ascii() &&
        desktop.split(':').any(|part| part.eq_ignore_ascii_case("COSMIC")) &&
        kind == "wayland" && !display.is_empty() && display.len() <= 128 &&
        display.bytes().all(|b| b.is_ascii_alphanumeric() || b"_.-".contains(&b))
}

// Exact fixed package set, no version/path text retained. A failed query or
// missing/duplicate/unknown/foreign-arch row is never evidence of compatibility.
pub(crate) fn installed_set(raw: &str, expected: &[&str], allow_all: bool) -> bool {
    let mut seen = std::collections::HashSet::new();
    for line in raw.lines() {
        let mut fields = line.split('|');
        let (Some(name), Some(arch), Some(status), None) =
            (fields.next(), fields.next(), fields.next(), fields.next()) else { return false };
        if !expected.contains(&name) || !seen.insert(name) ||
            !(arch == "amd64" || (allow_all && arch == "all")) ||
            status != "install ok installed" { return false; }
    }
    seen.len() == expected.len()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn session_is_only_a_bounded_claim() {
        assert!(session_claim("COSMIC", "wayland", "wayland-0"));
        assert!(session_claim("cosmic:GNOME", "wayland", "wayland-1"));
        for (d,k,w) in [("GNOME","wayland","wayland-0"),("COSMIC","x11","wayland-0"),
            ("COSMIC","wayland","/run/user/1000/wayland-0"),("COSMIC","wayland",""),
            ("COSMIC-other","wayland","wayland-0")] {
            assert!(!session_claim(d,k,w));
        }
    }
    #[test]
    fn installed_packages_require_exact_set_arch_and_status() {
        let good = "a|amd64|install ok installed\nb|amd64|install ok installed\n";
        assert!(installed_set(good, &["a","b"], false));
        for raw in ["", "a|amd64|install ok installed", "a|amd64|install ok installed\na|amd64|install ok installed",
            "a|arm64|install ok installed\nb|amd64|install ok installed",
            "a|amd64|install ok installed\nb|amd64|deinstall ok config-files",
            "a|amd64|install ok installed\nb|amd64|install ok installed\nc|amd64|install ok installed"] {
            assert!(!installed_set(raw, &["a","b"], false));
        }
        assert!(installed_set("a|all|install ok installed", &["a"], true));
        assert!(!installed_set("a|all|install ok installed", &["a"], false));
    }
    #[test]
    fn only_target_with_all_three_observations_is_metadata_complete() {
        for identity in [HostIdentity::NotChecked, HostIdentity::Blocked, HostIdentity::Other, HostIdentity::PopOs24Amd64] {
            for bits in 0..8 {
                let m = GraphicsMetadata::from_observations(identity, bits & 1 != 0, bits & 2 != 0, bits & 4 != 0);
                assert_eq!(m.metadata_complete(), identity == HostIdentity::PopOs24Amd64 && bits == 7);
                assert_ne!(m.diagnostic(), "approved");
            }
        }
    }
}
