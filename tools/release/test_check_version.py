import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from check_version import parse_version, release_version


class ReleaseVersionTest(unittest.TestCase):
    def git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.PIPE, text=True).strip()

    def write_version(self, version, code):
        path = Path("app/build.gradle.kts")
        path.parent.mkdir(exist_ok=True)
        path.write_text(f'versionName = "{version}"\nversionCode = {code}\n')
        self.git("add", str(path))
        self.git("commit", "-qm", "test version " + version)

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        original = Path.cwd()
        self.addCleanup(os.chdir, original)
        os.chdir(self.directory.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "release@example.invalid")
        self.write_version("0.1.30", 31)
        self.git("tag", "v0.1.30")

    def test_valid_new_release_and_retry_at_same_commit(self):
        self.write_version("0.1.31", 32)
        self.assertEqual(("0.1.31", 32), release_version())
        self.git("tag", "v0.1.31")
        self.assertEqual(("0.1.31", 32), release_version())

    def test_version_comparison_is_numeric(self):
        self.write_version("0.1.100", 32)
        self.assertEqual(("0.1.100", 32), release_version())

    def test_rejects_reused_version_or_non_increasing_code(self):
        for version, code in (("0.1.30", 32), ("0.1.31", 31), ("0.1.29", 40)):
            with self.subTest(version=version, code=code):
                self.write_version(version, code)
                with self.assertRaisesRegex(ValueError, "Increase both"):
                    release_version()

    def test_does_not_retarget_an_existing_tag(self):
        self.git("tag", "v0.1.31")
        self.write_version("0.1.31", 32)
        with self.assertRaisesRegex(ValueError, "another commit"):
            release_version()

    def test_rejects_malformed_versions(self):
        for version, code in (("0.1.31; echo unsafe", 32), ("latest", 32), ("0.1.31", 0)):
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    parse_version(f'versionName = "{version}"\nversionCode = {code}')


if __name__ == "__main__":
    unittest.main()
