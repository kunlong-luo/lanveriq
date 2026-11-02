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
from urllib.parse import urlparse

CONSOLE = "https://console.lanveriq.internal"
PROBE = CONSOLE + "/__synthetic_browser_probe"
COOKIE = "__Host-lanveriq-session"
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
    require("no-store" in result["cacheControl"].lower(), label + "_cache_control")
    require(isinstance(result["json"], dict), label + "_json")
    return result["json"]


def require_csrf(value, label):
    require(isinstance(value, str) and 8 <= len(value) <= 4096, label)


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
            page = context.new_page()
            page.set_default_timeout(15000)
            # Unlike a Node API client, the page must complete full Chromium TLS
            # validation with only the disposable CA imported into private NSS.
            response = page.goto(PROBE, wait_until="domcontentloaded", timeout=20000)
            require(response is not None and response.status == 200, "probe_html_status")
            require(page.title() == "Lanveriq synthetic browser gate", "probe_html_origin")
            require(page.evaluate("window.isSecureContext") is True, "browser_not_secure")
            require(page.url == PROBE, "unexpected_browser_url")

            observed_posts = []
            def observe(request):
                # Record ONLY Origin and Fetch Metadata, NEVER bodies/credentials.
                if urlparse(request.url).path in (
                        "/api/v1/auth/login", "/api/v1/auth/logout"):
                    headers = request.all_headers()
                    observed_posts.append((
                        headers.get("origin") == CONSOLE,
                        headers.get("sec-fetch-site") == "same-origin",
                    ))
            page.on("request", observe)

            def fetch(path, method="GET", form=None, csrf=None):
                return page.evaluate(
                    FETCH_SCRIPT,
                    {"path": path, "method": method, "form": form, "csrfToken": csrf},
                )

            require(fetch("/api/v1/auth/session")["status"] == 401,
                    "anonymous_session_must_be_401")
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
            require(fetch("/api/v1/auth/session")["status"] == 401,
                    "missing_csrf_must_remain_anonymous")
            login = fetch("/api/v1/auth/login", "POST",
                          {"username": "synthetic-owner", "password": password}, csrf)
            require(login["status"] == 204, "synthetic_browser_login")
            after = session_cookie(context)
            require(before != after, "session_fixation_rotation")
            csrf_after = strict_json(
                fetch("/api/v1/auth/csrf"), 200, "csrf_postlogin").get("csrfToken")
            require_csrf(csrf_after, "csrf_postlogin_value")
            require(csrf_after != csrf, "csrf_must_rotate_after_login")
            session = strict_json(fetch("/api/v1/auth/session"), 200, "session_authenticated")
            require(session == {"login": "synthetic-owner"}, "session_minimal_identity")
            devices = strict_json(fetch("/api/v1/devices?limit=50"), 200, "devices_scoped")
            require(devices.get("items") == [], "fresh_pg_must_have_no_devices")
            logout = fetch("/api/v1/auth/logout", "POST", None, csrf_after)
            require(logout["status"] == 204, "synthetic_browser_logout")
            require(fetch("/api/v1/auth/session")["status"] == 401,
                    "logout_must_invalidate_session")
            require(fetch("/api/v1/devices")["status"] == 401,
                    "logout_must_deny_devices")
            require(len(observed_posts) == 3 and all(
                origin and metadata for origin, metadata in observed_posts),
                "real_browser_origin_fetch_metadata")
            context.close()
        finally:
            browser.close()
    print("MANUAL SYNTHETIC CHROMIUM PASS: trusted TLS with negative CA control, browser Origin/Fetch-Site, "
          "host-only HttpOnly Strict cookie, CSRF, rotation, login, scoped empty read and logout.")
    print("NOT PROVEN: actual Next.js runtime, multi-group RBAC, real people, devices or Agent.")


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
