import copy
import json
import pathlib
import tempfile
import unittest
import zipfile

from gpui_compiled_notice_review import join, read_archive, sha, add_link_observation

def fixture():
    row = {"name": "example", "version": "1.0", "review": "MISSING_MATERIAL_REVIEW_OPEN",
           "registry_checksum": "a"*64, "target_kinds": ["lib"], "artifact_messages": 1,
           "fresh_messages": 0, "graph_scope": "NORMAL_PATH", "declared_license": "MIT",
           "license_material": []}
    common = {"source_sha": "b"*40, "lock_sha256": "c"*64, "release_approved": False}
    identity = {"elf_sha256": "d"*64, "elf_bytes": 1}
    bom, material = b"{}", b"copyright fixture"
    key = sha(material)
    encode = lambda obj: json.dumps(obj).encode()
    c = encode({**common, **identity, "schema": "lanveriq.gpui.release-compiler-artifacts-v1", "packages": [row]})
    s = encode({**common, "schema": "lanveriq.gpui.untrusted-supply-chain-v1", "packages": [row],
                "sbom_sha256": sha(bom), "install_authorized": False})
    a = encode({**common, **identity, "schema": "lanveriq.gpui.release-license-association-v1",
                "packages": [row], "compiled_package_count": 1, "inventory_package_count": 1,
                "compiled_registry_material_missing": [["example", "1.0"]], "inventory_only": [],
                "compiler_report_sha256": sha(c), "license_inventory_sha256": sha(s),
                "original_sbom_sha256": sha(bom), "install_authorized": False})
    n = encode({**common, "schema": "lanveriq.gpui.notice-gap-review-bundle-v1",
                "input_supply_chain_source": "e"*40, "install_authorized": False, "registry_gaps_closed": 0,
                "packages": {"example@1.0": {"registry_checksum": row["registry_checksum"],
                    "registry_material_missing": True, "legal_approved": False, "material_sha256": [key]}},
                "materials": {key: {"file": key+".txt", "bytes": len(material)}}})
    return [{"candidate-release-license-association.json": a},
            {"candidate-release-artifacts.json": c},
            {"candidate-supply-chain.json": s, "bom.json": bom},
            {"index.json": n, "READ-ME-FIRST.txt": b"review", key+".txt": material}]

class CompiledNoticeReview(unittest.TestCase):
    def test_join_preserves_open(self):
        report = join(*fixture())
        self.assertEqual(report["compiled_gaps_with_auxiliary_material"], 1)
        self.assertEqual(report["registry_gaps_closed"], 0)
        self.assertFalse(report["release_approved"])
        self.assertNotEqual(report["source_sha"], report["notice_inventory_source_sha"])

    def test_digest_and_identity_fail_closed(self):
        for mode in ("compiler", "material", "lock", "checksum", "approval", "extra"):
            with self.subTest(mode=mode):
                args = fixture()
                if mode == "compiler":
                    args[1]["candidate-release-artifacts.json"] += b" "
                elif mode == "material":
                    key = next(k for k in args[3] if len(k) == 68)
                    args[3][key] = b"changed"
                elif mode == "extra":
                    args[3]["extra.txt"] = b"extra"
                else:
                    n = json.loads(args[3]["index.json"])
                    if mode == "lock":
                        n["lock_sha256"] = "f"*64
                    elif mode == "checksum":
                        n["packages"]["example@1.0"]["registry_checksum"] = "f"*64
                    else:
                        n["release_approved"] = True
                    args[3]["index.json"] = json.dumps(n).encode()
                with self.assertRaises(ValueError):
                    join(*args)

    def test_zip_digest_and_path_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp)/"fixture.zip"
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("data.json", "{}")
            self.assertEqual(read_archive(path, sha(path.read_bytes())), {"data.json": b"{}"})
            with self.assertRaises(ValueError):
                read_archive(path, "0"*64)
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("../escape", "x")
            with self.assertRaises(ValueError):
                read_archive(path, sha(path.read_bytes()))

    def test_link_observation_keeps_all_compiled_packages(self):
        args = fixture()
        report = join(*args)
        raw = args[1]["candidate-release-artifacts.json"]
        m = {"schema": "lanveriq.gpui.release-link-map-v1",
             "scope": "LLD_NONZERO_RLIB_INPUT_SECTIONS_NOT_COMPLETE_BINARY_COMPOSITION",
             "license_review": "OPEN_NOT_APPROVED", "release_approved": False, "install_authorized": False,
             "source_sha": report["source_sha"], "lock_sha256": report["lock_sha256"],
             "elf_sha256": report["elf_sha256"], "compiler_report_sha256": sha(raw), "map_sha256": "e"*64,
             "observed_packages": [{"name": "example", "version": "1.0", "members": 1,
                                    "nonzero_input_sections": 2}],
             "compiler_rlib_packages_not_observed": [],
             "observed_nonzero_rlib_section_count": 3, "unbound_external_rlib_archive_count": 1}
        args[1]["candidate-release-link-map.json"] = json.dumps(m).encode()
        result = add_link_observation(report, args[1])
        self.assertEqual(result["observed_registry_material_gaps"], [["example", "1.0"]])
        self.assertEqual(result["registry_gaps_closed"], 0)
        self.assertFalse(result["release_approved"])
        for patch in ({"elf_sha256": "f"*64}, {"compiler_report_sha256": "f"*64},
                      {"release_approved": True}, {"compiler_rlib_packages_not_observed": [["example", "1.0"]]},
                      {"observed_packages": m["observed_packages"] * 2},
                      {"observed_nonzero_rlib_section_count": 1},
                      {"observed_packages": [{"name": "unknown", "version": "1", "members": 1,
                                             "nonzero_input_sections": 1}]}):
            with self.subTest(patch=patch):
                args[1]["candidate-release-link-map.json"] = json.dumps({**m, **patch}).encode()
                with self.assertRaises(ValueError):
                    add_link_observation(report, args[1])

    def test_link_non_observation_never_closes_gaps(self):
        args = fixture()
        report = join(*args)
        # Add two separately compiled rows for the observation-stage fixture.
        report["packages"].extend([{**report["packages"][0], "name": name} for name in ("observed", "macro")])
        raw = args[1]["candidate-release-artifacts.json"]
        m = {"schema": "lanveriq.gpui.release-link-map-v1",
             "scope": "LLD_NONZERO_RLIB_INPUT_SECTIONS_NOT_COMPLETE_BINARY_COMPOSITION",
             "license_review": "OPEN_NOT_APPROVED", "release_approved": False, "install_authorized": False,
             "source_sha": report["source_sha"], "lock_sha256": report["lock_sha256"],
             "elf_sha256": report["elf_sha256"], "compiler_report_sha256": sha(raw), "map_sha256": "e"*64,
             "observed_packages": [{"name": "observed", "version": "1.0", "members": 1,
                                    "nonzero_input_sections": 2}],
             "compiler_rlib_packages_not_observed": [["example", "1.0"]],
             "observed_nonzero_rlib_section_count": 2, "unbound_external_rlib_archive_count": 0}
        args[1]["candidate-release-link-map.json"] = json.dumps(m).encode()
        result = add_link_observation(report, args[1])
        self.assertEqual(result["other_registry_material_gaps"], [["example", "1.0"], ["macro", "1.0"]])
        self.assertEqual(len(result["packages"]), 3)
        self.assertEqual(result["registry_gaps_closed"], 0)
        self.assertEqual(result["not_observed_rlib_package_count"], 1)
        self.assertEqual(result["no_rlib_observation_package_count"], 1)
