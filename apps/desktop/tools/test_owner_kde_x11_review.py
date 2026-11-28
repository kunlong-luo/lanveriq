import subprocess
import unittest
from unittest.mock import patch
from owner_kde_x11_review import (verify_session, report, parse_answers, owner_exit_code, ALL_CHECKS, UI_CHECKS, STATUS,
                                  LINK_INPUTS, verify_link_inputs, default_cc_lookup)
from owner_cosmic_review import verify_session as cosmic_session

class KdeReview(unittest.TestCase):
    def setUp(self):
        self.env={"XDG_CURRENT_DESKTOP":"KDE","XDG_SESSION_TYPE":"x11","DISPLAY":":0",
                  "XDG_SESSION_ID":"2","XDG_RUNTIME_DIR":"/run/user/1000"}
        self.system={"ID":"pop","VERSION_ID":"24.04"}
        self.raw="User=1000\nDesktop=KDE\nType=x11\nRemote=no\nActive=yes\n"
    def verify(self, env=None, raw=None, uid=1000, euid=1000):
        return verify_session(uid,euid,self.system,"x86_64",
                              self.env if env is None else env,
                              lambda session:self.raw if raw is None else raw)
    def test_actual_kde_scope_and_cosmic_refusal(self):
        self.assertEqual(str(self.verify()),"/tmp/.X11-unix/X0")
        with self.assertRaises(ValueError):
            cosmic_session(1000,1000,self.system,"x86_64",self.env)
    def test_invalid_local_session_rejected(self):
        for key,value in (("DISPLAY","localhost:10.0"),("DISPLAY","host:0"),
            ("XDG_CURRENT_DESKTOP","COSMIC"),("XDG_SESSION_TYPE","wayland"),
            ("WAYLAND_DISPLAY","wayland-0"),("WAYLAND_SOCKET","7"),
            ("XDG_RUNTIME_DIR","/tmp"),("XDG_SESSION_ID","--all")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.verify(env={**self.env,key:value})
        for uid,euid in ((0,0),(1000,0)):
            with self.assertRaises(ValueError):
                self.verify(uid=uid,euid=euid)
    def test_logind_drift_rejected(self):
        for raw in (self.raw.replace("Remote=no","Remote=yes"),
                    self.raw.replace("Active=yes","Active=no"),
                    self.raw.replace("Desktop=KDE","Desktop=COSMIC"),
                    self.raw.replace("User=1000","User=1001"),
                    self.raw.replace("Type=x11","Type=wayland"),
                    self.raw+"User=1000\n", "", "x"*4097):
            with self.subTest(raw=raw[:40]),self.assertRaises(ValueError):
                self.verify(raw=raw)
    def test_report_never_promotes_cosmic_or_release(self):
        answers=parse_answers(" ".join(["p"]*12))
        for code,method in ((0,"y"),(0,"n"),(101,"y")):
            data=report("a"*40,"b"*64,"c"*64,code,answers,method)
            self.assertEqual(data["schema"],"lanveriq.gpui.owner-kde-x11-review-v2")
            self.assertEqual(data["cosmic_wayland"],"NOT_RUN")
            self.assertEqual(data["independent_l3"],"NOT_APPROVED")
            self.assertFalse(data["install_authorized"])
            self.assertFalse(data["release_approved"])
        self.assertEqual(owner_exit_code(0,answers,"y"),0)
        self.assertEqual(owner_exit_code(0,answers,"n"),2)
        self.assertEqual(owner_exit_code(101,answers,"y"),1)

    def test_link_inputs_missing_named_dev_package_before_build(self):
        seen = []
        def lookup(name):
            seen.append(name)
            return name if name == "libxkbcommon-x11.so" else "/usr/lib/"+name
        with self.assertRaisesRegex(ValueError, "libxkbcommon-x11-dev") as failure:
            verify_link_inputs(lookup, lambda path: True)
        self.assertNotIn("libxcb1-dev", str(failure.exception))
        self.assertEqual(seen, [name for name, _ in LINK_INPUTS])
        verify_link_inputs(lambda name: "/usr/lib/"+name, lambda path: True)

    def test_link_input_rejects_runtime_only_or_unreadable_and_redacts_errors(self):
        for result in ("libxkbcommon-x11.so", "/usr/lib/libxkbcommon-x11.so.0",
                       "/usr/lib/libxkbcommon-x11.so\n/private/path"):
            with self.subTest(result=result), self.assertRaisesRegex(ValueError, "libxkbcommon-x11-dev"):
                verify_link_inputs(lambda name: result if name == "libxkbcommon-x11.so" else "/usr/lib/"+name,
                                   lambda path: True)
        with self.assertRaisesRegex(ValueError, "libxcb1-dev"):
            verify_link_inputs(lambda name: "/usr/lib/"+name, lambda path: False)
        with patch("owner_kde_x11_review.subprocess.run",
                   side_effect=subprocess.TimeoutExpired(["/private/path"], 5)):
            with self.assertRaisesRegex(ValueError, "系统默认 cc 无法查询") as error:
                verify_link_inputs()
            self.assertNotIn("/private/path", str(error.exception))

    def test_cc_lookup_is_fixed_readonly_query(self):
        with patch("owner_kde_x11_review.subprocess.run",
                   return_value=subprocess.CompletedProcess([],0,"/usr/lib/libxcb.so\n")) as run:
            self.assertEqual(default_cc_lookup("libxcb.so"), "/usr/lib/libxcb.so")
            run.assert_called_once_with(["/usr/bin/cc", "-print-file-name=libxcb.so"],
                check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                timeout=5, env={"PATH":"/usr/bin:/bin","LANG":"C"})

    def test_new_ui_observations_required_for_v2(self):
        self.assertEqual(len(ALL_CHECKS),12)
        self.assertEqual(len(set(key for key,_ in ALL_CHECKS)),12)
        for raw in ("p "*9,"p "*11,"p "*13,"p "*11+"yes"):
            with self.assertRaises(ValueError):
                parse_answers(raw)
        good = parse_answers("p "*12)
        self.assertEqual(owner_exit_code(0,good,"y"),0)
        for key,_ in UI_CHECKS:
            for marker,code in (("f",1),("n",2)):
                with self.subTest(key=key,marker=marker):
                    answers={**good,key:STATUS[marker]}
                    self.assertEqual(owner_exit_code(0,answers,"y"),code)
                    data=report("a"*40,"b"*64,"c"*64,0,answers,"y")
                    self.assertEqual(data["checks"][key],STATUS[marker])
                    self.assertEqual(data["platform_appearance_event"],"NOT_TESTED")
                    self.assertEqual(data["physical_dpi"],"NOT_VERIFIED")
                    self.assertFalse(data["release_approved"])
        for answers in ({key:value for key,value in good.items() if key!="appearance"},
                        {**good,"appearance":"PASS"},{**good,"unexpected":STATUS["p"]}):
            with self.assertRaises(ValueError):
                owner_exit_code(0,answers,"y")
