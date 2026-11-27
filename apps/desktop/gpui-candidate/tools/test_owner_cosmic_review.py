import unittest
from owner_cosmic_review import verify_session, wayland_env, parse_answers, CHECKS

class OwnerReview(unittest.TestCase):
    def fixture(self):
        return {"XDG_CURRENT_DESKTOP":"COSMIC", "XDG_SESSION_TYPE":"wayland",
                "XDG_RUNTIME_DIR":"/run/user/1000", "WAYLAND_DISPLAY":"wayland-0",
                "DISPLAY":":0", "XAUTHORITY":"/private", "WAYLAND_SOCKET":"7"}

    def test_only_target_nonroot_session(self):
        env = self.fixture()
        system = {"ID":"pop","VERSION_ID":"24.04"}
        self.assertEqual(str(verify_session(1000,1000,system,"x86_64",env)),
                         "/run/user/1000/wayland-0")
        for uid,euid,os_id,arch in ((0,0,"pop","x86_64"),(1000,0,"pop","x86_64"),
                                     (1000,1000,"ubuntu","x86_64"),(1000,1000,"pop","arm64")):
            with self.assertRaises(ValueError):
                verify_session(uid,euid,{"ID":os_id,"VERSION_ID":"24.04"},arch,env)

    def test_wrong_display_or_runtime_rejected(self):
        for key,value in (("XDG_CURRENT_DESKTOP","GNOME"),("XDG_SESSION_TYPE","x11"),
                          ("XDG_RUNTIME_DIR","/tmp"),("WAYLAND_DISPLAY","../wayland-0"),
                          ("WAYLAND_DISPLAY","/run/user/1000/wayland-0")):
            env = self.fixture()
            env[key] = value
            with self.assertRaises(ValueError):
                verify_session(1000,1000,{"ID":"pop","VERSION_ID":"24.04"},"x86_64",env)

    def test_x11_and_inherited_fd_removed(self):
        original = self.fixture()
        env = wayland_env(original)
        self.assertEqual(env["WAYLAND_DISPLAY"],"wayland-0")
        for key in ("DISPLAY","XAUTHORITY","WAYLAND_SOCKET"):
            self.assertNotIn(key,env)
            self.assertIn(key,original)

    def test_owner_answers_never_implicit(self):
        values = parse_answers("p f n p p p p n n")
        self.assertEqual(len(values),len(CHECKS))
        self.assertEqual(values["navigation"],"OWNER_REPORTED_FAIL")
        self.assertEqual(values["keyboard"],"NOT_TESTED")
        self.assertEqual(values["chinese_ime"],"NOT_TESTED")
        self.assertEqual(parse_answers("p p p p p p p p p")["chinese_ime"],"OWNER_REPORTED_PASS")
        for raw in ("", "p", "p p p p p p p p", "p p p p p p p p yes"):
            with self.assertRaises(ValueError):
                parse_answers(raw)
