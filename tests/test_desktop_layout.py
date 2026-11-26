#!/usr/bin/env python3
"""Exercise config editing against fixtures; no live desktop is touched."""
import importlib.machinery
import importlib.util
import pathlib
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
loader = importlib.machinery.SourceFileLoader("layout", str(root / "linux/droi-desktop-layout"))
spec = importlib.util.spec_from_loader(loader.name, loader)
m = importlib.util.module_from_spec(spec)
loader.exec_module(m)
with tempfile.TemporaryDirectory() as td:
    m.CONFIG = pathlib.Path(td) / "config"
    m.WALLPAPER = pathlib.Path(td) / "wallpaper.png"
    m.wallpaper()
    assert m.WALLPAPER.read_bytes().startswith(b"\x89PNG")
    ini = "# comment\n[panel1]\nplugins=menu,taskbar\npanelSize=90\n[other]\nkey=value\n"
    path = m.CONFIG / "lxqt/panel.conf"
    path.parent.mkdir(parents=True)
    path.write_text(ini)
    m.lxqt(150, 48, 36)
    content = path.read_text()
    assert m.ini_get(content, "panel1", "panelSize") == "24"
    assert m.ini_get(content, "panel1", "plugins") == "menu,taskbar"
    assert m.ini_get(content, "other", "key") == "value"
    assert path.with_name("panel.conf.before-droi-ui").read_text() == ini
    p = m.CONFIG / "pcmanfm-qt/lxqt/settings.conf"
    assert m.ini_get(p.read_text(), "Desktop", "Wallpaper") == str(m.WALLPAPER)
    m.lxde(150,48,36)
    assert m.ini_get((m.CONFIG / "libfm/libfm.conf").read_text(), "config", "big_icon_size") == "48"
    m.icewm(150,48)
    assert "TaskBarDoubleHeight=0" in (m.CONFIG / "icewm/preferences").read_text()
    assert m.ini_get((m.CONFIG / "pcmanfm/droi-icewm/desktop-items-0.conf").read_text(), "*", "wallpaper") == str(m.WALLPAPER)
    calls=[]
    m.run=lambda *args: calls.append(args) or ('{"outputs":[{"id":1,"enabled":true,"connected":true,"scale":1}]}' if args[-1]=="-j" else "")
    assert m.plasma(150)
    assert calls[-1] == ("kscreen-doctor", "output.1.scale.1.5")
print("Desktop layout fixtures passed")
