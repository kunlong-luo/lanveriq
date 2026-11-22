"""C18i negative-only, fully invented offline WORM metadata; NO external WORM."""

import contextlib
import copy
import io
import json
import tempfile
import unittest
from pathlib import Path

from inspect_worm_acceptance_bundle import InvalidBundle, inspect, main, read_bundle


def invented_complete():
    domains = {
        "application": "test-app", "database": "test-db", "storage_api": "test-rgw",
        "storage_root": "test-rados-root", "backup": "test-backup",
        "witness_signer": "test-signing-vault", "high_water": "test-independent-journal",
        "recovery": "test-second-physical-custodian", "trusted_time": "test-clock",
        "reviewer": "test-independent-auditor",
    }
    graph_roots = [
        "test-app-team-root", "test-security-key-root", "test-journal-root",
        "test-recovery-root", "test-time-root", "test-review-root",
    ]
    graph_edges = [
        {"controller": graph_roots[0], "target": domains[role],
         "capability": "ROLE_ADMIN", "evidence_ref": f"role-review-{role}"}
        for role in ("application", "database", "storage_api", "storage_root", "backup")
    ] + [
        {"controller": graph_roots[index], "target": domains[role],
         "capability": "ROOT_ADMIN", "evidence_ref": f"role-review-{role}"}
        for index, role in enumerate(
            ("witness_signer", "high_water", "recovery", "trusted_time", "reviewer"), 1
        )
    ]
    surfaces = {
        name: "audit-" + name.replace("_", "-")
        for name in (
            "identity_provider", "break_glass", "host_root", "hypervisor",
            "backup_restore", "signing_key_admin", "high_water_admin",
            "recovery_custodian", "trusted_time", "rgw_admin", "cephx_mon",
            "cephx_mgr", "cephx_osd", "rados_pool_and_namespace",
        )
    }
    graph = {"principals": list(domains.values()) + graph_roots,
             "root_principals": graph_roots,
             "edges": graph_edges, "surfaces": surfaces,
             "inventory_ref": "independent-review-0001",
             "completeness_asserted": True}
    return {
        "schema": "lanveriq.worm-evidence-intake.v2",
        "environment": "DISPOSABLE_SYNTHETIC_LAB", "medium": "CEPH_WITH_EXTERNAL_CUSTODY",
        "functional_sha": "a" * 40,
        "authorization": {"ticket_ref": "LAB-0001", "scope_ref": "scope-0001",
                          "nonproduction_only": True, "destructive_scope_approved": True},
        "custody": {"domains": domains, "shared_root_or_break_glass": False,
                    "privilege_inventory_ref": "inventory-0001", "topology_ref": "topology-0001",
                    "key_custody_ref": "key-audit-0001", "time_attestation_ref": "time-audit-0001",
                    "control_graph": graph},
        "checkpoint": {"stream_id": "123e4567-e89b-42d3-a456-426614174000",
                       "previous_sequence": 4, "sequence": 5,
                       "previous_head_sha256": "b" * 64, "head_sha256": "c" * 64,
                       "witness_key_fingerprint_sha256": "d" * 64,
                       "readback_sha256": "e" * 64,
                       "primary_version": "primary-v05", "recovery_version": "remote-v05",
                       "external_publication_ref": "outside-ledger-0005",
                       "independent_time": "2026-11-22T20:00:00+08:00"},
        "tests": [{"id": f"WORM-{i:02d}", "status": "NOT_APPLICABLE" if i == 9 else "PASS",
                   "actor_role": f"lab-role-{i}", "evidence_ref": f"lab-proof-{i}",
                   "reviewer_ref": f"lab-review-{i}"} for i in range(1, 12)],
    }


class WormAcceptanceIntakeTest(unittest.TestCase):
    def setUp(self):
        self.bundle = invented_complete()

    def test_invented_complete_only_ready_for_review_never_passed(self):
        self.assertEqual(inspect(self.bundle), [])
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "synthetic-only.json"
            path.write_text(json.dumps(self.bundle), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = main([str(path)])
        self.assertEqual(code, 0)
        self.assertIn("READY_FOR_INDEPENDENT_REVIEW", output.getvalue())
        self.assertIn("G-02/G-08 OPEN", output.getvalue())
        self.assertNotIn("RELEASE READY", output.getvalue())

    def assert_incomplete(self, data, fragment):
        problems = inspect(data)
        self.assertTrue(problems, "must fail-closed")
        self.assertTrue(any(fragment in problem for problem in problems), problems)

    def test_common_root_or_break_glass_denied(self):
        self.bundle["custody"]["shared_root_or_break_glass"] = True
        self.assert_incomplete(self.bundle, "joint root")

    def test_underlying_ceph_root_equals_external_highwater_denied(self):
        d = self.bundle["custody"]["domains"]
        d["high_water"] = d["storage_root"]
        self.assert_incomplete(self.bundle, "shared administration")

    def test_ceph_api_and_root_distinction_not_ignored(self):
        d = self.bundle["custody"]["domains"]
        d["recovery"] = d["storage_api"]
        self.assert_incomplete(self.bundle, "shared administration")

    def test_shared_signer_and_recovery_denied(self):
        d = self.bundle["custody"]["domains"]
        d["witness_signer"] = d["recovery"]
        self.assert_incomplete(self.bundle, "distinct declared domains")

    def test_unauthorized_destructive_scope_denied(self):
        self.bundle["authorization"]["destructive_scope_approved"] = False
        self.assert_incomplete(self.bundle, "authorization")

    def test_production_scope_denied(self):
        self.bundle["environment"] = "PRODUCTION"
        self.assert_incomplete(self.bundle, "production/shared")

    def test_monotonic_rollback_denied(self):
        self.bundle["checkpoint"]["previous_sequence"] = 5
        self.assert_incomplete(self.bundle, "monotonic")

    def test_head_replay_denied(self):
        self.bundle["checkpoint"]["head_sha256"] = "b" * 64
        self.assert_incomplete(self.bundle, "must differ")

    def test_same_primary_recovery_version_denied(self):
        self.bundle["checkpoint"]["recovery_version"] = "primary-v05"
        self.assert_incomplete(self.bundle, "same copy")

    def test_missing_external_publication_ref_denied(self):
        self.bundle["checkpoint"]["external_publication_ref"] = ""
        self.assert_incomplete(self.bundle, "external_publication_ref")

    def test_stale_test_case_cannot_claim_review(self):
        self.bundle["tests"][3]["status"] = "NOT_RUN"
        self.assert_incomplete(self.bundle, "WORM-04")

    def test_duplicate_test_id_denied(self):
        self.bundle["tests"][3]["id"] = "WORM-03"
        self.assert_incomplete(self.bundle, "duplicate")

    def test_duplicate_proof_denied(self):
        self.bundle["tests"][3]["evidence_ref"] = self.bundle["tests"][2]["evidence_ref"]
        self.assert_incomplete(self.bundle, "same sole evidence")

    def test_non_lto_test_cannot_escape_with_not_applicable(self):
        self.bundle["tests"][1]["status"] = "NOT_APPLICABLE"
        self.assert_incomplete(self.bundle, "WORM-02 cannot")

    def test_lto_requires_physical_test(self):
        self.bundle["medium"] = "LTO_WORM"
        for key in ("rgw_admin", "cephx_mon", "cephx_mgr", "cephx_osd", "rados_pool_and_namespace"):
            del self.bundle["custody"]["control_graph"]["surfaces"][key]
        self.assert_incomplete(self.bundle, "WORM-09 cannot")

    def test_explicit_lto_physical_test_can_submit_for_review_only(self):
        self.bundle["medium"] = "LTO_WORM"
        for key in ("rgw_admin", "cephx_mon", "cephx_mgr", "cephx_osd", "rados_pool_and_namespace"):
            del self.bundle["custody"]["control_graph"]["surfaces"][key]
        self.bundle["tests"][8]["status"] = "PASS"
        self.assertEqual(inspect(self.bundle), [])

    def test_legacy_or_extra_schema_must_not_be_silently_accepted(self):
        self.bundle["approved"] = True
        self.assert_incomplete(self.bundle, "unknown")

    def test_boolean_sequence_is_not_an_integer(self):
        self.bundle["checkpoint"]["sequence"] = True
        self.assert_incomplete(self.bundle, "positive")

    def test_bad_timezone_and_hash_denied(self):
        for bad_time in ("2026-11-22T12:00:00", "2026-13-22T00:00:00Z"):
            with self.subTest(bad_time=bad_time):
                candidate = copy.deepcopy(self.bundle)
                candidate["checkpoint"]["independent_time"] = bad_time
                self.assert_incomplete(candidate, "independent_time")
        self.bundle["checkpoint"]["head_sha256"] = "X" * 64
        self.assert_incomplete(self.bundle, "lowercase")

    def test_duplicate_json_keys_strictly_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "duplicate.json"
            path.write_text('{"schema":"one","schema":"two"}', encoding="utf-8")
            with self.assertRaisesRegex(InvalidBundle, "duplicate JSON key"):
                read_bundle(path)

    def test_oversize_and_nonfinite_denied(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "large.json"
            path.write_bytes(b" " * (128 * 1024 + 1))
            with self.assertRaisesRegex(InvalidBundle, "oversized"):
                read_bundle(path)
            path.write_text('{"nan": NaN}', encoding="utf-8")
            with self.assertRaisesRegex(InvalidBundle, "non-JSON"):
                read_bundle(path)

    def test_missing_file_is_not_a_success(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main(["/nonexistent/worm-intake-should-not-exist.json"])
        self.assertEqual(code, 2)
        self.assertIn("INCOMPLETE", output.getvalue())


    def test_v1_schema_downgrade_rejected(self):
        self.bundle["schema"] = "lanveriq.worm-evidence-intake.v1"
        self.assert_incomplete(self.bundle, "unsupported schema")

    def test_shared_ultimate_superadmin_even_when_role_names_differ(self):
        g = self.bundle["custody"]["control_graph"]
        g["principals"].append("test-common-org-superadmin")
        g["root_principals"].append("test-common-org-superadmin")
        for target in ("test-app-team-root", "test-journal-root"):
            g["edges"].append({"controller": "test-common-org-superadmin",
                               "target": target, "capability": "ROOT_ADMIN",
                               "evidence_ref": "invented-superadmin-authorization"})
        self.assert_incomplete(self.bundle, "shared transitive control")

    def test_long_indirect_control_path_rejected(self):
        g = self.bundle["custody"]["control_graph"]
        g["principals"].extend(["test-administrator-jump1", "test-administrator-jump2"])
        g["edges"].extend([
            {"controller": "test-app-team-root", "target": "test-administrator-jump1",
             "capability": "IDENTITY_ADMIN", "evidence_ref": "jump-1"},
            {"controller": "test-administrator-jump1", "target": "test-administrator-jump2",
             "capability": "BREAK_GLASS", "evidence_ref": "jump-2"},
            {"controller": "test-administrator-jump2", "target": "test-independent-journal",
             "capability": "ROLE_ADMIN", "evidence_ref": "jump-3"},
        ])
        self.assert_incomplete(self.bundle, "shared transitive control")

    def test_same_underlying_root_can_escalate_to_signer(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"].append({"controller": "test-rados-root",
                           "target": "test-signing-vault", "capability": "KEY_ADMIN",
                           "evidence_ref": "compromised-key-admin"})
        self.assert_incomplete(self.bundle, "shared transitive control")

    def test_backup_admin_root_cannot_control_recovery(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"].append({"controller": "test-backup", "target": "test-second-physical-custodian",
                           "capability": "BACKUP_RESTORE", "evidence_ref": "snapshot-overwrite"})
        self.assert_incomplete(self.bundle, "shared transitive control")

    def test_protected_signer_and_highwater_must_have_distinct_superadmins(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"].append({"controller": "test-security-key-root",
                           "target": "test-independent-journal",
                           "capability": "ROOT_ADMIN", "evidence_ref": "shared-security-owner"})
        self.assert_incomplete(self.bundle, "shared transitive control")

    def test_ceph_missing_explicit_mon_caps_review_denied(self):
        del self.bundle["custody"]["control_graph"]["surfaces"]["cephx_mon"]
        self.assert_incomplete(self.bundle, "cephx_mon")

    def test_ceph_missing_pool_or_osd_review_denied(self):
        for key in ("rados_pool_and_namespace", "cephx_osd"):
            with self.subTest(key=key):
                b = copy.deepcopy(self.bundle)
                del b["custody"]["control_graph"]["surfaces"][key]
                self.assert_incomplete(b, key)

    def test_unattributed_root_authority_denied(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"] = [edge for edge in g["edges"] if edge["target"] != "test-clock"]
        self.assert_incomplete(self.bundle, "unattributed role")

    def test_empty_or_fabricated_coverage_flag_denied(self):
        g = self.bundle["custody"]["control_graph"]
        g["completeness_asserted"] = "true"
        self.assert_incomplete(self.bundle, "completeness not asserted")

    def test_missing_root_or_unknown_control_principal_denied(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"][0]["controller"] = "not-in-inventory"
        self.assert_incomplete(self.bundle, "unknown principal")

    def test_cycle_in_delegated_administration_denied(self):
        g = self.bundle["custody"]["control_graph"]
        g["edges"].append({"controller": "test-app", "target": "test-app-team-root",
                           "capability": "ROLE_ADMIN", "evidence_ref": "looped-grant"})
        self.assert_incomplete(self.bundle, "cyclic")

    def test_duplicate_or_unknown_edge_denied(self):
        original = self.bundle["custody"]["control_graph"]["edges"][0]
        self.bundle["custody"]["control_graph"]["edges"].append(dict(original))
        self.assert_incomplete(self.bundle, "duplicates authority")
        self.bundle["custody"]["control_graph"]["edges"][-1]["capability"] = "READ_ONLY"
        self.assert_incomplete(self.bundle, "non-administrative capability")

    def test_non_ceph_surface_schema_remains_explicit(self):
        b = copy.deepcopy(self.bundle)
        b["medium"] = "MANAGED_IMMUTABLE"
        for key in ("rgw_admin", "cephx_mon", "cephx_mgr", "cephx_osd", "rados_pool_and_namespace"):
            del b["custody"]["control_graph"]["surfaces"][key]
        self.assertEqual(inspect(b), [])
        b["custody"]["control_graph"]["surfaces"]["rgw_admin"] = "unrequested-ceph-surface"
        self.assert_incomplete(b, "unknown")

    def test_legacy_bundle_without_control_graph_denied(self):
        del self.bundle["custody"]["control_graph"]
        self.assert_incomplete(self.bundle, "control_graph")


if __name__ == "__main__":
    unittest.main()
