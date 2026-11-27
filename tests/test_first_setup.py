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
assert "https://opencode.ai/v2/install" in apps
assert "--no-modify-path" in apps
assert "npm install" not in apps
assert "npm" not in update[update.index("def installed_opencode"):update.index("def release_status")]
assert apps.index("PYVERIFY\n        )") < apps.index('mv -Tf "$stage" "$bin/opencode"')
assert ".droi-v2-version" in apps
assert "exit 3" in apps
assert "[droi-setup]" in update
assert "droiddeck-pipewire start" in session

assert "openbox metacity xwayland" in apps
assert "metacity --replace" in session
assert "focus-mode click" in session
assert "start_wm openbox" in session

assert 'if [ "${1:-}" = --prepare ]; then exit 0; fi' in session
assert 'if [ "${1:-}" = --recovery ]; then' in session
assert "droi-opencode.desktop" in apps
