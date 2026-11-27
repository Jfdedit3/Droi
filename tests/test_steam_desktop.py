#!/usr/bin/env python3
import os
import subprocess
import sys
from pathlib import Path
source=Path(sys.argv[1]).read_text()
start=source.index('    # Droi X11: do not mix')
end=source.index('\n    fi\n',start)+len('\n    fi\n')
block=source[start:end]
for env, expected in [("x11","-cef-disable-gpu -cef-disable-gpu-compositing"),("plasma","-cef-force-gpu"),("","-cef-force-gpu")]:
    result=subprocess.run(["bash","-c",'set -u\n_dd_cef=(-cef-force-gpu)\n'+block+'\nprintf "FLAGS=%s\\n" "${_dd_cef[*]}"\n'],env={**os.environ,"DROI_SHELL_BACKEND":env,"DD_STEAM_UI":"desktop"},capture_output=True,text=True,check=True)
    assert "FLAGS="+expected in result.stdout,result.stdout
assert '&& [ "${DROI_SHELL_BACKEND:-}" != x11 ]; then\n        /usr/local/bin/droiddeck-steam-desktop-ui' in source
print("Droi Steam desktop profile tests passed")
