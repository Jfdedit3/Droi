from importlib.machinery import SourceFileLoader
from pathlib import Path
m=SourceFileLoader("downloader",str(Path(__file__).resolve().parents[1]/"linux/droi-game-downloader")).load_module()
assert m.app_id("https://store.steampowered.com/app/620/Portal_2/")=="620"
assert m.app_id("620")=="620"
for bad in ("0","-1","https://example.com/app/620","4294967296"):
    try: m.app_id(bad)
    except ValueError: pass
    else: raise AssertionError(bad)
args=m.command("DepotDownloader","620","windows",16,"qr","",True)
assert "-qr" in args and "-validate" in args and "-password" not in args
assert args[args.index("-max-downloads")+1]=="16"
print("Droi Game Downloader arguments passed")
