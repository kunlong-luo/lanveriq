"""Synthetic source-fixture negative tests for the RD-2 release lock."""
from __future__ import annotations

import tempfile
from pathlib import Path
import unittest

from verify_rd2_release_boundary import verify


class RemoteReleaseBoundaryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="lanveriq-rd2-release-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.write(
            "apps/server/src/main/resources/application.yml",
            "lanveriq:\n  remote:\n    enabled: false\n",
        )
        self.write(
            "apps/server/src/main/java/Synthetic.java",
            "class Synthetic { void noop() {} }\n",
        )
        self.write(
            "apps/web-next/src/app/page.tsx",
            "export default function Page() { return null; }\n",
        )
        self.caddy = self.write(
            "deploy/caddy/Caddyfile.reference",
            "# 21116 would be forbidden if active\n:443 { respond \"not active\" }\n",
        )

    def write(self, name: str, data: str) -> Path:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data, encoding="utf-8")
        return path

    def test_valid_synthetic_source_is_default_denied(self) -> None:
        self.assertEqual([], verify(self.root))

    def test_requires_explicit_unoverrideable_disabled_remote_flag(self) -> None:
        self.app.write_text(
            "lanveriq:\n  remote:\n    enabled: ${LANVERIQ_REMOTE_ENABLED:false}\n",
            encoding="utf-8",
        )
        self.assertTrue(any("literal false" in i for i in verify(self.root)))
        self.app.write_text("lanveriq:\n", encoding="utf-8")
        self.assertTrue(any("literal false" in i for i in verify(self.root)))

    def test_spring_mapping_for_remote_sessions_is_blocked(self) -> None:
        self.write(
            "apps/server/src/main/java/Remote.java",
            '@PostMapping(path = "/api/v1/devices/{id}/remote-sessions")\n'
            "class Remote {}\n",
        )
        self.assertTrue(any("Spring mapping" in i for i in verify(self.root)))

    def test_nextjs_remote_route_is_blocked(self) -> None:
        self.write(
            "apps/web-next/src/app/(console)/devices/remote-control/page.tsx",
            "export default function P() {return null;}\n",
        )
        self.assertTrue(any("Next.js route" in i for i in verify(self.root)))

    def test_live_caddy_rustdesk_port_is_blocked_but_comment_is_not(self) -> None:
        self.assertEqual([], verify(self.root))
        self.caddy.write_text(
            ":443 { reverse_proxy localhost:21117 }\n", encoding="utf-8"
        )
        self.assertTrue(any("RustDesk port" in i for i in verify(self.root)))

    def test_unrelated_endpoint_does_not_fail(self) -> None:
        self.write(
            "apps/server/src/main/java/Other.java",
            '@GetMapping(path = "/api/v1/devices")\nclass Other {}\n',
        )
        self.assertEqual([], verify(self.root))


if __name__ == "__main__":
    unittest.main()
