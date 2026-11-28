import json
import pathlib
import unittest
from gpui_selected_icon_review import selection

class SelectedIconReview(unittest.TestCase):
    def test_exact_selection_and_drift(self):
        index = json.loads((pathlib.Path(__file__).resolve().parent / "gpui_default_icon_source_index.json").read_text())
        selected, excluded = selection(index)
        self.assertEqual((len(selected), len(excluded)), (96, 12))
        self.assertFalse(selected.keys() & excluded.keys())
        index["rows"].append(index["rows"][0])
        with self.assertRaises(ValueError):
            selection(index)
