//! Manual local OS/CPU/RAM snapshot; no process, user, disk, network, or hostname API.
use lanveriq_desktop_gpui_candidate::host_identity::HostIdentity;
use sysinfo::{CpuRefreshKind, MemoryRefreshKind, RefreshKind, System};

#[derive(Default)]
pub struct SystemSummary {
    os: Option<String>,
    version: Option<String>,
    kernel: Option<String>,
    cpu: Option<String>,
    logical: Option<usize>,
    physical: Option<usize>,
    total: Option<u64>,
    available: Option<u64>,
}

fn bounded(value: Option<String>) -> Option<String> {
    value.and_then(|value| {
        let trimmed = value.trim();
        (!trimmed.is_empty() && trimmed.len() <= 200 &&
            !trimmed.chars().any(char::is_control)).then(|| trimmed.to_owned())
    })
}
fn count(value: usize) -> Option<usize> { (value > 0).then_some(value) }
fn memory(total: u64, available: u64) -> (Option<u64>, Option<u64>) {
    ((total > 0).then_some(total),
     (total > 0 && available > 0 && available <= total).then_some(available))
}
fn number(value: Option<usize>) -> String {
    value.map(|n| n.to_string()).unwrap_or_else(|| "未核验".into())
}
fn gib(value: Option<u64>) -> String {
    value.map(|n| format!("{:.2} GiB", n as f64 / 1_073_741_824.0))
        .unwrap_or_else(|| "未核验".into())
}

impl SystemSummary {
    pub fn inspect(identity: HostIdentity) -> Self {
        if !matches!(identity, HostIdentity::PopOs24Amd64 | HostIdentity::Other) {
            return Self::default();
        }
        let system = System::new_with_specifics(
            RefreshKind::new().with_cpu(CpuRefreshKind::new().with_frequency())
                .with_memory(MemoryRefreshKind::new().with_ram()));
        let (total, available) = memory(system.total_memory(), system.available_memory());
        Self {
            os: bounded(System::name()),
            version: bounded(System::os_version()),
            kernel: bounded(System::kernel_version()),
            cpu: bounded(system.cpus().first().map(|cpu| cpu.brand().to_owned())),
            logical: count(system.cpus().len()),
            physical: system.physical_core_count().and_then(count),
            total, available,
        }
    }

    pub fn metrics(&self) -> [(&'static str, String); 3] {
        [("逻辑核心", number(self.logical)), ("物理内存", gib(self.total)),
         ("可用内存", gib(self.available))]
    }

    pub fn rows(&self) -> [(&'static str, String); 8] {
        let text = |v: &Option<String>| v.clone().unwrap_or_else(|| "未核验".into());
        [("操作系统", text(&self.os)), ("系统版本", text(&self.version)),
         ("内核版本", text(&self.kernel)), ("CPU 型号", text(&self.cpu)),
         ("物理核心", number(self.physical)), ("逻辑核心", number(self.logical)),
         ("总内存", gib(self.total)), ("可用内存（快照）", gib(self.available))]
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unknown_and_blocked_never_fabricate_values() {
        for identity in [HostIdentity::NotChecked, HostIdentity::Blocked] {
            assert!(SystemSummary::inspect(identity).rows().iter().all(|(_, v)| v == "未核验"));
        }
        assert_eq!(bounded(Some("  ".into())), None);
        assert_eq!(bounded(Some("bad\ntext".into())), None);
        assert_eq!(bounded(Some("a".repeat(201))), None);
        assert_eq!(bounded(Some(" CPU model ".into())), Some("CPU model".into()));
        assert_eq!(memory(0, 0), (None, None));
        assert_eq!(memory(100, 101), (Some(100), None));
        assert_eq!(gib(Some(1_073_741_824)), "1.00 GiB");
        assert!(SystemSummary::default().metrics().iter().all(|(_, v)| v == "未核验"));
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn actual_nonroot_linux_snapshot_if_supported() {
        let identity = lanveriq_desktop_gpui_candidate::host_identity::inspect_host_identity();
        assert!(matches!(identity, HostIdentity::PopOs24Amd64 | HostIdentity::Other),
                "CI must run in an ordinary Linux user session");
        let data = SystemSummary::inspect(identity);
        assert!(data.os.is_some() && data.version.is_some() && data.kernel.is_some());
        assert!(data.cpu.is_some() && data.logical.is_some() && data.total.is_some());
    }
}
