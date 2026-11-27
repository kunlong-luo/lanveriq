//! Disposable offline GPUI compatibility candidate. No privileged or runtime interfaces.
use gpui_kit::component::{
    ActiveTheme, Disableable, Selectable,
    button::Button,
    label::Label,
};
use gpui_kit::base::{SelectableText, StyledExt};
use gpui_kit::*;
use lanveriq_desktop_gpui_candidate::{IdleInterval, LOCKED_ACTIONS, Page, Workspace};

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
            .child(Label::new("离线候选 · 无真实主机证据 · 不可安装"));
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
