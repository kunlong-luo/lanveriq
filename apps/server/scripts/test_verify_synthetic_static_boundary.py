"""NXT-11 pure regressions for the real-Caddy Chromium static-asset gate.

No live listener, browser, credentials, PostgreSQL or Docker are accessed.
"""
import importlib.util
from pathlib import Path
import unittest

source = Path(__file__).with_name("verify-synthetic-console-browser.py")
spec = importlib.util.spec_from_file_location("lanveriq_browser_static_probe", source)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class StaticEdgeSecurityTests(unittest.TestCase):
    def test_accepts_only_same_origin_versioned_javascript(self):
        self.assertEqual(
            probe.canonical_public_static_path("/_next/static/chunks/abc123.js"),
            "/_next/static/chunks/abc123.js",
        )
        self.assertEqual(
            probe.canonical_public_static_path(
                "https://console.lanveriq.internal/_next/static/chunks/a.js"),
            "/_next/static/chunks/a.js",
        )
        for invalid in (
            None, "", "/", "/_next/static/chunks/a.js.map",
            "/_next/static/../private.js",
            "/_next/static/chunks/a.js?__rsc=1",
            "/_next/static/chunks/a.js#fragment",
            "https://evil.example/_next/static/chunks/a.js",
            "//evil.example/_next/static/chunks/a.js",
            "/api/v1/auth/session",
            "/_next/static/chunks/%2e%2e/secret.js",
        ):
            with self.subTest(invalid=invalid):
                with self.assertRaises(probe.BrowserGateFailure):
                    probe.canonical_public_static_path(invalid)

    def test_static_response_requires_immutable_identity_neutral_metadata(self):
        baseline = {
            "status": 200, "mime": "text/javascript; charset=utf-8",
            "cache": "public, max-age=31536000, immutable",
            "nosniff": "nosniff", "digest": "a" * 64, "bytes": 1234,
        }
        self.assertEqual(
            probe.strict_public_static_asset(baseline, "baseline"), baseline)
        changes = (
            {"status": 401},
            {"mime": "text/html"},
            {"cache": "no-store"},
            {"cache": "private, max-age=0"},
            {"nosniff": ""},
            {"nosniff": "invalid"},
            {"digest": "not-a-sha"},
            {"bytes": 0},
            {"bytes": 2 * 1024 * 1024 + 1},
        )
        for change in changes:
            with self.subTest(change=change):
                with self.assertRaises(probe.BrowserGateFailure):
                    probe.strict_public_static_asset(
                        {**baseline, **change}, "negative")


if __name__ == "__main__":
    unittest.main()
