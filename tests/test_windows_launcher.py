import os
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
                 patch.dict(g, {"run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main(["--", str(exe), "--flag"]), 0)
                self.assertEqual(g["run_game"].call_args.args[1], [g["OPEN"], "--", str(exe), "--flag"])
                self.assertEqual(g["run_game"].call_args.kwargs["cwd"], tmp)
                self.assertEqual(g["run_game"].call_args.kwargs["env"]["DROI_WINDOWS_LAUNCH"], "1")

    def test_geometry_dash_uses_compatibility_without_shared_prefix(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "GeometryDash.exe"
            exe.write_bytes(b"MZ" + b"\0" * 64)
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": tmp}), \
                 patch.dict(g, {"installed": lambda: {"Proton": ("tool", "run")}}), \
                 patch.dict(g, {"run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main([str(exe)]), 0)
                self.assertEqual(g["run_game"].call_args.args[1], [g["PROTON"], "--window", "--", str(exe)])
                env = g["run_game"].call_args.kwargs["env"]
                self.assertEqual(env["MESA_LOADER_DRIVER_OVERRIDE"], "swrast")
                self.assertEqual(env["GALLIUM_DRIVER"], "llvmpipe")
                self.assertEqual(env["DD_DESKTOP_GPU"], "off")
                self.assertNotIn("--compatdata", g["run_game"].call_args.args[1])
                self.assertNotIn("--prefix", g["run_game"].call_args.args[1])

    def test_nfsc_uses_compatibility_and_esync_is_optional(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "NFSC.exe"
            exe.write_bytes(b"MZ")
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": tmp}), \
                 patch.dict(g, {"installed": lambda: {"Proton": ("tool", "run")},
                                "run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main([str(exe)]), 0)
                self.assertEqual(g["run_game"].call_args.args[1], [g["PROTON"], "--window", "--", str(exe)])
                self.assertEqual(g["run_game"].call_args.kwargs["env"]["DROI_SYNC_MODE"], "auto")
                self.assertEqual(main(["--sync", "esync", str(exe)]), 0)
                self.assertEqual(g["run_game"].call_args.kwargs["env"]["DROI_SYNC_MODE"], "esync")

    def test_duplicate_is_rejected_and_cancel_is_scoped(self):
        import fcntl
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "game.exe"
            with patch.dict(os.environ, {"XDG_CACHE_HOME": tmp}), \
                 patch.dict(g, {"error": lambda message: 1}), \
                 patch.object(g["subprocess"], "Popen") as launch:
                lock = g["launch_lock"](exe)
                with lock.open("w+") as handle:
                    fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    token = "a" * 32
                    handle.write(token)
                    handle.flush()
                    self.assertEqual(g["run_game"](exe, ["unused"], tmp, {}), 1)
                    launch.assert_not_called()
                    self.assertEqual(g["request_cancel"](exe), 0)
                    self.assertTrue((lock.parent / (token + ".cancel")).exists())
                self.assertEqual(g["request_cancel"](exe), 1)

    def test_graphics_choice_persists_per_executable(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = Path(tmp) / "GeometryDash.exe"
            other = Path(tmp) / "another.exe"
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": tmp}):
                profile = g["graphics_profile"]
                self.assertEqual(profile(a), "compatibility")
                self.assertEqual(profile(a, "performance"), "performance")
                self.assertEqual(profile(a), "performance")
                self.assertEqual(profile(other), "auto")
                self.assertEqual(profile(a, "auto"), "compatibility")

    def test_fast_launch_and_debug_opt_in(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "test.exe"
            exe.write_bytes(b"MZ")
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": tmp, "PROTON_LOG": "1"}), \
                 patch.dict(g, {"installed": lambda: {"Proton": ("tool", "run")}}), \
                 patch.dict(g, {"run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main([str(exe)]), 0)
                env = g["run_game"].call_args.kwargs["env"]
                self.assertEqual(env["DROI_FAST_LAUNCH"], "1")
                self.assertEqual(env["DD_GAME_LOGS"], "0")
                self.assertNotIn("PROTON_LOG", env)
                self.assertEqual(env["WINEDEBUG"], "-all,err+all")
                self.assertEqual(main(["--debug", str(exe)]), 0)
                env = g["run_game"].call_args.kwargs["env"]
                self.assertEqual(env["DROI_FAST_LAUNCH"], "0")
                self.assertEqual(env["DD_GAME_LOGS"], "1")

    def test_prepare_uses_target_prefix_without_game_arguments(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe = Path(tmp) / "game.exe"
            exe.write_bytes(b"MZ")
            with patch.dict(os.environ, {"XDG_CONFIG_HOME": tmp, "DISPLAY": "", "WAYLAND_DISPLAY": ""}), \
                 patch.dict(g, {"installed": lambda: {"Proton": ("tool", "run")}}), \
                 patch.dict(g, {"run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main(["--prepare", str(exe)]), 0)
                self.assertEqual(g["run_game"].call_args.args[1], [g["PROTON"], "--window", "--", str(exe)])
                env = g["run_game"].call_args.kwargs["env"]
                self.assertEqual(env["DROI_PREPARE_PREFIX"], "1")
                self.assertEqual(env["DD_DESKTOP_GPU"], "off")

    def test_wine_process_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            proc = Path(tmp)
            (proc / "self").mkdir()
            (proc / "self/status").write_text("Uid:\t1234 1234 1234 1234\n")
            def fake(pid, uid, comm, env=b""):
                folder = proc / str(pid)
                folder.mkdir()
                (folder / "status").write_text("Uid:\t%d %d %d %d\n" % ((uid,) * 4))
                (folder / "stat").write_text("%d (%s) S " % (pid, comm) + " ".join(["0"] * 18 + ["321"]))
                (folder / "comm").write_text(comm)
                (folder / "cmdline").write_bytes(comm.encode() + b"\0")
                (folder / "environ").write_bytes(env)
            fake(99901, 1234, "wineserver", b"WINEPREFIX=/tmp/prefix\0")
            fake(99902, 1234, "game.exe", b"WINEPREFIX=/tmp/prefix\0")
            fake(99903, 5678, "wineserver")
            fake(99904, 1234, "unrelated.exe")
            info = g["process_info"]
            self.assertEqual(info(99901, proc)["start"], "321")
            self.assertTrue(info(99901, proc)["server"])
            self.assertEqual(info(99902, proc)["prefix"], "/tmp/prefix")
            self.assertIsNone(info(99903, proc))
            self.assertIsNone(info(99904, proc))

    def test_stop_only_selected_wine_process(self):
        row = {"pid": 123, "start": "42", "name": "wine", "prefix": "/tmp/a", "server": False, "token": ""}
        other = dict(row, pid=456)
        stop = unittest.mock.Mock(return_value=0)
        with patch.dict(g, {"wine_processes": lambda: [row, other], "stop_wine": stop}):
            self.assertEqual(main(["--stop-wine", "123", "--yes"]), 0)
            stop.assert_called_once_with([row], True)

    def test_msi_wine_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            msi = Path(tmp) / "setup.msi"
            msi.write_bytes(bytes.fromhex("D0CF11E0A1B11AE1"))
            with patch.dict(g, {"installed": lambda: {}}), \
                 patch.object(g["shutil"], "which", return_value="/usr/bin/wine"), \
                 patch.dict(g, {"run_game": unittest.mock.Mock(return_value=0)}):
                self.assertEqual(main([str(msi)]), 0)
                self.assertEqual(g["run_game"].call_args.args[1][:3], ["/usr/bin/wine", "msiexec", "/i"])

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
