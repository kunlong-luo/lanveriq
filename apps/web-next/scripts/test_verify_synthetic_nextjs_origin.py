"""NXT-11: hermetic selection/response regressions for public Next.js assets.

No browser, credentials, network, real server, or Docker are used here.
The full Caddy/Next.js/Chromium run independently verifies actual responses.
"""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("verify-synthetic-nextjs-origin.py")
spec = importlib.util.spec_from_file_location("nxt11_origin_probe", SCRIPT)
origin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(origin)


class PublicAssetGateTests(unittest.TestCase):
    def test_fails_closed_without_real_public_javascript_chunk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "static"
            with self.assertRaises(origin.OriginGateFailure):
                origin.select_public_chunk(root)
            root.mkdir()
            (root / "bundle.css").write_text("body{}", encoding="utf-8")
            with self.assertRaises(origin.OriginGateFailure):
                origin.select_public_chunk(root)
            (root / "too-large.js").write_bytes(b"x" * (2 * 1024 * 1024 + 1))
            with self.assertRaises(origin.OriginGateFailure):
                origin.select_public_chunk(root)

    def test_selects_real_nested_js_and_ignores_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "static"
            chunks = root / "chunks"
            chunks.mkdir(parents=True)
            actual = chunks / "a.js"
            actual.write_text("console.log('public');", encoding="utf-8")
            (root / "z.js").symlink_to(actual)
            self.assertEqual(origin.select_public_chunk(root), actual)

    def test_public_chunk_is_requested_with_identity_encoding(self):
        class FakeResponse:
            status = 200

            def getheader(self, name, default=""):
                return {
                    "Content-Type": "text/javascript",
                    "Cache-Control": "public, max-age=31536000, immutable",
                    "X-Content-Type-Options": "nosniff",
                }.get(name, default)

            def read(self, limit):
                return b"immutable asset"

        class FakeConnection:
            def __init__(self, host, port, timeout):
                self.host, self.port, self.timeout = host, port, timeout

            def request(self, method, path, headers):
                self.requested = (method, path, headers)
                self.assert_request(self.requested)

            def assert_request(self, requested):
                assert requested[0] == "GET"
                assert requested[1].startswith("/_next/static/")
                assert requested[2]["Accept-Encoding"] == "identity"

            def getresponse(self):
                return FakeResponse()

            def close(self):
                pass

        with patch.object(origin.http.client, "HTTPConnection", FakeConnection):
            metadata = origin.request_public_chunk("/_next/static/chunks/a.js", {})
        self.assertEqual(metadata[:4], (
            200, "text/javascript", "public, max-age=31536000, immutable",
            "nosniff",
        ))
        self.assertEqual(len(metadata[4]), 32)


if __name__ == "__main__":
    unittest.main()
