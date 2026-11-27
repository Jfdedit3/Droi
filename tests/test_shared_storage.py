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

resume=activity.split("override fun onResume()",1)[1].split("private fun requestSharedStorage()",1)[0]
assert 'getBoolean("initial_permission_prompt_v1", false)' in resume
assert 'putBoolean("initial_permission_prompt_v1", true)' in resume
assert "storagePromptForLaunch = false" in resume and "storagePrompt = true" in resume
assert "if (launchRequested) startUbuntuSession()" in activity
assert "storagePromptForLaunch = true" in activity
print("Initial storage permission prompt checks passed")
