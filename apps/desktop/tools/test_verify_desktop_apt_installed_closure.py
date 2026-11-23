"""L2B-7 synthetic python-apt-installed OR-group rejection tests."""
import importlib.util
import pathlib
import unittest

BASE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "aptclosure", BASE / "verify_desktop_apt_installed_closure.py"
)
v = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(v)

class Package:
    def __init__(self, name, version):
        self.fullname = name
        self.installed = Version(self, version, [])

class Version:
    def __init__(self, package, version, groups):
        self.package = package
        self.version = version
        self._groups = groups
    def get_dependencies(self, *kinds):
        return self._groups

class BaseDep:
    def __init__(self, name, versions=(), rawtype="Depends"):
        self.rawstr = name
        self.rawtype = rawtype
        self.installed_target_versions = list(versions)

class OrGroup:
    def __init__(self, *deps):
        self.or_dependencies = list(deps)

def cache_graph(ambiguous=False):
    a = Package("alpha:amd64", "1.0")
    b = Package("beta:amd64", "2.0")
    c = Package("gamma:amd64", "3.0")
    a.installed._groups = [OrGroup(BaseDep("beta | gamma", [b.installed, c.installed] if ambiguous else [b.installed]))]
    return {p.fullname:p for p in (a,b,c)}

class AptInstalledClosureTests(unittest.TestCase):
    def test_one_installed_dependency(self):
        result=v.inspect_installed(cache_graph(),{"alpha:amd64":"1.0"})
        self.assertEqual(len(result["packages"]),2)
        self.assertEqual(len(result["mandatory_dependency_groups"]),1)
        self.assertEqual(result["installed_alternative_ambiguities"],0)

    def test_multi_installed_alternative_remains_ambiguous(self):
        result=v.inspect_installed(cache_graph(True),{"alpha:amd64":"1.0"})
        self.assertEqual(len(result["packages"]),3)
        self.assertEqual(result["installed_alternative_ambiguities"],1)

    def test_empty_satisfier_fails_closed(self):
        with self.assertRaisesRegex(ValueError,"missing installed satisfier"):
            v.installed_satisfiers([BaseDep("missing", [])])

    def test_reject_nonmandatory_dependency_type(self):
        with self.assertRaisesRegex(ValueError,"unsupported APT dependency type"):
            v.installed_satisfiers([BaseDep("optional",[],rawtype="Recommends")])

    def test_reject_empty_or_group(self):
        with self.assertRaisesRegex(ValueError,"invalid APT alternative"):
            v.installed_satisfiers([])

    def test_reject_version_drift(self):
        with self.assertRaisesRegex(ValueError,"differs from L2B-6"):
            v.inspect_installed(cache_graph(),{"alpha:amd64":"7.0"})

    def test_reject_missing_installed_package(self):
        with self.assertRaisesRegex(ValueError,"required installed root"):
            v.inspect_installed(cache_graph(),{"delta:amd64":"1.0"})

    def test_reject_source_owner_forged_authority(self):
        with self.assertRaisesRegex(ValueError,"forged or mismatched"):
            v.check_prior({"schema":"lanveriq.desktop.untrusted-ubuntu-package-map.v1",
                           "source_sha":"a"*40,"run_id":1,"attempt":1,
                           "release_approved":True}, "a"*40,1,1)

    def test_reject_unknown_owner_count(self):
        base={"schema":"lanveriq.desktop.untrusted-ubuntu-package-map.v1",
              "source_sha":"a"*40,"run_id":1,"attempt":1,
              "ubuntu_release":"24.04","architecture":"amd64",
              "license_review":v.LICENSE_OPEN,
              "transitive_runtime_closure":"NOT_VERIFIED",
              "signed_provenance":"NOT_AVAILABLE",
              "release_approved":False,"install_authorized":False,
              "installed_owner_packages":[],"soname_owner_map":[]}
        with self.assertRaisesRegex(ValueError,"wrong actual L2B-6"):
            v.check_prior(base, "a"*40,1,1)

    def test_reject_unsafe_name(self):
        with self.assertRaisesRegex(ValueError,"invalid APT installed package name"):
            p=Package("../bad", "1.0")
            v.name_version(p.installed)

    def test_reject_version_not_installed(self):
        p=Package("gamma:amd64", "1.0")
        with self.assertRaisesRegex(ValueError,"not actually installed"):
            v.name_version(Version(p,"1.1",[]))

    def test_reject_bad_target_no_mutation(self):
        p=Package("delta:amd64","2.0")
        with self.assertRaisesRegex(ValueError,"not actually installed"):
            v.installed_satisfiers([BaseDep("delta",[Version(p,"2.5",[])])])

    def test_copyright_unavailable_keeps_legal_open(self):
        from unittest.mock import patch
        with patch.object(v.owner, "copyright_receipt", side_effect=ValueError("missing")):
            result=v.copyright_input("test:amd64")
        self.assertEqual(result["copyright_review"],"MISSING_MATERIAL_LEGAL_REVIEW_OPEN")

    def test_missing_apt_module_does_not_create_approval(self):
        self.assertEqual(v.LICENSE_OPEN,"OPEN_NOT_APPROVED")

if __name__ == "__main__":
    unittest.main()
