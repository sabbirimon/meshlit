import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from launch import VERSION, build_launch


class LaunchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        package = self.root / "runtime/node_modules/@deepseek-ai/dsh"
        (package / "lib").mkdir(parents=True)
        (package / "lib/bin.js").write_text("")
        (package / "package.json").write_text(json.dumps({"name": "@deepseek-ai/dsh", "version": VERSION}))
        self.args = (self.root / "runtime", self.root / "home", self.root)

    @patch("launch.subprocess.check_output", return_value="v24.18.0")
    @patch("launch.shutil.which", return_value="/test/node")
    def test_private_home_no_inherited_keys_and_no_browser(self, *_):
        with patch.dict(os.environ, {"OPENAI_API_KEY": "must-not-pass", "NODE_OPTIONS": "must-not-pass", "DSH_PERMISSION_MODE": "danger-full-access"}):
            command, env, cwd = build_launch(*self.args)
        self.assertNotIn("OPENAI_API_KEY", env)
        self.assertNotIn("NODE_OPTIONS", env)
        self.assertEqual(env["DSH_PERMISSION_MODE"], "read-only")
        self.assertEqual(env["DSH_TELEMETRY_MODE"], "DISABLED")
        self.assertIn("--no-open", command)
        self.assertIn("127.0.0.1", command)
        self.assertIn("enabled: false", (self.root / "home/meshlit-privacy.patch.yml").read_text())
        if os.name != "nt": self.assertEqual(0o700, (self.root / "home").stat().st_mode & 0o777)

    def test_remote_plaintext_and_wildcard_fail(self):
        for host in ["0.0.0.0", "::", "192.168.1.20", "127.0.0.2"]:
            with self.assertRaises(ValueError): build_launch(*self.args, host=host)

    def test_version_drift_fails(self):
        (self.root / "runtime/node_modules/@deepseek-ai/dsh/package.json").write_text('{"name":"@deepseek-ai/dsh","version":"99"}')
        with self.assertRaises(ValueError): build_launch(*self.args)

    def test_policy_symlink_fails(self):
        (self.root / "home").mkdir()
        (self.root / "victim").write_text("unchanged")
        (self.root / "home/meshlit-privacy.patch.yml").symlink_to(self.root / "victim")
        with self.assertRaises(ValueError): build_launch(*self.args)
        self.assertEqual("unchanged", (self.root / "victim").read_text())


if __name__ == "__main__": unittest.main()
