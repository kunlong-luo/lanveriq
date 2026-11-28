pub mod graphics;
pub mod runtime_metadata;
use runtime_metadata::RuntimeMetadata;
pub mod host_identity;
use host_identity::HostIdentity;
pub mod review;
use review::{Notice, NoticeReview};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Page {
    Overview,
    Setup,
    Configuration,
    Enrollment,
    Service,
    Diagnostics,
    About,
}

impl Page {
    pub const ALL: [Self; 7] = [
        Self::Overview,
        Self::Setup,
        Self::Configuration,
        Self::Enrollment,
        Self::Service,
        Self::Diagnostics,
        Self::About,
    ];

    pub const fn id(self) -> &'static str {
        match self {
            Self::Overview => "overview",
            Self::Setup => "setup",
            Self::Configuration => "configuration",
            Self::Enrollment => "enrollment",
            Self::Service => "service",
            Self::Diagnostics => "diagnostics",
            Self::About => "about",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::Overview => "概览",
            Self::Setup => "安装向导",
            Self::Configuration => "服务器与配置",
            Self::Enrollment => "设备注册",
            Self::Service => "Agent 服务",
            Self::Diagnostics => "安全与诊断",
            Self::About => "关于",
        }
    }

    /// Each restricted action has exactly one destination. No capability is granted.
    pub const fn locked_actions(self) -> &'static [&'static str] {
        match self {
            Self::Setup => &["安装 Agent", "升级 Agent", "卸载 Agent"],
            Self::Configuration => &["保存配置", "连接检测"],
            Self::Enrollment => &["注册设备", "导入凭据"],
            Self::Service => &["启动服务", "停止服务", "开机自启"],
            Self::Overview | Self::Diagnostics | Self::About => &[],
        }
    }

    pub const fn description(self) -> &'static str {
        match self {
            Self::Overview => "本机系统与 Agent 状态可手动只读检测；服务器与注册状态未核验。",
            Self::Setup => "安装需要独立发行身份、受限权限代理和逐次授权。当前不可安装。",
            Self::Configuration => "仅生成非机密的 Agent 空闲周期草稿；不读取或保存真实服务器配置。",
            Self::Enrollment => "未接入设备注册、证书或私钥操作。",
            Self::Service => "仅手动查询 systemd 元数据，不启动或停止任何服务。",
            Self::Diagnostics => "不读取日志、采集资产、发送诊断或改变系统权限。",
            Self::About => "Lanveriq · GPUI 原生候选 · 非发行版本",
        }
    }
}

/// GPUI viewport widths are logical pixels; do not multiply by monitor scale.
pub const fn compact_navigation(viewport_width: f32) -> bool {
    viewport_width < 760.0
}

/// In-memory appearance preference; never changes the system theme or writes configuration.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum ThemePreference {
    #[default]
    System,
    Light,
    Dark,
}

impl ThemePreference {
    pub const ALL: [Self; 3] = [Self::System, Self::Light, Self::Dark];

    pub const fn id(self) -> &'static str {
        match self {
            Self::System => "theme-system",
            Self::Light => "theme-light",
            Self::Dark => "theme-dark",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::System => "跟随系统",
            Self::Light => "浅色",
            Self::Dark => "深色",
        }
    }

    pub const fn is_dark(self, system_dark: bool) -> bool {
        match self {
            Self::System => system_dark,
            Self::Light => false,
            Self::Dark => true,
        }
    }
}

/// The same five reviewed choices as the existing desktop preview.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum IdleInterval {
    Seconds15,
    #[default]
    Seconds30,
    Seconds60,
    Seconds120,
    Seconds300,
}

impl IdleInterval {
    pub const ALL: [Self; 5] = [
        Self::Seconds15, Self::Seconds30, Self::Seconds60,
        Self::Seconds120, Self::Seconds300,
    ];

    pub const fn seconds(self) -> u64 {
        match self {
            Self::Seconds15 => 15,
            Self::Seconds30 => 30,
            Self::Seconds60 => 60,
            Self::Seconds120 => 120,
            Self::Seconds300 => 300,
        }
    }

    pub const fn id(self) -> &'static str {
        match self {
            Self::Seconds15 => "idle-15",
            Self::Seconds30 => "idle-30",
            Self::Seconds60 => "idle-60",
            Self::Seconds120 => "idle-120",
            Self::Seconds300 => "idle-300",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::Seconds15 => "15 秒",
            Self::Seconds30 => "30 秒（推荐）",
            Self::Seconds60 => "1 分钟",
            Self::Seconds120 => "2 分钟",
            Self::Seconds300 => "5 分钟",
        }
    }

    /// Only reviewed enum variants can produce a preview; no arbitrary text input.
    pub fn preview(self) -> String {
        format!("schema_version = 1\n\n[runtime]\ncycle_seconds = {}\n", self.seconds())
    }
}

#[derive(Debug)]
pub struct Workspace {
    page: Page,
    idle_interval: IdleInterval,
    notices: NoticeReview,
    host_identity: HostIdentity,
    runtime_metadata: RuntimeMetadata,
}

impl Default for Workspace {
    fn default() -> Self {
        Self { page: Page::Overview, idle_interval: IdleInterval::default(), notices: NoticeReview::default(), host_identity: HostIdentity::default(), runtime_metadata: RuntimeMetadata::default() }
    }
}

impl Workspace {
    pub fn page(&self) -> Page {
        self.page
    }

    pub fn navigate(&mut self, page: Page) {
        if self.page != page {
            self.idle_interval = IdleInterval::default();
            self.notices = NoticeReview::default();
        }
        self.page = page;
    }

    pub fn idle_interval(&self) -> IdleInterval {
        self.idle_interval
    }

    pub fn host_identity(&self) -> HostIdentity {
        self.host_identity
    }

    pub fn replace_host_identity(&mut self, identity: HostIdentity) {
        self.host_identity = identity;
        self.runtime_metadata = RuntimeMetadata::default();
        self.notices = NoticeReview::default();
    }

    pub fn runtime_metadata(&self) -> RuntimeMetadata { self.runtime_metadata }

    pub fn replace_runtime_metadata(&mut self, identity: HostIdentity, metadata: RuntimeMetadata) {
        self.replace_host_identity(identity);
        if matches!(identity, HostIdentity::PopOs24Amd64 | HostIdentity::Other) {
            self.runtime_metadata = metadata;
        }
    }

    pub fn notices(&self) -> NoticeReview {
        self.notices
    }

    pub fn set_notice(&mut self, notice: Notice, checked: bool) {
        if self.page == Page::Setup {
            self.notices.set(notice, checked);
        }
    }

    pub fn select_idle_interval(&mut self, interval: IdleInterval) {
        if self.page == Page::Configuration {
            self.idle_interval = interval;
        }
    }
}

pub const LOCKED_ACTIONS: [&str; 10] = [
    "安装 Agent", "保存配置", "注册设备", "连接检测", "启动服务", "开机自启", "导入凭据", "升级 Agent", "卸载 Agent", "停止服务",
];

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    #[test]
    fn all_destinations_are_distinct_and_navigable() {
        let mut workspace = Workspace::default();
        assert_eq!(workspace.page(), Page::Overview);
        assert_eq!(Page::ALL.iter().map(|p| p.id()).collect::<HashSet<_>>().len(), 7);
        for page in Page::ALL {
            workspace.navigate(page);
            assert_eq!(workspace.page(), page);
            assert!(!page.description().is_empty());
        }
    }

    #[test]
    fn locked_actions_are_partitioned_without_omission_or_duplication() {
        let actions: Vec<_> = Page::ALL.into_iter().flat_map(Page::locked_actions).copied().collect();
        assert_eq!(actions.len(), LOCKED_ACTIONS.len());
        assert_eq!(actions.into_iter().collect::<HashSet<_>>(), LOCKED_ACTIONS.into_iter().collect());
        assert!(Page::Overview.locked_actions().is_empty());
        assert!(Page::Diagnostics.locked_actions().is_empty());
        assert!(Page::About.locked_actions().is_empty());
    }

    #[test]
    fn overview_does_not_claim_live_evidence() {
        assert!(Page::Overview.description().contains("未核验"));
        assert_eq!(LOCKED_ACTIONS.len(), 10);
    }

    #[test]
    fn navigation_compacts_at_logical_viewport_boundary() {
        assert!(compact_navigation(640.0));
        assert!(compact_navigation(759.99));
        assert!(!compact_navigation(760.0));
        assert!(!compact_navigation(900.0));
        assert!(!compact_navigation(1920.0));
    }

    #[test]
    fn theme_choices_follow_system_only_in_system_mode() {
        assert_eq!(ThemePreference::default(), ThemePreference::System);
        assert_eq!(ThemePreference::ALL.map(|p| p.is_dark(false)), [false, false, true]);
        assert_eq!(ThemePreference::ALL.map(|p| p.is_dark(true)), [true, false, true]);
        assert_eq!(ThemePreference::ALL.iter().map(|p| p.id()).collect::<HashSet<_>>().len(), 3);
        assert!(ThemePreference::ALL.iter().all(|p| !p.label().is_empty()));
    }

    #[test]
    fn offline_config_matches_reviewed_agent_v1_choices() {
        assert_eq!(IdleInterval::ALL.map(IdleInterval::seconds), [15, 30, 60, 120, 300]);
        assert_eq!(IdleInterval::default().preview(), "schema_version = 1\n\n[runtime]\ncycle_seconds = 30\n");
        assert_eq!(IdleInterval::ALL.iter().map(|i| i.id()).collect::<HashSet<_>>().len(), 5);
        for choice in IdleInterval::ALL {
            assert_eq!(choice.preview(), format!("schema_version = 1\n\n[runtime]\ncycle_seconds = {}\n", choice.seconds()));
            for forbidden in ["token", "password", "endpoint", "capture", "network", "remote", "key", "issuer"] {
                assert!(!choice.preview().contains(forbidden));
            }
        }
    }

    #[test]
    fn config_selection_is_page_local_and_never_persists() {
        let mut workspace = Workspace::default();
        workspace.select_idle_interval(IdleInterval::Seconds300);
        assert_eq!(workspace.idle_interval(), IdleInterval::Seconds30);
        workspace.navigate(Page::Configuration);
        workspace.select_idle_interval(IdleInterval::Seconds120);
        assert_eq!(workspace.idle_interval(), IdleInterval::Seconds120);
        workspace.navigate(Page::Configuration);
        assert_eq!(workspace.idle_interval(), IdleInterval::Seconds120);
        workspace.navigate(Page::Diagnostics);
        workspace.navigate(Page::Configuration);
        assert_eq!(workspace.idle_interval(), IdleInterval::Seconds30);
    }

    #[test]
    fn review_is_page_local_and_resets_without_authorizing_actions() {
        let mut workspace = Workspace::default();
        workspace.set_notice(Notice::PackageSource, true);
        assert!(!workspace.notices().checked(Notice::PackageSource));
        workspace.navigate(Page::Setup);
        for notice in Notice::ALL {
            workspace.set_notice(notice, true);
        }
        assert!(workspace.notices().all_read());
        workspace.navigate(Page::Setup);
        assert!(workspace.notices().all_read());
        workspace.navigate(Page::Diagnostics);
        workspace.navigate(Page::Setup);
        assert!(Notice::ALL.into_iter().all(|notice| !workspace.notices().checked(notice)));
        assert_eq!(LOCKED_ACTIONS.len(), 10);
    }

    #[test]
    fn replacing_identity_clears_review_and_old_metadata() {
        let mut w = Workspace::default();
        w.replace_host_identity(HostIdentity::PopOs24Amd64);
        w.navigate(Page::Setup);
        w.set_notice(Notice::PackageSource, true);
        w.replace_host_identity(HostIdentity::Blocked);
        assert_eq!(w.host_identity(), HostIdentity::Blocked);
        assert!(!w.notices().checked(Notice::PackageSource));
    }
}
