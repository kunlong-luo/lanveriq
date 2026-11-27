import unittest
from performance_baseline import parse_memory, summary, MAX_SMAPS

class PerformanceBaseline(unittest.TestCase):
    def test_memory_units_and_keys(self):
        self.assertEqual(parse_memory("Rss: 4096 kB\nPss: 2048 kB\n"), {"rss_kib": 4096, "pss_kib": 2048})
        for raw in ("", "Rss: 10 kB\nPss: 11 kB", "Rss: 10 kB\nRss: 10 kB\nPss: 5 kB",
                    "Rss: 10 MB\nPss: 5 kB", "x" * (MAX_SMAPS + 1)):
            with self.assertRaises(ValueError):
                parse_memory(raw)

    def test_summary_three_samples(self):
        samples = [{"spawn_to_nonuniform_pixel_probe_ms": v, "rss_kib": v*2, "pss_kib": v}
                   for v in (3, 1, 2)]
        self.assertEqual(summary(samples)["rss_kib"], {"median": 4, "min": 2, "max": 6})
        with self.assertRaises(ValueError):
            summary(samples[:2])
