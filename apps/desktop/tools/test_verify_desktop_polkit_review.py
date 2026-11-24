"""L2B-22 malicious Polkit default/action mutations, never a live policy test."""
import importlib.util
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "desktop_polkit_review", HERE / "verify_desktop_polkit_review.py"
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
ORIGINAL = guard.POLICY.read_bytes()


class InertPolkitReviewTests(unittest.TestCase):
    def mutation(self, before, after, error):
        self.assertGreaterEqual(ORIGINAL.count(before), 1, "missing fixture anchor")
        data = ORIGINAL.replace(before, after, 1)
        with self.assertRaisesRegex(ValueError, error):
            guard.validate_xml(data)

    def test_original_actions_are_still_all_denied(self):
        self.assertEqual(guard.validate_xml(ORIGINAL), guard.RESULT)
        self.assertEqual(guard.validate_folder(), guard.RESULT)
        self.assertEqual(guard.validate_context(), guard.RESULT)

    def test_exact_three_distinct_operations_exist(self):
        self.assertEqual(set(guard.ACTIONS), {"install", "upgrade", "remove"})
        for operation in guard.ACTIONS:
            self.assertEqual(
                ORIGINAL.count(
                    f'<action id="org.lanveriq.agent.package.{operation}">'.encode()
                ), 1,
            )
        self.assertNotIn(b"allow_gui", ORIGINAL)

    def test_reject_allow_any_yes(self):
        self.mutation(b"<allow_any>no</allow_any>",
                      b"<allow_any>yes</allow_any>", "grant or cached")

    def test_reject_allow_inactive_admin(self):
        self.mutation(b"<allow_inactive>no</allow_inactive>",
                      b"<allow_inactive>auth_admin</allow_inactive>", "grant or cached")

    def test_reject_allow_active_yes(self):
        self.mutation(b"<allow_active>no</allow_active>",
                      b"<allow_active>yes</allow_active>", "grant or cached")

    def test_reject_cached_admin_authorization(self):
        self.mutation(b"<allow_active>no</allow_active>",
                      b"<allow_active>auth_admin_keep</allow_active>", "grant or cached")

    def test_reject_cached_self_authorization(self):
        self.mutation(b"<allow_active>no</allow_active>",
                      b"<allow_active>auth_self_keep</allow_active>", "grant or cached")

    def test_reject_self_authentication(self):
        self.mutation(b"<allow_active>no</allow_active>",
                      b"<allow_active>auth_self</allow_active>", "grant or cached")

    def test_reject_active_admin_authentication(self):
        self.mutation(b"<allow_active>no</allow_active>",
                      b"<allow_active>auth_admin</allow_active>", "grant or cached")

    def test_reject_extra_action_even_if_no(self):
        self.mutation(
            b"</policyconfig>",
            b'<action id="org.lanveriq.agent.package.start"><description>start</description>'
            b'<message>start</message><defaults><allow_any>no</allow_any>'
            b'<allow_inactive>no</allow_inactive><allow_active>no</allow_active>'
            b'</defaults></action>\n</policyconfig>',
            "unexpected policy action",
        )

    def test_reject_action_name_confusion(self):
        self.mutation(b"org.lanveriq.agent.package.install",
                      b"org.lanveriq.agent.package.install-all", "unknown, missing")

    def test_reject_action_namespace_spoof(self):
        self.mutation(b"org.lanveriq.agent.package.install",
                      b"org.freedesktop.policykit.exec", "unreviewed Polkit namespace")

    def test_reject_duplicate_action_identity(self):
        self.mutation(b"org.lanveriq.agent.package.upgrade",
                      b"org.lanveriq.agent.package.install", "duplicate privileged action")

    def test_reject_polkit_meta_action_imply(self):
        self.mutation(
            b"<defaults>",
            b'<annotate key="org.freedesktop.policykit.imply">'
            b'org.freedesktop.policykit.exec</annotate><defaults>',
            "unexpected action annotation",
        )

    def test_reject_pkexec_program_override(self):
        self.mutation(
            b"<defaults>",
            b'<annotate key="org.freedesktop.policykit.exec.path">'
            b'/bin/sh</annotate><defaults>',
            "unexpected action annotation",
        )

    def test_reject_generic_shell_argv_override(self):
        self.mutation(
            b"<defaults>",
            b'<annotate key="org.freedesktop.policykit.exec.argv1">'
            b'/bin/sh</annotate><defaults>',
            "unexpected action annotation",
        )

    def test_reject_extra_default_permission_field(self):
        self.mutation(b"</defaults>", b"<allow_remote>yes</allow_remote></defaults>",
                      "missing or unexpected Polkit default")

    def test_reject_missing_active_permission(self):
        self.mutation(b"      <allow_active>no</allow_active>\n", b"",
                      "missing or unexpected Polkit default")

    def test_reject_wrong_publisher(self):
        self.mutation(b"<vendor>Lanveriq</vendor>", b"<vendor>Attacker</vendor>",
                      "publisher label")

    def test_reject_prompt_parameter_injection(self):
        self.mutation(b"Lanveriq Agent install is not approved",
                      b"$(command_line) is approved", "action text/identity mismatch")

    def test_reject_unreviewed_action_attributes(self):
        self.mutation(b'<action id="org.lanveriq.agent.package.install">',
                      b'<action id="org.lanveriq.agent.package.install" implicit="yes">',
                      "unexpected action attributes")

    def test_reject_wrong_policy_root(self):
        altered = ORIGINAL.replace(b"<policyconfig>", b"<polkit>", 1).replace(
            b"</policyconfig>", b"</polkit>", 1
        )
        with self.assertRaisesRegex(ValueError, "unsupported Polkit root"):
            guard.validate_xml(altered)

    def test_reject_doctype(self):
        self.mutation(
            b"<policyconfig>", b'<!DOCTYPE policyconfig [<!ENTITY a "allowed">]>'
            b"\n<policyconfig>", "DTD declarations forbidden")

    def test_reject_external_entity_declaration(self):
        self.mutation(
            b"<policyconfig>",
            b'<!DOCTYPE policyconfig [<!ENTITY a SYSTEM "file:///etc/shadow">]>'
            b"\n<policyconfig>",
            "DTD declarations forbidden",
        )

    def test_reject_malformed_xml(self):
        self.mutation(b"</policyconfig>", b"</broken>", "invalid Polkit XML")

    def test_reject_missing_xml_header(self):
        with self.assertRaisesRegex(ValueError, "reviewed UTF-8 XML header"):
            guard.validate_xml(ORIGINAL.split(b"\n", 1)[1])

    def test_reject_invalid_binary_or_oversized_input(self):
        for payload in [b"", b"0" * 8192]:
            with self.assertRaisesRegex(ValueError, "size invalid"):
                guard.validate_xml(payload)

    def test_reject_policy_symlink_and_unreviewed_executable(self):
        with tempfile.TemporaryDirectory(prefix="lanveriq-polkit-review-") as tmp:
            folder = pathlib.Path(tmp)
            (folder / "README.md").write_text("offline only")
            source = folder / "policy-real"
            source.write_bytes(ORIGINAL)
            (folder / guard.POLICY.name).symlink_to(source)
            with self.assertRaisesRegex(ValueError, "unreviewed policy helper"):
                guard.validate_folder(folder)
            source.unlink()
            (folder / guard.POLICY.name).unlink()
            policy = folder / guard.POLICY.name
            policy.write_bytes(ORIGINAL)
            self.assertEqual(guard.validate_folder(folder), guard.RESULT)
            policy.chmod(0o755)
            with self.assertRaisesRegex(ValueError, "must not be executable"):
                guard.validate_folder(folder)

    def test_reject_rules_and_broker_artifacts_even_if_inert_policy_stays(self):
        with tempfile.TemporaryDirectory(prefix="lanveriq-polkit-review-") as tmp:
            folder = pathlib.Path(tmp)
            (folder / "README.md").write_text("offline only")
            (folder / guard.POLICY.name).write_bytes(ORIGINAL)
            for filename in ("99-allow-root.rules", "install-agent.sh",
                             "org.lanveriq.Agent.service", "pkexec-helper"):
                test_file = folder / filename
                test_file.write_text("not allowed")
                with self.assertRaisesRegex(ValueError, "unreviewed policy helper"):
                    guard.validate_folder(folder)
                test_file.unlink()

    def test_context_refuses_native_bundle_and_privileged_command(self):
        with tempfile.TemporaryDirectory(prefix="lanveriq-polkit-context-") as tmp:
            root = pathlib.Path(tmp)
            t = root / "apps/desktop/src-tauri"
            (t / "src").mkdir(parents=True)
            (t / "tauri.conf.json").write_text(
                '{"bundle":{"active":false,"targets":[],"linux":{"deb":{}}}}'
            )
            native = t / "src/lib.rs"
            native.write_text(
                "#[tauri::command]\nfn inspect_host() {}\n"
                "#[tauri::command]\nfn inspect_popos_preflight() {}"
            )
            self.assertEqual(guard.validate_context(root), guard.RESULT)
            native.write_text(native.read_text() + '\nstd::process::Command::new("pkexec");')
            with self.assertRaisesRegex(ValueError, "privileged broker"):
                guard.validate_context(root)
            native.write_text(native.read_text().replace("pkexec", "inspect"))
            (t / "tauri.conf.json").write_text(
                '{"bundle":{"active":true,"targets":["deb"],"linux":{"deb":{}}}}'
            )
            with self.assertRaisesRegex(ValueError, "installer bundle"):
                guard.validate_context(root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
