#!/usr/bin/env python3
"""Purely synthetic L1 tests; never open a real socket."""
import importlib.util
import io
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path
from unittest import TestCase, main, mock


script = Path(__file__).with_name("verify_dual_access.py")
spec = importlib.util.spec_from_file_location("verify_dual_access", script)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class FakeSocket:
    def getpeername(self):
        return ("10.42.0.12", 443)


class FakeResponse:
    status = 200

    def getheader(self, name):
        return None


class FakeConnection:
    sock = FakeSocket()

    def __init__(self, status=200, location=None):
        self.resp = FakeResponse()
        self.resp.status = status
        self.location = location
        self.closed = False

    def connect(self):
        pass

    def request(self, method, path, headers):
        assert method == "GET"
        assert not any(key.lower() == "authorization" for key in headers)

    def getresponse(self):
        self.resp.getheader = lambda name: self.location if name == "Location" else None
        return self.resp

    def close(self):
        self.closed = True


class NetworkProbeContractTests(TestCase):
    def test_accepts_only_plain_https_origin(self):
        self.assertEqual(probe.validate_origin("https://console.example.org").hostname, "console.example.org")
        for bad in (
            "http://console.example.org",
            "https://operator:pass@console.example.org",
            "https://console.example.org/path",
            "https://console.example.org?token=abc",
            "https://console.example.org/#section",
            "https://console.example.org:bad",
        ):
            with self.subTest(bad=bad), self.assertRaises(probe.ProbeError):
                probe.validate_origin(bad)

    def test_rejects_cross_origin_or_insecure_redirect(self):
        for target in ("http://console.example.org/login", "https://other.example.org/login", "//evil.example.org/"):
            with self.subTest(target=target), self.assertRaises(probe.ProbeError):
                probe.validate_redirect("https://console.example.org", target)
        probe.validate_redirect("https://console.example.org", "/login?next=%2Fdevices")

    def test_peer_address_bound_to_requested_network(self):
        probe.validate_peer("10.42.0.12", ["10.42.0.0/16"])
        with self.assertRaises(probe.ProbeError):
            probe.validate_peer("198.51.100.2", ["10.42.0.0/16"])

    def test_anonymous_api_cannot_appear_authenticated(self):
        for status in (200, 302, 404, 500):
            with self.subTest(status=status), self.assertRaises(probe.ProbeError):
                probe.validate_status("/api/v1/devices", status)
        for status in (401, 403):
            probe.validate_status("/api/v1/devices", status)

    def test_console_503_cannot_be_marked_as_reachable(self):
        with self.assertRaises(probe.ProbeError):
            probe.validate_status("/login", 503)

    def test_verified_https_passes_when_network_and_auth_boundary_correct(self):
        instances = []
        for status, path in ((200, "/login"), (403, "/api/v1/devices")):
            fake = FakeConnection(status)
            instances.append(fake)
            with mock.patch.object(probe.http.client, "HTTPSConnection", return_value=fake):
                result = probe.probe_https("https://console.example.org", path, 2.0, ["10.42.0.0/16"])
            self.assertTrue(result["tls_verified"])
            self.assertEqual(result["http_status"], status)
        self.assertTrue(all(instance.closed for instance in instances))

    def test_public_peer_does_not_pass_required_private_cidr(self):
        fake = FakeConnection()
        with mock.patch.object(probe.http.client, "HTTPSConnection", return_value=fake):
            with self.assertRaises(probe.ProbeError):
                probe.probe_https("https://console.example.org", "/login", 2.0, ["192.168.0.0/16"])
        self.assertTrue(fake.closed)

    def test_main_is_read_only_and_explicitly_network_only(self):
        out = io.StringIO()
        with mock.patch.object(probe, "probe_https", side_effect=[
            {"path": "/login", "http_status": 200, "tls_verified": True},
            {"path": "/api/v1/devices", "http_status": 403, "tls_verified": True},
        ]) as mocked, redirect_stdout(out):
            status = probe.main(["--console-origin", "https://console.example.org", "--vantage", "wan"])
        self.assertEqual(status, 0)
        self.assertEqual(mocked.call_count, 2)
        self.assertIn('"verdict": "PASS_NETWORK_ONLY"', out.getvalue())
        self.assertIn('"vantage_independently_verified": false', out.getvalue())

    def test_main_fails_closed(self):
        err = io.StringIO()
        with mock.patch.object(probe, "probe_https", side_effect=probe.ProbeError("unreachable")):
            with redirect_stderr(err):
                code = probe.main(["--console-origin", "https://console.example.org", "--vantage", "lan"])
        self.assertEqual(code, 2)
        self.assertIn('"verdict": "FAIL"', err.getvalue())


if __name__ == "__main__":
    main()
