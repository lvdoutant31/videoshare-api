"""Windows CMD regression checks; fixtures never contain an installable service."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == "nt", "Requires Windows CMD")
class ServiceLauncherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        output = ROOT / "output" / "service-launcher-tests"
        output.mkdir(parents=True, exist_ok=True)
        cls.release = Path(tempfile.mkdtemp(prefix="release (spaces) ", dir=output))
        cls.bat = cls.release / "deploy-windows-service.bat"
        shutil.copyfile(ROOT / "deploy" / cls.bat.name, cls.bat)
        java = shutil.which("java.exe")
        assert java, "Java 17 is required for preflight checks"
        settings = subprocess.run(
            [java, "-XshowSettings:properties", "-version"], capture_output=True, check=True
        ).stderr.decode(errors="replace")
        cls.java_home = re.search(r"java.home = (.+)", settings).group(1).strip()
        cls.java = str(Path(cls.java_home) / "bin" / "java.exe")

    def setUp(self):
        self.env = os.environ.copy()
        self.env["YINGLIU_JAVA"] = self.java

    def command(self, action=None):
        return f'"{os.environ["COMSPEC"]}" /d /c call "{self.bat}"' + (f" {action}" if action else "")

    def run_action(self, action):
        return subprocess.run(self.command(action), env=self.env, capture_output=True, timeout=20)

    def fixture(self):
        release = Path(tempfile.mkdtemp(prefix="valid (spaces) ", dir=self.release.parent))
        self.bat = release / self.bat.name
        shutil.copyfile(ROOT / "deploy" / self.bat.name, self.bat)
        api = release / "api"
        (api / "config").mkdir(parents=True)
        # Empty placeholders satisfy file checks but cannot install or run a service.
        (api / "yingliu-api.jar").touch()
        (api / "YingliuApiService.exe").touch()
        shutil.copyfile(ROOT / "deploy/windows-service/YingliuApiService.xml.template", api / "YingliuApiService.xml.template")
        ffmpeg = api / "ffmpeg.cmd"
        ffmpeg.write_bytes(b"@echo off\r\nexit /b 0\r\n")
        config = api / "config/yingliu-env.properties"
        config.write_text(
            "spring.profiles.active=prod\nserver.port=8080\n"
            "spring.datasource.url=jdbc:unused\nspring.datasource.username=test\n"
            "spring.datasource.password=test\nspring.data.redis.host=unused\n"
            "spring.data.redis.port=6379\nmedia.storage-type=LOCAL\n"
            f"media.storage-root=unused\nyingliu.worker.video-transcode.ffmpeg-path={ffmpeg}\n",
            encoding="ascii",
        )
        return api, config

    def test_help_does_not_pause(self):
        result = self.run_action("help")
        self.assertEqual(0, result.returncode)
        self.assertIn(b"Usage:", result.stdout)

    def test_unknown_command_fails(self):
        result = self.run_action("invalid-command")
        self.assertEqual(1, result.returncode)
        self.assertIn(b"Unknown command", result.stdout)

    def test_cli_missing_release_fails_without_pausing(self):
        result = self.run_action("check")
        self.assertEqual(1, result.returncode)
        self.assertIn(b"Required release file is missing", result.stdout)

    def test_double_click_keeps_error_visible_and_preserves_exit_code(self):
        process = subprocess.Popen(self.command(), env=self.env, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            with self.assertRaises(subprocess.TimeoutExpired):
                process.wait(timeout=1)
            stdout, stderr = process.communicate(input=b"\r\n", timeout=10)
            self.assertEqual(1, process.returncode, stderr)
            self.assertIn(b"Required release file is missing", stdout)
            self.assertIn(b'exited with code 1', stdout)
        finally:
            if process.poll() is None:
                process.kill()
            process.communicate()

    def test_missing_runtime_config_fails(self):
        _, config = self.fixture()
        config.rename(config.with_suffix(".missing"))
        result = self.run_action("check")
        self.assertEqual(1, result.returncode)
        self.assertIn(b"Runtime configuration was not found", result.stdout)

    def test_preflight_renders_java_from_java_home(self):
        api, _ = self.fixture()
        self.env.pop("YINGLIU_JAVA", None)
        self.env["JAVA_HOME"] = self.java_home
        result = self.run_action("check")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(b"[READY]", result.stdout)
        self.assertEqual(self.java, ET.parse(api / "YingliuApiService.xml").findtext("executable"))

    def test_invalid_java_home_falls_back_to_path(self):
        self.fixture()
        self.env.pop("YINGLIU_JAVA", None)
        self.env["JAVA_HOME"] = str(self.release / "missing-jdk")
        self.env["PATH"] = str(Path(self.java).parent) + os.pathsep + self.env["PATH"]
        result = self.run_action("check")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(b"[READY]", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
