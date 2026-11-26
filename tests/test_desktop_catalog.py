#!/usr/bin/env python3
"""Check every selectable desktop has an explicit guest session and dispatch."""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
activity = (root / "android/DesktopLauncherActivity.kt").read_text()
guest = pathlib.Path(sys.argv[1]).read_text()
ids = set(re.findall(r'^    [A-Z]+\("([a-z]+)",', activity, re.M))
expected = {"plasma", "xfce", "lxqt", "mate", "cinnamon", "lxde", "enlightenment", "icewm"}
assert ids == expected, (ids, expected)
for name in expected - {"plasma"}:
    assert re.search(r'^    ' + name + r'\).*session=', guest, re.M), name
dispatch = re.search(r'^  ([a-z|]+)\)\n    droi_alternative "\$desktop_choice"', guest, re.M)
assert dispatch and set(dispatch.group(1).split("|")) == expected - {"plasma"}
assert '-displayfd 3' in guest
assert 'no automatic Plasma fallback' in guest
print("Eight desktop choices and guest dispatch verified")
