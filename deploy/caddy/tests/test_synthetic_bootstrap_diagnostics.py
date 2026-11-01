"""No Docker/PG/Java tests: only fixed-label, secret-free diagnostic behavior."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
MODULE = ROOT / "apps/server/scripts/diagnose-synthetic-bootstrap.py"
spec = importlib.util.spec_from_file_location("diagnose_synthetic_bootstrap", MODULE)
diagnose = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diagnose)


class SyntheticBootstrapDiagnosticSafetyTest(unittest.TestCase):
    def test_an_empty_or_unknown_log_has_only_one_fixed_label(self):
        self.assertEqual(
            ("unclassified_offline_bootstrap_failure",),
            diagnose.categories("secret-password=CANARY-SECRET\n"),
        )

    def test_known_console_error_returns_constant_label_only(self):
        raw = (
            "ABC_FAKE_PASSWORD_DO_NOT_PRINT\n"
            "New administrator password: "
            "Attached interactive terminal required for bootstrap\n"
            "org.example.secretaccount\n"
        )
        result = diagnose.categories(raw)
        self.assertIn("interactive_terminal_unavailable", result)
        self.assertIn("first_password_prompt_seen", result)
        self.assertNotIn("ABC_FAKE_PASSWORD_DO_NOT_PRINT", repr(result))
        self.assertNotIn("org.example.secretaccount", repr(result))

    def test_known_db_or_jpa_error_uses_fixed_category_no_sql(self):
        raw = (
            "org.postgresql.util.PSQLException: password: CANARY-SECRET "
            "SQL: INSERT INTO users VALUES ('CANARY-ACCOUNT')\n"
            "org.hibernate.query.IllegalSelectQueryException\n"
        )
        result = diagnose.categories(raw)
        self.assertIn("postgresql_connection_or_query_failure", result)
        self.assertIn("jpa_returning_or_result_failure", result)
        self.assertNotIn("CANARY-SECRET", repr(result))
        self.assertNotIn("CANARY-ACCOUNT", repr(result))

    def test_specific_spring_root_causes_never_reveal_source_details(self):
        for exact_source, expected in (
            ("org.springframework.beans.factory.UnsatisfiedDependencyException: "
             "example.canary.secret", "spring_missing_bean"),
            ("BeanCreationException: example.canary.secret", "spring_bean_initialization"),
            ("No ServletContext set: CANARY-SESSION", "spring_context_missing_servlet"),
            ("org.postgresql.util.PSQLException: Connection refused "
             "CANARY-PRIVATE", "db_connection_refused"),
            ("EntityManagerFactory invalid CANARY-ENTITY", "jpa_entity_manager_failure"),
            ("FirstAdminBootstrapRunner.run error CANARY-USER", "bootstrap_exception"),
            ("BeanInstantiationException: Failed to instantiate [com.lanveriq.server.security.FirstAdminBootstrapRunner] No default constructor found; SECRET-CANARY", "spring_bootstrap_constructor_selection"),
        ):
            with self.subTest(expected=expected):
                categories = diagnose.categories(exact_source)
                self.assertIn(expected, categories)
                for canary in ("canary", "CANARY", "secret", "PRIVATE"):
                    self.assertNotIn(canary, repr(categories))

    def test_success_marker_cannot_be_confused_with_exceptions(self):
        self.assertIn(
            "application_reported_bootstrap_success",
            diagnose.categories("Initial administrator created.\n"),
        )

    def test_main_never_prints_raw_log(self):
        from contextlib import redirect_stderr
        from io import StringIO
        from unittest.mock import patch
        import sys

        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "internal.log"
            path.write_text(
                "Password input aborted\npassword='CANARY-PRIVATE'\n",
                encoding="utf-8",
            )
            capture = StringIO()
            with patch.object(sys, "argv", [str(MODULE), str(path)]):
                with redirect_stderr(capture):
                    code = diagnose.main()
            self.assertEqual(0, code)
            self.assertIn("interactive_input_aborted", capture.getvalue())
            self.assertNotIn("CANARY-PRIVATE", capture.getvalue())


if __name__ == "__main__":
    unittest.main()
