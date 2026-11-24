"""L2B-17: adversarial source-only install-intent protocol tests; never run polkit."""
import copy
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_agent_install_intent", HERE / "verify_desktop_agent_install_intent.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)
FIXTURE = HERE.parents[2] / "docs/planning/desktop-agent-install-intent-deny-all.json"


class DesktopAgentInstallIntentDenyAllTests(unittest.TestCase):
    def setUp(self):
        self.data = guard.read_intent(FIXTURE)

    def change(self, mutate, error):
        changed = copy.deepcopy(self.data)
        mutate(changed)
        with self.assertRaisesRegex((ValueError, TypeError), error):
            guard.validate_intent(changed)

    def test_valid_schema_is_still_denied_and_not_an_approval(self):
        self.assertEqual(guard.validate_intent(self.data), guard.DENIED)
        self.assertIn("DENIED", guard.DENIED)

    def test_reject_privileged_install_action(self):
        self.change(lambda x: x.update(action="install-agent"), "cannot invoke privileged")

    def test_reject_shell_action(self):
        self.change(lambda x: x.update(action="/bin/sh -c"), "cannot invoke privileged")

    def test_reject_polkit_action(self):
        self.change(lambda x: x.update(action="org.lanveriq.install"), "cannot invoke privileged")

    def test_reject_dynamic_unit_name(self):
        self.change(lambda x: x.update(action="systemctl enable lanveriq-agent"),
                    "cannot invoke privileged")

    def test_reject_package_identity_drift(self):
        self.change(lambda x: x["agent_package"].update(name="systemd"), "package identity")

    def test_reject_unicode_confusable_package_name(self):
        self.change(lambda x: x["agent_package"].update(name="lаnveriq-agent"),
                    "package identity")

    def test_reject_numeric_package_name(self):
        self.change(lambda x: x["agent_package"].update(name=12345), "package identity")

    def test_reject_unapproved_version(self):
        self.change(lambda x: x["agent_package"].update(version="0.1.0"),
                    "no signed or distributable")

    def test_reject_fake_sha256_as_release_package(self):
        self.change(lambda x: x["agent_package"].update(sha256="a" * 64),
                    "no signed or distributable")

    def test_reject_local_deb_path(self):
        self.change(lambda x: x["agent_package"].update(source="../../agent.deb"),
                    "no signed or distributable")

    def test_reject_network_package_url(self):
        self.change(lambda x: x["agent_package"].update(
            source="https://example.invalid/agent.deb"), "no signed or distributable")

    def test_reject_extra_package_arg(self):
        self.change(lambda x: x["agent_package"].update(deb_path="/tmp/agent.deb"),
                    "unknown or missing package detail")

    def test_reject_cross_os_execution(self):
        self.change(lambda x: x.update(host_target="ubuntu-24.04"),
                    "unsupported target host")

    def test_reject_forged_install_opt_in(self):
        self.change(lambda x: x.update(install_opt_in=True), "install_opt_in")

    def test_reject_forged_boot_opt_in(self):
        self.change(lambda x: x.update(boot_enable_opt_in=True), "boot_enable_opt_in")

    def test_reject_forged_service_start_opt_in(self):
        self.change(lambda x: x.update(service_start_opt_in=True),
                    "service_start_opt_in")

    def test_reject_forged_privacy_notice_ack(self):
        self.change(lambda x: x.update(notice_ack=True), "notice_ack")

    def test_reject_forged_network_opt_in(self):
        self.change(lambda x: x.update(network_opt_in=True), "network_opt_in")

    def test_reject_forged_collection_opt_in(self):
        self.change(lambda x: x.update(collection_opt_in=True), "collection_opt_in")

    def test_reject_forged_remote_control_opt_in(self):
        self.change(lambda x: x.update(remote_control_opt_in=True),
                    "remote_control_opt_in")

    def test_reject_numeric_false_coercion(self):
        self.change(lambda x: x.update(install_opt_in=0), "install_opt_in")

    def test_reject_untrusted_signature(self):
        self.change(lambda x: x.update(publisher_signature="signed-by-ci"),
                    "publisher_signature")

    def test_reject_forged_external_release_approval(self):
        self.change(lambda x: x.update(external_approvals={"ok": True}),
                    "external_approvals")

    def test_reject_forged_polkit_grant(self):
        self.change(lambda x: x.update(polkit_grant=True), "polkit_grant")

    def test_reject_forged_artifact_attestation(self):
        self.change(lambda x: x.update(authorized_artifact="test-sha"),
                    "authorized_artifact")

    def test_reject_arbitrary_shell_arguments(self):
        self.change(lambda x: x.update(request_arguments=["/bin/sh", "-c", "id"]),
                    "arbitrary command")

    def test_reject_systemd_unit_arguments(self):
        self.change(lambda x: x.update(request_arguments=["--unit", "lanveriq-agent"]),
                    "arbitrary command")

    def test_reject_path_traversal_arguments(self):
        self.change(lambda x: x.update(request_arguments=["../../etc/shadow"]),
                    "arbitrary command")

    def test_reject_string_argument_container(self):
        self.change(lambda x: x.update(request_arguments=""), "arbitrary command")

    def test_reject_unknown_top_level_field(self):
        self.change(lambda x: x.update(root=True), "missing or unknown")

    def test_reject_missing_top_level_field(self):
        self.change(lambda x: x.pop("notice_ack"), "missing or unknown")

    def test_reject_changed_schema(self):
        self.change(lambda x: x.update(schema="lanveriq.desktop.release.AUTHORIZED"),
                    "unsupported install-intent schema")

    def test_reject_nonobject_intent(self):
        with self.assertRaisesRegex(ValueError, "root must be an object"):
            guard.validate_intent([])

    def test_reject_symlink_intent(self):
        with tempfile.TemporaryDirectory() as directory:
            src = pathlib.Path(directory) / "intent.json"
            sym = pathlib.Path(directory) / "link.json"
            src.write_text(json.dumps(self.data))
            sym.symlink_to(src)
            with self.assertRaisesRegex(ValueError, "not a regular file"):
                guard.read_intent(sym)

    def test_reject_duplicate_json_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            src = pathlib.Path(directory) / "intent.json"
            src.write_text('{"action":"good","action":"bad"}')
            with self.assertRaisesRegex(ValueError, "duplicate install-intent field"):
                guard.read_intent(src)

    def test_reject_nonstandard_nan(self):
        with tempfile.TemporaryDirectory() as directory:
            src = pathlib.Path(directory) / "intent.json"
            src.write_text('{"install_opt_in":NaN}')
            with self.assertRaisesRegex(ValueError, "nonstandard"):
                guard.read_intent(src)

    def test_reject_oversized_intent(self):
        with tempfile.TemporaryDirectory() as directory:
            src = pathlib.Path(directory) / "intent.json"
            src.write_bytes(b" " * (guard.MAX_BYTES + 1))
            with self.assertRaisesRegex(ValueError, "size invalid"):
                guard.read_intent(src)

    def test_reject_missing_intent(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError):
                guard.read_intent(pathlib.Path(directory) / "missing.json")


if __name__ == "__main__":
    unittest.main()
