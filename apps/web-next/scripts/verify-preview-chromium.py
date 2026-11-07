#!/usr/bin/env python3
"""NXT-14 automatic Chromium acceptance of the isolated Next.js B+C preview.

Runs against a production Next.js process on loopback with NO backend.
Does not accept credentials, load external websites, collect screenshots,
store browser traces or enable staged authentication/device APIs.
"""
import sys
from urllib.parse import urlsplit

BASE = "http://127.0.0.1:3100"
DETAIL = "/devices/00000000-0000-7000-8000-000000000001"
ROUTES = {
    "/overview": "设备运行概览",
    "/monitoring": "监控中心",
    "/devices": "终端资产",
    DETAIL: "设备详情（未连接）",
    "/enrollment": "设备注册",
    "/alerts": "告警中心",
    "/policies": "策略管理",
    "/reports": "报表分析",
    "/settings": "系统设置",
    "/design-system": "设计系统",
    "/login": "管理员登录",
}
WIDTHS = (320, 390, 768, 1024, 1440)


class PreviewGateFailure(Exception):
    pass


def require(ok, label):
    if not ok:
        raise PreviewGateFailure(label)


def allows_preview_request(url):
    """Strict origin and path, no localhost API, WSS or arbitrary network."""
    try:
        url_parts = urlsplit(url)
        return (url_parts.scheme == "http"
                and url_parts.hostname == "127.0.0.1"
                and url_parts.port == 3100
                and url_parts.username is None and url_parts.password is None
                and not url_parts.path.startswith(("/api/", "/actuator/"))
                and not url_parts.path.startswith("//"))
    except ValueError:
        return False


def gate(context):
    blocked = []

    def restrict(route):
        if allows_preview_request(route.request.url):
            route.continue_()
        else:
            # No user-entered data; don't copy full URLs/headers into logs.
            blocked.append("unexpected_browser_request")
            route.abort()

    context.route("**/*", restrict)
    return blocked


def check_browser(page, blocked):
    page.on("websocket", lambda _: blocked.append("unexpected_websocket"))
    for route, heading in ROUTES.items():
        response = page.goto(BASE + route, wait_until="networkidle", timeout=25000)
        require(response is not None and response.status == 200,
                "route_status_" + route.replace("/", "_"))
        require(page.get_by_role("heading", name=heading, level=1).count() == 1,
                "missing_page_heading_" + route.replace("/", "_"))
        if route == "/login":
            require(page.get_by_role("status").filter(
                has_text="禁止输入真实账号或密码").count() == 1,
                "missing_login_synthetic_warning")
            for selector in ("#login-username", "#login-password"):
                require(page.locator(selector).is_disabled(),
                        "credentials_must_remain_disabled")
            require(page.get_by_role("button", name="安全登录（未启用）").is_disabled(),
                    "login_must_remain_disabled")
            require(page.locator("form").count() == 0, "login_form_must_not_submit")
        else:
            require(page.get_by_role("status").filter(
                has_text="独立预览 · 不含真实数据").count() >= 1,
                "missing_unconnected_notice")
        if route in ("/alerts", "/policies", "/reports"):
            require(page.get_by_text("未连接 · 不展示假数据").count() == 1,
                    "planned_module_must_stay_disconnected")
            require(page.locator("main input:not([disabled])").count() == 0,
                    "planned_module_must_not_enable_business_inputs")
        if route == "/design-system":
            require(page.get_by_text("#6A5BFF").count() == 1,
                    "design_token_palette_missing")
        require(not blocked, "blocked_network_request")
    require(page.context.cookies() == [], "preview_must_not_set_session_cookie")

    page.goto(BASE + "/overview", wait_until="networkidle")
    # NXT-13: check actual rendered editorial overview, not just source CSS.
    # Decorative artwork must never masquerade as network/device data.
    require(page.locator(".overview-hero").count() == 1,
            "designed_overview_hero_missing")
    require(page.locator(".overview-hero h1").inner_text() == "设备运行概览",
            "designed_hero_must_keep_accessible_page_heading")
    require(page.locator(".overview-hero .hero-artwork[aria-hidden='true']").count() == 1,
            "decorative_artwork_must_be_hidden_from_screen_readers")
    require(page.locator(".bc-insight-grid > [data-slot='card']").count() == 3,
            "bc_dashboard_three_distinct_insight_panels")
    require(page.locator(".bc-empty-chart").get_attribute("aria-label") ==
            "暂无经过授权的设备活动趋势数据",
            "bc_activity_chart_must_not_invent_time_series")
    require(page.evaluate(
        "getComputedStyle(document.querySelector('.overview-hero')).color") ==
            "rgb(28, 26, 46)",
            "bc_light_hero_stylesheet_not_loaded")
    require(page.locator(".metric-card").count() == 4,
            "designed_overview_four_unknown_metrics")
    for metric in page.locator(".metric-card").all():
        require(metric.locator(".metric-placeholder").inner_text() == "—" and
                metric.locator(".metric-card-unconnected").inner_text() == "未连接",
                "visual_redesign_must_not_invent_device_metrics")
    require(page.locator(".skip-to-content[href='#main']").count() == 1,
            "missing_skip_to_main")
    page.keyboard.press("Tab")
    require(page.evaluate("document.activeElement?.classList.contains('skip-to-content')"),
            "keyboard_skip_link_not_first")
    page.get_by_role("navigation", name="主导航").get_by_role(
        "link", name="终端资产").click()
    # Next.js App Router changes location asynchronously after a client-side
    # Link click. Chromium may return from click before navigation commits;
    # use Playwright's built-in navigation wait, never a fixed sleep.
    try:
        page.wait_for_url(BASE + "/devices", wait_until="domcontentloaded",
                          timeout=15000)
    except Exception:
        raise PreviewGateFailure("client_navigation_failed") from None
    require(page.url == BASE + "/devices", "client_navigation_failed")
    require(page.get_by_role("navigation", name="主导航").get_by_role(
        "link", name="终端资产").get_attribute("aria-current") == "page",
        "navigation_aria_current_missing")
    require(page.get_by_text("设备数据尚未连接").count() >= 1,
            "device_preview_must_be_unconnected")
    require(page.get_by_role("textbox", name="搜索终端名称、系统或设备组").is_disabled(),
            "search_must_be_disabled_without_authorized_rows")

    page.goto(BASE + DETAIL, wait_until="networkidle")
    hardware = page.get_by_role("tab", name="硬件")
    require(hardware.get_attribute("aria-selected") == "true",
            "initial_hardware_tab")
    hardware.focus()
    page.keyboard.press("ArrowRight")
    network = page.get_by_role("tab", name="网络")
    require(network.get_attribute("aria-selected") == "true",
            "keyboard_tab_navigation")
    require(network.evaluate("(el) => document.activeElement === el"),
            "keyboard_tab_focus")
    require(page.get_by_role("tabpanel").get_by_text("网络信息受限").count() == 1,
            "network_tab_confidentiality_message")
    require(page.get_by_role("button", name="撤销证书").is_disabled(),
            "revoke_action_must_be_disabled")
    require(page.get_by_role("button", name="申请刷新资产").is_disabled(),
            "refresh_action_must_be_disabled")
    require(not blocked, "blocked_network_request")
    require(page.context.cookies() == [], "unexpected_browser_cookie")


def check_viewports(browser):
    for width in WIDTHS:
        context = browser.new_context(
            viewport={"width": width, "height": 840},
            reduced_motion="reduce", service_workers="block",
            accept_downloads=False,
        )
        try:
            blocked = gate(context)
            page = context.new_page()
            page.on("websocket", lambda _: blocked.append("unexpected_websocket"))
            for route in ("/overview", "/devices", "/login"):
                response = page.goto(BASE + route, wait_until="networkidle", timeout=25000)
                require(response is not None and response.status == 200,
                        "viewport_route_http_failure")
                require(page.evaluate(
                    "document.documentElement.scrollWidth <= window.innerWidth + 2"),
                    "horizontal_page_overflow_at_" + str(width))
                if route == "/overview":
                    hero = page.locator(".overview-hero")
                    require(hero.is_visible() and
                            hero.bounding_box()["width"] <= width + 1,
                            "designed_hero_exceeds_viewport_at_" + str(width))
                    require(page.locator(".overview-hero h1").is_visible(),
                            "designed_heading_hidden_at_" + str(width))
                require(page.evaluate(
                    "matchMedia('(prefers-reduced-motion: reduce)').matches"),
                    "reduced_motion_not_applied")
            if width <= 750:
                page.goto(BASE + "/overview", wait_until="networkidle")
                menu = page.get_by_role("button", name="打开导航")
                require(menu.is_visible() and menu.get_attribute("aria-expanded") == "false",
                        "bc_mobile_menu_missing_or_open_by_default")
                menu.click()
                require(menu.get_attribute("aria-expanded") == "true",
                        "bc_mobile_menu_failed_to_open")
                alert_link = page.get_by_role("navigation", name="主导航").locator(
                    'a[href="/alerts"]')
                require(alert_link.is_visible(), "bc_mobile_planned_navigation_missing")
                alert_link.click()
                page.wait_for_url(BASE + "/alerts", wait_until="domcontentloaded",
                                  timeout=15000)
                require(page.get_by_role("heading", name="告警中心", level=1).count() == 1,
                        "bc_mobile_client_navigation_failed")
                require(not page.locator(".sidebar").evaluate(
                    "(el) => el.classList.contains('mobile-open')"),
                    "bc_mobile_drawer_stayed_open_after_navigation")
                require(page.evaluate(
                    "document.documentElement.scrollWidth <= window.innerWidth + 2"),
                    "bc_mobile_drawer_causes_horizontal_overflow")
            require(not blocked, "viewport_unexpected_network")
            require(context.cookies() == [], "viewport_unexpected_cookie")
        finally:
            context.close()


def main():
    require(len(sys.argv) == 1, "unexpected_args")
    from playwright.sync_api import sync_playwright

    with sync_playwright() as driver:
        browser = driver.chromium.launch(headless=True, args=["--no-proxy-server"])
        try:
            context = browser.new_context(service_workers="block", accept_downloads=False)
            try:
                blocked = gate(context)
                check_browser(context.new_page(), blocked)
            finally:
                context.close()
            check_viewports(browser)
            print("NXT-14 CHROMIUM PASS: 11 real Next.js routes, no API/WSS/external "
                  "network or cookie, disabled login/operations, keyboard tabs/mobile drawer/focus, "
                  "320-1440px and reduced motion.")
            print("NOT PROVEN: screen reader/manual visual parity, Caddy HTTPS, Spring "
                  "login/CSRF/RBAC, PostgreSQL browser sessions or real device operations.")
        finally:
            browser.close()


if __name__ == "__main__":
    try:
        main()
    except PreviewGateFailure as exc:
        print("FAIL: NXT-02 isolated Chromium gate: " + str(exc), file=sys.stderr)
        raise SystemExit(1) from None
    except Exception:
        print("FAIL: NXT-02 isolated Chromium could not complete browser checks",
              file=sys.stderr)
        raise SystemExit(1) from None
