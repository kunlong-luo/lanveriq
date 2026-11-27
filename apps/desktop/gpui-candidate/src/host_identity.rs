//! Fixed-path, operator-requested OS identity metadata only. No command execution.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum HostIdentity {
    #[default]
    NotChecked,
    Blocked,
    PopOs24Amd64,
    Other,
}

impl HostIdentity {
    pub const fn label(self) -> &'static str {
        match self {
            Self::NotChecked => "尚未核验",
            Self::Blocked => "检测不可用或非普通用户",
            Self::PopOs24Amd64 => "Pop!_OS 24.04 / x86_64（仅元数据）",
            Self::Other => "非目标系统或架构（仅元数据）",
        }
    }
    pub const fn diagnostic_source(self) -> &'static str {
        match self {
            Self::NotChecked => "not_verified",
            Self::Blocked => "native_inspection_failed",
            _ => "native_readonly_identity_only",
        }
    }
    pub const fn diagnostic_target(self) -> &'static str {
        match self {
            Self::PopOs24Amd64 => "popos_24_04_x86_64_metadata_only",
            Self::Other => "unverified_or_unsupported",
            _ => "unverified",
        }
    }
}

// Adapt the existing Tauri parse_os_release ID/VERSION_ID comparison.
// Do not retain PRETTY_NAME or arbitrary OS strings; malformed/duplicate
// identity fields cannot be elevated to matching metadata.
fn classify_os(text: &str, amd64: bool) -> HostIdentity {
    let mut id = None;
    let mut version = None;
    for line in text.lines() {
        let Some((key, value)) = line.split_once('=') else { continue };
        let slot = match key {
            "ID" => &mut id,
            "VERSION_ID" => &mut version,
            _ => continue,
        };
        let value = value.trim();
        let value = if value.starts_with('"') {
            match value.strip_prefix('"').and_then(|v| v.strip_suffix('"')) {
                Some(v) => v,
                None => return HostIdentity::Blocked,
            }
        } else { value };
        if value.is_empty() || value.len() > 64 || !value.bytes().all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-')) || slot.replace(value).is_some() {
            return HostIdentity::Blocked;
        }
    }
    match (id, version) {
        (Some("pop"), Some("24.04")) if amd64 => HostIdentity::PopOs24Amd64,
        (Some(_), Some(_)) => HostIdentity::Other,
        _ => HostIdentity::Blocked,
    }
}

#[cfg(target_os = "linux")]
fn ordinary_user(status: &str) -> bool {
    let mut rows = status.lines().filter_map(|line| line.strip_prefix("Uid:"));
    let Some(row) = rows.next() else { return false };
    let uids: Vec<_> = row.split_ascii_whitespace().collect();
    rows.next().is_none() && uids.len() == 4 &&
        uids.iter().all(|uid| uid.parse::<u32>().is_ok_and(|n| n != 0))
}

#[cfg(target_os = "linux")]
enum FixedInput { ProcessStatus, OsRelease }

#[cfg(target_os = "linux")]
fn read_fixed(input: FixedInput) -> Option<String> {
    use std::io::Read;
    let path = match input {
        FixedInput::ProcessStatus => "/proc/self/status",
        FixedInput::OsRelease => "/etc/os-release",
    };
    let mut text = String::new();
    let file = std::fs::File::open(path).ok()?;
    if !file.metadata().ok()?.is_file() { return None; }
    file.take(16_385).read_to_string(&mut text).ok()?;
    (text.len() <= 16_384).then_some(text)
}

/// The only public I/O entry point. No arguments, writes, or raw data returned.
pub fn inspect_host_identity() -> HostIdentity {
    #[cfg(target_os = "linux")]
    {
        if !read_fixed(FixedInput::ProcessStatus).is_some_and(|text| ordinary_user(&text)) {
            return HostIdentity::Blocked;
        }
        read_fixed(FixedInput::OsRelease)
            .map(|text| classify_os(&text, cfg!(target_arch = "x86_64")))
            .unwrap_or(HostIdentity::Blocked)
    }
    #[cfg(not(target_os = "linux"))]
    { HostIdentity::Blocked }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn exact_identity_and_architecture_only() {
        let pop = "ID=pop\nVERSION_ID=\"24.04\"\nPRETTY_NAME=private-host\n";
        assert_eq!(classify_os(pop, true), HostIdentity::PopOs24Amd64);
        assert_eq!(classify_os(pop, false), HostIdentity::Other);
        assert_eq!(classify_os("ID=ubuntu\nVERSION_ID=24.04\n", true), HostIdentity::Other);
        for invalid in ["", "ID=pop", "ID=pop\nID=pop\nVERSION_ID=24.04", "ID=pop\nVERSION_ID=\"24.04", "ID=pop\nVERSION_ID=24.04;evil"] {
            assert_eq!(classify_os(invalid, true), HostIdentity::Blocked);
        }
    }
    #[cfg(target_os = "linux")]
    #[test]
    fn root_or_invalid_uid_is_refused() {
        assert!(ordinary_user("Uid:\t1000\t1000\t1000\t1000\n"));
        for invalid in ["", "Uid: 1000 0 1000 1000", "Uid: 0 0 0 0", "Uid: 1000 1000 1000", "Uid: 1000 x 1000 1000", "Uid: 1000 1000 1000 1000\nUid: 1000 1000 1000 1000"] {
            assert!(!ordinary_user(invalid));
        }
    }
    #[cfg(target_os = "linux")]
    #[test]
    #[ignore = "Explicit non-root Ubuntu runner smoke only"]
    fn live_ubuntu_identity_only() {
        assert_eq!(inspect_host_identity(), HostIdentity::Other);
    }
}
