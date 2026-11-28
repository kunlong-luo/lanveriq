import subprocess
import unittest
from unittest.mock import patch
from owner_cosmic_review import (verify_session, wayland_env, parse_answers, CHECKS,
                                review_exit_code, run_review_process)

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

    def test_exit_code_requires_every_observation_and_clean_quit(self):
        passed = parse_answers("p p p p p p p p p")
        self.assertEqual(review_exit_code(0, passed, "y"), 0)
        self.assertEqual(review_exit_code(0, passed, "n"), 2)
        for key, _ in CHECKS:
            with self.subTest(key=key):
                self.assertEqual(review_exit_code(0, {**passed, key: "NOT_TESTED"}, "y"), 2)
                self.assertEqual(review_exit_code(0, {**passed, key: "OWNER_REPORTED_FAIL"}, "y"), 1)
        for code in (1, -9, 124):
            self.assertEqual(review_exit_code(code, passed, "y"), 1)
        self.assertEqual(review_exit_code(1, parse_answers("n n n n n n n n n"), "n"), 1)
        for answers, method in (({}, "y"), ({**passed, "unknown": "NOT_TESTED"}, "y"),
                                ({**passed, "window": "bogus"}, "y"), (passed, "maybe")):
            with self.assertRaises(ValueError):
                review_exit_code(0, answers, method)

    def test_timeout_and_build_failure_are_redacted_and_not_success(self):
        for phase, timeout in (("离线构建", 3600), ("窗口验收", 900)):
            with self.subTest(phase=phase), patch("owner_cosmic_review.subprocess.run",
                    side_effect=subprocess.TimeoutExpired(["/private/path"], timeout)) as run:
                with self.assertRaisesRegex(ValueError, phase + "超时") as error:
                    run_review_process(["/private/path"], phase=phase, timeout=timeout)
                self.assertNotIn("/private/path", str(error.exception))
                run.assert_called_once_with(["/private/path"], timeout=timeout)
        with patch("owner_cosmic_review.subprocess.run", side_effect=subprocess.CalledProcessError(
                101, ["/private/path"], output="private output")):
            with self.assertRaisesRegex(ValueError, "离线构建失败") as error:
                run_review_process(["/private/path"], phase="离线构建", check=True)
            self.assertNotIn("private", str(error.exception))

    def test_real_subprocess_timeout_and_nonzero_status(self):
        import sys
        with self.assertRaisesRegex(ValueError, "窗口验收超时"):
            run_review_process([sys.executable, "-c", "import time; time.sleep(5)"],
                               phase="窗口验收", timeout=0.1)
        result = run_review_process([sys.executable, "-c", "raise SystemExit(7)"],
                                    phase="窗口验收", timeout=5)
        self.assertEqual(result.returncode, 7)
