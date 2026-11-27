#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
apps=(root/"linux/droi-apps").read_text()
session=(root/"linux/droi-shell-session").read_text()
update=(root/"linux/droi-update").read_text()
assert all(f"== SETUP {i}/5" in apps for i in range(1,6))
assert apps.index('apt-get -o DPkg::Lock::Timeout=600 -o Acquire::Retries=3') < apps.index('== SETUP 4/5')
assert apps.index('if [ "$app_result" != 0 ] || ! defaults_needed') < apps.index('mv -f "$setup_marker.tmp" "$setup_marker"')
assert session.index("droi-update --first-run") < session.rindex("/usr/bin/droi-shell")
assert '"setup"],stdout=subprocess.PIPE' in update
assert "--no-remove" in apps and "--with-new-pkgs" in apps
print("First setup integration checks passed")

assert apps.index('mv -f "$system_marker.tmp" "$system_marker"') < apps.index("== SETUP 4/5")
assert "timeout=30" in apps
assert "@opencode/cli-linux-arm64@$version" in apps
assert "--ignore-scripts" in apps
assert apps.index("PYVERIFY\n        [") < apps.index('mv -Tf "$prefix/bin/.opencode-new"')
assert "exit 3" in apps
assert "[droi-setup]" in update
assert "droiddeck-pipewire start" in session
