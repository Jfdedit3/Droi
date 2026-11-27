#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
activity=(root/"android/DesktopLauncherActivity.kt").read_text()
patch=(root/"patches/android.patch").read_text()
assert "AllFilesAccess.missing()" in activity
assert "AllFilesAccess.open(this)" in activity
assert "override fun onResume()" in activity
assert "if (!SessionState.running && !storageGranted)" in activity
assert "Continuer sans accès" in activity
assert "Gérer l’accès au stockage" in activity
assert 'externalStorage.list() != null' in patch
assert 'host + ":/sdcard"' in patch
assert 'host + ":/storage/emulated/0"' in patch
print("Shared storage permission and bind integration checks passed")
