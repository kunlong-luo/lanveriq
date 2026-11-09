#!/usr/bin/env python3
"""Offline RustDesk upstream rendezvous request diagnostics fail-closed tests."""
import unittest
from unittest import mock
import run_remote_session as remote
from verify import EvidenceRejected


class NativePunchRequestTelemetryTest(unittest.TestCase):
    def test_reports_only_aggregated_count_no_real_peer_id(self):
        with mock.patch.object(remote.lab, "compose", return_value="2") as cli:
            self.assertEqual(remote.count_real_hbbs_punch_requests(), 2)
            command = cli.call_args.args
            self.assertEqual(command[0:3], ("exec", "-T", "rd-server"))
            self.assertIn("awk", command[-1])
            self.assertIn("127.0.0.1 21115", command[-1])
            self.assertNotIn("cat /data", command[-1])

    def test_fails_closed_on_unparseable_large_negative_or_empty_count(self):
        for count in ("", "-1", "not connected", "100001", "RUSTDESK PEER ID"):
            with self.subTest(result=count), mock.patch.object(
                remote.lab, "compose", return_value=count
            ):
                with self.assertRaises(EvidenceRejected):
                    remote.count_real_hbbs_punch_requests()

    def test_actual_peer_request_mandatory_before_video_test(self):
        with mock.patch.object(remote, "count_real_hbbs_punch_requests", return_value=1):
            self.assertEqual(remote.await_real_connection_request(), 1)
        with mock.patch.object(remote, "count_real_hbbs_punch_requests", return_value=0):
            with mock.patch.object(remote.time, "sleep"):
                with self.assertRaisesRegex(EvidenceRejected, "No actual hbbs peer punch request"):
                    remote.await_real_connection_request()


if __name__ == "__main__":
    unittest.main()
