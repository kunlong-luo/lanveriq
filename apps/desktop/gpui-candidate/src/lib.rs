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

    pub const fn description(self) -> &'static str {
        match self {
            Self::Overview => "本机系统、Agent、服务器连接和注册状态均未核验。",
            Self::Setup => "安装需要独立发行身份、受限权限代理和逐次授权。当前不可安装。",
            Self::Configuration => "仅作页面导航验证；不读取或保存真实服务器配置。",
            Self::Enrollment => "未接入设备注册、证书或私钥操作。",
            Self::Service => "未读取 systemd 状态，未启动或停止任何服务。",
            Self::Diagnostics => "不读取日志、采集资产、发送诊断或改变系统权限。",
            Self::About => "Lanveriq · GPUI 原生候选 · 非发行版本",
        }
    }
}

#[derive(Debug)]
pub struct Workspace {
    page: Page,
}

impl Default for Workspace {
    fn default() -> Self {
        Self { page: Page::Overview }
    }
}

impl Workspace {
    pub fn page(&self) -> Page {
        self.page
    }

    pub fn navigate(&mut self, page: Page) {
        self.page = page;
    }
}

pub const LOCKED_ACTIONS: [&str; 6] = [
    "安装 Agent", "保存配置", "注册设备", "连接检测", "启动服务", "开机自启",
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
    fn overview_does_not_claim_live_evidence() {
        assert!(Page::Overview.description().contains("未核验"));
        assert_eq!(LOCKED_ACTIONS.len(), 6);
    }
}
