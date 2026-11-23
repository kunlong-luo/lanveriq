"""L2B-5: host-independent negative tests using actual compiler and readelf."""
import importlib.util
import json
import pathlib
import subprocess
import unittest

from test_verify_desktop_deb import DesktopDebPayloadNegatives

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "elf_runtime", HERE / "verify_desktop_elf_runtime_evidence.py"
)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class DirectElfRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.fx = DesktopDebPayloadNegatives("test_synthetic_offline_desktop_shape_is_accepted")
        self.fx.setUp()
        self.addCleanup(self.fx.tearDown)
        self.binary = self.fx.stage / "usr/bin/lanveriq-desktop"
        self.source = self.fx.root / "simple.c"
        self.source.write_text('int main(int argc, char **argv) { return argc == 12345; }\n')
        self.compile()
        old = self.fx.build()
        self.package = self.fx.root / guard.payload.base.PACKAGE
        old.rename(self.package)
        self.root = self.fx.root / "uninstalled"
        self.root.mkdir()
        self.extract()
        self.source_spdx = self.fx.root / "source.spdx.json"
        self.source_spdx.write_text(json.dumps({
            "spdxVersion": "SPDX-2.3", "SPDXID": "SPDXRef-DOCUMENT",
            "packages": [{"name": "tauri"}, {"name": "react"}]
        }))
        self.scan = self.fx.root / "partial.spdx.json"
        self.scan.write_text(json.dumps({
            "spdxVersion": "SPDX-2.3", "SPDXID": "SPDXRef-DOCUMENT",
            "packages": []
        }))
        self.inputs = {}
        for name in guard.payload.base.SOURCE_FILES:
            self.inputs[name] = self.fx.root / name
            self.inputs[name].write_text("fixture-" + name)
        self.sha = "a" * 40
        self.run = 61
        self.attempt = 1
        self.receipt = self.fx.root / "source-receipt.json"
        self.receipt.write_text(json.dumps(guard.payload.base.receipt(
            self.package, self.source_spdx, self.inputs,
            self.sha, self.run, self.attempt
        )))
        self.payload_record = self.fx.root / "payload.json"
        self.payload_record.write_text(json.dumps(guard.payload.evidence(
            self.package, self.root, self.scan, self.receipt, self.source_spdx,
            self.inputs, self.sha, self.run, self.attempt
        )))
        self.output = self.fx.root / "runtime.json"
        self.output.write_text(json.dumps(self.generate()))

    def compile(self, flags=()):
        subprocess.run(
            ["cc", "-fPIE", "-pie", "-o", str(self.binary), str(self.source), *flags],
            check=True, capture_output=True, timeout=20
        )
        self.binary.chmod(0o755)

    def extract(self):
        subprocess.run(["dpkg-deb", "--extract", str(self.package), str(self.root)],
                       check=True, capture_output=True, timeout=20)

    def args(self):
        return (self.package, self.root, self.scan, self.receipt,
                self.source_spdx, self.inputs, self.sha, self.run,
                self.attempt, self.payload_record)

    def generate(self):
        return guard.evidence(*self.args())

    def check(self):
        return guard.verify(self.output, *self.args())

    def readelf(self):
        proc = subprocess.run(
            ["readelf", "-h", "-lW", "-dW", str(self.root / guard.GUI)],
            check=True, capture_output=True, timeout=15
        )
        return proc.stdout

    def test_real_compiled_pie_and_untrusted_runtime_report(self):
        self.assertIn("NOT APPROVED", self.check())
        report = self.generate()
        self.assertIn("libc.so.6", report["elf"]["direct_needed_sonames"])
        self.assertEqual(report["shared_library_license_review"], "OPEN")
        self.assertEqual(report["transitive_runtime_resolution"], "NOT_VERIFIED")
        self.assertFalse(report["install_authorized"])

    def test_direct_lib_and_loader_are_recorded_without_executing_elf(self):
        report = self.generate()["elf"]
        self.assertIn(report["interpreter"], guard.APPROVED_INTERPRETERS)
        self.assertEqual(report["elf_type"], "DYN_PIE")
        self.assertIn(report["gnu_stack"], ("R", "RW"))

    def test_reject_runpath_in_real_compiled_elf(self):
        self.compile(("-Wl,-rpath,/tmp/untrusted",))
        with self.assertRaisesRegex(ValueError, "forbidden dynamic loader metadata"):
            guard.inspect_binary(self.binary)

    def test_reject_modified_interpreter(self):
        self.compile(("-Wl,--dynamic-linker,/tmp/untrusted-loader",))
        with self.assertRaisesRegex(ValueError, "program interpreter"):
            guard.inspect_binary(self.binary)

    def test_reject_non_pie_executable(self):
        subprocess.run(["cc", "-no-pie", "-o", str(self.binary), str(self.source)],
                       check=True, capture_output=True)
        with self.assertRaisesRegex(ValueError, "PIE"):
            guard.inspect_binary(self.binary)

    def test_reject_executable_gnu_stack(self):
        self.compile(("-Wl,-z,execstack",))
        with self.assertRaisesRegex(ValueError, "GNU_STACK"):
            guard.inspect_binary(self.binary)

    def test_reject_unreviewed_direct_soname(self):
        raw = self.readelf()
        modified = raw.replace(
            b"Shared library: [libc.so.6]",
            b"Shared library: [librogue.so.1]\n 0x0000000000000001 (NEEDED) Shared library: [libc.so.6]",
            1
        )
        with self.assertRaisesRegex(ValueError, "unreviewed direct SONAME"):
            guard.inspect_readelf_output(modified)

    def test_exact_real_x86_loader_soname_is_allowed_only_as_pt_interp(self):
        raw = self.readelf()
        original = b"Shared library: [libc.so.6]"
        self.assertIn(original, raw)
        adjusted = raw.replace(
            original,
            original + b"\n 0x0000000000000001 (NEEDED) Shared library: [ld-linux-x86-64.so.2]",
            1,
        )
        report = guard.inspect_readelf_output(adjusted)
        self.assertIn("ld-linux-x86-64.so.2", report["direct_needed_sonames"])

    def test_reject_foreign_loader_as_direct_needed(self):
        raw = self.readelf()
        adjusted = raw.replace(
            b"Shared library: [libc.so.6]",
            b"Shared library: [libc.so.6]\n 0x0000000000000001 (NEEDED) Shared library: [ld-linux-aarch64.so.1]",
            1,
        )
        with self.assertRaisesRegex(ValueError, "unsafe shared library"):
            guard.inspect_readelf_output(adjusted)

    def test_unobserved_library_from_previous_broad_allowlist_is_rejected(self):
        raw = self.readelf()
        adjusted = raw.replace(
            b"Shared library: [libc.so.6]",
            b"Shared library: [libc.so.6]\n 0x0000000000000001 (NEEDED) Shared library: [libX11.so.6]",
            1,
        )
        with self.assertRaisesRegex(ValueError, "unreviewed direct SONAME"):
            guard.inspect_readelf_output(adjusted)

    def test_reject_unsafe_needed_filename(self):
        raw = self.readelf().replace(b"[libc.so.6]", b"[/tmp/libfake.so.1]", 1)
        with self.assertRaisesRegex(ValueError, "unsafe shared library"):
            guard.inspect_readelf_output(raw)

    def test_reject_duplicate_needed(self):
        raw = self.readelf()
        modified = raw.replace(
            b"Shared library: [libc.so.6]",
            b"Shared library: [libc.so.6]\n 0x0000000000000001 (NEEDED) Shared library: [libc.so.6]",
            1
        )
        with self.assertRaisesRegex(ValueError, "duplicate ELF"):
            guard.inspect_readelf_output(modified)

    def test_reject_missing_needed(self):
        raw = self.readelf().replace(b"Shared library: [libc.so.6]",
                                     b"Shared library: [libnotlibc.so.6]")
        with self.assertRaisesRegex(ValueError, "missing expected libc"):
            guard.inspect_readelf_output(raw)

    def test_reject_dynamic_audit_tag(self):
        raw = self.readelf()
        pos = raw.find(b"(NEEDED)")
        self.assertGreater(pos, 0)
        raw = raw[:pos] + raw[pos:].replace(b"(NEEDED)", b"(AUDIT)", 1)
        with self.assertRaisesRegex(ValueError, "forbidden dynamic loader metadata"):
            guard.inspect_readelf_output(raw)

    def test_reject_bad_machine_header(self):
        raw = self.readelf().replace(b"Advanced Micro Devices X86-64", b"ARM AArch64")
        with self.assertRaisesRegex(ValueError, "architecture"):
            guard.inspect_readelf_output(raw)

    def test_reject_corrupt_readelf_text(self):
        with self.assertRaisesRegex(ValueError, "not ELF64"):
            guard.inspect_readelf_output(b"ELF64?")

    def test_reject_tampered_payload_evidence(self):
        doc = json.loads(self.payload_record.read_text())
        doc["release_approved"] = True
        self.payload_record.write_text(json.dumps(doc))
        with self.assertRaisesRegex(ValueError, "forged release"):
            self.check()

    def test_reject_tampered_extracted_elf(self):
        exe = self.root / guard.GUI
        binary = bytearray(exe.read_bytes())
        binary[-1] ^= 1
        exe.write_bytes(binary)
        with self.assertRaisesRegex(ValueError, "digest mismatch"):
            self.check()

    def test_reject_replayed_workflow_attempt(self):
        self.attempt += 1
        with self.assertRaisesRegex(ValueError, "mismatch"):
            self.check()

    def test_reject_swapped_source_spdx(self):
        self.source_spdx.write_bytes(self.source_spdx.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "mismatch"):
            self.check()

    def test_reject_forged_approval_even_when_report_recomputed(self):
        original = json.loads(self.output.read_text())
        for key, value in (("signature", "SIGNED"), ("release_approved", True),
                           ("install_authorized", True), ("licenses_approved", True)):
            with self.subTest(key=key):
                self.output.write_text(json.dumps(original | {key: value}))
                with self.assertRaisesRegex(ValueError, "forged authority"):
                    self.check()

    def test_reject_missing_readelf_tool(self):
        original = guard.subprocess.run
        def shim(args, *rest, **kwargs):
            if args[0] == "readelf":
                raise FileNotFoundError("readelf missing")
            return original(args, *rest, **kwargs)
        from unittest.mock import patch
        with patch.object(guard.subprocess, "run", side_effect=shim):
            with self.assertRaisesRegex(ValueError, "unavailable"):
                guard.inspect_binary(self.root / guard.GUI)


if __name__ == "__main__":
    unittest.main()
