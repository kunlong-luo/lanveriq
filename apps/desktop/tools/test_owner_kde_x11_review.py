import unittest
from owner_kde_x11_review import verify_session, report, parse_answers, review_exit_code
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
        answers=parse_answers("p p p p p p p p p")
        for code,method in ((0,"y"),(0,"n"),(101,"y")):
            data=report("a"*40,"b"*64,"c"*64,code,answers,method)
            self.assertEqual(data["schema"],"lanveriq.gpui.owner-kde-x11-review-v1")
            self.assertEqual(data["cosmic_wayland"],"NOT_RUN")
            self.assertEqual(data["independent_l3"],"NOT_APPROVED")
            self.assertFalse(data["install_authorized"])
            self.assertFalse(data["release_approved"])
        self.assertEqual(review_exit_code(0,answers,"y"),0)
        self.assertEqual(review_exit_code(0,answers,"n"),2)
        self.assertEqual(review_exit_code(101,answers,"y"),1)
