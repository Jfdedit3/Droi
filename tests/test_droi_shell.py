#!/usr/bin/env python3
from importlib.machinery import SourceFileLoader
from pathlib import Path
m=SourceFileLoader("shell",str(Path(__file__).resolve().parents[1]/"linux/droi-shell")).load_module()
for p in (100,125,150,200):
    x=m.metrics(1560,720,p)
    assert 42<=x["bar"]<=68
    assert x["menu_h"]+x["bar"]+8<720
    assert x["menu_w"]<1560
assert m.scale_value("bad")==150
assert m.matches("Gestionnaire de fichiers","Ubuntu","fichiers ubuntu")
assert not m.matches("Terminal","","steam")
print("Droi shell geometry and search passed")
