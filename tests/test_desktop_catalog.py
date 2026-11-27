#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
activity=(root/"android/DesktopLauncherActivity.kt").read_text()
guest=Path(sys.argv[1]).read_text()
assert set(re.findall(r'^    [A-Z]+\("([a-z]+)",',activity,re.M))=={"droi"}
assert "DroiDesktop.values().forEach" not in activity
assert 'dbus-run-session -- /usr/bin/droi-shell-session' in guest
assert 'startplasma-wayland' in guest
assert 'droi_shell --prepare' in guest
assert 'droi_shell --recovery' in guest
assert 'droi-plasma-3' in guest
assert 'org.kde.plasma.panelspacer' in guest
assert 'applications:droi-chatgpt.desktop' in guest
assert guest.index('droi_shell --prepare') < guest.rindex('startplasma-wayland')
assert '-displayfd 3' in guest
print("Native custom Plasma with lightweight recovery verified")

assert 'panel.floating = true' in guest
assert 'panel.lengthMode = "fill"' in guest
assert 'DroiAurora/contents/images/1920x1080.svg' in guest
assert 'droi-start.svg' in guest
assert 'AccentColor 72,150,246' in guest
assert 'ButtonsOnRight IAX' in guest
assert 'cmp -s "$desktop_dir/Return.desktop"' in guest

import json
meta=guest.split("<<'DROI_START_META'\n",1)[1].split("\nDROI_START_META",1)[0]
qml=guest.split("<<'DROI_START_QML'\n",1)[1].split("\nDROI_START_QML",1)[0]
assert json.loads(meta)["KPlugin"]["Id"]=="org.droi.start"
assert 'panel.addWidget("org.droi.start")' in guest
assert 'panel.addWidget("org.kde.plasma.kickoff")' not in guest
assert "PlasmoidItem" in qml and "Kicker.RootModel" in qml and "Kicker.RunnerModel" in qml
assert 'source.trigger(row, "", null)' in qml
assert "Behavior on opacity" in qml and "Behavior on scale" in qml and "Behavior on y" in qml
assert "interval: 135" in qml and "transformOrigin: Item.Bottom" in qml
assert 'translate(1660 100)' in guest
assert '"droi-plasma-3"' in guest or "droi-plasma-3" in guest
