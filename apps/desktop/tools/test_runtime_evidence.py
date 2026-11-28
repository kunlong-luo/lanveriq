#!/usr/bin/env python3
import unittest
from runtime_evidence import parse_maps, system_library, MAX_MAP_BYTES

class RuntimeEvidence(unittest.TestCase):
    def test_only_system_executable_libraries(self):
        raw = ("0000-1000 r-xp 0 00:00 1 /tmp/candidate\n"
               "1000-2000 r-xp 0 00:00 1 /usr/lib/x86_64-linux-gnu/libc.so.6\n"
               "2000-3000 r--p 0 00:00 1 /usr/lib/x86_64-linux-gnu/libc.so.6\n"
               "3000-4000 r-xp 0 00:00 1 [vdso]\n"
               "4000-5000 r-xp 0 00:00 1 /home/private/unknown.so\n")
        libs, unknown = parse_maps(raw, "/tmp/candidate")
        self.assertEqual(libs, ["/usr/lib/x86_64-linux-gnu/libc.so.6"])
        self.assertEqual(unknown, 1)

    def test_path_allowlist(self):
        for path in ("/usr/lib/x86_64-linux-gnu/libxcb.so.1", "/lib64/ld-linux-x86-64.so.2",
                     "/usr/lib/x86_64-linux-gnu/dri/swrast_dri.so"):
            self.assertTrue(system_library(path))
        for path in ("/tmp/lib.so", "/usr/lib/x86_64-linux-gnu/../private.so",
                     "/usr/lib/x86_64-linux-gnu/lib.so (deleted)", "/usr/lib/x86_64-linux-gnu/font.ttf"):
            self.assertFalse(system_library(path))

    def test_missing_malformed_and_excessive_rejected(self):
        for raw in ("", "bad", "x" * (MAX_MAP_BYTES + 1)):
            with self.assertRaises(ValueError):
                parse_maps(raw, "/tmp/candidate")

if __name__ == "__main__":
    unittest.main()
