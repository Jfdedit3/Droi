#!/usr/bin/env python3
import os
from pathlib import Path
import subprocess
import tempfile
source=(Path(__file__).resolve().parents[1]/"linux/droi-chromium-launch").read_text()
with tempfile.TemporaryDirectory() as d:
    root=Path(d)
    home=root/"home"; home.mkdir()
    mocks=root/"bin"; mocks.mkdir()
    def mock(name,body):
        path=mocks/name;path.write_text("#!/bin/sh\n"+body+"\n");path.chmod(0o755);return path
    executable=mock("app",'printf "%s\\n" "$@" >> "$TEST_LAUNCH"')
    mock("id","echo 0")
    mock("kdialog",'printf "%s\\n" "$*" >> "$TEST_DIALOG"; case "$*" in *--warningyesno*) exit "$TEST_DECISION";; *) exit "$TEST_REMEMBER";; esac')
    script=root/"launch"
    script.write_text(source.replace("/usr/bin/chatgpt",str(executable)).replace("/usr/bin/brave-browser",str(executable)))
    env={**os.environ,"HOME":str(home),"PATH":str(mocks)+":"+os.environ["PATH"],"DISPLAY":":99","TEST_LAUNCH":str(root/"launched"),"TEST_DIALOG":str(root/"dialogs"),"DROI_DESKTOP_ENV":"droi"}
    def run(app,decision=0,*args):
        subprocess.run(["bash",str(script),app,*args],env={**env,"TEST_DECISION":str(decision if decision==2 else 0),"TEST_REMEMBER":str(decision)},stdin=subprocess.DEVNULL,check=True,capture_output=True)
    def count(file):
        p=root/file;return len(p.read_text().splitlines()) if p.exists() else 0
    run("chatgpt",2)
    assert count("launched")==0 and count("dialogs")==1
    run("chatgpt",1)
    assert count("launched")>0 and count("dialogs")==3
    marker=home/".local/state/droi/chatgpt-sandbox-consent-v1"
    assert not marker.exists()
    run("chatgpt",0)
    assert marker.read_text().strip()=="allow"
    assert marker.stat().st_mode & 0o077 == 0
    run("chatgpt",2)
    assert count("dialogs")==5
    run("brave",2)
    assert count("dialogs")==6
    run("chatgpt",0,"--reset-sandbox-consent")
    assert not marker.exists()
    run("chatgpt",2)
    assert count("dialogs")==7
    run("chatgpt",2,"--no-sandbox")
    assert count("dialogs")==7 and not marker.exists()
print("Chromium consent tests passed")
