import pathlib
import tempfile
import unittest
from gpui_distribution_material_review import source_file, sha

class MaterialReview(unittest.TestCase):
    def test_exact_source_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp).resolve()
            p = root / "LICENSE"
            p.write_bytes(b"copyright\r\n")
            record = {"path": "LICENSE", "bytes": 11, "sha256": sha(p.read_bytes())}
            self.assertEqual(source_file(root, record), p)

    def test_drift_and_escape_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp).resolve()
            p = root / "LICENSE"
            p.write_bytes(b"actual")
            for record in (
                {"path": "LICENSE", "bytes": 6, "sha256": "0"*64},
                {"path": "../LICENSE", "bytes": 6, "sha256": sha(b"actual")},
                {"path": "/LICENSE", "bytes": 6, "sha256": sha(b"actual")},
            ):
                with self.assertRaises(ValueError):
                    source_file(root, record)
            (root / "NOTICE").symlink_to(p)
            with self.assertRaises(ValueError):
                source_file(root, {"path": "NOTICE", "bytes": 6, "sha256": sha(b"actual")})
