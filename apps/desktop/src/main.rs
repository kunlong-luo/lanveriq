//! Disposable offline GPUI compatibility candidate. No privileged or runtime interfaces.
use gpui_kit::component::{
    ActiveTheme, Collapsible, Disableable, Selectable, Theme, ThemeMode,
    button::{Button, ButtonVariants},
    sidebar::{Sidebar, SidebarItem},
    IconName,
    checkbox::Checkbox,
    label::Label,
    input::{Input, InputState},
};
use gpui_kit::base::{SelectableText, StyledExt};
use gpui_kit::*;
use gpui_kit::prelude::FluentBuilder;
use lanveriq_desktop_gpui_candidate::{IdleInterval, Page, ThemePreference, Workspace, compact_navigation, AGENT_DRAFT_ORIGIN, ServerDraft};

use lanveriq_desktop_gpui_candidate::review::{runtime_review_gates, Notice, runtime_diagnostic};
use lanveriq_desktop_gpui_candidate::host_identity::HostIdentity;
use lanveriq_desktop_gpui_candidate::runtime_metadata::inspect_runtime;

mod candidate_assets;
mod system_summary;
use system_summary::SystemSummary;

actions!(lanveriq_desktop, [Quit]);

struct Desktop {
    workspace: Workspace,
    inspecting: bool,
    system_summary: SystemSummary,
    ime_probe: Option<Entity<InputState>>,
    appearance: ThemePreference,
    _appearance_subscription: Subscription,
}

fn apply_appearance(preference: ThemePreference, window: &Window, cx: &mut App) {
    let system_dark = ThemeMode::from(window.appearance()).is_dark();
    let dark = preference.is_dark(system_dark);
    if cx.theme().is_dark() != dark {
        Theme::change(if dark { ThemeMode::Dark } else { ThemeMode::Light }, None, cx);
    }
}

impl Desktop {
    fn new(window: &mut Window, cx: &mut Context<Self>) -> Self {
        let appearance = ThemePreference::default();
        apply_appearance(appearance, window, cx);
        let subscription = cx.observe_window_appearance(window, |this, window, cx| {
            if this.appearance == ThemePreference::System {
                apply_appearance(this.appearance, window, cx);
                cx.notify();
            }
        });
        Self {
            workspace: Workspace::default(),
            inspecting: false,
            system_summary: SystemSummary::default(),
            ime_probe: None,
            appearance,
            _appearance_subscription: subscription,
        }
    }
}

// SidebarMenuItem in the pinned component release has a click handler but
// does not register a tab stop. Reuse native Button focus/activation instead.
#[derive(Clone)]
struct DestinationItem {
    page: Page,
    active: bool,
    collapsed: bool,
    handler: std::rc::Rc<dyn Fn(&ClickEvent, &mut Window, &mut App)>,
}

impl Collapsible for DestinationItem {
    fn is_collapsed(&self) -> bool { self.collapsed }
    fn collapsed(mut self, collapsed: bool) -> Self {
        self.collapsed = collapsed;
        self
    }
}

impl SidebarItem for DestinationItem {
    fn render(self, id: impl Into<ElementId>, _: &mut Window, _: &mut App) -> impl IntoElement {
        let handler = self.handler;
        let button = Button::new(id)
            .ghost()
            .accessibility_label(self.page.label())
            .selected(self.active).toggled(self.active)
            .tab_stop(true).w_full().h(px(40.))
            .on_click(move |event, window, cx| handler(event, window, cx));
        if self.collapsed {
            button.icon(page_icon(self.page)).tooltip(self.page.label())
        } else {
            // Button's inner label container is centered independently of its
            // outer style. A full-width child owns actual icon/text alignment.
            button.child(div().w_full().h_full().flex().items_center().justify_start()
                .gap_2()
                .child(div().size(px(18.)).flex_shrink_0().flex().items_center().justify_center()
                    .child(page_icon(self.page)))
                .child(div().flex_1().min_w_0().flex().items_center().justify_start()
                    .child(self.page.label())))
        }
    }
}

fn page_icon(page: Page) -> IconName {
    match page {
        Page::Overview => IconName::LayoutDashboard,
        Page::Setup => IconName::HardDrive,
        Page::Configuration => IconName::Settings,
        Page::Enrollment => IconName::CircleUser,
        Page::Service => IconName::Cpu,
        Page::Diagnostics => IconName::FileText,
        Page::About => IconName::Info,
    }
}

fn panel(cx: &App) -> Div {
    div().flex().flex_col().gap_3().p_4()
        .rounded(cx.theme().radius).border_1().border_color(cx.theme().border)
        .bg(cx.theme().background)
}

fn section_title(title: &'static str) -> Label {
    Label::new(title).text_size(px(16.)).font_bold()
}

fn hint(text: &'static str, cx: &App) -> Label {
    Label::new(text).text_size(px(13.)).text_color(cx.theme().muted_foreground)
}

fn state_label(value: &str) -> &'static str {
    match value {
        "inactive" => "未运行（元数据）",
        "active" => "运行中（未批准）",
        "failed" => "服务失败",
        "enabled" => "已启用（未批准）",
        "disabled" => "未启用（元数据）",
        "masked" => "已屏蔽",
        "not_installed" => "未安装（元数据）",
        "preview_candidate" => "候选包（未批准）",
        "unrecognized_version" => "未知版本",
        "none_reported" => "未报告覆盖（未独立验证）",
        "present_unreviewed" => "存在，待审核",
        "reload_required" => "需要重新加载",
        "not_required" => "未报告需要重新加载",
        "not_detected" => "未检出（非证明）",
        _ => "未核验",
    }
}

fn row_title(key: &str) -> &'static str {
    match key {
        "agent_package" => "Agent 软件包",
        "systemd_service" => "后台服务",
        "systemd_unit" => "开机自启",
        "systemd_dropins" => "服务覆盖配置",
        "systemd_reload" => "服务重载",
        "legacy_binary" => "旧版可执行文件",
        _ => "未知字段",
    }
}

impl Render for Desktop {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let page = self.workspace.page();
        let compact = compact_navigation(window.viewport_size().width.as_f32());
        let destinations = Page::ALL.into_iter().map(|destination| DestinationItem {
            page: destination,
            active: page == destination,
            collapsed: compact,
            handler: std::rc::Rc::new(cx.listener(move |this, _, window, cx| {
                if this.workspace.page() != destination {
                    // Drop the entire editing entity on leaving About.
                    this.ime_probe = if destination == Page::About {
                        Some(cx.new(|cx| InputState::new(window, cx)
                            .placeholder("仅输入示例：中文输入测试")
                            .context_menu(false)))
                    } else { None };
                }
                this.workspace.navigate(destination);
                cx.notify();
            })),
        });
        let nav = Sidebar::<DestinationItem>::new("workspace-nav")
            .w(px(200.)).collapsible(true).collapsed(compact)
            .children(destinations);
        let nav = if compact {
            nav
        } else {
            nav.header(div().flex().flex_col().gap_1().px_3().py_3()
                .child(Label::new("Lanveriq").text_size(px(20.)).font_bold())
                .child(hint("设备管理 · 本机客户端", cx)))
            .footer(div().flex().flex_col().gap_1().p_3()
                .child(Label::new("离线候选").font_bold())
                .child(hint("仅本机只读 · 非发行版", cx)))
        };
        let mut content = div().id(page.id()).w_full().min_w_0().max_w(px(1120.))
            .flex().flex_col().gap_4()
            .child(div().flex().flex_wrap().items_center().justify_between().gap_3()
                .child(div().flex().flex_col().gap_1()
                    .child(Label::new(page.label()).text_size(px(24.)).font_bold())
                    .child(hint(page.description(), cx)))
                .when(matches!(page, Page::Overview | Page::Setup | Page::Service | Page::Diagnostics), |header| {
                    header.child(Button::new("inspect-host-identity")
                    .primary().icon(IconName::RefreshCw).tooltip("手动读取本机系统、Agent 与服务；不上传")
                    .label(if self.inspecting { "正在检测…" } else { "刷新信息" })
                    .disabled(self.inspecting)
                    .on_click(cx.listener(|this, _, _, cx| {
                        if this.inspecting { return; }
                        this.inspecting = true;
                        this.workspace.replace_host_identity(HostIdentity::NotChecked);
                        this.system_summary = SystemSummary::default();
                        cx.notify();
                        cx.spawn(async move |this, cx| {
                            let (identity, metadata, summary) = cx.background_spawn(async {
                                let (identity, metadata) = inspect_runtime();
                                (identity, metadata, SystemSummary::inspect(identity))
                            }).await;
                            this.update(cx, |this, cx| {
                                this.workspace.replace_runtime_metadata(identity, metadata);
                                this.system_summary = summary;
                                this.inspecting = false;
                                cx.notify();
                            }).ok();
                        }).detach();
                    })))
                }))
            .when(self.inspecting, |content| content.child(hint("正在读取本机数据，旧结果已清空。", cx)));
        if page == Page::Enrollment {
            content = content.child(panel(cx)
                .child(section_title("设备注册尚未开放"))
                .child(Label::new("注册状态：未核验"))
                .child(Label::new("设备凭据：未核验"))
                .child(hint("没有注册或凭据导入接口，不读取证书或私钥。", cx)));
        }
        if page == Page::Configuration {
            let draft = self.workspace.server_draft();
            content = content.child(panel(cx)
                .child(section_title("服务器配置 · 离线草稿"))
                .child(hint("仅允许现有 Agent 的固定 HTTPS 地址；不接收自定义地址、凭据或文件路径。", cx))
                .child(div().p_3().border_1().border_color(cx.theme().border)
                    .child(SelectableText::new("server-origin-preview", AGENT_DRAFT_ORIGIN)))
                .child(div().flex().flex_wrap().gap_2()
                    .child(Button::new("validate-server-draft").primary().label("校验草稿")
                        .on_click(cx.listener(|this, _, _, cx| {
                            this.workspace.validate_server_draft(AGENT_DRAFT_ORIGIN);
                            cx.notify();
                        })))
                    .child(Button::new("clear-server-draft").label("清空草稿")
                        .disabled(draft == ServerDraft::Unconfigured)
                        .on_click(cx.listener(|this, _, _, cx| {
                            this.workspace.clear_server_draft();
                            cx.notify();
                        }))))
                .child(Label::new(draft.label()))
                .child(hint("离线通过不代表服务器在线、证书可信或设备注册。真实检测需独立批准目的地与可信 CA。", cx))
                .child(hint("不写配置、不读取密钥、不联网，离开本页重置。", cx)));
        }
        if page == Page::About {
            let appearance = self.appearance;
            let choices = ThemePreference::ALL.into_iter().fold(
                div().flex().flex_wrap().gap_2(),
                |group, choice| group.child(Button::new(choice.id())
                    .icon(match choice {
                        ThemePreference::System => IconName::LayoutDashboard,
                        ThemePreference::Light => IconName::Sun,
                        ThemePreference::Dark => IconName::Moon,
                    })
                    .label(choice.label())
                    .selected(appearance == choice).toggled(appearance == choice)
                    .on_click(cx.listener(move |this, _, window, cx| {
                        this.appearance = choice;
                        apply_appearance(choice, window, cx);
                        cx.notify();
                    }))),
            );
            content = content.child(panel(cx)
                .child(section_title("外观"))
                .child(choices)
                .child(Label::new(if cx.theme().is_dark() { "当前界面：深色" } else { "当前界面：浅色" }))
                .child(hint("仅本次运行有效；退出后恢复跟随系统，不修改系统设置或写入配置。", cx)))
                .child(panel(cx)
                .child(section_title("Lanveriq Desktop"))
                .child(Label::new("0.1.0 · 原生 GPUI 候选"))
                .child(hint("Rust / GPUI Kit 0.11.0 · 离线 · 非发行版", cx))
                .child(hint("发行许可及实机兼容性仍待审核。", cx)));
        }
        if page == Page::Overview {
            let metrics = self.system_summary.metrics().into_iter().fold(
                div().flex().flex_wrap().gap_3(), |group, (key, value)| {
                    group.child(panel(cx).flex_1().min_w(px(150.))
                        .child(hint(key, cx))
                        .child(Label::new(value).text_size(px(20.)).font_bold()))
                });
            let summary = self.system_summary.rows().into_iter().enumerate().fold(
                div().flex().flex_col().gap_1(), |group, (i, (key, value))| {
                    group.child(div().id(format!("system-summary-{i}")).flex().flex_wrap()
                        .items_center().justify_between().gap_3().py_2().border_b_1()
                        .border_color(cx.theme().border)
                        .child(Label::new(key))
                        .child(Label::new(value).text_color(cx.theme().muted_foreground)))
                });
            let service_rows = self.workspace.runtime_metadata().rows();
            content = content.child(metrics)
                .child(panel(cx)
                    .child(section_title("本机系统信息"))
                    .child(Label::new(self.workspace.host_identity().label()))
                    .child(hint("手动检测时更新的只读快照；不保存、不上传。", cx))
                    .child(summary))
                .child(panel(cx)
                    .child(section_title("运行状态"))
                    .child(Label::new(format!("Agent 软件包：{}", state_label(service_rows[0].1))))
                    .child(Label::new(format!("后台服务：{}", state_label(service_rows[1].1))))
                    .child(Label::new("服务器：未核验 · 设备注册：未核验"))
                    .child(hint("未核验不代表离线、未安装或已停止。", cx)));
        }
        if page == Page::Service {
            let rows = self.workspace.runtime_metadata().rows().into_iter().fold(
                div().flex().flex_col().gap_1(), |group, (key, value)| {
                    group.child(div().id(key).flex().flex_wrap().items_center().justify_between().gap_3()
                        .py_2().border_b_1().border_color(cx.theme().border)
                        .child(Label::new(row_title(key)))
                        .child(Label::new(state_label(value)).text_color(cx.theme().muted_foreground)))
                });
            content = content.child(panel(cx)
                .child(section_title("代理服务状态"))
                .child(hint("仅本机只读检查；未核验项不代表未安装或已停止。", cx))
                .child(rows));
        }
        if matches!(page, Page::Setup | Page::Diagnostics) {
            content = content.child(panel(cx)
                .child(section_title("图形环境预检"))
                .child(Label::new(self.workspace.host_identity().label()))
                .child(self.workspace.runtime_metadata().graphics().rows().into_iter().enumerate().fold(
                    div().flex().flex_col().gap_2(), |group, (i, (key, value))| {
                        group.child(div().id(format!("graphics-{i}")).flex().flex_wrap().items_center().justify_between().gap_3()
                            .py_2().border_b_1().border_color(cx.theme().border)
                            .child(Label::new(key))
                            .child(Label::new(value).text_color(cx.theme().muted_foreground)))
                    }))
                .child(hint("仅会话声明及软件包元数据；KDE 不要求 COSMIC 组件。未验证 GPU、实际 ABI、显示连接或窗口启动。", cx)));
        }
        if page == Page::Configuration {
            let current = self.workspace.idle_interval();
            let choices = IdleInterval::ALL.into_iter().fold(
                div().flex().flex_wrap().gap_2(),
                |group, interval| group.child(
                    Button::new(interval.id())
                        .label(interval.label())
                        .selected(current == interval).toggled(current == interval)
                        .on_click(cx.listener(move |this, _, _, cx| {
                            this.workspace.select_idle_interval(interval);
                            cx.notify();
                        })),
                ),
            );
            content = content.child(
                panel(cx)
                    .child(section_title("Agent 空闲运行周期（不授权启动）"))
                    .child(choices)
                    .child(Label::new("只读 TOML 草稿 · 可选择文本"))
                    .child(div().p_3().border_1().border_color(cx.theme().border)
                        .child(SelectableText::new("idle-config-preview", current.preview())))
                    .child(Label::new("仅内存预览，不写入文件，不修改服务，离开此页即重置。")),
            );
        }
        if page == Page::Setup {
            let gates = runtime_review_gates(self.workspace.host_identity(), self.workspace.runtime_metadata()).iter().fold(div().flex().flex_col().gap_3(), |group, gate| {
                group.child(div().id(gate.id).flex().flex_col().gap_1().p_3()
                    .rounded(cx.theme().radius).border_1().border_color(cx.theme().border)
                    .child(Label::new(gate.title))
                    .child(Label::new(gate.status.label()))
                    .child(Label::new(gate.detail)))
            });
            let review = self.workspace.notices();
            let notices = Notice::ALL.into_iter().fold(div().flex().flex_col().gap_3(), |group, notice| {
                group.child(Checkbox::new(notice.id())
                    .label(notice.label())
                    .checked(review.checked(notice))
                    .on_change(cx.listener(move |this, checked: &bool, _, cx| {
                        this.workspace.set_notice(notice, *checked);
                        cx.notify();
                    })))
            });
            content = content
                 .child(panel(cx).child(section_title("安装前置条件 · 独立安全审核")).child(gates))
                .child(panel(cx).child(section_title("权限与安装限制（非授权）"))
                .child(notices)
                .child(Label::new(if review.all_read() {
                    "全部说明已阅；发行、Polkit、L3 门禁仍未批准，无法安装或启动 Agent。"
                } else {
                    "阅读说明不会启动 Agent，也不会向系统索取管理员权限。"
                })));
        }
        if page == Page::Diagnostics {
            content = content
                .child(panel(cx).child(section_title("本机脱敏诊断 · 部分只读元数据"))
                .child(Label::new("只读文本，未签名，非安装授权；不保存、不联网、不上传。"))
                .child(div().p_3().border_1().border_color(cx.theme().border)
                    .child(SelectableText::new("offline-diagnostic", runtime_diagnostic(self.workspace.host_identity(), self.workspace.runtime_metadata()))))
                .child(hint("向第三方分享前请自行确认内容。", cx)));
        }
        if page == Page::About {
            if let Some(input) = self.ime_probe.as_ref() {
                content = content.child(panel(cx)
                    .child(section_title("中文输入法兼容性测试（仅合成文本）"))
                    .child(Label::new("请使用本机输入法组合并确认：中文输入测试。不要输入密码、地址或个人信息。"))
                    .child(Input::new(input).id("synthetic-ime-probe").aria_label("中文输入法合成测试").cleanable(true))
                    .child(Label::new("仅临时编辑状态；离开本页后重新进入为空，不写配置、不记录、不上传。")));
            }
        }
        if !page.locked_actions().is_empty() {
            let actions = page.locked_actions().iter().copied().fold(
                div().flex().flex_wrap().gap_2(),
                |group, action| group.child(Button::new(action).label(action)
                    .disabled(true).tooltip("未获独立授权，当前不可操作")),
            );
            content = content.child(panel(cx)
                .child(section_title("受限操作"))
                .child(hint("发行、权限或业务门禁未完成；当前操作全部禁用。", cx))
                .child(actions));
        }
        div().size_full().flex().text_size(px(14.))
            .bg(cx.theme().background).text_color(cx.theme().foreground)
            .child(nav)
            .child(div().flex_1().min_w_0().h_full().flex().flex_col()
                .child(div().id("workspace-content").flex_1().min_h_0().min_w_0()
                    .overflow_y_scroll().bg(cx.theme().muted.opacity(0.3))
                    .child(div().w_full().min_w_0().flex().justify_center()
                        .p(if compact { px(12.) } else { px(24.) }).child(content)))
                .child(div().flex_shrink_0().flex().flex_wrap().items_center().justify_between()
                    .gap_2().px_3().py_2().border_t_1().border_color(cx.theme().border)
                    .child(hint("离线候选 · 仅本机只读 · 安装及联网未授权", cx))
                    .child(hint("v0.1.0", cx))))
    }
}

fn main() {
    gpui_kit::application()
        .with_assets(candidate_assets::CandidateAssets)
        .run(|cx| {
            gpui_kit::init(cx);
            cx.bind_keys([KeyBinding::new("ctrl-q", Quit, None)]);
            cx.on_action(|_: &Quit, cx: &mut App| cx.quit());
            let options = WindowOptions {
                window_bounds: Some(WindowBounds::centered(None, size(px(1200.), px(800.)), cx)),
                ..Default::default()
            };
            gpui_kit::open_window(options, cx, |window, cx| {
                window.set_window_title("Lanveriq Desktop - Offline Candidate");
                cx.new(|cx| Desktop::new(window, cx))
            })
            .expect("Failed to open offline candidate window");
        });
}
