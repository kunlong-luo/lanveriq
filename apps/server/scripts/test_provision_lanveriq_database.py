"""Static safety and SQL-client portability checks for manual database bootstrap.

No PostgreSQL, Docker, shared spark-db service, role or database is touched.
This deliberately does not replace a manual, disposable PostgreSQL acceptance.
"""

from pathlib import Path
import re
import unittest


SQL_FILE = Path(__file__).with_name("provision-lanveriq-database.sql")


def active_sql() -> str:
    return "\n".join(
        line for line in SQL_FILE.read_text(encoding="utf-8").splitlines()
        if not line.lstrip().startswith("--")
    )


class ProvisioningSqlTest(unittest.TestCase):
    def test_only_standard_sql_and_no_psql_meta_commands(self):
        sql = active_sql()
        # A psql-only \set is rejected by JDBC/IDEA/DataGrip as SQLSTATE 42601.
        self.assertNotRegex(sql, r"(?m)^\s*\\")
        self.assertNotIn(":ON_ERROR_STOP", sql)
        self.assertEqual(sql.count("CREATE DATABASE lanveriq OWNER lanveriq;"), 1)
        self.assertNotRegex(sql, r"(?im)^\s*BEGIN\s*;")
        self.assertNotRegex(sql, r"(?im)^\s*COMMIT\s*;")

    def test_rejects_unintended_database_or_existing_objects(self):
        sql = active_sql()
        self.assertIn("current_database() <> 'postgres'", sql)
        self.assertIn("rolname = current_user AND rolsuper", sql)
        self.assertIn("pg_roles WHERE rolname = 'lanveriq'", sql)
        self.assertIn("pg_database WHERE datname = 'lanveriq'", sql)
        self.assertGreaterEqual(sql.count("RAISE EXCEPTION"), 4)
        self.assertLess(sql.index("pg_database WHERE datname = 'lanveriq'"),
                        sql.index("CREATE ROLE lanveriq"))

    def test_creates_only_login_disabled_database_owner(self):
        sql = active_sql()
        self.assertIn("CREATE ROLE lanveriq\n    NOLOGIN", sql)
        for policy in ("NOSUPERUSER", "NOCREATEDB", "NOCREATEROLE", "NOREPLICATION"):
            self.assertIn(policy, sql)
        self.assertIn("REVOKE ALL ON DATABASE lanveriq FROM PUBLIC;", sql)
        self.assertLess(sql.index("END\n$lanveriq_preflight$;"),
                        sql.index("CREATE DATABASE lanveriq OWNER lanveriq;"))

    def test_never_hardcodes_secrets_or_modifies_shared_database(self):
        sql = active_sql()
        self.assertNotRegex(sql, r"(?i)\bPASSWORD\s+['\"]")
        self.assertNotRegex(sql, r"(?i)\b(?:DROP|TRUNCATE|DELETE)\b")
        self.assertNotRegex(sql, r"(?i)\b(?:ALTER\s+ROLE|GRANT\s+ALL)\b")
        self.assertNotIn("iot_farm", sql)


if __name__ == "__main__":
    unittest.main()
