"""L2B-11 synthetic Pop!_OS host identity/ABI/Wayland/repo privacy refusals."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location("popos",ROOT/"verify_desktop_popos_local_preflight.py")
v=importlib.util.module_from_spec(s)
s.loader.exec_module(v)
OK_OS='PRETTY_NAME="Pop!_OS 24.04"\nID=pop\nVERSION_ID="24.04"\n'
ENV={"XDG_CURRENT_DESKTOP":"COSMIC","XDG_SESSION_TYPE":"wayland","WAYLAND_DISPLAY":"wayland-1"}
HASH="a"*64

class Version:
    def __init__(self,v): self.version=v
class Package:
    def __init__(self,version,installed=False):
        self.candidate=Version(version)
        self.installed=Version(version) if installed else None
class Apt:
    @staticmethod
    def parse_depends(value,*args):
        if " (" not in value: return [[(value,"","")]]
        name, constraint=value.split(" (",1)
        op,ver=constraint[:-1].split(" ",1)
        return [[(name,ver,op)]]
    @staticmethod
    def version_compare(a,b):
        def numbers(v): return tuple(int(x) for x in v.split("."))
        return (numbers(a)>numbers(b))-(numbers(a)<numbers(b))

def cache():
    result={}
    for dep in v.ABI:
        parsed=Apt.parse_depends(dep)[0][0]
        result[parsed[0]]=Package(parsed[1] or "1.0")
    for name in v.COSMIC:
        result[name]=Package("1.0",True)
    return result

class PopOsPreflightTests(unittest.TestCase):
    def mkconfig(self,root):
        file=pathlib.Path(root)/"tauri.conf.json"
        file.write_text(json.dumps({"bundle":{"active":False,"targets":[],
                "linux":{"deb":{"depends":list(v.ABI)}}}}))
        return file

    def test_accept_pop_os_release_only(self):
        self.assertEqual(v.os_identity(OK_OS)["version"],"24.04")

    def test_reject_ubuntu_release(self):
        with self.assertRaisesRegex(ValueError,"NOT_POP_OS_TARGET"):
            v.os_identity(OK_OS.replace("ID=pop","ID=ubuntu"))

    def test_reject_pop_2204(self):
        with self.assertRaisesRegex(ValueError,"NOT_POP_OS_TARGET"):
            v.os_identity(OK_OS.replace("24.04","22.04"))

    def test_reject_duplicate_os_field(self):
        with self.assertRaisesRegex(ValueError,"DUPLICATE_OS_IDENTITY"):
            v.os_identity(OK_OS+"ID=pop\n")

    def test_wayland_claim_not_launch_proof(self):
        self.assertFalse(v.session_claim(ENV)["compositor_running_proven"])

    def test_reject_x11(self):
        with self.assertRaisesRegex(ValueError,"NOT_COSMIC"):
            v.session_claim({**ENV,"XDG_SESSION_TYPE":"x11"})

    def test_reject_gnome(self):
        with self.assertRaisesRegex(ValueError,"NOT_COSMIC"):
            v.session_claim({**ENV,"XDG_CURRENT_DESKTOP":"GNOME"})

    def test_reject_no_display(self):
        with self.assertRaisesRegex(ValueError,"NOT_COSMIC"):
            v.session_claim({**ENV,"WAYLAND_DISPLAY":""})

    def test_apt_source_no_uri_exposure(self):
        with tempfile.TemporaryDirectory() as t:
            root=pathlib.Path(t)
            d=root/"sources.list.d"
            d.mkdir()
            (d/"pop.sources").write_text("URIs: https://private.invalid/secret\nSuites: noble\n")
            data=v.sources_hash(root)
            self.assertEqual(data["file_count"],1)
            self.assertNotIn("private.invalid",json.dumps(data))

    def test_reject_symlink_apt_source(self):
        with tempfile.TemporaryDirectory() as t:
            root=pathlib.Path(t);d=root/"sources.list.d";d.mkdir()
            (d/"evil.sources").symlink_to("/etc/passwd")
            with self.assertRaisesRegex(ValueError,"UNTRUSTED_APT_SOURCE_PATH"):
                v.sources_hash(root)

    def test_reject_missing_apt_sources(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaisesRegex(ValueError,"MISSING_OR_EXCESS"):
                v.sources_hash(t)

    def test_source_config_locked(self):
        with tempfile.TemporaryDirectory() as t:
            cfg=self.mkconfig(t)
            self.assertEqual(v.reviewed_config(cfg),list(v.ABI))
            obj=json.loads(cfg.read_text())
            obj["bundle"]["active"]=True
            cfg.write_text(json.dumps(obj))
            with self.assertRaisesRegex(ValueError,"RELEASE_BUNDLE_LOCK_LIFTED"):
                v.reviewed_config(cfg)

    def test_reject_unreviewed_deb_installer_hook(self):
        with tempfile.TemporaryDirectory() as t:
            cfg=self.mkconfig(t)
            data=json.loads(cfg.read_text())
            data["bundle"]["linux"]["deb"]["postInstallScript"]="/etc/evil.sh"
            cfg.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError,"UNREVIEWED_DEBIAN_INSTALLER_HOOK"):
                v.reviewed_config(cfg)

    def test_abi_candidates_present_without_install(self):
        p,c=v.check_packages(cache(),Apt,list(v.ABI))
        self.assertEqual(len(p),10)
        self.assertEqual(len(c),3)
        self.assertTrue(all(not item["installed"] for item in p))

    def test_missing_t64_candidate_fails(self):
        item=cache();del item["libgtk-3-0t64"]
        with self.assertRaisesRegex(ValueError,"NO_LOCAL_ABI_APT_CANDIDATE"):
            v.check_packages(item,Apt,list(v.ABI))

    def test_older_glibc_version_fails(self):
        item=cache();item["libc6"]=Package("2.38")
        with self.assertRaisesRegex(ValueError,"ABI_CANDIDATE_VERSION_TOO_OLD"):
            v.check_packages(item,Apt,list(v.ABI))

    def test_cosmic_component_not_installed_fails(self):
        item=cache();item["cosmic-session"]=Package("1.0")
        with self.assertRaisesRegex(ValueError,"COSMIC_COMPONENT_NOT_INSTALLED"):
            v.check_packages(item,Apt,list(v.ABI))

    def test_success_remains_untrusted_and_private(self):
        with tempfile.TemporaryDirectory() as t:
            report=v.facts(OK_OS,"amd64",ENV,{"file_count":1,"contents_sha256":HASH},
                           self.mkconfig(t),cache(),Apt,False)
            self.assertEqual(report["physical_l3"],"NOT_APPROVED")
            self.assertEqual(report["popos_install_solver"],"NOT_RUN")
            self.assertFalse(report["install_authorized"])
            self.assertFalse(report["agent_start_authorized"])
            self.assertFalse(report["release_approved"])
            self.assertNotIn("wayland-1",json.dumps(report))
            self.assertNotIn("HOME",json.dumps(report))

    def test_root_gui_probe_is_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaisesRegex(ValueError,"PRIVILEGED_GUI"):
                v.facts(OK_OS,"amd64",ENV,{"file_count":1,"contents_sha256":HASH},
                        self.mkconfig(t),cache(),Apt,True)

    def test_arch_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaisesRegex(ValueError,"UNSUPPORTED_POP_OS_ARCH"):
                v.facts(OK_OS,"arm64",ENV,{"file_count":1,"contents_sha256":HASH},
                        self.mkconfig(t),cache(),Apt,False)

if __name__=="__main__":
    unittest.main()
