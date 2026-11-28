import hashlib
import io
import tarfile
import unittest
from unittest.mock import patch

from gpui_example_resource_review import ROOT, inspect, sha, platform_review

class ExampleResourceReview(unittest.TestCase):
    def archive(self, extras=None):
        output = io.BytesIO()
        files = {"Cargo.toml": b'[package]\nname="gpui-pre"\nversion="0.3.14"\n',
                 "examples/image/a.svg": b"svg",
                 "examples/image/main.rs": b'include_bytes!("a.svg")'}
        files.update(extras or {})
        with tarfile.open(fileobj=output, mode="w:gz") as z:
            for path, data in files.items():
                info = tarfile.TarInfo(ROOT+"/"+path)
                info.size = len(data)
                z.addfile(info, io.BytesIO(data))
        return output.getvalue()

    @patch("gpui_example_resource_review.ASSETS", {"examples/image/a.svg": (3, sha(b"svg"))})
    def test_reference_scope_and_fail_closed_inventory(self):
        data = self.archive({"src/lib.rs": b'include_bytes!("../examples/image/a.svg")'})
        row = inspect(data, sha(data))["resources"][0]
        self.assertEqual(row["outside_examples"], ["src/lib.rs"])
        self.assertFalse(row["legal_approved"])
        with self.assertRaises(ValueError):
            inspect(data, "0"*64)
        changed = self.archive({"examples/image/a.svg": b"new"})
        with self.assertRaises(ValueError):
            inspect(changed, sha(changed))

    @patch("gpui_example_resource_review.ASSETS", {"examples/image/a.svg": (3, sha(b"svg"))})
    def test_reject_traversal(self):
        data = self.archive({"../escape": b"bad"})
        with self.assertRaises(ValueError):
            inspect(data, sha(data))

    def test_platform_exact_blob_or_open(self):
        body = b"reviewed source"
        blob = hashlib.sha1(b"blob " + str(len(body)).encode() + b"\0" + body).hexdigest()
        with patch("gpui_example_resource_review.PLATFORM_BLOB", blob):
            self.assertEqual(platform_review(body)["status"], "EXACT_UPSTREAM_TEST_MODULE_SOURCE_MATCH")
            self.assertEqual(platform_review(body + b"changed")["status"], "SOURCE_DIFFERS_OPEN")
            self.assertEqual(platform_review(None)["status"], "SOURCE_NOT_PRESENT_OPEN")
