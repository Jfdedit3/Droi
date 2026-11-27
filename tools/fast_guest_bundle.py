#!/usr/bin/env python3
"""Package only known guest-side scripts; never modify Android or native binaries."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GUEST = ("droi-apps", "droi-doctor", "droi-windows", "droi-shell",
         "droi-shell-session", "droi-update", "droi-chromium-launch",
         "droi-notify", "droi-game-downloader", "opencode")
UPSTREAM = ("droiddeck-session", "droiddeck-game-env", "droiddeck-steam-install",
            "droiddeck-proton-run", "droiddeck-game-debug")
def main():
    upstream, dest = map(Path, sys.argv[1:])
    dest.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        folder=Path(tmp)
        sources={f"usr/bin/{n}":ROOT/"linux"/n for n in GUEST}
        sources.update({f"usr/local/bin/{n}":upstream/"tools/linuxfs/overlay/usr/local/bin"/n for n in UPSTREAM})
        sources["usr/local/bin/droiddeck-desktop"]=upstream/"tools/linuxfs/desktop/droiddeck-desktop"
        sums=[]
        for name, source in sorted(sources.items()):
            target=folder/name
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(source,target)
            target.chmod(0o755)
            sums.append(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {name}")
        (folder/"SHA256SUMS").write_text("\n".join(sums)+"\n")
        (folder/"COMMIT").write_text(subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True))
        # Explicit manual installer: verify all files, preserve old copies, only install allowlisted files.
        names=" ".join(sorted(sources))
        (folder/"install.sh").write_text("""#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
[ "$(id -u)" = 0 ] || { echo "Lancer depuis le terminal root Ubuntu de Droi."; exit 1; }
[ "$(dpkg --print-architecture)" = arm64 ] && [ -x /usr/bin/droi-apps ] || { echo "Droi Ubuntu ARM64 requis"; exit 1; }
sha256sum -c SHA256SUMS
backup=$(mktemp -d "$HOME/.droi-hotfix-backup.XXXXXX")
for file in """+names+"""; do
  [ -f "$file" ] && [ ! -L "$file" ] || exit 1
  if [ -f "/$file" ]; then
    mkdir -p "$backup/$(dirname "$file")"
    cp -pL "/$file" "$backup/$file"
  fi
  install -m755 "$file" "/$file"
done
echo "Correctif Linux installé. Sauvegarde : $backup"
echo "Ferme puis relance Ubuntu. Le numéro de version APK ne change pas."
""")
        (folder/"install.sh").chmod(0o755)
        with tarfile.open(dest/"Droi-Linux-Hotfix.tar.gz","w:gz",compresslevel=1) as archive:
            for name in list(sorted(sources))+["SHA256SUMS","COMMIT","install.sh"]:
                archive.add(folder/name,arcname=name,recursive=False)
if __name__=="__main__":
    main()
