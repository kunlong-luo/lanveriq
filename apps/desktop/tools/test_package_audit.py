#!/usr/bin/env python3
import pathlib
import tarfile
import unittest
import package_audit as audit

def entry(name, kind=tarfile.REGTYPE, mode=0o644):
    member = tarfile.TarInfo(name)
    member.type = kind
    member.mode = mode
    member.uid = member.gid = 0
    return member

def fixture():
    control = {"control": entry("control")}
    payload = {name: entry(name, mode=mode) for name, mode in audit.FILES.items()}
    for name in audit.FILES:
        for parent in pathlib.PurePosixPath(name).parents:
            if str(parent) != ".":
                payload[str(parent)] = entry(str(parent), tarfile.DIRTYPE, 0o755)
    return control, payload

class PackageAudit(unittest.TestCase):
    def test_exact_known_diagnostic_pair(self):
        warning = (audit.DIVERSION_HEADER + "\n diversion by libc6 from: " + audit.LOADER + "\n"
                   + audit.DIVERSION_HEADER + "\n diversion by libc6 to: " + audit.DIVERTED + "\n"
                   + audit.LAYOUT_WARNING + "\n")
        self.assertEqual(audit.classify_diagnostics(warning),
                         {"loader_diversion": True, "synthetic_layout": True})
        self.assertEqual(audit.classify_diagnostics(""), {"loader_diversion": False, "synthetic_layout": False})
        for bad in (warning.replace("libc6 from:", "unknown from:"), warning.replace(audit.DIVERTED, "/tmp/loader"),
                    audit.DIVERSION_HEADER, warning + "unexpected warning\n",
                    audit.LAYOUT_WARNING + "\n" + audit.LAYOUT_WARNING,
                    audit.DIVERSION_HEADER + "\n diversion by libc6 from: " + audit.LOADER):
            with self.assertRaises(ValueError):
                audit.classify_diagnostics(bad)

    def test_installed_floor_requires_complete_direct_set(self):
        with self.assertRaises(ValueError):
            audit.verify_installed_floors([[{"name":"libc6","op":">=","version":"2.39"}]], None)

    def test_exact_archive(self):
        audit.validate_members(*fixture())

    def test_extra_hooks_and_service_paths_rejected(self):
        for name in ("postinst", "prerm", "triggers", "conffiles"):
            control, payload = fixture()
            control[name] = entry(name)
            with self.assertRaises(ValueError):
                audit.validate_members(control, payload)
        for name in ("usr/bin/lanveriq-agent", "usr/lib/systemd/system/lanveriq-agent.service",
                     "etc/xdg/autostart/lanveriq.desktop", "usr/share/polkit-1/actions/agent.policy"):
            control, payload = fixture()
            payload[name] = entry(name)
            with self.assertRaises(ValueError):
                audit.validate_members(control, payload)

    def test_links_privileged_modes_and_wrong_ownership_rejected(self):
        for kind, mode, uid in ((tarfile.SYMTYPE, 0o755, 0), (tarfile.LNKTYPE, 0o755, 0),
                                (tarfile.REGTYPE, 0o4755, 0), (tarfile.REGTYPE, 0o755, 1000)):
            control, payload = fixture()
            bad = entry(audit.EXECUTABLE, kind, mode)
            bad.uid = uid
            payload[audit.EXECUTABLE] = bad
            with self.assertRaises(ValueError):
                audit.validate_members(control, payload)

    def test_abi_allowlist_rejects_extras_and_alternatives(self):
        libc = {"name": "libc6", "op": ">=", "version": "2.38"}
        audit.validate_relations([[libc]])
        for groups in ([], [[libc, libc]], [[libc], [libc]], [[{"name": "lanveriq-agent", "op": "", "version": ""}]],
                       [[libc], [{"name": "libwebkit2gtk-4.1-0", "op": "", "version": ""}]]):
            with self.assertRaises(ValueError):
                audit.validate_relations(groups)

if __name__ == "__main__":
    unittest.main()
