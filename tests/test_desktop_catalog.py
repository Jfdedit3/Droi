#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
activity=(root/"android/DesktopLauncherActivity.kt").read_text()
guest=Path(sys.argv[1]).read_text()
assert set(re.findall(r'^    [A-Z]+\("([a-z]+)",',activity,re.M))=={"droi"}
assert "DroiDesktop.values().forEach" not in activity
assert 'dbus-run-session -- /usr/bin/droi-shell-session' in guest
assert 'startplasma-wayland' not in guest
assert '-displayfd 3' in guest
print("Single custom Droi desktop verified")
