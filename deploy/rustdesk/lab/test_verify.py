#!/usr/bin/env python3
"""Synthetic unit checks for isolated RustDesk peer-registration evidence."""
import copy
import importlib.util
import pathlib
import sqlite3
import tempfile
from unittest import TestCase, main, mock
from types import SimpleNamespace
import run as lab_run


module_path = pathlib.Path(__file__).with_name("verify.py")
spec = importlib.util.spec_from_file_location("r2_verify", module_path)
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


def valid_topology():
    names = ("rd-server", "peer-a", "peer-b")
    services = {}
    for name in names:
        services[name] = {
            "image": verify.SERVER_IMAGE if name == "rd-server" else verify.CLIENT_IMAGE,
            "restart": "no",
            "networks": {"r2_internal": None},
        }
    services["rd-server"]["environment"] = {
        "RELAY": "rd-server:21117",
        "ENCRYPTED_ONLY": "1",
        "ALWAYS_USE_RELAY": "N",
        "RUST_LOG": "info",
    }
    services["rd-server"]["volumes"] = [
        {"type": "volume", "source": "lanveriq-rustdesk-r2-synthetic_r2_server_state", "target": "/data"}
    ]
    for name in ("peer-a", "peer-b"):
        services[name].update({
            "pull_policy": "never", "cap_drop": ["ALL"],
            "security_opt": ["no-new-privileges:true"],
        })
    return {
        "services": services,
        "networks": {"r2_internal": {"internal": True}},
    }


class RealPeerProofUnitTests(TestCase):
    def test_accepts_only_private_sandbox(self):
        self.assertEqual(verify.verify_topology(valid_topology())["clients"], 2)

    def test_rejects_public_network_or_ports(self):
        for modify in (
            lambda d: d["networks"]["r2_internal"].update(internal=False),
            lambda d: d["services"]["rd-server"].update(ports=[{"published": "21117", "target": 21117}]),
            lambda d: d["services"]["peer-a"].update(network_mode="host"),
            lambda d: d["services"]["peer-b"].update(privileged=True),
            lambda d: d["services"]["peer-a"].update(cap_add=["SYS_ADMIN"]),
            lambda d: d["services"]["peer-a"].update(networks={"other": None}),
        ):
            d = valid_topology()
            modify(d)
            with self.subTest(modify=modify), self.assertRaises(verify.EvidenceRejected):
                verify.verify_topology(d)

    def test_rejects_unapproved_images_and_mounts(self):
        for modify in (
            lambda d: d["services"]["rd-server"].update(image="rustdesk/rustdesk-server:latest"),
            lambda d: d["services"]["peer-a"].update(image="rustdesk:latest"),
            lambda d: d["services"]["peer-b"].update(pull_policy="always"),
            lambda d: d["services"]["rd-server"].update(volumes=[{
                "type": "bind", "source": "/var/lib/postgresql", "target": "/data"}]),
            lambda d: d["services"]["peer-a"].update(volumes=[{"type": "bind", "source": "/etc", "target": "/etc"}]),
            lambda d: d["services"]["rd-server"]["environment"].update(ENCRYPTED_ONLY="0"),
        ):
            d = valid_topology()
            modify(d)
            with self.subTest(modify=modify), self.assertRaises(verify.EvidenceRejected):
                verify.verify_topology(d)

    def test_registration_requires_two_genuine_distinct_db_rows(self):
        with tempfile.TemporaryDirectory() as tmp:
            dbfile = pathlib.Path(tmp) / "test.sqlite"
            with sqlite3.connect(dbfile) as db:
                db.execute("CREATE TABLE peer (id TEXT)")
                db.executemany("INSERT INTO peer (id) VALUES (?)", [("a",), ("b",)])
                db.commit()
            self.assertEqual(verify.check_registered_db(dbfile), 2)
            with sqlite3.connect(dbfile) as db:
                db.execute("INSERT INTO peer (id) VALUES ('a')")
                db.commit()
            with self.assertRaises(verify.EvidenceRejected):
                verify.check_registered_db(dbfile)

    def test_sqlite_snapshot_uses_supported_compose_cp_without_quiet_flag(self):
        with mock.patch.object(lab_run.subprocess, "run", return_value=SimpleNamespace(returncode=0)) as call:
            lab_run.registration_snapshot(pathlib.Path("synthetic-registrations.sqlite3"))
        self.assertEqual(call.call_count, 3)
        for invocation in call.call_args_list:
            args = invocation.args[0]
            self.assertEqual(args[len(lab_run.CMD)], "cp")
            self.assertNotIn("-q", args)
            self.assertTrue(args[len(lab_run.CMD) + 1].startswith("rd-server:/data/db_v2.sqlite3"))

    def test_failed_sqlite_copy_never_masquerades_as_no_peers(self):
        with mock.patch.object(lab_run.subprocess, "run", return_value=SimpleNamespace(returncode=2)):
            with self.assertRaisesRegex(verify.EvidenceRejected, "snapshot copy failed"):
                lab_run.registration_snapshot(pathlib.Path("synthetic-registrations.sqlite3"))

    def test_registration_rejects_missing_or_untrusted_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "missing.sqlite"
            with self.assertRaises(verify.EvidenceRejected):
                verify.check_registered_db(path)
            path.write_text("not sqlite")
            with self.assertRaises(verify.EvidenceRejected):
                verify.check_registered_db(path)


if __name__ == "__main__":
    main()
