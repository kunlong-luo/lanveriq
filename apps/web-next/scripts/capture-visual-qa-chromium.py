#!/usr/bin/env python3
"""NXT-18: deterministic, synthetic-safe screenshot and visual-layout checks.

Runs exclusively against the *ordinary disconnected* production-built Next.js
preview at 127.0.0.1:3100, BEFORE any temporary synthetic test route exists.
Screen captures contain no credentials, devices, session cookies or real data.
A screenshot is REVIEW EVIDENCE, not a pixel-perfect golden-image assertion.
"""
import json
import os
from pathlib import Path
from urllib.parse import urlsplit

BASE = "http://127.0.0.1:3100"
ROUTES = ("overview", "devices", "monitoring", "design-system", "login")
VIEWPORTS = (
    (390, 844, "mobile"),
    (768, 1024, "tablet"),
    (1440, 900, "desktop"),
)


def require(ok, reason):
    if not ok:
        raise AssertionError("NXT-18 visual gate: " + reason)


def allowed(url):
    try:
        parsed = urlsplit(url)
        return (
            parsed.scheme == "http" and parsed.hostname == "127.0.0.1"
            and parsed.port == 3100 and parsed.username is None
            and parsed.password is None
            and not parsed.path.startswith(("/api/", "/actuator/", "//"))
        )
    except ValueError:
        return False


def measure(page):
    return page.evaluate("""() => {
      const read = (selector) => {
        const el = document.querySelector(selector);
        if (!el) return null;
        const bounds = el.getBoundingClientRect();
        const style = getComputedStyle(el);
        return {
          width: Math.round(bounds.width),
          height: Math.round(bounds.height),
          x: Math.round(bounds.x),
          y: Math.round(bounds.y),
          display: style.display,
          color: style.color,
          background: style.backgroundColor
        };
      };
      return {
        rootBackground: getComputedStyle(document.documentElement)
          .getPropertyValue('--background').trim(),
        rootViolet: getComputedStyle(document.documentElement)
          .getPropertyValue('--lan-violet').trim(),
        viewportWidth: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        main: read('main'),
        heading: read('h1'),
        sidebar: read('.bc-console .sidebar'),
        topbar: read('.bc-console .topbar'),
        hero: read('.bc-overview .overview-hero'),
        directory: read('.directory-panel')
      };
    }""")


def run():
    from playwright.sync_api import sync_playwright

    directory = os.environ.get("LANVERIQ_NXT18_VISUAL_DIR")
    require(directory and os.environ.get("GITHUB_ACTIONS") == "true",
            "screenshots are only permitted inside isolated GitHub Actions")
    output = Path(directory)
    output.mkdir(parents=True, exist_ok=True)

    records = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            for width, height, label in VIEWPORTS:
                context = browser.new_context(
                    viewport={"width": width, "height": height},
                    device_scale_factor=1,
                    reduced_motion="reduce", service_workers="block",
                    accept_downloads=False,
                )
                rejected = []
                context.route("**/*", lambda route: route.continue_()
                              if allowed(route.request.url) else
                              (rejected.append("non-preview request"), route.abort()))
                try:
                    page = context.new_page()
                    page.on("websocket", lambda _: rejected.append("websocket"))
                    for route in ROUTES:
                        response = page.goto(BASE + "/" + route,
                                             wait_until="networkidle", timeout=25000)
                        require(response is not None and response.status == 200,
                                "missing " + route + " at " + label)
                        require(page.locator("h1").first.is_visible(),
                                "hidden heading on " + route + " at " + label)
                        if route == "login":
                            require(page.locator("#login-username").is_disabled()
                                    and page.locator("#login-password").is_disabled()
                                    and page.locator("form").count() == 0,
                                    "credentials were enabled during capture")
                        else:
                            require(page.get_by_role("status").filter(
                                has_text="独立预览 · 不含真实数据").count() >= 1,
                                "missing preview-only warning")
                        page.evaluate("() => document.fonts.ready")
                        metrics = measure(page)
                        require(metrics["rootBackground"] == "#f8f8fc" and
                                metrics["rootViolet"] == "#6a5bff",
                                "brand tokens changed on " + route)
                        require(metrics["documentWidth"] <= width + 2,
                                "document horizontal overflow on " + route + " " + label)
                        require(metrics["heading"] is not None and
                                metrics["heading"]["width"] > 80,
                                "collapsed page title on " + route + " " + label)
                        if route != "login":
                            require(metrics["main"] is not None and
                                    metrics["main"]["width"] <= width + 2,
                                    "console main exceeds " + label)
                            if width == 390:
                                require(metrics["sidebar"]["display"] == "none" and
                                        page.get_by_role(
                                            "button", name="打开导航").is_visible(),
                                        "mobile layout failed to use native Dialog trigger")
                            else:
                                require(metrics["sidebar"]["display"] != "none" and
                                        metrics["sidebar"]["width"] >= 220,
                                        "tablet/desktop nav unexpectedly hidden")
                            if route == "devices":
                                require(metrics["directory"] is not None,
                                        "missing operational directory card")
                                if width == 1440:
                                    require(metrics["directory"]["y"] < 750,
                                            "asset list fell below desktop first viewport")
                        filename = label + "-" + route + ".png"
                        page.screenshot(
                            path=str(output / filename),
                            full_page=True,
                            animations="disabled", caret="hide",
                        )
                        records.append({
                            "viewport": label,
                            "width": width,
                            "height": height,
                            "route": "/" + route,
                            "screenshot": filename,
                            "metrics": metrics,
                            "disconnectedOnly": True,
                        })
                    require(context.cookies() == [], "unexpected browser cookies")
                    require(not rejected, "unexpected non-preview network request")
                finally:
                    context.close()
        finally:
            browser.close()

    (output / "manifest.json").write_text(
        json.dumps({
            "scope": "NXT-18 disconnected-only design review",
            "productionData": False,
            "visualDiffGoldenBaseline": False,
            "captures": records,
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output / "README.txt").write_text(
        "Lanveriq NXT-18 isolated UI screenshots.\n"
        "All 15 captures are disconnected public-preview pages only; "
        "no synthetic device fixtures, credentials or business data.\n"
        "The accompanying manifest contains layout geometry and design tokens.\n"
        "Screenshots are for visual review, NOT automated pixel-diff approval.\n",
        encoding="utf-8",
    )
    require(len(records) == len(ROUTES) * len(VIEWPORTS),
            "not all 15 visual captures completed")
    print("NXT-18 VISUAL QA PASS: 15 screenshot captures, 3 responsive "
          "viewports, isolated requests, disconnected states and layout metrics.")


if __name__ == "__main__":
    run()
