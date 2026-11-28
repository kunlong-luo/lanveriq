//! GPUI-specific advisory metadata, never a runtime ABI or physical L3 proof.
use crate::host_identity::HostIdentity;

// Mapped from the reviewed seven direct ELF DT_NEEDED entries (libc6
// supplies libc, libm and ld-linux); no Tauri WebKitGTK assumptions.
pub(crate) const DIRECT_PACKAGES: [&str; 5] =
    ["libc6", "libgcc-s1", "libxcb1", "libxkbcommon0", "libxkbcommon-x11-0"];
pub(crate) const COSMIC_PACKAGES: [&str; 3] =
    ["cosmic-session", "cosmic-comp", "xdg-desktop-portal-cosmic"];

// Bounded environment claims, not verified compositor/connection identity.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) enum SessionClaim {
    #[default]
    Unknown,
    KdeX11,
    KdeWayland,
    CosmicWayland,
}
impl SessionClaim {
    fn label(self) -> &'static str {
        match self {
            Self::Unknown => "未核验",
            Self::KdeX11 => "KDE / X11（仅声明）",
            Self::KdeWayland => "KDE / Wayland（仅声明）",
            Self::CosmicWayland => "COSMIC / Wayland（仅声明）",
        }
    }
    fn diagnostic(self) -> &'static str {
        match self {
            Self::Unknown => "unverified",
            Self::KdeX11 => "kde_x11_claim",
            Self::KdeWayland => "kde_wayland_claim",
            Self::CosmicWayland => "cosmic_wayland_claim",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct GraphicsMetadata {
    attempted: bool,
    session_claim: SessionClaim,
    direct_packages: bool,
    cosmic_packages: bool,
}

impl GraphicsMetadata {
    pub(crate) fn from_observations(identity: HostIdentity, session: SessionClaim, direct: bool, cosmic: bool) -> Self {
        match identity {
            HostIdentity::NotChecked | HostIdentity::Blocked => Self::default(),
            HostIdentity::Other => Self { attempted: true, direct_packages: direct, ..Self::default() },
            HostIdentity::PopOs24Amd64 => Self { attempted: true, session_claim: session,
                direct_packages: direct, cosmic_packages: session == SessionClaim::CosmicWayland && cosmic },
        }
    }

    pub fn attempted(self) -> bool { self.attempted }

    pub fn metadata_complete(self) -> bool {
        self.attempted && self.direct_packages && match self.session_claim {
            SessionClaim::KdeX11 | SessionClaim::KdeWayland => true,
            SessionClaim::CosmicWayland => self.cosmic_packages,
            SessionClaim::Unknown => false,
        }
    }

    pub fn rows(self) -> [(&'static str, &'static str); 3] {
        [
            ("桌面会话声明", self.session_claim.label()),
            ("GPUI 直接 ELF 依赖包", if self.direct_packages { "软件包元数据齐全" } else { "未核验" }),
            ("COSMIC 专用组件包", match self.session_claim {
                SessionClaim::KdeX11 | SessionClaim::KdeWayland => "不适用（KDE 会话）",
                _ if self.cosmic_packages => "软件包元数据齐全",
                _ => "未核验",
            }),
        ]
    }

    pub fn session_diagnostic(self) -> &'static str { self.session_claim.diagnostic() }

    // Preserve the historical COSMIC field: KDE metadata must never imply
    // that COSMIC was checked.
    pub fn cosmic_diagnostic(self) -> &'static str {
        match self.session_claim {
            SessionClaim::CosmicWayland => self.diagnostic(),
            SessionClaim::KdeX11 | SessionClaim::KdeWayland => "not_verified",
            SessionClaim::Unknown => if self.attempted { "blocked_not_verified" } else { "not_verified" },
        }
    }

    pub fn diagnostic(self) -> &'static str {
        if self.metadata_complete() { "metadata_checked_not_l3" }
        else if self.attempted { "blocked_not_verified" }
        else { "not_verified" }
    }
}

// Only fixed XDG classifications are retained. No display socket or raw value
// is stored, and X11 does not need COSMIC packages or a Wayland display.
pub(crate) fn session_claim(desktop: &str, kind: &str, display: &str) -> SessionClaim {
    if desktop.len() > 256 || !desktop.is_ascii() { return SessionClaim::Unknown; }
    let kde = desktop.split(':').any(|part| part.eq_ignore_ascii_case("KDE"));
    let cosmic = desktop.split(':').any(|part| part.eq_ignore_ascii_case("COSMIC"));
    if kde && cosmic { return SessionClaim::Unknown; }
    let wayland = !display.is_empty() && display.len() <= 128 &&
        display.bytes().all(|b| b.is_ascii_alphanumeric() || b"_.-".contains(&b));
    match (kde, cosmic, kind) {
        (true, false, "x11") if display.is_empty() => SessionClaim::KdeX11,
        (true, false, "wayland") if wayland => SessionClaim::KdeWayland,
        (false, true, "wayland") if wayland => SessionClaim::CosmicWayland,
        _ => SessionClaim::Unknown,
    }
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
        assert_eq!(session_claim("COSMIC", "wayland", "wayland-0"), SessionClaim::CosmicWayland);
        assert_eq!(session_claim("cosmic:GNOME", "wayland", "wayland-1"), SessionClaim::CosmicWayland);
        assert_eq!(session_claim("KDE", "x11", ""), SessionClaim::KdeX11);
        assert_eq!(session_claim("kde", "wayland", "wayland-1"), SessionClaim::KdeWayland);
        for (d,k,w) in [("GNOME","wayland","wayland-0"),("COSMIC","x11","wayland-0"),
            ("COSMIC","wayland","/run/user/1000/wayland-0"),("COSMIC","wayland",""),
            ("COSMIC-other","wayland","wayland-0"),("KDE:COSMIC","wayland","wayland-0"),
            ("KDE","x11","wayland-0"),("KDE","wayland",""),("KDE","wayland","/tmp/socket"),
            ("KDE-other","x11",""),("GNOME","x11","")] {
            assert_eq!(session_claim(d,k,w), SessionClaim::Unknown);
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
    fn metadata_complete_depends_on_actual_session_and_never_implies_approval() {
        for identity in [HostIdentity::NotChecked, HostIdentity::Blocked, HostIdentity::Other, HostIdentity::PopOs24Amd64] {
            for session in [SessionClaim::Unknown, SessionClaim::KdeX11, SessionClaim::KdeWayland, SessionClaim::CosmicWayland] {
                for direct in [false, true] {
                    for cosmic in [false, true] {
                        let m = GraphicsMetadata::from_observations(identity, session, direct, cosmic);
                        let expected = identity == HostIdentity::PopOs24Amd64 && direct && match session {
                            SessionClaim::Unknown => false,
                            SessionClaim::KdeX11 | SessionClaim::KdeWayland => true,
                            SessionClaim::CosmicWayland => cosmic,
                        };
                        assert_eq!(m.metadata_complete(), expected);
                        assert_ne!(m.diagnostic(), "approved");
                        if matches!(session, SessionClaim::KdeX11 | SessionClaim::KdeWayland) {
                            assert_ne!(m.cosmic_diagnostic(), "metadata_checked_not_l3");
                        }
                    }
                }
            }
        }
    }
}
