"""Negative mutation tests for the offline Pop!_OS systemd policy gate.

Every invocation runs the checker as a subprocess, including python -O.
Never installs, starts or connects a service; all units are disposable files.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


HERE = Path(__file__).resolve().parent
CHECKER = HERE / "verify-systemd-popos24.py"
UNIT = HERE.parents[1] / "deploy" / "systemd" / "lanveriq-agent.service"


class OfflineSystemdPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = UNIT.read_text(encoding="utf-8")

    def invoke(self, content, optimized=False):
        with tempfile.TemporaryDirectory(prefix="lanveriq-systemd-policy-") as folder:
            path = Path(folder) / "lanveriq-agent.service"
            path.write_text(content, encoding="utf-8")
            command = [sys.executable, "-O"] if optimized else [sys.executable]
            return subprocess.run(
                command + [str(CHECKER), str(path)],
                text=True, capture_output=True, check=False,
            )

    def denied(self, content, reason, optimized=True):
        result = self.invoke(content, optimized=optimized)
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn(reason, result.stderr + result.stdout)

    def change(self, name, new):
        old = next(
            line for line in self.template.splitlines() if line.startswith(name + "=")
        )
        return self.template.replace(old, f"{name}={new}", 1)

    def test_baseline_is_pass_under_normal_and_optimized_python(self):
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                run = self.invoke(self.template, optimized=optimized)
                self.assertEqual(run.returncode, 0, run.stderr)
                self.assertIn("static idle-only", run.stdout)
                self.assertIn("not an installed-unit", run.stdout)

    def test_python_optimization_cannot_disable_network_isolation(self):
        for name, altered in (
            ("PrivateNetwork", "no"),
            ("IPAddressDeny", "localhost"),
            ("SocketBindDeny", ""),
            ("RestrictAddressFamilies", "AF_UNIX AF_INET"),
            ("DynamicUser", "no"),
            ("NoNewPrivileges", "no"),
            ("CapabilityBoundingSet", "CAP_NET_ADMIN"),
            ("AmbientCapabilities", "CAP_NET_RAW"),
            ("ProtectSystem", "no"),
            ("StateDirectoryMode", "0755"),
            ("UMask", "0022"),
            ("PrivateDevices", "no"),
            ("ProtectHome", "no"),
            ("RestrictNamespaces", "no"),
            ("MemoryDenyWriteExecute", "no"),
        ):
            with self.subTest(setting=name):
                self.denied(self.change(name, altered), name)

    def test_unexpected_extra_service_directives_are_rejected_even_under_optimized_python(self):
        for extra in (
            "IPAddressAllow=any",
            "ExecStartPre=/usr/bin/true",
            "EnvironmentFile=/etc/default/lanveriq-agent",
            "User=root",
            "RootDirectory=/",
            "JoinsNamespaceOf=networking.service",
            "LoadCredential=token:/etc/shadow",
            "ReadWritePaths=/",
            "PermissionsStartOnly=true",
        ):
            with self.subTest(extra=extra):
                mutated = self.template.replace("\n[Install]", "\n" + extra + "\n[Install]")
                self.denied(mutated, "unexpected")

    def test_extra_service_trigger_or_socket_section_is_rejected(self):
        self.denied(self.template + "\n[Socket]\nListenStream=127.0.0.1:1234\n", "sections")

    def test_unexpected_network_dependency_is_rejected(self):
        mutated = self.template.replace("\n[Service]", "\nAfter=network-online.target\n[Service]")
        self.denied(mutated, "unexpected")

    def test_service_command_cannot_be_replaced(self):
        mutated = self.change(
            "ExecStart", "/bin/sh -c 'echo synthetic-should-not-run'"
        )
        self.denied(mutated, "ExecStart")

    def test_start_or_reboot_behavior_cannot_be_relaxed(self):
        for name, altered in (
            ("Restart", "always"),
            ("RestartSec", "0s"),
            ("TimeoutStopSec", "0s"),
        ):
            with self.subTest(setting=name):
                self.denied(self.change(name, altered), name)

    def test_unit_condition_cannot_be_removed(self):
        self.denied(self.change("ConditionPathExists", "/etc/passwd"), "ConditionPathExists")

    def test_install_target_cannot_be_redirected(self):
        self.denied(self.change("WantedBy", "default.target"), "WantedBy")

    def test_duplicate_directives_rejected(self):
        mutated = self.template.replace(
            "PrivateNetwork=yes", "PrivateNetwork=yes\nPrivateNetwork=no"
        )
        self.denied(mutated, "malformed")

    def test_defaults_inheritance_is_rejected(self):
        self.denied(self.template + "\n[DEFAULT]\nPrivateNetwork=no\n", "[DEFAULT]")

    def test_missing_unit_section_is_rejected(self):
        self.denied(self.template.replace("[Install]", "[NotInstall]"), "sections")

    def test_missing_protected_directive_is_rejected(self):
        self.denied(
            self.template.replace("IPAddressDeny=any\n", ""),
            "IPAddressDeny",
        )

    def test_missing_file_must_fail_closed(self):
        result = subprocess.run(
            [sys.executable, "-O", str(CHECKER),
             "/this-file-must-not-exist/lanveriq-agent.service"],
            text=True, capture_output=True, check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unreadable", result.stderr + result.stdout)

    def test_invalid_arguments_must_fail_closed(self):
        result = subprocess.run(
            [sys.executable, "-O", str(CHECKER)],
            text=True, capture_output=True, check=False,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("usage", result.stderr + result.stdout)

    def test_all_security_predicates_run_with_optimized_mode(self):
        # python -O used to eliminate every 'assert'. This gate must not use it.
        self.assertNotIn("\nassert ", CHECKER.read_text(encoding="utf-8"))
        self.assertNotIn("\n    assert ", CHECKER.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
