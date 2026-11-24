"""L2B-25 OCI-only unit tests of actual review service fail-closed branches.

Run with distro python3-dbus + GI *inside the disposable Ubuntu24 image*.
Mocks fake policy decisions only; live D-Bus/Polkit integration is tested
separately in test_polkit_dbus_broker_oci.sh.
"""
import importlib.util
import pathlib
from types import SimpleNamespace
from unittest import TestCase, main
from unittest.mock import patch

SOURCE = pathlib.Path(__file__).with_name("polkit-oci-dbus-broker-lab.py")
spec = importlib.util.spec_from_file_location("lanveriq_review_lab", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FakeBusDaemon:
    def __init__(self, uid=65534, pid=1729):
        self.uid, self.pid = uid, pid

    def GetConnectionUnixUser(self, sender):
        if not str(sender).startswith(":"):
            raise ValueError("unknown sender")
        return self.uid

    def GetConnectionUnixProcessID(self, sender):
        return self.pid


class FailClosedReviewTests(TestCase):
    def service(self, uid=65534):
        # The class is the real D-Bus service. Do not connect to an OS bus in
        # unit tests; live OS sender records are covered separately by CI.
        service = module.ReviewOnlyService.__new__(module.ReviewOnlyService)
        service.daemon = FakeBusDaemon(uid=uid)
        service.seen = set()
        return service

    def review(self, service, code, operation="install", nonce="a" * 32):
        with patch.object(module, "kernel_identity", return_value=8824), patch.object(
            module.subprocess, "run",
            return_value=SimpleNamespace(returncode=code)
        ) as check:
            result = service.Review(operation, nonce, sender=":1.42")
        self.assertEqual(check.call_count, 1)
        args, kwargs = check.call_args
        self.assertEqual(args[0], [
            "/usr/bin/pkcheck", "--action-id",
            module.PREFIX + operation,
            "--process", f"{service.daemon.pid},8824,{service.daemon.uid}",
        ])
        self.assertEqual(kwargs["env"], {})
        self.assertEqual(kwargs["timeout"], 5)
        self.assertFalse(kwargs["check"])
        return result

    def test_hypothetical_polkit_yes_still_denies_without_publisher_and_broker(self):
        for op in sorted(module.OPERATIONS):
            with self.subTest(op=op):
                result = self.review(self.service(), 0, operation=op)
                self.assertEqual(result, "NOT_APPROVED")

    def test_native_polkit_denial_is_distinct_from_service_or_daemon_failure(self):
        self.assertEqual(self.review(self.service(), 1), "POLKIT_DENIED")
        for code in (2, 3, 126, 127, -9):
            with self.subTest(code=code):
                self.assertEqual(
                    self.review(self.service(), code), "NOT_APPROVED"
                )

    def test_unknown_ops_and_malformed_nonce_never_call_pkcheck(self):
        cases = (("start", "a" * 32, "INVALID_OPERATION"),
                 ("remove; /bin/sh", "a" * 32, "INVALID_OPERATION"),
                 ("install", "upperABC", "INVALID_NONCE"),
                 ("upgrade", "../mnt", "INVALID_NONCE"),
                 ("install", "a" * 31, "INVALID_NONCE"))
        for op, nonce, expected in cases:
            with self.subTest(op=op, nonce=nonce):
                service = self.service()
                with patch.object(module.subprocess, "run") as check:
                    self.assertEqual(
                        service.Review(op, nonce, sender=":1.42"), expected
                    )
                    check.assert_not_called()

    def test_unverified_bus_sender_fails_closed(self):
        for sender in (None, "caller-supplied-uid", ""):
            with self.subTest(sender=sender):
                with patch.object(module.subprocess, "run") as check:
                    self.assertEqual(
                        self.service().Review("install", "a" * 32, sender=sender),
                        "UNVERIFIED_PEER"
                    )
                    check.assert_not_called()

    def test_kernel_identity_failure_has_no_authority(self):
        service = self.service()
        with patch.object(module, "kernel_identity", side_effect=ValueError("uid")), \
             patch.object(module.subprocess, "run") as check:
            self.assertEqual(
                service.Review("install", "a" * 32, sender=":1.42"),
                "UNVERIFIED_PEER",
            )
            check.assert_not_called()

    def test_replay_under_same_uid_cannot_reuse_a_checked_nonce(self):
        service = self.service()
        self.assertEqual(self.review(service, 1, nonce="b" * 32),
                         "POLKIT_DENIED")
        with patch.object(module, "kernel_identity", return_value=8824), patch.object(
            module.subprocess, "run"
        ) as check:
            result = service.Review("upgrade", "b" * 32, sender=":1.99")
        self.assertEqual(result, "REPLAY_DENIED")
        check.assert_not_called()

    def test_volatile_memory_capacity_is_fail_closed(self):
        service = self.service()
        service.seen = {(65534, f"{i:032x}") for i in range(module.MAX_NONCES)}
        with patch.object(module, "kernel_identity", return_value=8824), patch.object(
            module.subprocess, "run"
        ) as check:
            result = service.Review("remove", "f" * 32, sender=":1.42")
        self.assertEqual(result, "CAPACITY_DENIED")
        check.assert_not_called()

    def test_pkcheck_missing_or_timeout_is_not_misreported_as_denial(self):
        for fault in (OSError("missing"), __import__("subprocess").TimeoutExpired(
                ["/usr/bin/pkcheck"], 5)):
            with self.subTest(fault=fault):
                service = self.service()
                with patch.object(module, "kernel_identity", return_value=8824), patch.object(
                    module.subprocess, "run", side_effect=fault
                ):
                    self.assertEqual(
                        service.Review("install", "a" * 32, sender=":1.42"),
                        "UNVERIFIED_POLICY",
                    )

    def test_zero_or_root_bus_uid_is_not_an_approved_subject(self):
        for uid in (0, -1):
            with self.subTest(uid=uid):
                with patch.object(module.subprocess, "run") as check:
                    self.assertEqual(
                        self.service(uid).Review(
                            "install", "a" * 32, sender=":1.42"
                        ), "UNVERIFIED_PEER"
                    )
                    check.assert_not_called()


if __name__ == "__main__":
    main(verbosity=2)
