"""No-network unit regression for synthetic-only mTLS backend check labels."""

import hashlib
import importlib.util
import os
from email.message import Message
from pathlib import Path
import unittest
from unittest.mock import patch


MODULE = Path(__file__).with_name("synthetic-mtls-backend.py")
spec = importlib.util.spec_from_file_location("synthetic_mtls_backend", MODULE)
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)


class SyntheticOnlyBackendPolicyTest(unittest.TestCase):
    def setUp(self):
        self.proxy = b"synthetic-proxy-certificate-der-test-bytes"
        self.agent_fp = hashlib.sha256(b"synthetic-agent").hexdigest()
        self.device = "00000000-0000-7000-8000-000000000001"
        self.path = f"/api/v1/devices/{self.device}/activate"
        self.env = patch.dict(os.environ, {
            "SYNTHETIC_DEVICE_ID": self.device,
            "SYNTHETIC_PROXY_SHA256": hashlib.sha256(self.proxy).hexdigest(),
            "SYNTHETIC_AGENT_SHA256": self.agent_fp,
        })
        self.env.start()
        self.addCleanup(self.env.stop)

    def headers(self):
        value = Message()
        value["Host"] = "agent.lanveriq.internal"
        value["X-Lanveriq-Verified-Client-Fingerprint"] = self.agent_fp
        return value

    def failures(self, headers=None, *, cert=None, path=None):
        return backend.failed_synthetic_checks(
            self.proxy if cert is None else cert,
            self.headers() if headers is None else headers,
            self.path if path is None else path,
        )

    def test_authorized_synthetic_certificate_and_exact_header_pass(self):
        self.assertEqual([], self.failures())

    def test_wrong_proxy_tls_client_identity_fails(self):
        self.assertIn("proxy_certificate", self.failures(cert=b"forged-proxy"))

    def test_missing_or_duplicated_fingerprint_fails_closed(self):
        headers = self.headers()
        del headers["X-Lanveriq-Verified-Client-Fingerprint"]
        self.assertIn("fingerprint_count", self.failures(headers))
        headers = self.headers()
        headers["X-Lanveriq-Verified-Client-Fingerprint"] = "forged"
        self.assertIn("fingerprint_count", self.failures(headers))

    def test_forged_fingerprint_fails_closed(self):
        headers = self.headers()
        headers.replace_header("X-Lanveriq-Verified-Client-Fingerprint", "0" * 64)
        self.assertIn("fingerprint_value", self.failures(headers))

    def test_extra_attacker_identity_header_rejected(self):
        headers = self.headers()
        headers["x-lAnveriq-vERIfied-cLIent-alias"] = "forged"
        self.assertIn("unexpected_identity_header", self.failures(headers))

    def test_trusted_forwarding_never_accepts_raw_certificate_headers(self):
        for field, rejected in (
            ("X-Forwarded-Client-Cert", "forwarded_cert_header"),
            ("SSL-Client-Cert", "ssl_client_cert_header"),
        ):
            with self.subTest(field=field):
                headers = self.headers()
                headers[field] = "forged"
                self.assertIn(rejected, self.failures(headers))

    def test_host_and_path_must_match_exactly(self):
        headers = self.headers()
        headers.replace_header("Host", "console.lanveriq.internal")
        self.assertIn("upstream_host", self.failures(headers))
        self.assertIn("request_path", self.failures(path="/api/v1/unknown"))

    def test_only_public_boolean_check_labels_are_emitted(self):
        headers = self.headers()
        headers["X-Forwarded-Client-Cert"] = "secret-sentinel"
        failures = self.failures(headers)
        self.assertIn("forwarded_cert_header", failures)
        self.assertNotIn("secret-sentinel", repr(failures))
        self.assertNotIn(self.agent_fp, repr(failures))


if __name__ == "__main__":
    unittest.main()
