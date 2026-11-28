import pathlib
import tempfile
import unittest
from gpui_release_license import capture, reconcile, NAME, REGISTRY

class ReleaseLicense(unittest.TestCase):
    def fixture(self, binary):
        metadata = {"packages": [
            {"id": "local", "name": NAME, "version": "0.1.0", "source": None},
            {"id": "dep", "name": "dep", "version": "1.0", "source": REGISTRY}]}
        def artifact(pid, name, kinds, executable=None):
            return {"reason": "compiler-artifact", "package_id": pid, "fresh": False,
                    "target": {"name": name, "kind": kinds}, "executable": executable}
        messages = [artifact("dep", "dep", ["lib"]), artifact("dep", "build-script", ["custom-build"]),
                    artifact("local", NAME, ["bin"], str(binary)), {"reason": "build-finished", "success": True}]
        def row(name, version, review, scope):
            return {"name": name, "version": version, "review": review, "graph_scope": scope,
                    "declared_license": "MIT", "license_material": []}
        inventory = {"schema": "lanveriq.gpui.untrusted-supply-chain-v1", "source_sha": "a"*40,
                     "lock_sha256": "b"*64, "sbom_sha256": "c"*64, "release_approved": False,
                     "linux_normal_build_graph_count": 3, "packages": [
                        row(NAME, "0.1.0", "LOCAL_PRODUCT_LICENSE_OPEN", "NORMAL_PATH"),
                        row("dep", "1.0", "MISSING_MATERIAL_REVIEW_OPEN", "BUILD_PATH_ONLY"),
                        row("extra", "2.0", "MISSING_MATERIAL_REVIEW_OPEN", "NORMAL_PATH")]}
        return messages, metadata, inventory

    def test_capture_and_join_preserves_missing_and_extra(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = pathlib.Path(temp) / NAME
            binary.write_bytes(b"fixture")
            messages, metadata, inventory = self.fixture(binary)
            report = capture(messages, metadata, binary, "a"*40, "b"*64)
            dep = next(p for p in report["packages"] if p["name"] == "dep")
            self.assertEqual(dep["target_kinds"], ["custom-build", "lib"])
            self.assertEqual(dep["artifact_messages"], 2)
            result = reconcile(report, inventory)
            self.assertEqual(result["compiled_registry_material_missing"], [["dep", "1.0"]])
            self.assertEqual(result["inventory_only"], [("extra", "2.0")])
            self.assertFalse(result["release_approved"])

    def test_capture_rejects_failed_build_unknown_id_and_wrong_elf(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = pathlib.Path(temp) / NAME
            binary.write_bytes(b"fixture")
            messages, metadata, _ = self.fixture(binary)
            for change in ([*messages[:-1], {"reason": "build-finished", "success": False}],
                           [{**messages[0], "package_id": "unknown"}, *messages[1:]],
                           [*messages[:2], {**messages[2], "executable": "/wrong"}, messages[3]]):
                with self.assertRaises(ValueError):
                    capture(change, metadata, binary, "a"*40, "b"*64)

    def test_join_rejects_mismatch_duplicate_or_missing_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = pathlib.Path(temp) / NAME
            binary.write_bytes(b"fixture")
            messages, metadata, inventory = self.fixture(binary)
            report = capture(messages, metadata, binary, "a"*40, "b"*64)
            for modified in ({**inventory, "source_sha": "d"*40},
                             {**inventory, "lock_sha256": "e"*64},
                             {**inventory, "packages": [inventory["packages"][0]]*3},
                             {**inventory, "packages": [inventory["packages"][0], inventory["packages"][2]],
                              "linux_normal_build_graph_count": 2},
                             {**inventory, "release_approved": True}):
                with self.assertRaises(ValueError):
                    reconcile(report, modified)
