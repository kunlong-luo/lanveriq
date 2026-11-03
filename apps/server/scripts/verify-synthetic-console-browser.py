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
            require_browser_post_metadata([
                (request.all_headers().get("origin") == CONSOLE,
                 request.all_headers().get("sec-fetch-site") == "same-origin")
                for request in observed_posts
            ])
            require(not blocked_network, "browser_must_not_contact_external_origin_or_websocket")
            context.close()
        finally:
            browser.close()
    print("MANUAL SYNTHETIC CHROMIUM PASS: trusted TLS with negative CA control, browser Origin/Fetch-Site, "
          "host-only HttpOnly Strict cookie, CSRF, rotation, login, scoped empty device/overview reads, anonymous denials and logout.")
    if nextjs_mode:
        print("MANUAL SYNTHETIC SAME-ORIGIN NEXT.JS PASS: real Caddy HTTPS Next.js "
              "preview pages and client navigation; disabled login; no automatic "
              "API or session cookie. Spring login remains isolated fixture-only.")
        print("NOT PROVEN: activated Next.js login/data hooks, multi-group RBAC, "
              "real people, devices, Agent or production deployment.")
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
