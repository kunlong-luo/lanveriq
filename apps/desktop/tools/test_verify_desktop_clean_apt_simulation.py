"""Fail-closed L2B-10 OCI clean APT simulation proof tests, no Docker required."""
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parent
s = importlib.util.spec_from_file_location("clean_apt", ROOT / "verify_desktop_clean_apt_simulation.py")
v = importlib.util.module_from_spec(s)
s.loader.exec_module(v)
H = "b" * 64
IMAGE = "ubuntu@sha256:" + "c" * 64
LOG = """LNV_RELEASE ubuntu:24.04
LNV_ARCH amd64
LNV_BASE_COUNT 77
LNV_BASE_GUI_LIBS absent
LNV_PACKAGE_SHA """ + H + """
Reading package lists...
Building dependency tree...
The following NEW packages will be installed:
Inst libcairo2 (1.18.0-3 [amd64])
Inst libwebkit2gtk-4.1-0 (2.48-0ubuntu [amd64])
Inst libgtk-3-0t64 (3.24.41-4 [amd64])
Inst libglib2.0-0t64 (2.80.0-3 [amd64])
Inst libjavascriptcoregtk-4.1-0 (2.48-0ubuntu [amd64])
Inst libsoup-3.0-0 (3.4.4-1 [amd64])
Inst libgdk-pixbuf-2.0-0 (2.42.10 [amd64])
Inst libdbus-1-3 (1.14.10 [amd64])
Inst lanveriq-desktop (0.1.0 local-deb [amd64])
Conf lanveriq-desktop (0.1.0 local-deb [amd64])
"""

class CleanOCIAptTests(unittest.TestCase):
    def test_accept_plan_with_clean_root(self):
        res=v.parse_simulation(LOG,H)
        self.assertEqual(res["planned_install_count"],9)
        self.assertEqual(res["initial_installed_package_count"],77)

    def test_reject_wrong_candidate_hash(self):
        with self.assertRaisesRegex(ValueError,"bytes mismatch"):
            v.parse_simulation(LOG, "a"*64)

    def test_reject_duplicate_release_header(self):
        with self.assertRaisesRegex(ValueError,"missing/duplicate"):
            v.parse_simulation(LOG+"LNV_RELEASE ubuntu:24.04\n",H)

    def test_reject_untrusted_release(self):
        with self.assertRaisesRegex(ValueError,"not a fresh"):
            v.parse_simulation(LOG.replace("ubuntu:24.04","pop:24.04"),H)

    def test_reject_non_amd64(self):
        with self.assertRaisesRegex(ValueError,"not a fresh"):
            v.parse_simulation(LOG.replace("LNV_ARCH amd64","LNV_ARCH arm64"),H)

    def test_reject_gui_preinstalled(self):
        with self.assertRaisesRegex(ValueError,"not a fresh"):
            v.parse_simulation(LOG.replace("LNV_BASE_GUI_LIBS absent","LNV_BASE_GUI_LIBS present"),H)

    def test_reject_oversized_base(self):
        with self.assertRaisesRegex(ValueError,"population"):
            v.parse_simulation(LOG.replace("BASE_COUNT 77","BASE_COUNT 999"),H)

    def test_reject_missing_candidate(self):
        with self.assertRaisesRegex(ValueError,"exactly one GUI"):
            v.parse_simulation(LOG.replace("Inst lanveriq-desktop","Inst other-desktop"),H)

    def test_reject_no_configure(self):
        with self.assertRaisesRegex(ValueError,"install and configure"):
            v.parse_simulation(LOG.replace("Conf lanveriq-desktop","Conf other-desktop"),H)

    def test_reject_removal(self):
        with self.assertRaisesRegex(ValueError,"unexpected removals"):
            v.parse_simulation(LOG+"Remv libc6 [2.39]\n",H)

    def test_reject_agent_in_plan(self):
        with self.assertRaisesRegex(ValueError,"Agent or service"):
            v.parse_simulation(LOG+"Inst lanveriq-agent (0.1)\n",H)

    def test_reject_duplicate_image_digests(self):
        with self.assertRaisesRegex(ValueError,"ambiguous"):
            v.parse_image_digests('["'+IMAGE+'","ubuntu@sha256:'+"d"*64+'"]')

    def test_accept_single_resolved_digest(self):
        self.assertEqual(v.parse_image_digests('["'+IMAGE+'"]'),IMAGE)

    def test_reject_mutable_tag_execution(self):
        with self.assertRaisesRegex(ValueError,"resolved sha256 digest"):
            v.docker_args("ubuntu:24.04","/tmp/invalid.deb")

    def test_container_runtime_has_no_package_install(self):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)/"candidate.deb"
            p.write_bytes(b"x"*2048)
            args=v.docker_args(IMAGE,p)
        self.assertIn("--read-only",args)
        self.assertIn("--cap-drop=ALL",args)
        self.assertIn("--security-opt=no-new-privileges",args)
        self.assertEqual(args[-3:], ["/bin/sh","-ceu",v.CONTAINER_SCRIPT])
        self.assertIn("apt-get -s",v.CONTAINER_SCRIPT)
        self.assertNotIn("apt-get install -y",v.CONTAINER_SCRIPT)
        self.assertFalse(any("docker.sock" in x for x in args))

    def test_prior_wrong_source_fails_before_any_network(self):
        with patch.object(v.abi.chain.owner.elfguard.payload.base,"json_read",return_value={
            "schema":"lanveriq.desktop.untrusted-dpkg-shlibdeps-v1",
            "source_sha":"f"*40,
            "run_id":7,"attempt":1,
            "deb_sha256":H,
            "direct_package_metadata":"NO_GAP_DETECTED_UNAPPROVED",
            "comparison":{"direct_declaration_gap_count":0},
            "license_review":"OPEN_NOT_APPROVED",
            "release_approved":False,"install_authorized":False}):
            with self.assertRaisesRegex(ValueError,"prior exact-SHA"):
                v.check_previous("/tmp/none",H,"e"*40,7,1)

if __name__ == "__main__":
    unittest.main()
