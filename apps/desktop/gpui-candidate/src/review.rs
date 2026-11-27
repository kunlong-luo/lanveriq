use crate::runtime_metadata::RuntimeMetadata;
use crate::host_identity::HostIdentity;
// Offline-only projections. No host evidence is acquired by this module.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum GateStatus {
    NotChecked,
    NotApproved,
    MetadataOnly,
    Blocked,
}

impl GateStatus {
    pub const fn label(self) -> &'static str {
        match self {
            Self::NotChecked => "尚未核验",
            Self::NotApproved => "独立审批未完成",
            Self::MetadataOnly => "仅有元数据",
            Self::Blocked => "安全阻断",
        }
    }
}

pub struct ReviewGate {
    pub id: &'static str,
    pub title: &'static str,
    pub status: GateStatus,
    pub detail: &'static str,
}

/// Deliberately only the no-evidence branch of the old review contract.
pub const GATES: [ReviewGate; 7] = [
    ReviewGate { id: "host", title: "系统与架构", status: GateStatus::NotChecked,
        detail: "必须在受信任的 Pop!_OS 24.04 设备上完成独立核验。" },
    ReviewGate { id: "package-state", title: "既有 Agent 包", status: GateStatus::NotChecked,
        detail: "已有包、未知版本或检测失败时不得覆盖安装。" },
    ReviewGate { id: "service-state", title: "后台服务与旧版文件", status: GateStatus::NotChecked,
        detail: "服务、旧版文件、覆盖或 systemd 状态不明时禁止修改。" },
    ReviewGate { id: "compatibility", title: "COSMIC 与系统库", status: GateStatus::NotChecked,
        detail: "无可信的 COSMIC/ABI 元数据，不允许预设为兼容。" },
    ReviewGate { id: "publisher", title: "可信软件源与正式签名", status: GateStatus::NotApproved,
        detail: "未独立预置信任根、发行身份、撤销和 Signed-By 正式仓库。" },
    ReviewGate { id: "broker", title: "逐次管理员授权与受限 Polkit", status: GateStatus::NotApproved,
        detail: "未提供受审 broker；GUI 不得获得 root 或调用任意命令。" },
    ReviewGate { id: "real-host-l3", title: "Pop!_OS COSMIC 实机与法务批准", status: GateStatus::NotApproved,
        detail: "无正式的设备所有人同意、实机 L3 与法务/运维签署。" },
];

pub fn review_gates(identity: HostIdentity) -> [ReviewGate; 7] {
    let mut gates = GATES;
    match identity {
        HostIdentity::NotChecked => {},
        HostIdentity::PopOs24Amd64 => {
            gates[0].status = GateStatus::MetadataOnly;
            gates[0].detail = "仅核对 Pop!_OS 24.04 / x86_64 系统标识，非实机安全批准。";
        }
        HostIdentity::Blocked | HostIdentity::Other => {
            gates[0].status = GateStatus::Blocked;
        }
    }
    gates
}

pub fn diagnostic(identity: HostIdentity) -> String {
    OFFLINE_DIAGNOSTIC
        .replace("inspection_source=not_verified", &format!("inspection_source={}", identity.diagnostic_source()))
        .replace("os_target=unverified", &format!("os_target={}", identity.diagnostic_target()))
}

pub fn runtime_review_gates(identity: HostIdentity, metadata: RuntimeMetadata) -> [ReviewGate; 7] {
    let mut gates = review_gates(identity);
    if metadata.attempted() {
        gates[1].status = if metadata.package_clean() { GateStatus::MetadataOnly } else { GateStatus::Blocked };
        gates[2].status = if metadata.service_clean() { GateStatus::MetadataOnly } else { GateStatus::Blocked };
        if metadata.package_clean() { gates[1].detail = "仅报告未安装状态，不是安装许可。"; }
        if metadata.service_clean() { gates[2].detail = "仅报告未启用、未运行及无已知覆盖，不是授权。"; }
    }
    let graphics = metadata.graphics();
    if graphics.attempted() {
        gates[3].status = if identity == HostIdentity::PopOs24Amd64 && graphics.metadata_complete() {
            GateStatus::MetadataOnly
        } else { GateStatus::Blocked };
        if gates[3].status == GateStatus::MetadataOnly {
            gates[3].detail = "仅有会话声明和软件包元数据；GPU、实际 ABI、窗口及 L3 仍未验证。";
        }
    }
    gates
}

pub fn runtime_diagnostic(identity: HostIdentity, metadata: RuntimeMetadata) -> String {
    let mut report = diagnostic(identity);
    if metadata.attempted() {
        report = report.replace("inspection_source=native_readonly_identity_only", "inspection_source=native_readonly_partial");
        for (key, value) in metadata.diagnostic_rows() {
            report = report.replace(&format!("{key}=unverified\n"), &format!("{key}={value}\n"));
        }
    }
    if metadata.graphics().attempted() {
        report = report.replace("cosmic_preflight=not_verified",
            &format!("cosmic_preflight={}", metadata.graphics().diagnostic()));
    }
    report
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Notice {
    PackageSource,
    AdminAuthorization,
    SeparateServiceStart,
    DataPrivacy,
}

impl Notice {
    pub const ALL: [Self; 4] = [Self::PackageSource, Self::AdminAuthorization,
        Self::SeparateServiceStart, Self::DataPrivacy];

    const fn index(self) -> usize {
        match self {
            Self::PackageSource => 0,
            Self::AdminAuthorization => 1,
            Self::SeparateServiceStart => 2,
            Self::DataPrivacy => 3,
        }
    }

    pub const fn id(self) -> &'static str {
        match self {
            Self::PackageSource => "notice-package",
            Self::AdminAuthorization => "notice-admin",
            Self::SeparateServiceStart => "notice-service",
            Self::DataPrivacy => "notice-data",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::PackageSource => "Agent 必须来自独立批准的签名仓库，不能选择任意本地 .deb 或网址。",
            Self::AdminAuthorization => "安装、升级、卸载分别需要独立验证的管理员身份与逐次授权。",
            Self::SeparateServiceStart => "安装、后台服务启动、开机自启需分别选择与批准，不能捆绑。",
            Self::DataPrivacy => "联网、采集、自动恢复和远控保持关闭，安装不产生这些权限。",
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct NoticeReview([bool; 4]);

impl NoticeReview {
    pub fn checked(self, notice: Notice) -> bool {
        self.0[notice.index()]
    }

    pub(crate) fn set(&mut self, notice: Notice, checked: bool) {
        self.0[notice.index()] = checked;
    }

    pub fn all_read(self) -> bool {
        self.0.into_iter().all(|checked| checked)
    }
}

/// No host input, credentials, free-form strings, or authority token.
/// Mirrors the existing redacted diagnostic when no native evidence exists.
pub const OFFLINE_DIAGNOSTIC: &str = concat!(
    "LANVERIQ DESKTOP - LOCAL REDACTED DIAGNOSTIC v1\n",
    "Local text only; NOT a signed report, installer authorization, or physical L3 evidence.\n",
    "This window does not upload, save or send the report.\n",
    "inspection_source=not_verified\n",
    "os_target=unverified\n",
    "agent_package=unverified\n",
    "systemd_service=unverified\n",
    "systemd_unit=unverified\n",
    "systemd_dropins=unverified\n",
    "systemd_reload=unverified\n",
    "legacy_binary=unverified\n",
    "cosmic_preflight=not_verified\n",
    "official_signed_agent_package=NOT_APPROVED\n",
    "independent_publisher_root_and_highwater=NOT_VERIFIED\n",
    "reviewed_polkit_installer_broker=NOT_IMPLEMENTED\n",
    "popos_physical_l3=NOT_RUN\n",
    "production_mfa_ca_and_worm=NOT_APPROVED\n",
    "legal_and_sre_signoff=NOT_RUN\n",
    "install_authorized=false\n",
    "agent_start_authorized=false\n",
    "production_network_collection_recovery_remote=OFF\n",
    "release_gates_closed=0/10\n",
    "release_status=NOT_RELEASE_READY\n",
    "Manual inspection of real host/session evidence and independent approvals still required.\n",
);

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    #[test]
    fn offline_gates_never_imply_clean_host_or_approval() {
        assert_eq!(GATES.iter().map(|g| g.id).collect::<HashSet<_>>().len(), 7);
        assert_eq!(GATES.iter().filter(|g| g.status == GateStatus::NotChecked).count(), 4);
        assert_eq!(GATES.iter().filter(|g| g.status == GateStatus::NotApproved).count(), 3);
    }

    #[test]
    fn all_sixteen_notice_combinations_are_only_read_acknowledgments() {
        for mask in 0..16 {
            let mut review = NoticeReview::default();
            for (i, notice) in Notice::ALL.into_iter().enumerate() {
                review.set(notice, mask & (1 << i) != 0);
                assert_eq!(review.checked(notice), mask & (1 << i) != 0);
            }
            assert_eq!(review.all_read(), mask == 15);
            assert!(GATES.iter().all(|g| matches!(g.status, GateStatus::NotChecked | GateStatus::NotApproved)));
            assert!(OFFLINE_DIAGNOSTIC.contains("install_authorized=false"));
            assert!(OFFLINE_DIAGNOSTIC.contains("agent_start_authorized=false"));
        }
    }

    #[test]
    fn diagnostics_explicitly_mark_missing_evidence_and_never_serialize_host_input() {
        for line in [
            "inspection_source=not_verified", "os_target=unverified",
            "agent_package=unverified", "systemd_service=unverified", "systemd_unit=unverified",
            "systemd_dropins=unverified", "systemd_reload=unverified", "legacy_binary=unverified",
            "cosmic_preflight=not_verified", "install_authorized=false", "agent_start_authorized=false",
            "production_network_collection_recovery_remote=OFF", "release_status=NOT_RELEASE_READY",
        ] {
            assert!(OFFLINE_DIAGNOSTIC.lines().any(|actual| actual == line));
        }
        for forbidden in ["=true", "native_readonly", "metadata_checked_not_l3", "/home/", "https://", "-----BEGIN"] {
            assert!(!OFFLINE_DIAGNOSTIC.contains(forbidden));
        }
    }

    #[test]
    fn identity_only_never_updates_package_service_cosmic_or_authority() {
        for identity in [HostIdentity::NotChecked, HostIdentity::Blocked, HostIdentity::PopOs24Amd64, HostIdentity::Other] {
            let gates = review_gates(identity);
            assert_eq!(gates[0].status, match identity {
                HostIdentity::NotChecked => GateStatus::NotChecked,
                HostIdentity::PopOs24Amd64 => GateStatus::MetadataOnly,
                _ => GateStatus::Blocked,
            });
            assert!(gates[1..4].iter().all(|g| g.status == GateStatus::NotChecked));
            assert!(gates[4..].iter().all(|g| g.status == GateStatus::NotApproved));
            let report = diagnostic(identity);
            assert!(report.contains("agent_package=unverified"));
            assert!(report.contains("systemd_service=unverified"));
            assert!(report.contains("cosmic_preflight=not_verified"));
            assert!(report.contains("install_authorized=false"));
            assert!(report.contains("agent_start_authorized=false"));
            assert!(!report.contains("private-host"));
        }
    }
}
