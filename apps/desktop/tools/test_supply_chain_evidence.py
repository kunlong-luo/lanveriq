import pathlib
import tempfile
import unittest
from supply_chain_evidence import graph_pairs, license_name, record, components, missing_review

class SupplyChainEvidence(unittest.TestCase):
    def test_graph_pairs_exact_versions_and_dedup(self):
        self.assertEqual(graph_pairs("a v1.0.0\na v1.0.0 (*)\na v2.0.0\n"), {("a","1.0.0"),("a","2.0.0")})
        with self.assertRaises(ValueError):
            graph_pairs("not a cargo package")

    def test_license_material_names(self):
        for name in ("LICENSE", "LICENSE-APACHE", "COPYING.LESSER", "OFL.txt", "NOTICE.md"):
            self.assertTrue(license_name(name))
        self.assertFalse(license_name("not-a-license.txt"))

    def test_file_hash_and_symlink_refusal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            file = root / "LICENSE"
            file.write_text("fixture")
            self.assertEqual(record(file, root)["bytes"], 7)
            link = root / "NOTICE"
            link.symlink_to(file)
            with self.assertRaises(ValueError):
                record(link, root)

    def test_nested_bom_component_pairs(self):
        bom = {"metadata":{"component":{"name":"app","version":"1","components":[{"name":"a","version":"2"}]}},
               "components":[{"name":"b","version":"3"}]}
        self.assertEqual(components(bom), {("app","1"),("a","2"),("b","3")})

    def test_nested_license_and_vcs_are_claims_not_approval(self):
        import json
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp).resolve()
            (root / "vendor").mkdir()
            (root / "vendor" / "LICENSE").write_text("fixture")
            (root / "vendor" / "license.rs").write_text("not legal material")
            (root / ".cargo_vcs_info.json").write_text(json.dumps({"git":{"sha1":"a"*40},"path_in_vcs":"crates/example"}))
            review = missing_review({"repository":"https://example.invalid/repo"}, root)
            self.assertEqual(len(review["nested_license_candidates"]), 1)
            self.assertEqual(review["cargo_vcs_claim"]["git_sha1_claim"], "a"*40)
            self.assertEqual(review["review"], "PACKAGE_MATERIAL_MISSING_UPSTREAM_REVIEW_OPEN")
            (root / ".cargo_vcs_info.json").write_text(json.dumps({"git":{"sha1":"a"*40},"path_in_vcs":"../bad"}))
            self.assertIsNone(missing_review({}, root)["cargo_vcs_claim"])
