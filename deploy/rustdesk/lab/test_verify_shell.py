#!/usr/bin/env python3
"""Reject malformed embedded POSIX-shell diagnostics BEFORE Docker lab build.

Prior bug: a JavaScript replacement containing '$\'' accidentally interpreted
that as a substitution token and silently truncated shell embedded in valid
Python triple quoted strings. Python py_compile alone cannot detect this.
"""
from __future__ import annotations

import ast
import pathlib
import subprocess
import unittest

SCRIPT = pathlib.Path(__file__).with_name("run_remote_session.py")
FUNCTIONS = {
    "try_operator_accept": "click",
    "diagnosis": "script",
}


def literal_shell(func: str, variable: str) -> str:
    tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
    matches = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == func]
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {func}")
    for node in ast.walk(matches[0]):
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Constant):
            if isinstance(node.value.value, str) and any(
                isinstance(t, ast.Name) and t.id == variable for t in node.targets
            ):
                return node.value.value
    raise ValueError(f"Missing static {variable} shell from {func}")


class ShellPreflight(unittest.TestCase):
    def test_embedded_posix_shell_parses_under_actual_sh_interpreter(self):
        for fn, var in FUNCTIONS.items():
            with self.subTest(function=fn):
                code = literal_shell(fn, var)
                proc = subprocess.run(["sh", "-n"], input=code, text=True,
                                      capture_output=True, timeout=4)
                self.assertEqual(proc.returncode, 0,
                                 f"POSIX shell syntax invalid in {fn}")
                self.assertGreater(len(code), 200)

    def test_operator_click_has_no_unrestricted_x11_window_search(self):
        script = literal_shell("try_operator_accept", "click")
        self.assertIn("/tmp/r2-cm.pid", script)
        self.assertIn("readlink", script)
        self.assertIn("--pid", script)
        self.assertIn("xdotool", script)
        self.assertNotIn("--name '.*'", script)
        self.assertNotIn("windowactivate --sync", script)

    def test_diagnostic_shell_references_no_private_key_material(self):
        shell = literal_shell("diagnosis", "script")
        self.assertNotIn("id_ed25519", shell)
        self.assertNotIn("cat /data", shell)
        self.assertIn("native_cm_process", shell)


if __name__ == "__main__":
    unittest.main()
