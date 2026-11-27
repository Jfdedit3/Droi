#!/usr/bin/env python3
from importlib.machinery import SourceFileLoader
from pathlib import Path
m=SourceFileLoader("updater",str(Path(__file__).resolve().parents[1]/"linux/droi-update")).load_module()
assert m.version_number("droi-1065")==1065
assert m.version_number("invalid")==0
for root in ([],["/usr/local/bin/droiddeck-root"]):
    steps=m.commands(root)
    assert len(steps)==3
    assert all(command[:len(root)]==root for _,command in steps)
    assert "--no-remove" in steps[1][1]
    assert "--with-new-pkgs" in steps[1][1]
    assert all("autoremove" not in command and "dist-upgrade" not in command for _,command in steps)
print("Droi Update command and version tests passed")
