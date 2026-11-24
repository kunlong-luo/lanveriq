"""L2B-16 offline synthetic denial tests. No signing, install, user data or network."""
import copy
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_release_boundary", HERE / "verify_desktop_release_evidence_boundary.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class DesktopReleaseEvidenceBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.fixture = guard.read_evidence(guard.DEFAULT)

    def fail_case(self, update, expected):
        data = copy.deepcopy(self.fixture)
        update(data)
        with self.assertRaisesRegex(ValueError, expected):
            guard.validate(data)

    def test_current_repository_remains_not_release_ready(self):
        self.assertIn("NOT APPROVED", guard.validate(self.fixture))

    def test_reject_untrusted_release_ready_flag(self):
        self.fail_case(
            lambda d: d["release_authority"].update(v01_release_ready=True),
            "v01_release_ready",
        )

    def test_reject_forged_available_agent_installer(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                agent_one_click_installer_available=True
            ),
            "agent_one_click_installer_available",
        )

    def test_reject_distributable_artifact_claim(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                distributable_artifact_available=True
            ),
            "distributable_artifact_available",
        )

    def test_reject_production_install_approval(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                production_agent_install_authorized=True
            ),
            "production_agent_install_authorized",
        )

    def test_reject_production_network_enable(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                production_network_authorized=True
            ),
            "production_network_authorized",
        )

    def test_reject_production_collection_enable(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                production_collection_authorized=True
            ),
            "production_collection_authorized",
        )

    def test_reject_production_remote_control_enable(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                production_remote_control_authorized=True
            ),
            "production_remote_control_authorized",
        )

    def test_reject_production_certificate_issuance_enable(self):
        self.fail_case(
            lambda d: d["release_authority"].update(
                production_ca_issuance_authorized=True
            ),
            "production_ca_issuance_authorized",
        )

    def test_reject_unsupported_approval_gate(self):
        self.fail_case(
            lambda d: d["release_gates"].update(
                **{"trusted_publisher_signing": "APPROVED"}
            ),
            "trusted_publisher_signing",
        )

    def test_reject_polkit_install_claim(self):
        self.fail_case(
            lambda d: d["release_gates"].update(
                restricted_polkit_agent_install="PASSED"
            ),
            "restricted_polkit_agent_install",
        )

    def test_reject_forged_popos_physical_acceptance(self):
        self.fail_case(
            lambda d: d["release_gates"].update(
                **{"G-05_popos24_cosmic_physical_l3": "PASS"}
            ),
            "G-05_popos24_cosmic_physical_l3",
        )

    def test_reject_external_worm_claim(self):
        self.fail_case(
            lambda d: d["release_gates"].update(
                **{"G-02_independent_worm": "CLOSED"}
            ),
            "G-02_independent_worm",
        )

    def test_reject_forged_package_hash(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                deb_sha256="0" * 64
            ),
            "deb_sha256",
        )

    def test_reject_altered_ci_source(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                source_sha="a" * 40
            ),
            "source_sha",
        )

    def test_reject_wrong_attempt_bool_as_int(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                github_run_attempt=True
            ),
            "github_run_attempt",
        )

    def test_reject_forged_release_signature(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                signed_by_release_publisher=True
            ),
            "signed_by_release_publisher",
        )

    def test_reject_forged_upload(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                uploaded_for_distribution=True
            ),
            "uploaded_for_distribution",
        )

    def test_reject_forged_install(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                installed_during_audit=True
            ),
            "installed_during_audit",
        )

    def test_reject_preview_claim_of_popos_l3(self):
        self.fail_case(
            lambda d: d["desktop_native_preview"].update(
                popos_device_proven=True
            ),
            "popos_device_proven",
        )

    def test_reject_extra_release_gate(self):
        self.fail_case(
            lambda d: d["release_gates"].update(
                extra_approval="APPROVED"
            ),
            "missing or unknown",
        )

    def test_reject_missing_approval_flag(self):
        self.fail_case(
            lambda d: d["release_authority"].pop("v01_release_ready"),
            "unknown or missing",
        )

    def test_reject_extra_evidence_field(self):
        self.fail_case(
            lambda d: d.update(fake_signature="lab-signed"),
            "evidence root has missing or extra",
        )

    def test_reject_changed_oci_digest(self):
        self.fail_case(
            lambda d: d["gui_only_debian_l2"].update(
                oci_ubuntu24_digest="ubuntu@sha256:" + "f" * 64
            ),
            "oci_ubuntu24_digest",
        )

    def test_reject_wrong_evidence_date(self):
        self.fail_case(
            lambda d: d.update(evidence_date="2099-01-01"),
            "unreviewed evidence date",
        )

    def test_reject_duplicated_json_field(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "dup.json"
            path.write_text('{"schema":"first","schema":"second"}')
            with self.assertRaisesRegex(ValueError, "duplicate JSON key"):
                guard.read_evidence(path)

    def test_reject_symlink_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = pathlib.Path(tmp) / "safe.json"
            link = pathlib.Path(tmp) / "linked.json"
            target.write_text(json.dumps(self.fixture))
            link.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "regular, not symlink"):
                guard.read_evidence(link)

    def test_reject_missing_evidence_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(FileNotFoundError):
                guard.read_evidence(pathlib.Path(tmp) / "missing.json")

    def test_reject_excessive_evidence_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "too-long.json"
            path.write_text("x" * (guard.MAX_BYTES + 1))
            with self.assertRaisesRegex(ValueError, "size out of bounds"):
                guard.read_evidence(path)

    def test_reject_nonstandard_json_nan(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "nan.json"
            path.write_text('{"untrusted":NaN}')
            with self.assertRaisesRegex(ValueError, "non-standard JSON constant"):
                guard.read_evidence(path)


if __name__ == "__main__":
    unittest.main()
