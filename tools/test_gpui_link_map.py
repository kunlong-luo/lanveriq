import json
import os
import pathlib
import subprocess
import tempfile
import unittest

from gpui_link_map import archive_index, observe

HEADER = "VMA LMA Size Align Out In Symbol\n"

class LinkMap(unittest.TestCase):
    def test_sections_and_non_observation(self):
        with tempfile.TemporaryDirectory() as tmp:
            deps = pathlib.Path(tmp)
            a, b = deps / "liba-1.rlib", deps / "libb-2.rlib"
            index = {a: ("a", "1"), b: ("b", "2")}
            lines = [HEADER, f"10 10 a 1 {a}(x.o):(.text)\n",
                     f"20 20 1 1 {a}(x.o):(.rodata)\n",
                     f"20 20 0 1 {b}(y.o):(.text)\n",
                     "30 30 1 1 /toolchain/libstd.rlib(std.o):(.text)\n"]
            result = observe(iter(lines), index, deps)
            self.assertEqual(result["observed_packages"], [
                {"name": "a", "version": "1", "nonzero_input_sections": 2, "members": 1}])
            self.assertEqual(result["compiler_rlib_packages_not_observed"], [("b", "2")])
            self.assertEqual(result["unbound_external_rlib_archive_count"], 1)

    def test_unknown_format_and_unbound_local_archive_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            deps = pathlib.Path(tmp)
            a = deps / "liba.rlib"
            index = {a: ("a", "1")}
            for lines in (["GNU ld map\n"], [HEADER],
                          [HEADER, f"10 10 a 1 {deps}/libunknown.rlib(x.o):(.text)\n"]):
                with self.assertRaises(ValueError):
                    observe(iter(lines), index, deps)

    def test_archive_owner_from_exact_cargo_filename(self):
        with tempfile.TemporaryDirectory() as tmp:
            deps = pathlib.Path(tmp)
            path = deps / "build" / "a-123" / "out" / "liba-123.rlib"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"fixture")
            metadata = {"packages": [{"id": "a", "name": "a", "version": "1"}]}
            messages = [{"reason": "compiler-artifact", "package_id": "a",
                         "filenames": [str(path)]}]
            self.assertEqual(archive_index(messages, metadata, deps), {path: ("a", "1")})
            with self.assertRaises(ValueError):
                archive_index([{**messages[0], "package_id": "unknown"}], metadata, deps)

    @unittest.skipUnless(os.environ.get("GPUI_LINK_REAL_TEST") == "1", "explicit Rust fixture only")
    def test_real_cargo_lld_map(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for folder in ("src", "dep/src"):
                (root / folder).mkdir(parents=True)
            (root / "Cargo.toml").write_text(
                '[package]\nname="link-fixture"\nversion="0.1.0"\nedition="2024"\n'
                '[dependencies]\nlink-dep={path="dep"}\n')
            (root / "src/main.rs").write_text(
                'fn main() { println!("{}", link_dep::compute(std::hint::black_box(7))); }\n')
            (root / "dep/Cargo.toml").write_text(
                '[package]\nname="link-dep"\nversion="0.1.0"\nedition="2024"\n')
            (root / "dep/src/lib.rs").write_text(
                '#[inline(never)]\npub fn compute(n: u64) -> u64 { n.wrapping_mul(13) }\n')
            mapping = root / "fixture.map"
            def run(*args):
                return subprocess.check_output(["cargo", "+1.100.0", *args], cwd=root, text=True, timeout=120)
            metadata = json.loads(run("metadata", "--offline", "--format-version=1"))
            messages = [json.loads(line) for line in run(
                "rustc", "--offline", "--locked", "--release", "--message-format=json",
                "--", "-C", f"link-arg=-Wl,-Map={mapping}").splitlines()]
            deps = root / "target/release"
            index = archive_index(messages, metadata, deps)
            with mapping.open() as stream:
                result = observe(iter(stream), index, deps)
            self.assertIn(("link-dep", "0.1.0"), {
                (row["name"], row["version"]) for row in result["observed_packages"]})
