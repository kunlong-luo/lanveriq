"""Offline negative regressions for source-only developer preview boundaries."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

source = Path(__file__).with_name("verify_source_preview.py")
spec = importlib.util.spec_from_file_location("preview_gate", source)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

GOOD = {
    "schema_version": 1, "candidate_version": "v0.1.0-dev.1",
    "channel": "developer_preview_source_only",
    "publication_state": "NOT_PUBLISHED",
    "production_release_state": "NOT_RELEASE_READY",
    "distribution": "github_generated_source_archives_only",
    "release_creation_authorized": False, "approved_publisher_signer": False,
    "independent_apt_highwater_approved": False,
    "trusted_release_clock_approved": False,
    "privileged_polkit_install_approved": False,
    "physical_popos_l3_approved": False,
    "attached_assets": [], "installable_agent_packages": [],
    "production_switches": dict.fromkeys(gate.SWITCHES, False),
    "gates": dict.fromkeys(gate.GATES, "OPEN"),
    "verified_synthetic_evidence": {
        "functional_sha": "db570181d603a7f069a85a23c009658e9a8bed4c",
        "linux_l2_run_id": 45554279736,
        "agent_l1_run_id": 45554279741,
        "environment": "disposable_ubuntu_24_04_only",
    },
}


class SourceOnlyPreviewTests(unittest.TestCase):
    def test_exact_source_only_candidate_passes_without_network(self):
        gate.validate_contract(copy.deepcopy(GOOD))

    def test_no_stable_release_or_fake_published_preview(self):
        for key, value in (
            ("candidate_version", "v0.1.0"),
            ("candidate_version", "v0.1.0-dev.2"),
            ("publication_state", "PUBLISHED"),
            ("production_release_state", "READY"),
            ("distribution", "installable_agent_deb"),
            ("channel", "production"),
        ):
            with self.subTest(key=key, value=value):
                bad = copy.deepcopy(GOOD)
                bad[key] = value
                with self.assertRaises(gate.PreviewRejected):
                    gate.validate_contract(bad)

    def test_unauthorized_signer_highwater_time_polkit_and_l3_denied(self):
        for key in (
            "release_creation_authorized", "approved_publisher_signer",
            "independent_apt_highwater_approved",
            "trusted_release_clock_approved",
            "privileged_polkit_install_approved", "physical_popos_l3_approved",
        ):
            with self.subTest(key=key):
                bad = copy.deepcopy(GOOD)
                bad[key] = True
                with self.assertRaises(gate.PreviewRejected):
                    gate.validate_contract(bad)

    def test_no_deb_or_gui_installer_can_be_attached(self):
        for key, value in (
            ("attached_assets", ["lanveriq-agent_0.1.0_amd64.deb"]),
            ("attached_assets", ["/tmp/lanveriq-desktop.AppImage"]),
            ("attached_assets", "no assets"),
            ("installable_agent_packages", ["agent.rpm"]),
            ("installable_agent_packages", ["agent.deb"]),
        ):
            with self.subTest(key=key):
                bad = copy.deepcopy(GOOD)
                bad[key] = value
                with self.assertRaises(gate.PreviewRejected):
                    gate.validate_contract(bad)

    def test_source_contract_never_enables_a_production_feature(self):
        for key in gate.SWITCHES:
            for value in (True, 1, "false"):
                with self.subTest(key=key, value=value):
                    bad = copy.deepcopy(GOOD)
                    bad["production_switches"][key] = value
                    with self.assertRaises(gate.PreviewRejected):
                        gate.validate_contract(bad)

    def test_all_ten_release_gates_remain_open(self):
        for key in gate.GATES:
            bad = copy.deepcopy(GOOD)
            bad["gates"][key] = "APPROVED"
            with self.subTest(key=key), self.assertRaises(gate.PreviewRejected):
                gate.validate_contract(bad)
        for key in ("G-05", "G-10"):
            bad = copy.deepcopy(GOOD)
            del bad["gates"][key]
            with self.assertRaises(gate.PreviewRejected):
                gate.validate_contract(bad)

    def test_unknown_keys_and_type_confusion_are_rejected(self):
        for key, value in (
            ("schema_version", True),
            ("schema_version", "1"),
            ("release_creation_authorized", 0),
            ("approved_publisher_signer", "false"),
        ):
            with self.subTest(key=key, value=value):
                bad = copy.deepcopy(GOOD)
                bad[key] = value
                with self.assertRaises(gate.PreviewRejected):
                    gate.validate_contract(bad)
        bad = copy.deepcopy(GOOD)
        bad["publisher_private_key"] = "secret"
        with self.assertRaises(gate.PreviewRejected):
            gate.validate_contract(bad)

    def test_synthetic_evidence_is_not_real_device_approval(self):
        for key, value in (
            ("functional_sha", "db570181"),
            ("linux_l2_run_id", True),
            ("linux_l2_run_id", 0),
            ("agent_l1_run_id", "45554279741"),
            ("environment", "real_popos24_approved"),
        ):
            with self.subTest(key=key):
                bad = copy.deepcopy(GOOD)
                bad["verified_synthetic_evidence"][key] = value
                with self.assertRaises(gate.PreviewRejected):
                    gate.validate_contract(bad)

    def test_checked_in_repository_contract(self):
        self.assertEqual(gate.verify_repository(gate.REPO), GOOD)

    def test_checkout_sha_binding_denies_mismatch(self):
        with patch.dict("os.environ", {"GITHUB_SHA": "0" * 40}):
            with self.assertRaises(gate.PreviewRejected):
                gate.git_commit_sha(gate.REPO)

    def test_info_manifest_cannot_be_a_release_signature(self):
        manifest = gate.build_preview_manifest(gate.REPO, GOOD)
        self.assertEqual(manifest["candidate_version"], "v0.1.0-dev.1")
        self.assertEqual(manifest["attached_assets"], [])
        self.assertFalse(manifest["official_release_authorized"])
        self.assertRegex(manifest["source_commit_sha"], r"^[0-9a-f]{40}$")


if __name__ == "__main__":
    unittest.main()
