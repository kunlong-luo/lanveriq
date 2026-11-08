#!/usr/bin/env python3
"""NXT-17 isolated real Chromium acceptance for CI-ONLY synthetic asset rows.

Only the temporary /synthetic-directory-probe route is tested, exclusively
inside an ephemeral GitHub runner. The route is never committed or deployed.
Every network request must target the isolated Next.js loopback listener.
"""
from urllib.parse import urlsplit

BASE = "http://127.0.0.1:3100"
ROUTE = BASE + "/synthetic-directory-probe"


def require(condition, label):
    if not condition:
        raise AssertionError(label)


def allowed(url):
    p = urlsplit(url)
    return (p.scheme == "http" and p.hostname == "127.0.0.1"
            and p.port == 3100 and p.username is None and p.password is None
            and not p.path.startswith(("/api/", "/actuator/", "//")))


def verify(browser, width):
    context = browser.new_context(
        viewport={"width": width, "height": 840},
        service_workers="block", accept_downloads=False,
        reduced_motion="reduce",
    )
    blocked = []
    context.route("**/*", lambda route: route.continue_() if
                  allowed(route.request.url) else
                  (blocked.append("outside isolated Next.js origin"), route.abort()))
    try:
        page = context.new_page()
        page.on("websocket", lambda _: blocked.append("websocket"))
        response = page.goto(ROUTE, wait_until="networkidle", timeout=25000)
        require(response is not None and response.status == 200,
                "synthetic_fixture_route_not_available")
        require(page.get_by_role("heading", name="CI合成设备目录交互验收").count() == 1,
                "explicit_synthetic_ci_label_missing")
        search = page.get_by_role("textbox", name="搜索终端名称、系统或设备组")
        group = page.get_by_role("combobox", name="筛选设备组")
        sorting = page.get_by_role("combobox", name="排序当前授权页")
        require(search.is_enabled() and group.is_enabled() and sorting.is_enabled(),
                "authorized_fixture_must_enable_local_controls")
        require(page.locator(".device-table tbody tr").count() == 2,
                "initial_authorized_test_rows_missing")
        require(page.get_by_text("仅本次授权页：匹配 2 / 2 条").count() == 1,
                "initial_authorized_page_count_missing")
        table_region = page.get_by_role("region", name="设备目录表格（可横向滚动）")
        require(table_region.count() == 1 and
                table_region.get_attribute("tabindex") == "0",
                "accessible_keyboard_scroller_missing")
        require(page.locator(".device-table tbody th[scope='row']").count() == 2,
                "accessible_device_row_headers_missing")
        sorting.select_option("label-desc")
        require(page.locator(".device-table tbody th[scope='row'] strong").first.inner_text() ==
                "CI合成设备乙", "page_sort_desc_not_applied")
        sorting.select_option("server")
        require(page.locator(".device-table tbody th[scope='row'] strong").first.inner_text() ==
                "CI合成设备甲", "page_sort_did_not_restore_server_order")
        if width <= 390:
            for control in (search, group, sorting):
                require(control.bounding_box()["height"] >= 44,
                        "nxt19_mobile_asset_control_hitbox_below_44px")
            table_region.focus()
            require(table_region.evaluate(
                "(el) => document.activeElement === el"),
                "keyboard_table_region_not_focusable")
            page.keyboard.press("ArrowRight")
            page.wait_for_function(
                "() => document.querySelector('[data-slot=table-container]').scrollLeft > 0",
                timeout=5000)

        # Actual React keyboard/input updates rather than SSR-only snapshots.
        search.fill("合成设备甲")
        require(page.locator(".device-table tbody tr").count() == 1,
                "authorized_search_did_not_filter")
        require(page.get_by_text("CI合成设备乙").count() == 0,
                "search_exposed_nonmatching_test_row")
        search.fill("永远不存在的设备")
        require(page.get_by_text("没有匹配的设备").is_visible(),
                "empty_search_state_missing")
        clear = page.get_by_role("button", name="清除设备搜索和设备组筛选")
        clear.click()
        require(search.input_value() == "" and
                search.evaluate("(el) => document.activeElement === el"),
                "clear_filters_must_reset_and_restore_focus")
        require(page.locator(".device-table tbody tr").count() == 2,
                "clear_filters_must_restore_authorized_page")

        group.select_option("CI测试组甲")
        require(page.locator(".device-table tbody tr").count() == 1,
                "local_group_filter_did_not_filter")
        require(page.get_by_text("CI合成设备甲").is_visible(),
                "group_filter_wrong_row")
        page.get_by_role("button", name="切换合成授权页乙").click()
        page.wait_for_function("""() => {
          const el = document.querySelector('[data-slot="native-select"]');
          return el && el.value === '';
        }""", timeout=5000)
        require(page.get_by_text("CI合成设备丙").is_visible() and
                page.locator(".device-table tbody tr").count() == 1,
                "stale_group_must_not_hide_new_authorized_page")
        require(page.get_by_text("CI合成设备甲").count() == 0,
                "replaced_page_leaked_previous_authorized_row")

        page.get_by_role("button", name="模拟权限撤销").click()
        require(page.get_by_text("当前账号无权查看设备").is_visible(),
                "permission_denial_state_missing")
        require(page.locator("tbody tr").count() == 0,
                "permission_denial_leaked_authorized_rows")
        require(search.is_disabled() and group.is_disabled() and sorting.is_disabled(),
                "permission_denial_kept_inputs_active")
        require(table_region.count() == 0,
                "permission_denial_must_remove_scrollable_device_rows")
        require(page.get_by_text("CI合成设备丙").count() == 0,
                "permission_denial_retained_sensitive_test_row")

        page.get_by_role("button", name="恢复合成授权页甲").click()
        require(search.is_enabled() and group.is_enabled() and sorting.is_enabled() and
                search.input_value() == "" and group.input_value() == "" and
                sorting.input_value() == "server",
                "reauthorize_must_reset_previous_local_filters")
        require(page.locator(".device-table tbody tr").count() == 2,
                "new_authorized_page_not_rendered")
        require(page.evaluate(
            "document.documentElement.scrollWidth <= window.innerWidth + 2"),
            "synthetic_mobile_directory_causes_document_overflow")
        require(context.cookies() == [], "synthetic_directory_must_not_set_cookies")
        require(not blocked, "synthetic_directory_must_not_call_external_network")
    finally:
        context.close()


def main():
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            for width in (390, 1440):
                verify(browser, width)
        finally:
            browser.close()
    print("NXT-19 SYNTHETIC DIRECTORY CHROMIUM PASS: authorized-page sort/search/group,"
          " row headers/keyboard scroll, clear/focus, new-page normalization,"
          " revoke/reset, mobile width, no network/cookies.")


if __name__ == "__main__":
    main()
