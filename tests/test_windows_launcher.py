import runpy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "linux/droi-windows"))
main = module["main"]
g = main.__globals__

class WindowsLauncherTests(unittest.TestCase):
    def test_proton_preserves_spaces_and_arguments(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "A Game.EXE"
            exe.write_bytes(b"MZ" + b"\0" * 64)
            with patch.dict(g, {"installed": lambda: {"Proton": ("tool", "run")}}), \
                 patch.object(g["subprocess"], "call", return_value=0) as call:
                self.assertEqual(main(["--", str(exe), "--flag"]), 0)
                self.assertEqual(call.call_args.args[0], [g["OPEN"], "--", str(exe), "--flag"])
                self.assertEqual(call.call_args.kwargs["cwd"], tmp)
                self.assertEqual(call.call_args.kwargs["env"]["DROI_WINDOWS_LAUNCH"], "1")

    def test_msi_wine_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            msi = Path(tmp) / "setup.msi"
            msi.write_bytes(bytes.fromhex("D0CF11E0A1B11AE1"))
            with patch.dict(g, {"installed": lambda: {}}), \
                 patch.object(g["shutil"], "which", return_value="/usr/bin/wine"), \
                 patch.object(g["subprocess"], "call", return_value=0) as call:
                self.assertEqual(main([str(msi)]), 0)
                self.assertEqual(call.call_args.args[0][:3], ["/usr/bin/wine", "msiexec", "/i"])

    def test_missing_runtime(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "test.exe"
            exe.write_bytes(b"MZ")
            with patch.dict(g, {"installed": lambda: {}, "error": lambda message: 1}), \
                 patch.object(g["shutil"], "which", return_value=None), \
                 patch.object(g["subprocess"], "call") as call:
                self.assertEqual(main([str(exe)]), 1)
                call.assert_not_called()

    def test_invalid_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "fake.exe"
            exe.write_text("not a Windows executable")
            with patch.dict(g, {"error": lambda message: 1}), \
                 patch.object(g["subprocess"], "call") as call:
                self.assertEqual(main([str(exe)]), 1)
                call.assert_not_called()

if __name__ == "__main__":
    unittest.main()
