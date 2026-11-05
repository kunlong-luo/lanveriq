#!/usr/bin/env python3
"""MANUAL ONLY: actual Chromium network stack against disposable Caddy/Tomcat/PG18.

Invoked AFTER the curl-based Console acceptance, with a throwaway synthetic
administrator and independently trusted disposable Caddy root CA. No Next.js
runtime, device/Agent collection, screenshot, trace, password log or fake API.
The generated password arrives exclusively over inherited FD 3.
"""
import os
import re
import sys
import subprocess
from pathlib import Path
from urllib.parse import urlparse, urlsplit

CONSOLE = "https://console.lanveriq.internal"
PROBE = CONSOLE + "/__synthetic_browser_probe"
COOKIE = "__Host-lanveriq-session"
# Only intercept URLs OUTSIDE the exact disposable Console HTTPS origin.
# Routing even an allowed request can hide Chromium network-stack-owned
# Sec-Fetch-* headers from Playwright request.all_headers(). The allowed
# requests must reach the network stack without Playwright interception.
DISALLOWED_NETWORK_URL = re.compile(
    r"^(?!https://console\.lanveriq\.internal(?::443)?(?:/(?!/)|$)[^#]*$).+"
)
FETCH_SCRIPT = r"""async ({path, method, form, csrfToken}) => {
  const headers = {};
  if (form !== null) headers['Content-Type'] = 'application/x-www-form-urlencoded';
  if (csrfToken !== null) headers['X-CSRF-TOKEN'] = csrfToken;
  const response = await fetch(path, {
    method, headers, body: form === null ? undefined : new URLSearchParams(form),
    credentials: 'same-origin', mode: 'same-origin', cache: 'no-store',
    redirect: 'error', referrerPolicy: 'no-referrer'
  });
  const contentType = response.headers.get('content-type') || '';
  let json = null;
  if (contentType.toLowerCase().includes('application/json')) {
    try { json = await response.json(); } catch { /* malformed is rejection */ }
  }
  return { status: response.status,
           cacheControl: response.headers.get('cache-control') || '',
           contentType, json };
}"""


class BrowserGateFailure(Exception):
    pass


def require(condition, label):
    if not condition:
        raise BrowserGateFailure(label)


def strict_json(result, expected_status, label):
    require(result["status"] == expected_status, label + "_status")
    require("application/json" in result["contentType"].lower(), label + "_content_type")
    # Require an actual Cache-Control directive, not an arbitrary substring.
    require(any(directive.strip().lower() == "no-store"
                for directive in result["cacheControl"].split(",")),
            label + "_cache_control")
    require(isinstance(result["json"], dict), label + "_json")
    return result["json"]


def strict_anonymous_401(result, label):
    """Require a direct uncached anonymous 401, never an HTML redirect/error."""
    require(result["status"] == 401, label + "_status")
    directives = result["cacheControl"].lower().split(",")
    require(any(value.strip() == "no-store" for value in directives),
            label + "_cache_control")
    require("text/html" not in result["contentType"].lower(),
            label + "_no_html_error")
    require(result["json"] is None, label + "_no_identity_json")


def require_csrf(value, label):
    require(isinstance(value, str) and 8 <= len(value) <= 4096, label)


def is_synthetic_console_url(url):
    """Allow the isolated Console HTTPS origin only; never browse external apps.

    The test is deliberately more restrictive than the real Caddy config.
    It accepts only the fixed synthetic host and default HTTPS port,
    including the in-browser auth/CSRF requests and throwaway probe HTML.
    """
    try:
        parts = urlsplit(url)
        return (parts.scheme == "https"
                and parts.hostname == "console.lanveriq.internal"
                and parts.port in (None, 443)
                and parts.username is None and parts.password is None
                and not parts.path.startswith("//")
                and not parts.fragment)
    except ValueError:
        return False


def require_browser_post_metadata(observed_posts):
    """Fail closed with a fixed non-secret diagnostic for each POST boundary.

    The observed booleans are computed from actual Chromium request headers.
    Do not print raw request headers, URLs, payloads or session identifiers.
    Differentiating count vs Origin vs Fetch-Site is crucial to fixing the
    real Chromium failure without weakening the server/browser policy.
    """
    require(len(observed_posts) == 4,
            "real_browser_origin_fetch_metadata_post_count")
    for index, (valid_origin, valid_fetch_site) in enumerate(observed_posts, 1):
        require(valid_origin is True,
                "real_browser_origin_fetch_metadata_origin_post_" + str(index))
        require(valid_fetch_site is True,
                "real_browser_origin_fetch_metadata_fetch_site_post_" + str(index))


def browser_post_metadata(request):
    """Inspect real Chromium-generated Origin/Fetch-Site without logging data."""
    headers = request.all_headers()
    return (headers.get("origin") == CONSOLE,
            headers.get("sec-fetch-site") == "same-origin")


class ChromiumWirePostMetadata:
    """Capture only fixed booleans from Chromium's final wire-header events.

    Playwright routes can hide network-stack Sec-Fetch-* in Request.all_headers().
    CDP Network.requestWillBeSentExtraInfo reports final outgoing headers.
    Request identifiers let us match these to actual login/logout POSTs even
    when ExtraInfo arrives before its corresponding request event. Never keep
    cookies, credentials, token values, raw headers, or request body data.
    """

    def __init__(self):
        self.post_ids = []
        self.metadata_by_id = {}

    def on_request(self, event):
        request = event.get("request") or {}
        if (request.get("method") == "POST"
                and request.get("url") in (
                    CONSOLE + "/api/v1/auth/login",
                    CONSOLE + "/api/v1/auth/logout",
                )):
            self.post_ids.append(event.get("requestId"))

    def on_extra_info(self, event):
        request_id = event.get("requestId")
        if not isinstance(request_id, str):
            return
        headers = event.get("headers") or {}
        origin_values = []
        fetch_site_values = []
        for key, value in headers.items():
            name = key.lower()
            if name == "origin":
                origin_values.append(value)
            elif name == "sec-fetch-site":
                fetch_site_values.append(value)
        self.metadata_by_id[request_id] = (
            origin_values == [CONSOLE],
            fetch_site_values == ["same-origin"],
        )

    def checked_posts(self, expected_count=4):
        require(len(self.post_ids) == expected_count,
                "real_browser_origin_fetch_metadata_cdp_post_count")
        checked = []
        for index, request_id in enumerate(self.post_ids, 1):
            require(request_id in self.metadata_by_id,
                    "real_browser_origin_fetch_metadata_wire_headers_post_" + str(index))
            checked.append(self.metadata_by_id[request_id])
        return checked


def verify_nextjs_same_origin(context):
    """Read-only genuine Chromium probe of Caddy-hosted, unconnected Next.js.

    No credentials are entered, and the Next.js app must issue zero API
    requests itself. The separate synthetic browser security probe owns all
    explicit authentication HTTP requests after these inert page checks.
    """
    page = context.new_page()
    attempted_api = []
    attempted_websocket = []
    def inspect(request):
        if urlsplit(request.url).path.startswith(("/api/", "/actuator/")):
            attempted_api.append("unapproved_frontend_api")
    page.on("request", inspect)
    page.on("websocket", lambda _: attempted_websocket.append("unexpected_nextjs_websocket"))
    try:
        for path, heading in (
            ("/overview", "设备运行概览"),
            ("/devices", "终端资产"),
            ("/monitoring", "监控中心"),
            ("/login", "管理员登录"),
        ):
            response = page.goto(CONSOLE + path, wait_until="networkidle",
                                 timeout=20000)
            require(response is not None and response.status == 200,
                    "same_origin_nextjs_status")
            require(page.get_by_role("heading", name=heading, level=1).count() == 1,
                    "same_origin_nextjs_heading")
        require(page.locator("#login-username").is_disabled(),
                "same_origin_login_username_must_remain_disabled")
        require(page.locator("#login-password").is_disabled(),
                "same_origin_login_password_must_remain_disabled")
        require(page.get_by_role("button", name="安全登录（未启用）").is_disabled(),
                "same_origin_login_submit_must_remain_disabled")
        require(page.locator("form").count() == 0,
                "same_origin_nextjs_must_not_submit_credentials")
        require(not attempted_api, "same_origin_nextjs_must_not_request_business_api")
        require(not attempted_websocket, "same_origin_nextjs_must_not_open_websocket")
        require(context.cookies([CONSOLE]) == [],
                "same_origin_nextjs_must_not_create_session")
        # Next App Router needs its client runtime hydrated before Link
        # interaction; SSR DOMContentLoaded by itself is not sufficient.
        page.goto(CONSOLE + "/overview", wait_until="networkidle", timeout=20000)
        page.get_by_role("navigation", name="主导航").get_by_role(
            "link", name="终端资产").click()
        page.wait_for_url(CONSOLE + "/devices", wait_until="domcontentloaded",
                          timeout=15000)
        require(page.url == CONSOLE + "/devices",
                "same_origin_nextjs_client_navigation")
        require(not attempted_api, "same_origin_nextjs_navigation_must_not_request_api")
        require(not attempted_websocket, "same_origin_nextjs_navigation_must_not_open_websocket")
    finally:
        page.close()


def verify_nextjs_synthetic_auth_ui(context, password):
    """Use a real Next.js CLIENT page to exercise staged login/empty reads.

    This page exists ONLY in a manually opted-in disposable Next.js build.
    It cannot appear in the ordinary login preview or production artifact.
    Credentials come only from the isolated runner's inherited FD3 and are
    never logged. This does NOT test a nonempty two-admin browser fixture.
    """
    page = context.new_page()
    wire = ChromiumWirePostMetadata()
    cdp = context.new_cdp_session(page)
    cdp.on("Network.requestWillBeSent", wire.on_request)
    cdp.on("Network.requestWillBeSentExtraInfo", wire.on_extra_info)
    cdp.send("Network.enable")
    websocket_attempts = []
    page.on("websocket", lambda _: websocket_attempts.append("unexpected_synthetic_websocket"))
    try:
        response = page.goto(CONSOLE + "/synthetic-auth-probe",
                             wait_until="networkidle", timeout=25000)
        require(response is not None and response.status == 200,
                "synthetic_nextjs_auth_page_status")
        require(page.get_by_role("heading", name="合成登录浏览器联调").count() == 1,
                "synthetic_nextjs_auth_page_heading")
        require(page.locator("#synthetic-auth-state").get_attribute("data-state") == "idle",
                "synthetic_nextjs_auth_initial_state")
        page.locator("#synthetic-auth-user").fill("synthetic-owner")
        page.locator("#synthetic-auth-password").fill(password)
        page.get_by_role("button", name="合成登录并读取").click()
        page.locator("#synthetic-auth-state[data-state='authenticated-empty']").wait_for(
            state="visible", timeout=25000)
        require(page.locator("#synthetic-auth-password").input_value() == "",
                "synthetic_nextjs_auth_password_not_cleared")
        require(page.locator("#synthetic-auth-user").input_value() == "",
                "synthetic_nextjs_auth_user_not_cleared")
        page.get_by_role("button", name="合成登出并复核").click()
        page.locator("#synthetic-auth-state[data-state='signed-out-confirmed']").wait_for(
            state="visible", timeout=25000)
        page.wait_for_load_state("networkidle")
        # Two additional form POSTs belong to the mounted synthetic client.
        # As with the baseline browser gate, use final CDP wire headers.
        for index, (origin_ok, fetch_site_ok) in enumerate(
                wire.checked_posts(expected_count=2), 1):
            require(origin_ok, "synthetic_nextjs_auth_wire_origin_" + str(index))
            require(fetch_site_ok, "synthetic_nextjs_auth_wire_fetch_site_" + str(index))
        require(not websocket_attempts, "synthetic_nextjs_auth_no_websocket")
    finally:
        page.close()


def private_synthetic_pg_sql(db_container, sql):
    """Execute fixed test SQL in the isolated PG18 container; NEVER log SQL,
    hashes, database output or process diagnostics. No external DB URI allowed.
    """
    require(re.fullmatch(r"[a-f0-9]{12,64}", db_container) is not None,
            "multi_admin_disposable_pg_container_required")
    completed = subprocess.run(
        ["docker", "exec", "-i", db_container, "psql",
         "-X", "-q", "-v", "ON_ERROR_STOP=1", "-U", "lanveriq", "-d", "lanveriq"],
        input=sql, text=True, capture_output=True, timeout=20, check=False,
    )
    require(completed.returncode == 0, "multi_admin_synthetic_pg_mutation_failed")


def verify_nextjs_synthetic_multi_admin(browser, password, db_container):
    """Independent real Chromium contexts + real disposable PG18/JPA/Servlet.

    First owner/empty-read browser gate must already have passed. All data
    seeded here belongs exclusively to the disposable Testcontainers-style
    manually launched PG18 database. No production account provisioning.
    """
    sql_path = Path(__file__).with_name("seed-synthetic-multi-admin.sql")
    require(sql_path.is_file(), "multi_admin_fixture_sql_missing")
    private_synthetic_pg_sql(db_container, sql_path.read_text(encoding="utf-8"))
    contexts = []
    blocked = []
    stage = "context_setup"
    try:
        pages = []
        wires = []
        for _ in range(2):
            context = browser.new_context(
                ignore_https_errors=False,
                accept_downloads=False, service_workers="block",
            )
            contexts.append(context)
            def restrict(route):
                blocked.append("multi_admin_forbidden_external_origin")
                route.abort()
            context.route(DISALLOWED_NETWORK_URL, restrict)
            page = context.new_page()
            page.on("websocket", lambda _: blocked.append("multi_admin_forbidden_websocket"))
            wire = ChromiumWirePostMetadata()
            cdp = context.new_cdp_session(page)
            cdp.on("Network.requestWillBeSent", wire.on_request)
            cdp.on("Network.requestWillBeSentExtraInfo", wire.on_extra_info)
            cdp.send("Network.enable")
            response = page.goto(CONSOLE + "/synthetic-multi-admin-probe",
                                 wait_until="networkidle", timeout=25000)
            require(response is not None and response.status == 200,
                    "multi_admin_nextjs_page_status")
            require(page.get_by_role("heading", name="双管理员合成隔离测试").count() == 1,
                    "multi_admin_nextjs_heading")
            require(page.locator("#multi-admin-state").get_attribute("data-state") == "idle",
                    "multi_admin_no_implicit_auth")
            pages.append(page)
            wires.append(wire)

        def login(page, name, expected_labels, total):
            page.locator("#multi-admin-user").fill(name)
            page.locator("#multi-admin-secret").fill(password)
            page.get_by_role("button", name="测试登录并读取").click()
            try:
                page.locator("#multi-admin-state[data-state='ready']").wait_for(
                    state="visible", timeout=25000)
            except Exception:
                # Only a small fixed UI-state enum is published; never
                # Playwright's exception (may contain request/password data).
                observed = "missing"
                try:
                    candidate = page.locator("#multi-admin-state").get_attribute(
                        "data-state", timeout=1000)
                    if candidate in ("idle", "working", "ready",
                                     "signed-out", "scope-empty", "revoked", "denied"):
                        observed = candidate
                except Exception:
                    pass
                label = "synthetic_a" if name == "synthetic-browser-a" else "synthetic_b"
                raise BrowserGateFailure(
                    "multi_admin_" + label + "_login_state_" + observed) from None
            require(page.locator("#multi-admin-state").get_attribute("data-principal") == name,
                    "multi_admin_principal")
            require(page.locator("#multi-admin-state").get_attribute("data-cache-count") == "1",
                    "multi_admin_exactly_one_scoped_cache")
            require(page.locator("#multi-admin-overview").get_attribute("data-total") == str(total),
                    "multi_admin_overview_total")
            actual_labels = page.locator("#multi-admin-device-list li").all_text_contents()
            # PostgreSQL orders by UUIDv7, not by display label. Verify exact
            # membership and uniqueness regardless of allocation order.
            require(len(actual_labels) == len(expected_labels) and
                    len(set(actual_labels)) == len(actual_labels) and
                    sorted(actual_labels) == sorted(expected_labels),
                    "multi_admin_scope_rows")
            require(page.locator("#multi-admin-secret").input_value() == "",
                    "multi_admin_password_field_cleared")

        page_a, page_b = pages
        stage = "login_a"
        login(page_a, "synthetic-browser-a",
              ["SyntheticBrowserA-1", "SyntheticBrowserA-2"], 2)
        stage = "login_b"
        login(page_b, "synthetic-browser-b", ["SyntheticBrowserB-1"], 1)
        first_cookie = session_cookie(contexts[0])
        second_cookie = session_cookie(contexts[1])
        require(first_cookie != second_cookie, "multi_admin_isolated_session_cookies")
        first_device = page_a.locator("#multi-admin-device-list li").first.get_attribute(
            "data-device-id")
        require(isinstance(first_device, str) and
                re.fullmatch(r"[0-9a-f-]{36}", first_device) is not None,
                "multi_admin_device_id")
        stage = "cross_group_404"
        page_b.locator("#multi-other-device").fill(first_device)
        page_b.get_by_role("button", name="检查跨组详情").click()
        page_b.locator("#multi-admin-detail[data-state='not_found']").wait_for(
            state="visible", timeout=12000)

        # Switching A -> B in the SAME browser context must erase A's TanStack
        # Query rows before B can see its own scope. Context B remains logged
        # in independently with a different session cookie.
        stage = "same_context_logout"
        page_a.get_by_role("button", name="测试登出").click()
        page_a.locator("#multi-admin-state[data-state='signed-out']").wait_for(
            state="visible", timeout=18000)
        require(page_a.locator("#multi-admin-device-list li").count() == 0,
                "multi_admin_logout_rows_not_cleared")
        require(page_a.locator("#multi-admin-state").get_attribute("data-cache-count") == "0",
                "multi_admin_logout_cache_not_cleared")
        stage = "same_context_login_b"
        login(page_a, "synthetic-browser-b", ["SyntheticBrowserB-1"], 1)
        require(session_cookie(contexts[0]) != second_cookie,
                "multi_admin_new_cookie_must_not_equal_other_context")

        # NXT-06-A product-shaped Console candidate: this is the ACTUAL
        # read-only device/overview presentation proposed for future runtime,
        # not another bare diagnostic page. It is mounted only in the
        # ephemeral fourth-gated CI build; normal /login stays disabled.
        stage = "candidate_console_setup"
        candidate_context = browser.new_context(
            ignore_https_errors=False, accept_downloads=False,
            service_workers="block")
        contexts.append(candidate_context)
        candidate_context.route(DISALLOWED_NETWORK_URL, restrict)
        candidate_page = candidate_context.new_page()
        candidate_page.on("websocket", lambda _: blocked.append(
            "candidate_console_forbidden_websocket"))
        candidate_wire = ChromiumWirePostMetadata()
        candidate_cdp = candidate_context.new_cdp_session(candidate_page)
        candidate_cdp.on("Network.requestWillBeSent", candidate_wire.on_request)
        candidate_cdp.on("Network.requestWillBeSentExtraInfo", candidate_wire.on_extra_info)
        candidate_cdp.send("Network.enable")
        candidate_response = candidate_page.goto(
            CONSOLE + "/synthetic-console-candidate",
            wait_until="networkidle", timeout=25000)
        require(candidate_response is not None and candidate_response.status == 200,
                "candidate_console_https_status")
        require(candidate_page.get_by_role(
            "heading", name="只读管理控制台候选").count() == 1,
                "candidate_console_ui_title")
        require(candidate_page.locator("main[data-candidate-status='signed_out']").count() == 1,
                "candidate_console_no_implicit_session_or_api")
        require(candidate_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_no_automatic_device_rendering")

        def candidate_login(login_name, expected_labels, expected_total):
            candidate_page.locator("#candidate-username").fill(login_name)
            candidate_page.locator("#candidate-password").fill(password)
            candidate_page.get_by_role(
                "button", name="安全登录并获取授权设备").click()
            candidate_page.locator(
                "main[data-candidate-status='ready']").wait_for(
                state="visible", timeout=25000)
            require(candidate_page.locator("main").get_attribute(
                "data-candidate-principal") == login_name,
                "candidate_console_principal")
            labels = candidate_page.locator(
                ".device-table tbody tr td:first-child strong").all_text_contents()
            require(len(labels) == len(expected_labels) and
                    len(set(labels)) == len(labels) and
                    sorted(labels) == sorted(expected_labels),
                    "candidate_console_exact_scoped_device_membership")
            require(candidate_page.get_by_test_id(
                "candidate-metric-已授权设备总数").text_content() == str(expected_total),
                "candidate_console_server_scoped_total")
            require(candidate_page.locator(".device-detail-link").count() == 0,
                "candidate_console_no_inert_detail_navigation")

        stage = "candidate_console_login_a"
        candidate_login("synthetic-browser-a",
                        ["SyntheticBrowserA-1", "SyntheticBrowserA-2"], 2)
        require(session_cookie(candidate_context) not in (
            first_cookie, second_cookie),
            "candidate_console_cookie_independent")
        # Real React ARIA tabs partition the candidate's single authorized
        # in-memory snapshot; switching views must not reveal a disabled
        # login or display another principal's rows/counters.
        stage = "candidate_console_scoped_view_navigation"
        require(candidate_page.get_by_role(
            "tablist", name="已授权只读视图").count() == 1,
            "candidate_console_authorized_tablist")
        candidate_page.get_by_role("tab", name="概览").focus()
        candidate_page.keyboard.press("ArrowRight")
        candidate_page.locator(
            '#candidate-tab-devices[aria-selected="true"]').wait_for(
            state="visible", timeout=8000)
        require(candidate_page.locator(
            '[role="tabpanel"][data-candidate-view="devices"]').count() == 1,
            "candidate_console_devices_view_boundary")
        require(candidate_page.locator(".device-table tbody tr").count() == 2,
                "candidate_console_devices_view_scoped_rows")
        require(candidate_page.get_by_test_id(
            "candidate-metric-已授权设备总数").count() == 0,
            "candidate_console_devices_view_hides_monitoring")
        candidate_page.keyboard.press("ArrowRight")
        candidate_page.locator(
            '#candidate-tab-monitoring[aria-selected="true"]').wait_for(
            state="visible", timeout=8000)
        require(candidate_page.locator(
            '[role="tabpanel"][data-candidate-view="monitoring"]').count() == 1,
            "candidate_console_monitoring_view_boundary")
        require(candidate_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_monitoring_view_hides_directory")
        require(candidate_page.get_by_test_id(
            "candidate-metric-已授权设备总数").text_content() == "2",
            "candidate_console_monitoring_view_server_total")
        candidate_page.keyboard.press("Home")
        candidate_page.locator(
            '#candidate-tab-overview[aria-selected="true"]').wait_for(
            state="visible", timeout=8000)
        require(candidate_page.locator(
            '[role="tabpanel"][data-candidate-view="overview"]').count() == 1,
            "candidate_console_overview_view_boundary")
        require(candidate_page.locator(".device-table tbody tr").count() == 2,
                "candidate_console_overview_restores_only_authorized_rows")
        candidate_page.get_by_role("button", name="登出并清除数据").click()
        candidate_page.locator(
            "main[data-candidate-status='signed_out']").wait_for(
            state="visible", timeout=20000)
        require(candidate_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_logout_clears_rows")
        require(candidate_page.get_by_role("tab").count() == 0,
                "candidate_console_logout_removes_authorized_navigation")

        stage = "candidate_console_switch_b"
        candidate_login("synthetic-browser-b", ["SyntheticBrowserB-1"], 1)
        require(candidate_page.locator(
            '#candidate-tab-overview[aria-selected="true"]').count() == 1,
            "candidate_console_identity_change_resets_view")
        require(candidate_page.get_by_text("SyntheticBrowserA-1").count() == 0,
                "candidate_console_no_previous_principal_rows")

        # NXT-08-A: ACTUAL, independently served Next.js URLs (not the
        # previous client-side ARIA tab panels). Each document navigation
        # starts with an empty server-rendered shell and a fresh Spring
        # GET /auth/session before reading scoped devices or monitoring.
        # NXT-09: the synthetic HTML route is now protected by Caddy's
        # native forward_auth -> real Spring Session over the established
        # mTLS upstream. Anonymous/forged sessions must be denied at the
        # public edge before Next.js receives any HTML request.
        stage = "route_boundary_edge_anonymous"
        route_context = browser.new_context(
            ignore_https_errors=False, accept_downloads=False,
            service_workers="block")
        contexts.append(route_context)
        route_context.route(DISALLOWED_NETWORK_URL, restrict)
        route_page = route_context.new_page()
        route_page.on("websocket", lambda _: blocked.append(
            "route_boundary_forbidden_websocket"))
        route_sessions = []
        route_page.on("response", lambda response:
            route_sessions.append(response.status)
            if response.url.endswith("/api/v1/auth/session") else None)
        route_wire = ChromiumWirePostMetadata()
        route_cdp = route_context.new_cdp_session(route_page)
        route_cdp.on("Network.requestWillBeSent", route_wire.on_request)
        route_cdp.on("Network.requestWillBeSentExtraInfo", route_wire.on_extra_info)
        route_cdp.send("Network.enable")
        route_url = CONSOLE + "/synthetic-console-route/"
        first_route = route_page.goto(route_url + "overview",
                                      wait_until="networkidle", timeout=25000)
        require(first_route is not None and first_route.status == 401,
                "route_boundary_anonymous_edge_http_401")
        require("no-store" in first_route.headers.get("cache-control", "").lower(),
                "route_boundary_anonymous_edge_no_store")
        require("text/html" not in first_route.headers.get("content-type", "").lower(),
                "route_boundary_anonymous_edge_no_nextjs_html")
        require(route_page.locator("main[data-route-state]").count() == 0,
                "route_boundary_edge_never_serves_react_to_anonymous")
        require(not route_sessions,
                "route_boundary_anonymous_denied_before_client_session_get")
        # Next.js RSC and prefetch requests use the same page URL with
        # special headers. They must never be an edge-auth bypass, even
        # when a non-HTML response would escape ordinary DOM assertions.
        for view in ("overview", "devices", "monitoring"):
            rsc = route_page.evaluate("""async (path) => {
                const response = await fetch(path + '?__rsc=synthetic-negative', {
                    headers: {'RSC':'1','Next-Router-Prefetch':'1'},
                    credentials:'same-origin', cache:'no-store',
                    redirect:'error'
                });
                return { status:response.status,
                         cache:response.headers.get('cache-control') || '',
                         body:(await response.text()).includes('data-route-state=') };
            }""", "/synthetic-console-route/" + view)
            require(rsc["status"] == 401 and "no-store" in rsc["cache"].lower() and
                    not rsc["body"], "route_boundary_anonymous_rsc_prefetch_401")

        # A forged client-controlled session cookie must not pass the edge.
        stage = "route_boundary_edge_forged_cookie"
        route_context.add_cookies([{
            "name": COOKIE, "value": "synthetic-invalid-session",
            "url": CONSOLE, "secure": True, "httpOnly": True,
            "sameSite": "Strict",
        }])
        forged_route = route_page.goto(route_url + "devices",
                                       wait_until="networkidle", timeout=25000)
        require(forged_route is not None and forged_route.status == 401,
                "route_boundary_forged_session_edge_http_401")
        require(route_page.locator("main[data-route-state]").count() == 0,
                "route_boundary_forged_cookie_never_serves_react")
        # Client-crafted edge headers must never authorize a public request.
        forged_header = route_page.evaluate("""async () => {
            const response = await fetch('/synthetic-console-route/overview', {
                headers: {'X-Lanveriq-Synthetic-Edge-Token':'0'.repeat(64)},
                credentials:'same-origin', cache:'no-store', redirect:'error'
            });
            return response.status;
        }""")
        require(forged_header == 401,
                "route_boundary_forged_edge_header_still_spring_401")
        route_context.clear_cookies()

        # Establish synthetic A/B identities on a SEPARATE, disposable
        # login-only candidate. Protected pages themselves are not login
        # endpoints and must not present a credential form to anonymous users.
        def route_login(name):
            nonlocal stage
            label = "a" if name == "synthetic-browser-a" else "b"
            stage = "route_login_" + label + "_open_disposable_candidate"
            login_response = route_page.goto(
                CONSOLE + "/synthetic-console-candidate",
                wait_until="networkidle", timeout=25000)
            require(login_response is not None and login_response.status == 200,
                    "route_boundary_disposable_login_entry")
            stage = "route_login_" + label + "_wait_signed_out"
            route_page.locator("main[data-candidate-status='signed_out']").wait_for(
                state="visible", timeout=12000)
            stage = "route_login_" + label + "_fill_synthetic_credentials"
            route_page.locator("#candidate-username").fill(name)
            route_page.locator("#candidate-password").fill(password)
            stage = "route_login_" + label + "_submit"
            route_page.get_by_role(
                "button", name="安全登录并获取授权设备").click()
            stage = "route_login_" + label + "_await_authorized_ui"
            try:
                route_page.locator(
                    "main[data-candidate-status='ready']").wait_for(
                    state="visible", timeout=25000)
            except Exception:
                observed = "missing"
                try:
                    candidate = route_page.locator(
                        "main[data-candidate-status]").get_attribute(
                        "data-candidate-status", timeout=1000)
                    if candidate in ("signed_out", "working", "ready",
                                     "invalid_credentials", "forbidden",
                                     "throttled", "unavailable", "locked"):
                        observed = candidate
                except Exception:
                    pass
                # Safe fixed UI-state enum only; no Playwright exception,
                # headers, request bodies, credentials or cookies.
                raise BrowserGateFailure(
                    "route_boundary_login_" + label + "_state_" + observed) from None
            stage = "route_login_" + label + "_principal"
            require(route_page.locator("main").get_attribute(
                "data-candidate-principal") == name,
                "route_boundary_disposable_login_identity")

        def enter_route(view, expected_login, expected_total):
            # Full-document GET proves edge 200 only after Spring verified
            # the browser's Secure host-only cookie; Next itself also
            # rechecks Spring before rendering any authorized rows.
            response = route_page.goto(
                route_url + view, wait_until="networkidle", timeout=25000)
            require(response is not None and response.status == 200,
                    "route_boundary_authenticated_edge_200")
            require("no-store" in response.headers.get("cache-control", "").lower(),
                    "route_boundary_authenticated_html_no_store")
            route_page.locator("main[data-route-state='ready']").wait_for(
                state="visible", timeout=22000)
            require(route_page.locator("main").get_attribute(
                "data-route-principal") == expected_login,
                "route_boundary_principal_after_navigation")
            require(route_page.locator("main").get_attribute(
                "data-route-view") == view,
                "route_boundary_real_page_view")
            if view != "devices":
                require(route_page.get_by_test_id(
                    "route-metric-已授权设备总数").text_content() == str(expected_total),
                    "route_boundary_scoped_total")
            # Protected RSC/prefetch for a valid session traverses Caddy's
            # forward_auth and the origin's private edge-proof check.
            rsc = route_page.evaluate("""async (path) => {
                const response = await fetch(path + '?__rsc=synthetic-authorized', {
                    headers: {'RSC':'1', 'Next-Router-Prefetch':'1'},
                    credentials:'same-origin', cache:'no-store', redirect:'error'
                });
                return {status:response.status,
                        cache:response.headers.get('cache-control') || ''};
            }""", "/synthetic-console-route/" + view)
            require(rsc["status"] == 200 and
                    "no-store" in rsc["cache"].lower(),
                    "route_boundary_valid_session_rsc_200_no_store")
            return response

        def visit_route(view, expected_login, expected_total):
            route_page.get_by_role("link", name={
                "overview": "概览", "devices": "终端资产",
                "monitoring": "监控中心"}[view]).click()
            route_page.wait_for_url(route_url + view, timeout=18000)
            route_page.locator("main[data-route-state='ready']").wait_for(
                state="visible", timeout=22000)
            require(route_page.locator("main").get_attribute(
                "data-route-principal") == expected_login,
                "route_boundary_principal_after_navigation")
            require(route_page.locator("main").get_attribute(
                "data-route-view") == view,
                "route_boundary_real_page_view")
            if view != "devices":
                require(route_page.get_by_test_id(
                    "route-metric-已授权设备总数").text_content() == str(expected_total),
                    "route_boundary_scoped_total")

        stage = "route_boundary_edge_login_a"
        route_login("synthetic-browser-a")
        enter_route("overview", "synthetic-browser-a", 2)
        require(route_page.locator(".device-table tbody tr").count() == 2,
                "route_boundary_a_only_rows")
        stage = "route_boundary_edge_navigate_a"
        visit_route("devices", "synthetic-browser-a", 2)
        require(route_page.locator(".device-table tbody tr").count() == 2,
                "route_boundary_a_devices_page")
        require(route_page.get_by_test_id(
            "route-metric-已授权设备总数").count() == 0,
            "route_boundary_devices_hides_metrics")
        visit_route("monitoring", "synthetic-browser-a", 2)
        require(route_page.locator(".device-table tbody tr").count() == 0,
                "route_boundary_monitoring_hides_rows")
        visit_route("overview", "synthetic-browser-a", 2)
        require(route_page.locator(".device-table tbody tr").count() == 2,
                "route_boundary_overview_a_rows")
        require(route_sessions.count(200) >= 4,
                "route_boundary_each_url_rechecks_server_session")
        stage = "route_boundary_edge_logout_a"
        route_page.get_by_role("button", name="安全登出").click()
        route_page.locator("main[data-route-state='signed_out']").wait_for(
            state="visible", timeout=16000)
        require(route_page.get_by_role(
            "navigation", name="受控多页面导航").count() == 0,
            "route_boundary_logout_clears_navigation")
        unauthenticated = route_page.goto(
            route_url + "devices", wait_until="networkidle", timeout=25000)
        require(unauthenticated is not None and unauthenticated.status == 401,
                "route_boundary_logout_edge_http_401")
        require(route_page.locator("main[data-route-state]").count() == 0,
                "route_boundary_logout_cannot_serve_nextjs_html")
        stage = "route_boundary_edge_login_b"
        route_login("synthetic-browser-b")
        enter_route("devices", "synthetic-browser-b", 1)
        require(route_page.locator(".device-table tbody tr").count() == 1,
                "route_boundary_b_single_group_device")
        visit_route("overview", "synthetic-browser-b", 1)
        require(route_page.get_by_text("SyntheticBrowserA-1").count() == 0,
                "route_boundary_no_a_leak_after_b_navigation")

        # Actual PG18 permission removal without changing either browser
        # cookie: the two live sessions must both stop receiving group B data
        # immediately on explicitly authorized refetch.
        stage = "revoke_group_scope"
        private_synthetic_pg_sql(db_container, """
            DO $fixture$
            DECLARE affected integer;
            BEGIN
              DELETE FROM user_role_scopes
               WHERE user_id=(SELECT id FROM users
                               WHERE normalized_login='synthetic-browser-b');
              GET DIAGNOSTICS affected = ROW_COUNT;
              IF affected <> 1 THEN
                RAISE EXCEPTION 'unexpected synthetic B grant count';
              END IF;
            END $fixture$;
        """)
        for page in pages:
            page.get_by_role("button", name="刷新已授权设备").click()
            page.locator("#multi-admin-state[data-state='scope-empty']").wait_for(
                state="visible", timeout=16000)
            require(page.locator("#multi-admin-device-list li").count() == 0,
                    "multi_admin_revoked_grant_rows_not_cleared")
            require(page.locator("#multi-admin-state").get_attribute("data-cache-count") == "0",
                    "multi_admin_revoked_grant_cache_not_cleared")

        # The product-shaped candidate must likewise clear nonempty B
        # rows and show a server-authorized ZERO, not reuse stale B metrics.
        stage = "candidate_console_scope_revoked"
        candidate_page.get_by_role("button", name="手动刷新").click()
        candidate_page.locator(
            "main[data-candidate-status='ready']").wait_for(
            state="visible", timeout=16000)
        require(candidate_page.get_by_test_id(
            "candidate-metric-已授权设备总数").text_content() == "0",
            "candidate_console_revoked_scope_total_zero")
        require(candidate_page.get_by_text(
            "当前授权范围没有设备").count() == 1,
            "candidate_console_revoked_scope_clears_device_rows")
        require(candidate_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_revoked_scope_no_rows")

        # Authenticated but revoked group scope: edge allows the identity,
        # while the Next client and Spring RBAC return zero authorized rows.
        stage = "route_boundary_edge_scope_revoked"
        enter_route("monitoring", "synthetic-browser-b", 0)
        require(route_page.locator(".device-table tbody tr").count() == 0,
                "route_boundary_revoked_group_no_rows")

        stage = "disable_browser_account"
        private_synthetic_pg_sql(db_container, """
            DO $fixture$
            DECLARE affected integer;
            BEGIN
              UPDATE users SET disabled_at=clock_timestamp()
               WHERE normalized_login='synthetic-browser-b'
                 AND disabled_at IS NULL;
              GET DIAGNOSTICS affected = ROW_COUNT;
              IF affected <> 1 THEN
                RAISE EXCEPTION 'unexpected synthetic B disabled count';
              END IF;
            END $fixture$;
        """)
        for page in pages:
            page.get_by_role("button", name="刷新已授权设备").click()
            page.locator("#multi-admin-state[data-state='revoked']").wait_for(
                state="visible", timeout=16000)
            require(page.locator("#multi-admin-state").get_attribute("data-cache-count") == "0",
                    "multi_admin_disabled_session_cache_not_cleared")
        # Disabled account must drop the UI's group counters, visible
        # table and current identity before another principal can log in.
        stage = "candidate_console_account_disabled"
        candidate_page.get_by_role("button", name="手动刷新").click()
        candidate_page.locator(
            "main[data-candidate-status='signed_out']").wait_for(
            state="visible", timeout=16000)
        require(candidate_page.locator("main").get_attribute(
            "data-candidate-principal") == "",
            "candidate_console_disabled_identity_cleared")
        require(candidate_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_disabled_rows_cleared")
        require(candidate_page.get_by_test_id(
            "candidate-metric-已授权设备总数").count() == 0,
            "candidate_console_disabled_metrics_cleared")
        for origin_ok, fetch_site_ok in candidate_wire.checked_posts(expected_count=3):
            require(origin_ok and fetch_site_ok,
                    "candidate_console_real_browser_post_metadata")
        # Disabling B: Caddy rejects GET /devices with a direct Spring 401
        # before Next.js can generate or stream its empty SSR shell.
        stage = "route_boundary_edge_account_disabled"
        disabled_route = route_page.goto(
            route_url + "devices", wait_until="networkidle", timeout=25000)
        require(disabled_route is not None and disabled_route.status == 401,
                "route_boundary_disabled_edge_http_401")
        require("no-store" in disabled_route.headers.get("cache-control", "").lower(),
                "route_boundary_disabled_edge_no_store")
        require(route_page.locator("main[data-route-state]").count() == 0,
                "route_boundary_disabled_edge_never_serves_html")
        require(route_page.get_by_role(
            "navigation", name="受控多页面导航").count() == 0,
            "route_boundary_disabled_navigation_erased")
        for origin_ok, fetch_site_ok in route_wire.checked_posts(expected_count=3):
            require(origin_ok and fetch_site_ok,
                    "route_boundary_real_browser_post_metadata")

        # NXT-06-B: a background tab must lose all locally displayed
        # authorized rows/counters, even while Spring's cookie is still valid.
        # Use a DOM lifecycle event rather than unreliable headless focus.
        stage = "candidate_console_background_lock"
        privacy_context = browser.new_context(
            ignore_https_errors=False, accept_downloads=False,
            service_workers="block")
        contexts.append(privacy_context)
        privacy_context.route(DISALLOWED_NETWORK_URL, restrict)
        privacy_page = privacy_context.new_page()
        privacy_page.on("websocket", lambda _: blocked.append(
            "candidate_console_lifecycle_forbidden_websocket"))
        privacy_wire = ChromiumWirePostMetadata()
        privacy_cdp = privacy_context.new_cdp_session(privacy_page)
        privacy_cdp.on("Network.requestWillBeSent", privacy_wire.on_request)
        privacy_cdp.on("Network.requestWillBeSentExtraInfo", privacy_wire.on_extra_info)
        privacy_cdp.send("Network.enable")
        privacy_response = privacy_page.goto(
            CONSOLE + "/synthetic-console-candidate",
            wait_until="networkidle", timeout=25000)
        require(privacy_response is not None and privacy_response.status == 200,
                "candidate_console_lifecycle_https_status")
        # Real keyboard tab order/accessible labels on the product-shaped
        # form; not only CSS or string-based markup assertions.
        require(privacy_page.get_by_role(
            "textbox", name="合成管理员账号").count() == 1,
            "candidate_console_username_accessible_name")
        require(privacy_page.get_by_label(
            "一次性合成密码").count() == 1,
            "candidate_console_password_accessible_name")
        privacy_page.get_by_role(
            "textbox", name="合成管理员账号").focus()
        privacy_page.keyboard.press("Tab")
        require(privacy_page.evaluate(
            "() => document.activeElement?.id") == "candidate-password",
            "candidate_console_keyboard_password_focus")
        privacy_page.keyboard.press("Tab")
        require(privacy_page.get_by_role(
            "button", name="安全登录并获取授权设备").evaluate(
                "(button) => document.activeElement === button"),
            "candidate_console_keyboard_submit_focus")
        privacy_page.locator("#candidate-username").fill("synthetic-browser-a")
        privacy_page.locator("#candidate-password").fill(password)
        privacy_page.get_by_role(
            "button", name="安全登录并获取授权设备").click()
        privacy_page.locator(
            "main[data-candidate-status='ready']").wait_for(
                state="visible", timeout=25000)
        require(privacy_page.locator(".device-table tbody tr").count() == 2,
                "candidate_console_lifecycle_initial_authorized_rows")
        # Only in this synthetic browser, dispatch the real installed
        # visibilitychange handler with hidden state; never fake HTTP.
        privacy_page.evaluate("""() => {
            Object.defineProperty(document, 'visibilityState', {
                configurable: true, get: () => 'hidden'
            });
            document.dispatchEvent(new Event('visibilitychange'));
        }""")
        privacy_page.locator(
            "main[data-candidate-status='locked']").wait_for(
                state="visible", timeout=12000)
        require(privacy_page.locator(".device-table tbody tr").count() == 0,
                "candidate_console_hidden_tab_erases_rows")
        require(privacy_page.get_by_test_id(
            "candidate-metric-已授权设备总数").count() == 0,
            "candidate_console_hidden_tab_erases_metrics")
        require(privacy_page.locator("main").get_attribute(
            "data-candidate-principal") == "",
            "candidate_console_hidden_tab_erases_identity")
        require(privacy_page.locator("#candidate-password").count() == 0,
                "candidate_console_hidden_tab_requires_logout")
        require(privacy_page.get_by_role(
            "status").filter(has_text="已离开当前页面或标签").count() == 1,
            "candidate_console_locked_screen_reader_status")
        require(privacy_page.get_by_role(
            "button", name="验证登出后重新登录").is_enabled(),
            "candidate_console_locked_logout_accessible")
        privacy_page.get_by_role(
            "button", name="验证登出后重新登录").click()
        privacy_page.locator(
            "main[data-candidate-status='signed_out']").wait_for(
                state="visible", timeout=16000)
        for origin_ok, fetch_site_ok in privacy_wire.checked_posts(expected_count=2):
            require(origin_ok and fetch_site_ok,
                    "candidate_console_lifecycle_real_browser_post_metadata")

        for expected_count, wire in zip((3, 1), wires):
            for origin_ok, fetch_site_ok in wire.checked_posts(expected_count=expected_count):
                require(origin_ok and fetch_site_ok, "multi_admin_browser_post_wire_metadata")
        require(not blocked, "multi_admin_browser_must_not_contact_external_origin")
        print("SYNTHETIC STAGED CONSOLE UI PASS: real React device table and "
              "server-scoped overview, A/B browser login/logout, no old rows, "
              "live grant removal, disabled-session denial; normal /login remains inert.")
        print("SYNTHETIC SCOPED CONSOLE VIEWS PASS: keyboard-accessible "
              "overview/devices/monitoring view isolation, server totals, "
              "and authorization reset across identity changes.")
        print("SYNTHETIC MULTI-ROUTE SESSION PASS: real overview/devices/"
              "monitoring Next.js URLs rechecked Spring Session on every "
              "document navigation; anonymous, revoked and disabled views "
              "never rendered stale authorized data.")
        print("SYNTHETIC EDGE HTML AUTH PASS: Caddy forward_auth over "
              "mutual TLS directly denies anonymous, forged-cookie, "
              "post-logout and disabled-account Next.js HTML with 401; "
              "verified sessions alone receive no-store scoped pages.")
        print("SYNTHETIC EDGE RSC AUTH PASS: protected Next.js RSC/prefetch "
              "requires Caddy/Spring session and no-store; forged "
              "public edge headers do not authorize requests.")
        print("SYNTHETIC CONSOLE LIFECYCLE PASS: background-tab lock erased "
              "authorized rows, identity and metrics; explicit Spring logout "
              "restored the signed-out view without background API polling.")
    except BrowserGateFailure:
        raise
    except Exception:
        # Never emit sensitive browser errors, SQL, headers or credentials.
        # This fixed phase label makes unexpected Playwright runtime failures
        # actionable without weakening TLS/CSRF/session verification.
        raise BrowserGateFailure("multi_admin_runtime_" + stage) from None
    finally:
        for context in contexts:
            context.close()


def session_cookie(context):
    matching = [c for c in context.cookies([CONSOLE]) if c["name"] == COOKIE]
    require(len(matching) == 1, "host_cookie_count")
    cookie = matching[0]
    require(cookie["domain"] == "console.lanveriq.internal", "host_cookie_domain")
    require(cookie["path"] == "/", "host_cookie_path")
    require(cookie["secure"] and cookie["httpOnly"], "host_cookie_flags")
    require(cookie["sameSite"] == "Strict", "host_cookie_same_site")
    return cookie["value"]


def main():
    require(len(sys.argv) == 1, "unexpected_cli_arguments")
    require(os.environ.get("LANVERIQ_SYNTHETIC_BROWSER_PROBE") == "1", "browser_opt_in_required")
    require(os.environ.get("LANVERIQ_SYNTHETIC_CONSOLE_SESSION") == "1", "console_opt_in_required")
    require(os.environ.get("LANVERIQ_SYNTHETIC_CADDY_BRIDGE") == "1", "bridge_opt_in_required")
    require(os.environ.get("LANVERIQ_SYNTHETIC_NEXTJS_PROBE", "0") in ("0", "1"),
            "invalid_nextjs_probe_mode")
    nextjs_mode = os.environ.get("LANVERIQ_SYNTHETIC_NEXTJS_PROBE", "0") == "1"
    require(os.environ.get("LANVERIQ_SYNTHETIC_AUTH_UI_PROBE", "0") in ("0", "1"),
            "invalid_synthetic_auth_ui_mode")
    auth_ui_mode = os.environ.get("LANVERIQ_SYNTHETIC_AUTH_UI_PROBE", "0") == "1"
    require(not auth_ui_mode or nextjs_mode, "synthetic_auth_ui_requires_nextjs")
    require(os.environ.get("LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE", "0") in ("0", "1"),
            "invalid_synthetic_multi_admin_mode")
    multi_admin_mode = os.environ.get("LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE", "0") == "1"
    require(not multi_admin_mode or (auth_ui_mode and nextjs_mode),
            "multi_admin_requires_three_explicit_browser_gates")
    db_container = os.environ.get("LANVERIQ_SYNTHETIC_MULTI_ADMIN_DB_CID", "")
    require(not multi_admin_mode or re.fullmatch(r"[0-9a-f]{12,64}", db_container) is not None,
            "multi_admin_disposable_pg_container_required")
    home = os.environ.get("HOME", "")
    require(home and os.path.isdir(os.path.join(home, ".pki/nssdb")), "private_browser_trust_missing")
    with os.fdopen(3, "r", encoding="ascii") as private_input:
        password = private_input.readline().rstrip("\r\n")
    require(re.fullmatch(r"[0-9a-f]{48}", password) is not None,
            "synthetic_password_format_required")

    from playwright.sync_api import Error as PlaywrightError, sync_playwright

    chromium_args = [
        "--host-resolver-rules=MAP console.lanveriq.internal 127.0.0.1",
        "--no-proxy-server",
    ]
    with sync_playwright() as playwright:
        # Genuine negative control: same disposable Caddy, without importing
        # its private test CA. A generic network failure is NOT a valid TLS
        # rejection. Never allow ignore_https_errors or Chromium bypass flags.
        untrusted_home = os.path.join(home, "untrusted-browser-home")
        os.mkdir(untrusted_home, 0o700)
        untrusted = playwright.chromium.launch(
            headless=True, args=chromium_args,
            env={**os.environ, "HOME": untrusted_home},
        )
        try:
            negative_context = untrusted.new_context(ignore_https_errors=False)
            try:
                negative_page = negative_context.new_page()
                try:
                    negative_page.goto(PROBE, wait_until="domcontentloaded", timeout=20000)
                except PlaywrightError as error:
                    require("ERR_CERT_AUTHORITY_INVALID" in str(error),
                            "untrusted_browser_must_fail_ca_validation")
                else:
                    raise BrowserGateFailure("untrusted_browser_accepted_private_ca")
            finally:
                negative_context.close()
        finally:
            untrusted.close()

        browser = playwright.chromium.launch(headless=True, args=chromium_args)
        try:
            context = browser.new_context(
                ignore_https_errors=False,
                accept_downloads=False,
                service_workers="block",
            )
            blocked_network = []
            def restrict(route):
                # This route matches only URLs outside the exact trusted
                # synthetic HTTPS origin. Never forward or log those requests.
                blocked_network.append("unexpected_browser_origin")
                route.abort()
            context.route(DISALLOWED_NETWORK_URL, restrict)
            page = context.new_page()
            # CDP observes browser network-stack headers after interception.
            # This does not add or override any browser request header.
            wire_posts = ChromiumWirePostMetadata()
            cdp = context.new_cdp_session(page)
            cdp.on("Network.requestWillBeSent", wire_posts.on_request)
            cdp.on("Network.requestWillBeSentExtraInfo", wire_posts.on_extra_info)
            cdp.send("Network.enable")
            page.on("websocket", lambda _: blocked_network.append("unexpected_browser_websocket"))
            page.set_default_timeout(15000)
            # Unlike a Node API client, the page must complete full Chromium TLS
            # validation with only the disposable CA imported into private NSS.
            response = page.goto(PROBE, wait_until="domcontentloaded", timeout=20000)
            require(response is not None and response.status == 200, "probe_html_status")
            require(page.title() == "Lanveriq synthetic browser gate", "probe_html_origin")
            require(page.evaluate("window.isSecureContext") is True, "browser_not_secure")
            require(page.url == PROBE, "unexpected_browser_url")
            if nextjs_mode:
                # The same disposable Caddy HTTPS origin serves Next.js
                # pages and Spring API without any Next.js business proxy.
                verify_nextjs_same_origin(context)

            observed_posts = []
            def observe(request):
                # Capture Request references at issuance. requestfinished can
                # arrive after fetch() resolves with HTTP 403/204 headers, and
                # counting only completed responses races the final assertion.
                # Neither the callback nor the result prints request secrets.
                if request.method == "POST" and urlparse(request.url).path in (
                        "/api/v1/auth/login", "/api/v1/auth/logout"):
                    observed_posts.append(request)
            page.on("request", observe)

            def fetch(path, method="GET", form=None, csrf=None):
                return page.evaluate(
                    FETCH_SCRIPT,
                    {"path": path, "method": method, "form": form, "csrfToken": csrf},
                )

            strict_anonymous_401(fetch("/api/v1/auth/session"),
                                 "anonymous_session")
            strict_anonymous_401(fetch("/api/v1/devices"),
                                 "anonymous_devices")
            strict_anonymous_401(fetch("/api/v1/monitoring/overview"),
                                 "anonymous_overview")
            csrf = strict_json(fetch("/api/v1/auth/csrf"), 200, "csrf_prelogin").get("csrfToken")
            require_csrf(csrf, "csrf_prelogin_value")
            before = session_cookie(context)
            require(COOKIE not in page.evaluate("document.cookie"),
                    "httponly_visible_to_javascript")
            # A __Host- cookie must never be shared with purpose-separated
            # Agent or enrollment virtual hosts, including same-site subdomains.
            for other in ("agent.lanveriq.internal", "enroll.lanveriq.internal"):
                require(not any(c["name"] == COOKIE for c in
                                context.cookies(["https://" + other])),
                        "host_only_cookie_other_vhost")
            # A real browser form POST automatically sets Origin and Fetch Metadata;
            # never synthesize or override either header in page Javascript.
            denied = fetch("/api/v1/auth/login", "POST",
                           {"username": "synthetic-owner", "password": "wrong"}, None)
            require(denied["status"] == 403, "missing_csrf_must_be_403")
            strict_anonymous_401(fetch("/api/v1/auth/session"),
                                 "missing_csrf_must_remain_anonymous")
            login = fetch("/api/v1/auth/login", "POST",
                          {"username": "synthetic-owner", "password": password}, csrf)
            require(login["status"] == 204, "synthetic_browser_login")
            after = session_cookie(context)
            require(before != after, "session_fixation_rotation")
            for other in ("agent.lanveriq.internal", "enroll.lanveriq.internal"):
                require(not any(c["name"] == COOKIE for c in
                                context.cookies(["https://" + other])),
                        "authenticated_cookie_other_vhost")
            # The anonymous token must lose its authority after successful
            # session fixation and may never authorize authenticated logout.
            stale_logout = fetch("/api/v1/auth/logout", "POST", None, csrf)
            require(stale_logout["status"] == 403,
                    "prelogin_csrf_must_fail_after_fixation")
            csrf_after = strict_json(
                fetch("/api/v1/auth/csrf"), 200, "csrf_postlogin").get("csrfToken")
            require_csrf(csrf_after, "csrf_postlogin_value")
            require(csrf_after != csrf, "csrf_must_rotate_after_login")
            session = strict_json(fetch("/api/v1/auth/session"), 200, "session_authenticated")
            require(session == {"login": "synthetic-owner"}, "session_minimal_identity")
            devices = strict_json(fetch("/api/v1/devices?limit=50"), 200, "devices_scoped")
            require(devices.get("items") == [], "fresh_pg_must_have_no_devices")
            overview = strict_json(
                fetch("/api/v1/monitoring/overview"), 200, "overview_scoped")
            counters = ("total", "online", "stale", "offline",
                        "revoked", "pending", "retired")
            require(all(type(overview.get(key)) is int and overview[key] == 0
                        for key in counters), "fresh_pg_scoped_overview_empty")
            logout = fetch("/api/v1/auth/logout", "POST", None, csrf_after)
            require(logout["status"] == 204, "synthetic_browser_logout")
            strict_anonymous_401(fetch("/api/v1/auth/session"),
                                 "logout_must_invalidate_session")
            strict_anonymous_401(fetch("/api/v1/devices"),
                                 "logout_must_deny_devices")
            strict_anonymous_401(fetch("/api/v1/monitoring/overview"),
                                 "logout_must_deny_overview")
            # Read complete on-wire headers after all four browser fetches
            # have returned. Strictly require Origin and Fetch-Site on every
            # observed POST; never synthesize those headers in JavaScript.
            # Page request events independently prove four actual POSTs. CDP
            # verifies their browser-produced wire headers, not the provisional
            # headers visible to an intercepting Playwright route.
            require(len(observed_posts) == 4,
                    "real_browser_origin_fetch_metadata_post_count")
            for index, request in enumerate(observed_posts, 1):
                require(browser_post_metadata(request)[0],
                        "real_browser_origin_playwright_post_" + str(index))
            require_browser_post_metadata(wire_posts.checked_posts())
            if auth_ui_mode:
                # Only after the independent 4-POST security gate has passed.
                # The normal /login page remains disabled and unmodified.
                verify_nextjs_synthetic_auth_ui(context, password)
            require(not blocked_network, "browser_must_not_contact_external_origin_or_websocket")
            context.close()
            if multi_admin_mode:
                verify_nextjs_synthetic_multi_admin(browser, password, db_container)
        finally:
            browser.close()
    print("MANUAL SYNTHETIC CHROMIUM PASS: trusted TLS with negative CA control, browser Origin/Fetch-Site, "
          "host-only HttpOnly Strict cookie, CSRF, rotation, login, scoped empty device/overview reads, anonymous denials and logout.")
    if nextjs_mode:
        print("MANUAL SYNTHETIC SAME-ORIGIN NEXT.JS PASS: real Caddy HTTPS Next.js "
              "preview pages and client navigation; disabled login; no automatic "
              "API or session cookie. Spring login remains isolated fixture-only.")
        if auth_ui_mode:
            print("MANUAL SYNTHETIC NEXT.JS AUTH UI PASS: disposable browser-mounted "
                  "Next.js client used Spring CSRF/login/session, authorized empty "
                  "device/overview reads, logout and post-logout 401; real CDP POST headers.")
            if multi_admin_mode:
                print("MANUAL SYNTHETIC NEXT.JS MULTI-ADMIN RBAC PASS: separate Chromium "
                      "cookies, real PG18 nonempty A/B scopes, 404, cache purge on "
                      "identity switch, live grant removal and account disable.")
                print("NOT PROVEN: production /login, real managed devices, "
                      "human UI review or authorized deployment.")
            else:
                print("NOT PROVEN: production /login wiring, nonempty two-admin browser RBAC, "
                      "real people, devices, Agent or production deployment.")
        else:
            print("NOT PROVEN: mounted Next.js browser credential UI, "
                  "multi-principal browser RBAC, real people/devices/Agent or deployment.")
    else:
        print("NOT PROVEN: actual Next.js runtime, multi-group RBAC, "
              "real people, devices or Agent.")


if __name__ == "__main__":
    try:
        main()
    except BrowserGateFailure as exc:
        print("FAIL: synthetic Chromium security gate: " + str(exc), file=sys.stderr)
        raise SystemExit(1) from None
    except Exception:
        # Never print Playwright traces/headers/credentials/session IDs on CI.
        print("FAIL: synthetic Chromium runtime or certificate trust failed",
              file=sys.stderr)
        raise SystemExit(1) from None
