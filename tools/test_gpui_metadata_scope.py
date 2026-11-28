import unittest
from gpui_metadata_scope import NAME, REGISTRY, compare, resolved

def fixture():
    names = [NAME, "normal", "build", "dev"]
    packages = [{"id": n, "name": n, "version": "0.1.0",
                 "source": None if n == NAME else REGISTRY} for n in names]
    deps = [{"pkg": n, "name": n, "dep_kinds": [{"kind": k, "target": None}]}
            for n, k in zip(names[1:], [None, "build", "dev"])]
    nodes = [{"id": n, "features": [], "deps": deps if n == NAME else []} for n in names]
    locked = {(n, "0.1.0") for n in names}
    metadata = {"packages": packages, "resolve": {"root": NAME, "nodes": nodes}}
    bom = {"bomFormat": "CycloneDX", "specVersion": "1.6",
           "metadata": {"component": {"name": NAME, "version": "0.1.0",
                                     "components": [{"name": "local_lib", "version": "0.1.0"}]}},
           "components": [{"name": n, "version": "0.1.0", "scope": scope}
                          for n, scope in (("normal", "required"), ("build", "excluded"))]}
    return metadata, bom, locked

class ScopeTest(unittest.TestCase):
    def test_dev_edges_excluded_and_local_targets_distinct(self):
        metadata, bom, locked = fixture()
        tree = {(NAME, "0.1.0"), ("normal", "0.1.0")}
        result = compare(metadata, tree, bom, locked)
        self.assertEqual(result["bom_only_vs_tree"], [("build", "0.1.0")])
        self.assertEqual(result["metadata_only_vs_tree"], [("build", "0.1.0")])
        self.assertEqual(result["bom_only_vs_metadata"], [])
        self.assertEqual(result["bom_local_target_pairs"], [("local_lib", "0.1.0")])
        self.assertEqual(result["extra_bom_package_details"][0]["bom_scope"], "excluded")
        self.assertFalse(result["release_approved"])

    def test_dangling_and_unlocked_rejected(self):
        metadata, bom, locked = fixture()
        metadata["resolve"]["nodes"][0]["deps"][0]["pkg"] = "missing"
        with self.assertRaises(ValueError):
            resolved(metadata, locked)
        metadata, bom, locked = fixture()
        bom["components"][0]["name"] = "unknown"
        with self.assertRaises(ValueError):
            compare(metadata, {(NAME, "0.1.0")}, bom, locked)
