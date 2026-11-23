"""L2B-8 hostile dpkg-shlibdeps packaging metadata comparison."""
import importlib.util
import pathlib
import unittest
P = pathlib.Path(__file__).resolve().parent
s = importlib.util.spec_from_file_location("symbols_audit", P / "verify_desktop_deb_shlibdeps_audit.py")
v = importlib.util.module_from_spec(s)
s.loader.exec_module(v)


class FakeApt:
    @staticmethod
    def parse_depends(value, strip_multiarch=False):
        answer = []
        for item in value.split(","):
            group = []
            for part in item.split("|"):
                name = part.strip()
                if " (" in name:
                    package, constraint = name.split(" (", 1)
                    op, version = constraint[:-1].split(" ", 1)
                    group.append((package, version, op))
                else:
                    group.append((name, "", ""))
            answer.append(group)
        return answer
    @staticmethod
    def version_compare(a, b):
        def nums(x):
            return tuple(map(int, x.split(".")))
        return (nums(a)>nums(b))-(nums(a)<nums(b))


class ShlibdepsAuditTests(unittest.TestCase):
    def p(self, string):
        return v.parse_relations(string, FakeApt)
    def gaps(self, wanted, actual):
        return v.compare_requirements(self.p(wanted), self.p(actual), FakeApt)

    def test_absent_package_is_a_gap(self):
        r = self.gaps("libc6 (>= 2.38), libgtk-3-0t64 (>= 3.24)",
                      "libgtk-3-0")
        self.assertEqual(r["direct_declaration_gap_count"], 2)

    def test_unversioned_archive_is_too_weak(self):
        self.assertEqual(len(self.gaps("libc6 (>= 2.38)", "libc6")
                             ["declared_but_insufficient_constraint_groups"]), 1)

    def test_old_version_floor_is_too_weak(self):
        self.assertEqual(self.gaps("libc6 (>= 2.40)", "libc6 (>= 2.38)")
                         ["direct_declaration_gap_count"], 1)

    def test_matching_minimum_accepted_for_comparison_only(self):
        self.assertEqual(self.gaps("libc6 (>= 2.38)", "libc6 (>= 2.38)")
                         ["direct_declaration_gap_count"], 0)

    def test_stronger_floor_satisfies_minimum(self):
        self.assertEqual(self.gaps("libc6 (>= 2.38)", "libc6 (>= 2.39)")
                         ["direct_declaration_gap_count"], 0)

    def test_versioned_alt_can_be_covered_without_guessing(self):
        self.assertEqual(self.gaps("liba (>= 1.0) | libb", "libb")
                         ["direct_declaration_gap_count"], 0)

    def test_wrong_alt_is_reported_missing(self):
        self.assertEqual(self.gaps("liba | libb", "libc")
                         ["direct_declaration_gap_count"], 1)

    def test_exact_requirement_rejects_higher_equal_version(self):
        self.assertFalse(v.covers({"name":"libc6","version":"2.38","op":"="},
                                  {"name":"libc6","version":"2.39","op":"="},FakeApt))

    def test_corrupt_official_output_denied(self):
        with self.assertRaisesRegex(ValueError, "empty/oversized official"):
            v.parse_generated(b"shlibs:Other=bad")

    def test_multiple_substvars_lines_denied(self):
        with self.assertRaisesRegex(ValueError, "unexpected official"):
            v.parse_generated(b"shlibs:Depends=libc6 (>= 2.38)\nother=bad\n")

    def test_unresolved_substitution_denied(self):
        with self.assertRaisesRegex(ValueError, "unresolved"):
            v.parse_generated(b"shlibs:Depends=" + bytes((36,)) + b"{foo}")

    def test_dependency_newline_denied(self):
        with self.assertRaisesRegex(ValueError, "multiline"):
            self.p("libc6\nnew-header: bad")

    def test_missing_dependency_denied(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            self.p("")

    def test_invalid_apt_relation_denied(self):
        class Bad:
            @staticmethod
            def parse_depends(*args, **kwargs):
                return [[("libc6","2.38","???")]]
        with self.assertRaisesRegex(ValueError, "unsafe parsed"):
            v.parse_relations("libc6", Bad)

    def test_forged_apt_release_denied(self):
        obj={"schema":"lanveriq.desktop.untrusted-apt-installed-satisfier-v1",
             "source_sha":"a"*40,"run_id":1,"attempt":1,
             "actual_elf_sha256":"c"*64,
             "license_review":"OPEN_NOT_APPROVED","release_approved":True,
             "install_authorized":False}
        with self.assertRaisesRegex(ValueError, "forged or changed"):
            v.validate_prior(obj,{"elf_sha256":"c"*64},"a"*40,1,1)

    def test_mismatched_elf_hash_denied(self):
        obj={"schema":"lanveriq.desktop.untrusted-apt-installed-satisfier-v1",
             "source_sha":"a"*40,"run_id":1,"attempt":1,
             "actual_elf_sha256":"c"*64,
             "license_review":"OPEN_NOT_APPROVED","release_approved":False,
             "install_authorized":False}
        with self.assertRaisesRegex(ValueError, "forged or changed"):
            v.validate_prior(obj,{"elf_sha256":"d"*64},"a"*40,1,1)

    def test_symlinked_elf_refused(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path=pathlib.Path(directory)/"symlink"
            path.symlink_to("/usr/bin/ls")
            with self.assertRaisesRegex(ValueError, "unapproved executable"):
                v.official_deps(path)

    def test_real_python_apt_integration_when_available(self):
        try:
            import apt_pkg
        except ImportError:
            self.skipTest("real apt_pkg is exercised in Ubuntu CI")
        apt_pkg.init_system()
        groups=v.parse_relations("libc6 (>= 2.38), libgtk-3-0t64 (>= 3.24.41) | libgtk-3-0", apt_pkg)
        self.assertEqual(len(groups),2)
        self.assertEqual(len(groups[1]),2)

    def test_normal_substvars_is_parsed(self):
        self.assertEqual(v.parse_generated(b"shlibs:Depends=libc6 (>= 2.38)"),
                         "libc6 (>= 2.38)")

    def test_no_release_status_from_comparison(self):
        self.assertEqual(self.gaps("libc6","libc6")["direct_declaration_gap_count"],0)


if __name__ == "__main__":
    unittest.main()
