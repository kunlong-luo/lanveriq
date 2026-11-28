import unittest
from ui_interaction_smoke import PIXELS, VIEW_DELTA, distance, brightness, view_distance

class PixelMetrics(unittest.TestCase):
    def test_rgb_metrics(self):
        black,white = bytes(PIXELS),bytes([255])*PIXELS
        self.assertEqual(distance(black,black),0)
        self.assertEqual(distance(black,white),255)
        self.assertEqual(brightness(black),0)
        self.assertEqual(brightness(white),255)
    def test_invalid_rgb_length(self):
        for length in (0,PIXELS-1,PIXELS+1):
            with self.assertRaises(ValueError):
                distance(bytes(length),bytes(PIXELS))
            with self.assertRaises(ValueError):
                brightness(bytes(length))

    def test_shared_body_distinct_heading(self):
        body = bytes(PIXELS)
        before = {"body": body, "heading": body}
        after = {"body": body, "heading": bytes([8])*PIXELS}
        self.assertLessEqual(distance(before["body"], after["body"]), VIEW_DELTA)
        self.assertGreater(view_distance(before, after), VIEW_DELTA)

    def test_unchanged_or_nearly_unchanged_view_is_not_distinct(self):
        before = {"body": bytes(PIXELS), "heading": bytes(PIXELS)}
        after = {"body": bytes([1])*PIXELS, "heading": bytes([1])*PIXELS}
        self.assertEqual(view_distance(before, before), 0)
        self.assertLessEqual(view_distance(before, after), VIEW_DELTA)
        with self.assertRaises(ValueError):
            view_distance(before, {"body": bytes(PIXELS)})
