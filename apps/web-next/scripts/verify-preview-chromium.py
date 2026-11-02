#!/usr/bin/env python3
"""NXT-02 manual-only Chromium acceptance of the isolated Next.js preview.

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
    "/settings": "系统设置",
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
        require(not blocked, "blocked_network_request")
    require(page.context.cookies() == [], "preview_must_not_set_session_cookie")

    page.goto(BASE + "/overview", wait_until="networkidle")
    require(page.locator(".skip-to-content[href='#main']").count() == 1,
            "missing_skip_to_main")
    page.keyboard.press("Tab")
    require(page.evaluate("document.activeElement?.classList.contains('skip-to-content')"),
            "keyboard_skip_link_not_first")
    page.get_by_role("navigation", name="主导航").get_by_role(
        "link", name="终端资产").click()
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
                require(page.evaluate(
                    "matchMedia('(prefers-reduced-motion: reduce)').matches"),
                    "reduced_motion_not_applied")
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
            print("MANUAL NXT-02 CHROMIUM PASS: 7 real Next.js routes, no API/WSS/external "
                  "network or cookie, disabled login/operations, keyboard tab/focus, "
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
