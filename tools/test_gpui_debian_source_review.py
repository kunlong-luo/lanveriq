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

    def test_manifest_only_differences_and_preserved_original(self):
        source = b'[package]\nname="a"\nversion="1"\nlicense="MIT"\n'
        normalized = source + b'[dependencies]\nb="2"\n'
        registry = fixture([("a-1/Cargo.toml", source), ("a-1/src/lib.rs", b"same")])
        debian = fixture([("a-1/Cargo.toml", normalized), ("a-1/Cargo.toml.orig", source),
                          ("a-1/src/lib.rs", b"same")])
        row = compare(registry, debian, sha(registry), "a-1")
        review = row["manifest_review"]
        self.assertEqual(row["comparison"], "SOURCE_DIFFERENCES_OPEN")
        self.assertTrue(review["registry_matches_debian_orig_bytes"])
        self.assertTrue(review["non_manifest_files_identical"])
        self.assertEqual(review["debian_original_manifest_differences"], [])
        self.assertEqual(review["normalized_manifest_differences"],
                         [{"path": ["dependencies"], "kind": "DEBIAN_ONLY"}])
        self.assertEqual(row["license_review"], "OPEN_NOT_APPROVED")

    def test_license_changes_and_source_changes_are_not_normalized_away(self):
        source = b'[package]\nname="a"\nlicense="MIT"\n'
        modified = source.replace(b"MIT", b"GPL-3.0")
        registry = fixture([("a-1/Cargo.toml", source), ("a-1/src/lib.rs", b"same")])
        debian = fixture([("a-1/Cargo.toml", modified), ("a-1/Cargo.toml.orig", modified),
                          ("a-1/src/lib.rs", b"same")])
        row = compare(registry, debian, sha(registry), "a-1")
        review = row["manifest_review"]
        self.assertFalse(review["registry_matches_debian_orig_bytes"])
        self.assertEqual(review["normalized_manifest_differences"],
                         [{"path": ["package", "license"], "kind": "CHANGED"}])
        self.assertEqual(review["debian_original_manifest_differences"],
                         review["normalized_manifest_differences"])
        debian = fixture([("a-1/Cargo.toml", source), ("a-1/src/lib.rs", b"changed")])
        self.assertEqual(compare(registry, debian, sha(registry), "a-1")["manifest_review"],
                         {"status": "NON_MANIFEST_DIFFERENCES_OPEN"})

    def test_manifest_comments_and_type_changes(self):
        source = b'[package]\nname="a"\nflag=true\nvalues=[true]\n'
        registry = fixture([("a-1/Cargo.toml", source)])
        debian = fixture([("a-1/Cargo.toml", b"# repacked\n" + source)])
        self.assertEqual(compare(registry, debian, sha(registry), "a-1")[
            "manifest_review"]["normalized_manifest_differences"], [])
        debian = fixture([("a-1/Cargo.toml", source.replace(b"true", b"1"))])
        self.assertEqual(compare(registry, debian, sha(registry), "a-1")[
            "manifest_review"]["normalized_manifest_differences"],
            [{"path": ["package", "flag"], "kind": "CHANGED"},
             {"path": ["package", "values", 0], "kind": "CHANGED"}])
