#!/usr/bin/env python3
"""Assert every shipped guest script is registered for existing-root staging."""
import re
import sys
import zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
patch = (ROOT / "patches/android.patch").read_text()
match = re.search(r"for script in (droi-apps[^;]+); do", workflow)
assert match, "Missing guest script packaging loop"
scripts = match.group(1).split()
assert len(scripts) == len(set(scripts))
for script in scripts:
    assert (ROOT / "linux" / script).is_file(), script
    assert f'"usr/bin/{script}" to "usr/bin/{script}"' in patch, f"{script} not staged"
assert "droi-update" in scripts and "droi-notify" in scripts and "droi-game-downloader" in scripts
if len(sys.argv) > 1:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        for script in scripts:
            asset = f"assets/linuxfs/usr/bin/{script}"
            assert apk.read(asset) == (ROOT / "linux" / script).read_bytes(), f"APK script mismatch: {asset}"
print("Droi guest script packaging and staging checks passed")
