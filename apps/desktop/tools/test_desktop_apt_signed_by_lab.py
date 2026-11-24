"""L2B-20: real APT Signed-By/InRelease authentication lab, offline and NO install.

All GPG keys, synthetic package *indexes*, source lists, APT trust settings,
cache, lists and dpkg status live in temporary paths. There is NO actual
package .deb, Agent installation, real repo, root helper or release key.
"""
import datetime as dt
import hashlib
import os
import pathlib
import pwd
import shutil
import subprocess
import tempfile
import unittest

UTC = dt.timezone.utc


def checked(args):
    r = subprocess.run(args, capture_output=True, text=True, errors="replace",
                       timeout=40, check=False)
    if r.returncode:
        raise AssertionError(
            f"synthetic fixture command failed ({r.returncode}) "
            f"{args[0]}: {r.stderr[-600:]}"
        )
    return r


class AptSignedByLabTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        for cmd in ("apt-get", "gpg"):
            if not shutil.which(cmd):
                raise AssertionError(f"required Ubuntu package tool missing: {cmd}")
        cls.keys_temp = tempfile.TemporaryDirectory(prefix="lanveriq-apt-lab-keys-")
        cls.keys = pathlib.Path(cls.keys_temp.name)
        cls.keyrings = {}
        for role in ("publisher", "attacker"):
            home = cls.keys / (role + "-gnupg")
            home.mkdir(mode=0o700)
            checked([
                "gpg", "--batch", "--homedir", str(home), "--passphrase", "",
                "--quick-generate-key",
                f"Lanveriq {role} synthetic <lab-{role}@example.invalid>",
                "ed25519", "sign", "0",
            ])
            keyring = cls.keys / (role + ".gpg")
            with keyring.open("wb") as output:
                proc = subprocess.run(
                    ["gpg", "--batch", "--homedir", str(home), "--export"],
                    stdout=output, stderr=subprocess.PIPE, timeout=20,
                    check=False,
                )
            if proc.returncode:
                raise AssertionError("ephemeral test keyring export failed")
            cls.keyrings[role] = keyring

    @classmethod
    def tearDownClass(cls):
        cls.keys_temp.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="lanveriq-apt-signed-by-")
        self.addCleanup(self.temp.cleanup)
        self.base = pathlib.Path(self.temp.name)
        self.repo = self.base / "repo"
        self.release_dir = self.repo / "dists/stable"
        self.index = self.release_dir / "main/binary-amd64/Packages"
        self.index.parent.mkdir(parents=True)
        self.index.write_text(
            "Package: lanveriq-apt-lab-placeholder\n"
            "Version: 0.0.1\n"
            "Architecture: amd64\n"
            "Maintainer: Synthetic Only <lab@example.invalid>\n"
            "Description: signed index, NO REAL AGENT OR PACKAGE\n"
            "Filename: pool/lab/nonexistent-synthetic-package.deb\n"
            "Size: 1\n"
            "SHA256: " + "0" * 64 + "\n\n",
            encoding="utf-8",
        )
        self.etc = self.base / "etc"
        self.etc.mkdir()
        (self.etc / "sourceparts").mkdir()
        (self.etc / "trustedparts").mkdir()
        self.src = self.etc / "test.sources"
        self.state = self.base / "state"
        (self.state / "lists/partial").mkdir(parents=True)
        (self.state / "status").write_text("", encoding="utf-8")
        self.cache = self.base / "cache"
        (self.cache / "archives/partial").mkdir(parents=True)
        shutil.copy2(self.keyrings["publisher"], self.base / "publisher.gpg")
        self.make_release()
        self.sign_release()
        self.sources()

    def sources(self, role="publisher"):
        key = self.base / (role + ".gpg")
        if role == "attacker":
            shutil.copy2(self.keyrings["attacker"], key)
        self.src.write_text(
            f"Types: deb\n"
            f"URIs: {self.repo.as_uri()}\n"
            f"Suites: stable\n"
            f"Components: main\n"
            f"Signed-By: {key}\n"
            f"Check-Valid-Until: yes\n\n",
            encoding="utf-8",
        )

    def make_release(self, dated=-dt.timedelta(minutes=1),
                     valid=dt.timedelta(days=2)):
        now = dt.datetime.now(UTC)
        fmt = lambda value: value.strftime("%a, %d %b %Y %H:%M:%S +0000")
        raw = self.index.read_bytes()
        (self.release_dir / "Release").write_text(
            "Origin: Lanveriq APT LAB, NOT FOR DISTRIBUTION\n"
            "Label: Synthetic Only\n"
            "Suite: stable\n"
            "Codename: stable\n"
            f"Date: {fmt(now + dated)}\n"
            f"Valid-Until: {fmt(now + valid)}\n"
            "Architectures: amd64\n"
            "Components: main\n"
            "Description: Fake package metadata only\n"
            "SHA256:\n"
            f" {hashlib.sha256(raw).hexdigest()} {len(raw)} "
            "main/binary-amd64/Packages\n",
            encoding="utf-8",
        )

    def sign_release(self, role="publisher"):
        checked([
            "gpg", "--batch", "--yes",
            "--homedir", str(self.keys / (role + "-gnupg")),
            "--clearsign", "--output",
            str(self.release_dir / "InRelease"),
            str(self.release_dir / "Release"),
        ])

    def apt_update(self):
        # No system sources, global keys, system dpkg state, installed packages,
        # online endpoints or APT install command are ever involved.
        opts = [
            f"-oDir::Etc::sourcelist={self.src}",
            f"-oDir::Etc::sourceparts={self.etc / 'sourceparts'}",
            f"-oDir::Etc::trusted={self.etc / 'trusted.gpg'}",
            f"-oDir::Etc::trustedparts={self.etc / 'trustedparts'}",
            f"-oDir::State::lists={self.state / 'lists'}",
            f"-oDir::State::status={self.state / 'status'}",
            f"-oDir::Cache::archives={self.cache / 'archives'}",
            f"-oAPT::Sandbox::User={pwd.getpwuid(os.getuid()).pw_name}",
            "-oAcquire::Languages=none",
            "-oAcquire::AllowInsecureRepositories=false",
            "-oAPT::Get::AllowUnauthenticated=false",
            "-oAcquire::Check-Valid-Until=true",
            "-oAcquire::Check-Date=true",
            "-oDebug::NoLocking=true",
        ]
        return subprocess.run(
            ["apt-get", *opts, "update"], capture_output=True, text=True,
            errors="replace", timeout=45, check=False,
        )

    def assert_rejected(self):
        result = self.apt_update()
        self.assertNotEqual(
            result.returncode, 0, "APT accepted an unsafe synthetic repository: "
            + result.stdout + "\n" + result.stderr,
        )
        self.assertEqual((self.state / "status").read_bytes(), b"")
        self.assertFalse(list((self.cache / "archives").glob("*.deb")))

    def test_valid_signed_by_inrelease_and_index_pass_without_install(self):
        result = self.apt_update()
        self.assertEqual(result.returncode, 0, result.stdout + "\n" + result.stderr)
        self.assertEqual((self.state / "status").read_bytes(), b"")
        self.assertFalse(list((self.cache / "archives").glob("*.deb")))
        self.assertTrue(
            list((self.state / "lists").glob("*_Packages*")),
            "APT did not fetch/validate the synthetic Packages index",
        )

    def test_signer_outside_signed_by_is_rejected(self):
        self.sign_release("attacker")
        self.assert_rejected()

    def test_wrong_signed_by_keyring_is_rejected(self):
        self.sources("attacker")
        self.assert_rejected()

    def test_even_global_key_cannot_override_wrong_signed_by(self):
        shutil.copy2(self.keyrings["publisher"], self.etc / "trusted.gpg")
        self.sources("attacker")
        self.assert_rejected()

    def test_release_without_signature_is_rejected(self):
        (self.release_dir / "InRelease").unlink()
        self.assert_rejected()

    def test_modified_inrelease_is_rejected(self):
        inrelease = self.release_dir / "InRelease"
        inrelease.write_bytes(
            inrelease.read_bytes().replace(b"Synthetic Only", b"Synthetic Other")
        )
        self.assert_rejected()

    def test_tampered_packages_index_is_rejected(self):
        with self.index.open("ab") as out:
            out.write(b"Injected-Field: not-in-signed-release\n")
        self.assert_rejected()

    def test_expired_valid_until_is_rejected(self):
        self.make_release(
            dated=-dt.timedelta(days=5), valid=-dt.timedelta(days=1)
        )
        self.sign_release()
        self.assert_rejected()

    def test_future_dated_metadata_is_rejected(self):
        self.make_release(
            dated=dt.timedelta(days=1), valid=dt.timedelta(days=3)
        )
        self.sign_release()
        self.assert_rejected()


if __name__ == "__main__":
    unittest.main(verbosity=2)
