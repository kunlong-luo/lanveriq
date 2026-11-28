import unittest
from paired_desktop_performance import members, summarize

class PairedPerformance(unittest.TestCase):
    def test_tree_and_session_membership(self):
        rows = [(10, 1, 10, "S"), (11, 10, 10, "S"), (12, 11, 12, "S"),
                (13, 1, 10, "S"), (14, 10, 10, "Z"), (99, 1, 99, "S")]
        self.assertEqual(members(rows, 10), {10, 11, 12, 13})
    def test_incomplete_samples_refused(self):
        with self.assertRaises((ValueError, AssertionError, SystemExit)):
            summarize([{"status": "FAIL"}])
    def test_same_metric_summary(self):
        sample = {"status":"PASS", **{k: 10 for k in ("startup_ms", "root_rss_kib", "root_pss_kib",
            "tree_rss_kib", "tree_pss_kib", "process_count")}}
        self.assertEqual(summarize([sample]*3)["tree_pss_kib"]["median"], 10)
