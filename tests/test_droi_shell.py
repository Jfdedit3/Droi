#!/usr/bin/env python3
from importlib.machinery import SourceFileLoader
from pathlib import Path
m=SourceFileLoader("shell",str(Path(__file__).resolve().parents[1]/"linux/droi-shell")).load_module()
for width,height in ((960,540),(1280,720),(1560,720),(1920,1080),(2560,1440)):
    previous=0
    for p in (100,125,150,200):
        x=m.metrics(width,height,p)
        assert 34<=x["bar"]<=54
        assert previous<=x["bar"]
        previous=x["bar"]
        assert 18<=x["icon"]<=28
        assert x["icon"]+8<=x["bar"]
        assert 11<=x["font"]<=15
        assert x["menu_h"]+x["bar"]+8<height
        assert 280<=x["menu_h"]
        assert 310<=x["menu_w"]<=width-24
assert m.metrics(1560,720,100)["bar"]==34
assert m.scale_value("bad")==150
assert m.matches("Gestionnaire de fichiers","Ubuntu","fichiers ubuntu")
assert not m.matches("Terminal","","steam")
print("Droi shell geometry and search passed")
