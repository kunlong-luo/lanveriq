#!/usr/bin/env python3
"""Offline tests for RustDesk CI-only server behavioral acceptance."""
import importlib.util
import os
from pathlib import Path
from unittest import TestCase, main, mock

spec = importlib.util.spec_from_file_location("verify_native_server", Path(__file__).with_name("verify_native_server.py"))
subject = importlib.util.module_from_spec(spec)
spec.loader.exec_module(subject)


class NativeServerAcceptanceTests(TestCase):
    def test_mode_parser_does_not_guess(self):
        self.assertFalse(subject.parse_mode("ALWAYS_USE_RELAY: false\n"))
        self.assertTrue(subject.parse_mode("ALWAYS_USE_RELAY: true"))
        for response in ("", "OK", "true", "ALWAYS_USE_RELAY: unknown",
                         "ALWAYS_USE_RELAY: true\nALWAYS_USE_RELAY: false"):
            with self.subTest(response=response), self.assertRaises(subject.NativeServerError):
                subject.parse_mode(response)

    def test_only_exact_local_console_commands_are_accepted(self):
        with mock.patch.object(subject, "compose_exec", return_value="ALWAYS_USE_RELAY: false") as exe:
            subject.console("aur")
            self.assertEqual(exe.call_count, 1)
            self.assertIn("127.0.0.1 21115", exe.call_args.args[0])
            with self.assertRaises(subject.NativeServerError):
                subject.console("aur Y; rm -rf /")
            with self.assertRaises(subject.NativeServerError):
                subject.console("h")

    def test_requires_ci_opt_in_before_any_docker_call(self):
        with mock.patch.dict(os.environ, {"GITHUB_ACTIONS": "false", "LANVERIQ_RUSTDESK_CI_BEHAVIOR_TEST": "1"}):
            with mock.patch.object(subject, "run") as runner:
                with self.assertRaises(subject.NativeServerError):
                    subject.check_isolated_ci_only()
                runner.assert_not_called()

    def test_private_key_permission_fail_closed(self):
        with mock.patch.object(subject, "compose_exec", return_value="644"):
            with self.assertRaises(subject.NativeServerError):
                subject.assert_private_key_permissions()
        with mock.patch.object(subject, "compose_exec", return_value="600"):
            subject.assert_private_key_permissions()

    def test_public_key_hash_has_strict_shape(self):
        good = "a" * 64 + "  /data/id_ed25519.pub"
        with mock.patch.object(subject, "compose_exec", return_value=good):
            self.assertEqual(subject.public_key_hash(), "a" * 64)
        with mock.patch.object(subject, "compose_exec", return_value="NOT VALID"):
            with self.assertRaises(subject.NativeServerError):
                subject.public_key_hash()

    def test_refuses_to_mark_absent_mode_as_ready(self):
        with mock.patch.object(subject, "mode", side_effect=subject.NativeServerError("not ready")):
            with mock.patch.object(subject.time, "sleep"):
                with self.assertRaises(subject.NativeServerError):
                    subject.await_mode(True, retries=2)


if __name__ == "__main__":
    main()
