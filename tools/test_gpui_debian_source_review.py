import gzip
import io
import tarfile
import unittest

from gpui_debian_source_review import compare, manifest, sha

def fixture(files, mtime=0):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w") as archive:
        for name, body in files:
            member = tarfile.TarInfo(name)
            if body is None:
                member.type = tarfile.SYMTYPE
                member.linkname = "/etc/passwd"
                archive.addfile(member)
            else:
                member.size = len(body)
                archive.addfile(member, io.BytesIO(body))
    return gzip.compress(stream.getvalue(), mtime=mtime)

class SourceReview(unittest.TestCase):
    def test_exact_and_repacked_archive(self):
        files = [("a-1/Cargo.toml", b"fixture"), ("a-1/src/lib.rs", b"code")]
        registry = fixture(files)
        for candidate, expected in ((registry, "IDENTICAL_ARCHIVE_BYTES"),
                                    (fixture(files, 1), "IDENTICAL_FILE_CONTENTS")):
            row = compare(registry, candidate, sha(registry), "a-1")
            self.assertEqual(row["comparison"], expected)
            self.assertEqual(row["upstream_git_binding"], "OPEN")
            self.assertEqual(row["license_review"], "OPEN_NOT_APPROVED")

    def test_difference_and_bad_lock(self):
        registry = fixture([("a-1/Cargo.toml", b"a"), ("a-1/old", b"old")])
        debian = fixture([("a-1/Cargo.toml", b"b"), ("a-1/new", b"new")])
        row = compare(registry, debian, sha(registry), "a-1")
        self.assertEqual(row["comparison"], "SOURCE_DIFFERENCES_OPEN")
        self.assertEqual(row["changed"], ["Cargo.toml"])
        self.assertEqual(row["registry_only"], ["old"])
        self.assertEqual(row["debian_only"], ["new"])
        with self.assertRaisesRegex(ValueError, "checksum"):
            compare(registry, debian, "0" * 64, "a-1")

    def test_unsafe_archive_rejected(self):
        for extra in (("a-1/../bad", b"x"), ("a-1/link", None),
                      ("a-1/Cargo.toml", b"duplicate"), ("/absolute", b"x")):
            with self.subTest(extra=extra):
                with self.assertRaises(ValueError):
                    manifest(fixture([("a-1/Cargo.toml", b"a"), extra]), "a-1")
