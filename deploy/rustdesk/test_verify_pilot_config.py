#!/usr/bin/env python3
"""Synthetic-only checks for the RustDesk pilot Compose security contract."""
from __future__ import annotations

import copy
import importlib.util
import io
import json
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import TestCase, main, mock


spec = importlib.util.spec_from_file_location(
    "verify_pilot_config", Path(__file__).with_name("verify_pilot_config.py")
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def fixture():
    return {
        "name": "lanveriq-rustdesk-pilot",
        "services": {
            "rustdesk-pilot": {
                "image": guard.PILOT_IMAGE,
                "entrypoint": ["/bin/sh", "-ec", guard.PILOT_ENTRYPOINT],
                "profiles": ["pilot"],
                "restart": "no",
                "environment": {
                    "RELAY": "localhost",
                    "ENCRYPTED_ONLY": "1",
                    "ALWAYS_USE_RELAY": "N",
                    "RUST_LOG": "info",
                },
                "networks": {"rustdesk_pilot_only": None},
                "ports": [
                    {"host_ip": "127.0.0.1", "target": 21115, "published": "21115", "protocol": "tcp"},
                    {"host_ip": "127.0.0.1", "target": 21116, "published": "21116", "protocol": "tcp"},
                    {"host_ip": "127.0.0.1", "target": 21116, "published": "21116", "protocol": "udp"},
                    {"host_ip": "127.0.0.1", "target": 21117, "published": "21117", "protocol": "tcp"},
                ],
                "volumes": [{"type": "volume", "source": "lanveriq-rustdesk-pilot_rustdesk_pilot_state",
                             "target": "/data"}],
            }
        },
        "networks": {"rustdesk_pilot_only": {"name": "lanveriq-rustdesk-pilot_rustdesk_pilot_only"}},
    }


class RustDeskPilotSecurityTests(TestCase):
    def accepts(self, config=None, bind="127.0.0.1", relay="localhost"):
        return guard.check_contract(config if config is not None else fixture(), bind, relay)

    def rejects(self, mutate, bind="127.0.0.1", relay="localhost"):
        config = fixture()
        mutate(config)
        with self.assertRaises(guard.ConfigurationRejected):
            self.accepts(config, bind, relay)

    def test_valid_loopback_only(self):
        result = self.accepts()
        self.assertEqual(result["result"], "PASS_STATIC_CONFIG_ONLY")
        self.assertFalse(result["network_reachability_proven"])
        self.assertFalse(result["websocket_ports_exposed"])

    def test_lan_private_ip_allowed_if_explicitly_same_as_rendered(self):
        config = fixture()
        for port in config["services"]["rustdesk-pilot"]["ports"]:
            port["host_ip"] = "192.168.50.10"
        config["services"]["rustdesk-pilot"]["environment"]["RELAY"] = "relay.lanveriq.net"
        self.assertEqual(self.accepts(config, "192.168.50.10", "relay.lanveriq.net")["native_port_count"], 4)

    def test_explicit_mesh_vpn_address_is_allowed_but_never_wildcard(self):
        config = fixture()
        for port in config["services"]["rustdesk-pilot"]["ports"]:
            port["host_ip"] = "100.100.100.20"
        config["services"]["rustdesk-pilot"]["environment"]["RELAY"] = "rd.lanveriq.net"
        self.assertEqual(self.accepts(config, "100.100.100.20", "rd.lanveriq.net")["native_port_count"], 4)
        with self.assertRaises(guard.ConfigurationRejected):
            guard.approved_ip("100.128.0.1")  # outside RFC 6598 shared address space

    def test_unsafe_ip_rejected(self):
        for address in ("0.0.0.0", "::", "8.8.8.8", "169.254.1.1", "224.0.0.1", "bad-ip"):
            with self.subTest(address=address), self.assertRaises(guard.ConfigurationRejected):
                guard.approved_ip(address)

    def test_rejects_placeholder_or_unqualified_relay(self):
        for host in ("", "foo", "relay.example.com", "relay.example.org",
                     "relay.example.invalid", "http://relay.lanveriq.net", "relay.lanveriq.net:21117"):
            with self.subTest(host=host), self.assertRaises(guard.ConfigurationRejected):
                guard.approved_relay(host, "192.168.1.1")
        with self.assertRaises(guard.ConfigurationRejected):
            guard.approved_relay("localhost", "192.168.1.1")

    def test_digest_or_image_mutation_rejected(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(
            image="ghcr.io/rustdesk/rustdesk-server-s6:latest"))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(
            image=guard.PILOT_IMAGE.replace("5974cd", "000000")))

    def test_requires_secure_first_run_key_creation_umask(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(entrypoint=["/init"]))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(
            entrypoint=["/bin/sh", "-ec", guard.PILOT_ENTRYPOINT.replace("chmod 0600", "chmod 0644")]))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(entrypoint=None))

    def test_compose_config_escapes_awk_field_dollars_only(self):
        config = fixture()
        config["services"]["rustdesk-pilot"]["entrypoint"][2] = guard.PILOT_ENTRYPOINT.replace("$3", "$$3")
        self.assertEqual(self.accepts(config)["result"], "PASS_STATIC_CONFIG_ONLY")
        config["services"]["rustdesk-pilot"]["entrypoint"][2] = guard.PILOT_ENTRYPOINT.replace("$3", "$9")
        with self.assertRaises(guard.ConfigurationRejected):
            self.accepts(config)

    def test_wss_and_extra_exposed_ports_rejected(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["ports"].append(
            {"host_ip": "127.0.0.1", "target": 21118, "published": "21118", "protocol": "tcp"}))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["ports"][0].update(
            target=21119, published="21119"))

    def test_public_unapproved_host_binding_rejected(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["ports"][0].update(host_ip="0.0.0.0"))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["ports"][0].update(host_ip="192.168.1.1"))

    def test_encryption_and_relay_controls_must_remain_pinned(self):
        for key, value in (("ENCRYPTED_ONLY", "0"), ("RELAY", "another.net"), ("ALWAYS_USE_RELAY", "Y")):
            with self.subTest(key=key):
                self.rejects(lambda d: d["services"]["rustdesk-pilot"]["environment"].update({key: value}))

    def test_rejects_automatic_restarts_and_bypassed_profile(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(restart="unless-stopped"))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(profiles=[]))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(network_mode="host"))

    def test_no_shared_network_or_host_path(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["networks"].update(spark_db=None))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"]["volumes"][0].update(
            type="bind", source="/home/spark"))
        self.rejects(lambda d: d["networks"]["rustdesk_pilot_only"].update(external=True))

    def test_no_privileged_or_new_host_devices(self):
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(privileged=True))
        self.rejects(lambda d: d["services"]["rustdesk-pilot"].update(cap_add=["NET_ADMIN"]))

    def test_json_cli_is_read_only_and_reports_failure(self):
        with mock.patch.object(guard.sys, "stdin", io.StringIO(json.dumps(fixture()))):
            out = io.StringIO()
            with redirect_stdout(out):
                self.assertEqual(guard.main(["--bind-ip", "127.0.0.1", "--relay-host", "localhost"]), 0)
        self.assertIn("PASS_STATIC_CONFIG_ONLY", out.getvalue())
        bad = fixture()
        bad["services"]["rustdesk-pilot"]["ports"][0]["host_ip"] = "0.0.0.0"
        with mock.patch.object(guard.sys, "stdin", io.StringIO(json.dumps(bad))):
            err = io.StringIO()
            with redirect_stderr(err):
                self.assertEqual(guard.main(["--bind-ip", "127.0.0.1", "--relay-host", "localhost"]), 2)
        self.assertIn("FAIL_STATIC_CONFIG", err.getvalue())


if __name__ == "__main__":
    main()
