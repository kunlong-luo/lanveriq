import unittest
from clean_apt import parse, audit

DIGEST = "a" * 64
GROUPS = [[{"name": n, "op": ">=", "version": "1"}] for n in sorted(audit.DIRECT)]
def compare(a,b):
    return (int(a)>int(b)) - (int(a)<int(b))

def fixture():
    lines = ["LNV_RELEASE ubuntu:24.04", "LNV_ARCH amd64", "LNV_BASE_COUNT 100",
             "LNV_BASE_GUI_LIBS absent", "LNV_PACKAGE_SHA "+DIGEST, "LNV_DPKG unchanged",
             "LNV_BASE libc6 2", "LNV_BASE libgcc-s1 2"]
    for name in ("libxcb1", "libxkbcommon0", "libxkbcommon-x11-0"):
        lines += ["LNV_BASE "+name+" MISSING", "Inst "+name+" (2 Ubuntu [amd64])",
                  "Conf "+name+" (2 Ubuntu [amd64])"]
    lines += ["Inst "+audit.NAME+" ("+audit.VERSION+" local-deb [amd64])",
              "Conf "+audit.NAME+" ("+audit.VERSION+" local-deb [amd64])"]
    return "\n".join(lines)

class CleanApt(unittest.TestCase):
    def test_clean_plan(self):
        rec = parse(fixture(), DIGEST, GROUPS, compare)
        self.assertEqual(rec["planned_install_count"], 4)
        self.assertEqual(len(rec["direct_dependencies"]), 5)
        self.assertFalse(rec["direct_dependencies"]["libc6"]["planned"])

    def test_fail_closed(self):
        for bad in (fixture().replace(DIGEST, "b"*64),
                    fixture().replace("LNV_DPKG unchanged","LNV_DPKG changed"),
                    fixture().replace("LNV_BASE libxcb1 MISSING","LNV_BASE libxcb1 2"),
                    fixture().replace("libxcb1 (2","libxcb1 (0"),
                    fixture().replace("Conf libxcb1","Conf libunknown"),
                    fixture()+"\nRemv libc6 [2]", fixture()+"\nInst lanveriq-agent (2 local [amd64])",
                    fixture()+"\nInst libxcb1 (2 Ubuntu [amd64])"):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    parse(bad, DIGEST, GROUPS, compare)

if __name__ == "__main__":
    unittest.main()
