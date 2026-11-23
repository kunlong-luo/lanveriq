"""G05-LNX3 real-systemd property parser negatives with synthetic inputs only.

Never runs systemctl, dpkg installation or touches host service units.
"""
import importlib.util
import pathlib
import unittest

HERE = pathlib.Path(__file__).resolve().parent
MODULE = HERE / "verify-installed-agent-idle.py"
spec = importlib.util.spec_from_file_location("installed_idle_audit", MODULE)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def baseline():
    values = dict(audit.EXPECTED)
    values.update({
        "FragmentPath": "/usr/lib/systemd/system/lanveriq-agent.service",
        "ExecStart": (
            "{ path=/usr/bin/lanveriq-agent ; "
            "argv[]=/usr/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml ; "
            "ignore_errors=no ; start_time=[n/a] ; stop_time=[n/a] ; pid=0 ; code=(null) ; status=0/0 }"
        ),
        "StateDirectoryMode": "0700",
    })
    return values


def serialize(data):
    return "".join(f"{k}={v}\n" for k, v in data.items())


class InstalledIdleAuditNegativeTest(unittest.TestCase):
    def assert_refused(self, data, keyword):
        with self.assertRaises(audit.AuditFailure) as raised:
            audit.audit_properties(serialize(data))
        self.assertIn(keyword, str(raised.exception))

    def test_full_synthetic_disabled_inactive_unit_parses(self):
        result = audit.audit_properties(serialize(baseline()))
        self.assertEqual(result["ActiveState"], "inactive")

    def test_alternative_safe_mode_serialization_is_accepted(self):
        data = baseline()
        data["StateDirectoryMode"] = "700"
        self.assertEqual(audit.audit_properties(serialize(data))["StateDirectoryMode"], "700")

    def test_active_or_failed_agent_is_rejected(self):
        for state in ("active", "activating", "failed", "reloading"):
            with self.subTest(state=state):
                data = baseline()
                data["ActiveState"] = state
                self.assert_refused(data, "ActiveState")

    def test_enabled_or_masked_unit_is_rejected(self):
        for state in ("enabled", "enabled-runtime", "masked", "static", ""):
            with self.subTest(state=state):
                data = baseline()
                data["UnitFileState"] = state
                self.assert_refused(data, "UnitFileState")

    def test_any_drop_in_even_if_apparently_harmless_is_rejected(self):
        data = baseline()
        data["DropInPaths"] = "/etc/systemd/system/lanveriq-agent.service.d/override.conf"
        self.assert_refused(data, "DropInPaths")

    def test_stale_systemd_unit_cache_is_rejected(self):
        data = baseline()
        data["NeedDaemonReload"] = "yes"
        self.assert_refused(data, "NeedDaemonReload")

    def test_network_or_privilege_relaxation_rejected(self):
        changes = {
            "PrivateNetwork": "no",
            "IPAddressDeny": "",
            "IPAddressAllow": "any",
            "SocketBindDeny": "",
            "RestrictAddressFamilies": "AF_UNIX AF_INET",
            "CapabilityBoundingSet": "CAP_NET_ADMIN",
            "AmbientCapabilities": "CAP_SYS_ADMIN",
            "DynamicUser": "no",
            "NoNewPrivileges": "no",
            "ProtectSystem": "full",
            "PrivateDevices": "no",
            "ProtectHome": "no",
        }
        for key, value in changes.items():
            with self.subTest(key=key):
                data = baseline()
                data[key] = value
                self.assert_refused(data, key)

    def test_wrong_executable_or_shell_wrapping_is_rejected(self):
        for value in (
            "{ path=/bin/sh ; argv[]=/bin/sh -c /usr/bin/lanveriq-agent ; pid=0 }",
            "{ path=/usr/bin/lanveriq-agent ; argv[]=/usr/bin/lanveriq-agent --config /tmp/agent.toml ; pid=0 }",
            "/usr/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml",
            "{ path=/usr/bin/lanveriq-agent ; argv[]=/usr/bin/lanveriq-agent --config /etc/lanveriq-agent/agent.toml --network ; pid=0 }",
        ):
            with self.subTest(value=value):
                data = baseline()
                data["ExecStart"] = value
                self.assert_refused(data, "ExecStart")

    def test_wrong_unit_path_rejected(self):
        data = baseline()
        data["FragmentPath"] = "/etc/systemd/system/lanveriq-agent.service"
        self.assert_refused(data, "FragmentPath")

    def test_state_directory_cannot_be_public(self):
        for mode in ("0755", "0777", "0o700", "", "0000"):
            with self.subTest(mode=mode):
                data = baseline()
                data["StateDirectoryMode"] = mode
                self.assert_refused(data, "StateDirectoryMode")

    def test_missing_or_duplicate_properties_rejected(self):
        data = baseline()
        data.pop("PrivateNetwork")
        self.assert_refused(data, "missing systemctl properties")
        output = serialize(baseline()) + "PrivateNetwork=no\n"
        with self.assertRaisesRegex(audit.AuditFailure, "duplicate systemctl property"):
            audit.audit_properties(output)

    def test_unknown_systemctl_property_rejected(self):
        output = serialize(baseline()) + "EnvExtra=production\n"
        with self.assertRaisesRegex(audit.AuditFailure, "unexpected systemctl property"):
            audit.audit_properties(output)

    def test_main_has_no_mock_pass_mode_or_write_actions(self):
        code = MODULE.read_text(encoding="utf-8")
        for banned in (
            "systemctl start", "systemctl enable", "dpkg --install",
            "subprocess.Popen", "sudo ", "curl ",
        ):
            self.assertNotIn(banned, code)
        self.assertIn("NOT Pop!_OS L3 sign-off", code)

    def test_extra_command_args_are_rejected(self):
        # main is hard-coded to canonical unit / package, no --unit override.
        import sys
        previous = sys.argv
        try:
            sys.argv = [str(MODULE), "--fake-systemctl"]
            self.assertEqual(audit.main(), 2)
        finally:
            sys.argv = previous


if __name__ == "__main__":
    unittest.main()
