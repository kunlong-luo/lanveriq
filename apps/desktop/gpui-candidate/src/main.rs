//! Disposable offline GPUI compatibility candidate. No privileged or runtime interfaces.
use gpui_kit::component::{
    ActiveTheme, Disableable, Selectable,
    button::Button,
    checkbox::Checkbox,
    label::Label,
};
use gpui_kit::base::{SelectableText, StyledExt};
use gpui_kit::*;
use lanveriq_desktop_gpui_candidate::{IdleInterval, LOCKED_ACTIONS, Page, Workspace};

use lanveriq_desktop_gpui_candidate::review::{review_gates, Notice, diagnostic};
use lanveriq_desktop_gpui_candidate::host_identity::inspect_host_identity;

struct Desktop {
    workspace: Workspace,
}

impl Render for Desktop {
    fn render(&mut self, _: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let page = self.workspace.page();
        let nav = Page::ALL.into_iter().fold(
            div().id("workspace-nav").flex().flex_col().gap_2().w(px(200.)).flex_shrink_0().overflow_y_scroll()
                .child(Label::new("Lanveriq Desktop")),
            |nav, destination| {
                nav.child(
                    Button::new(destination.id())
                        .label(destination.label())
                        .selected(page == destination)
                        .on_click(cx.listener(move |this, _, _, cx| {
                            this.workspace.navigate(destination);
                            cx.notify();
                        })),
                )
            },
        );
        let locked = LOCKED_ACTIONS.into_iter().fold(
            div().flex().flex_col().gap_2(),
            |group, action| group.child(Button::new(action).label(action).disabled(true)),
        );
        let mut content = div().id("workspace-content").flex_1().min_w_0().overflow_y_scroll()
            .flex().flex_col().gap_4().p_4()
            .rounded(cx.theme().radius).border_1().border_color(cx.theme().border)
            .child(div().text_xl().font_bold().child(Label::new(page.label())))
            .child(Label::new(page.description()))
            .child(Label::new("离线候选 · 仅系统身份可核验 · 不可安装"));
        if matches!(page, Page::Overview | Page::Setup | Page::Diagnostics) {
            content = content
                .child(Label::new("本机系统身份（仅元数据）"))
                .child(Label::new(self.workspace.host_identity().label()))
                .child(Button::new("inspect-host-identity").label("手动检测系统与架构（只读）")
                    .on_click(cx.listener(|this, _, _, cx| {
                        this.workspace.replace_host_identity(inspect_host_identity());
                        cx.notify();
                    })))
                .child(Label::new("Agent 包、systemd、COSMIC 兼容性仍未核验。"));
        }
        if page == Page::Configuration {
            let current = self.workspace.idle_interval();
            let choices = IdleInterval::ALL.into_iter().fold(
                div().flex().flex_col().gap_2(),
                |group, interval| group.child(
                    Button::new(interval.id())
                        .label(interval.label())
                        .selected(current == interval)
                        .on_click(cx.listener(move |this, _, _, cx| {
                            this.workspace.select_idle_interval(interval);
                            cx.notify();
                        })),
                ),
            );
            content = content.child(
                div().flex().flex_col().gap_3().p_3()
                    .rounded(cx.theme().radius).border_1().border_color(cx.theme().border)
                    .child(Label::new("Agent 空闲运行周期（不授权启动）"))
                    .child(choices)
                    .child(Label::new("只读 TOML 草稿 · 可选择文本"))
                    .child(div().p_3().border_1().border_color(cx.theme().border)
                        .child(SelectableText::new("idle-config-preview", current.preview())))
                    .child(Label::new("仅内存预览，不写入文件，不修改服务，离开此页即重置。")),
            );
        }
        if page == Page::Setup {
            let gates = review_gates(self.workspace.host_identity()).iter().fold(div().flex().flex_col().gap_3(), |group, gate| {
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
                .child(Label::new("安装操作已锁定 · 独立安全审核"))
                .child(gates)
                .child(Label::new("请分别阅读权限与安装限制（非授权）"))
                .child(notices)
                .child(Label::new(if review.all_read() {
                    "全部说明已阅；发行、Polkit、L3 门禁仍未批准，无法安装或启动 Agent。"
                } else {
                    "阅读说明不会启动 Agent，也不会向系统索取管理员权限。"
                }));
        }
        if page == Page::Diagnostics {
            content = content
                .child(Label::new("本机脱敏诊断 · 仅系统身份元数据"))
                .child(Label::new("只读文本，未签名，非安装授权；不保存、不联网、不上传。"))
                .child(div().p_3().border_1().border_color(cx.theme().border)
                    .child(SelectableText::new("offline-diagnostic", diagnostic(self.workspace.host_identity()))))
                .child(Label::new("向第三方分享前请自行确认内容。"));
        }
        div()
            .size_full()
            .flex()
            .gap_4()
            .p_4()
            .bg(cx.theme().background)
            .text_color(cx.theme().foreground)
            .child(nav)
            .child(content.child(locked))
    }
}

fn main() {
    gpui_kit::application()
        .with_assets(gpui_kit::assets::Assets)
        .run(|cx| {
            gpui_kit::init(cx);
            let options = WindowOptions {
                window_bounds: Some(WindowBounds::centered(None, size(px(900.), px(620.)), cx)),
                ..Default::default()
            };
            gpui_kit::open_window(options, cx, |_, cx| {
                cx.new(|_| Desktop { workspace: Workspace::default() })
            })
            .expect("Failed to open offline candidate window");
        });
}
