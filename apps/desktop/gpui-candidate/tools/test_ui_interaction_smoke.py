import unittest
from ui_interaction_smoke import PIXELS, distance, brightness

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
