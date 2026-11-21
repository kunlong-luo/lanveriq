"""Static fail-closed checks for opt-in database role separation profile.

This suite never connects to a database, activates Spring or provisions roles.
Actual distinct-login PostgreSQL 18 permissions are tested in
PostgreSqlRoleSeparationIT on disposable Testcontainers.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "src/main/resources/application-db-runtime-separation-acceptance.yml"
DEFAULT = ROOT / "src/main/resources/application.yml"


class DbRoleSeparationProfileTest(unittest.TestCase):
    def test_explicit_profile_never_exposes_http_or_runs_flyway(self):
        text = PROFILE.read_text(encoding="utf-8")
        self.assertIn("on-profile: db-runtime-separation-acceptance", text)
        self.assertRegex(text, r"(?m)^    web-application-type: none$")
        self.assertRegex(text, r"(?m)^  flyway:\n    enabled: false$")
        self.assertRegex(text, r"(?m)^      ddl-auto: validate$")
        self.assertRegex(text, r"(?m)^      mode: never$")

    def test_runtime_credentials_are_required_and_migrator_never_exposed(self):
        text = PROFILE.read_text(encoding="utf-8")
        self.assertIn("${LANVERIQ_DB_RUNTIME_USER}", text)
        self.assertIn("${LANVERIQ_DB_RUNTIME_PASSWORD}", text)
        self.assertNotIn("LANVERIQ_DB_MIGRATOR_PASSWORD", text)
        self.assertNotIn("spring.flyway.password", text)
        self.assertNotRegex(text, r"(?m)^\s+password: (?:lanveriq|postgres)$")
        self.assertFalse(any(line.lstrip().startswith("password:") and ":-" in line
                             for line in text.splitlines()))

    def test_default_boot_remains_default_off_and_not_separated_implicitly(self):
        text = DEFAULT.read_text(encoding="utf-8")
        self.assertIn("remote:\n    enabled: false", text)
        self.assertIn("LANVERIQ_DB_USER:lanveriq", text)
        self.assertNotIn("db-runtime-separation-acceptance", text)


if __name__ == "__main__":
    unittest.main()
