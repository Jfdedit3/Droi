# Droi Desktop

Droi exécute Ubuntu KDE Plasma sur Android ARM64 sans root Android, à partir du moteur [DroidDeck](https://github.com/Droid-Deck/DroidDeck) (GPL v3).

## Installation et mises à jour

Télécharger l'APK ARM64 depuis [GitHub Releases](https://github.com/Jfdedit3/Droi/releases). Android 8.0 ou ultérieur. Dans Paramètres, Droi indique la version installée et les mises à jour disponibles. Le téléchargement automatique est facultatif ; avant installation, Droi contrôle la taille, le SHA-256, le nom de paquet, le code de version et le certificat de signature. Android confirme l'installation. Ne pas désinstaller l'ancienne application : les données Ubuntu sont conservées par une mise à jour compatible.

## Applications Linux : à la demande

Le bureau ne télécharge plus de logiciels au démarrage et ne désinstalle plus Firefox. Les logiciels déjà installés sont conservés. Dans Konsole :

```bash
droi-apps status
droi-apps install chatgpt VERSION SHA256 [FICHIER_DEB]
droi-apps install opencode VERSION
droi-apps install brave VERSION
```

Remplacer VERSION par une version réelle, SHA256 par le condensat attendu obtenu d'une source de confiance. Le fichier DEB local est facultatif : sinon Droi télécharge le paquet ChatGPT ARM64 depuis l'URL de distribution et refuse une version ou empreinte différente. Pour Brave, le dépôt APT signé doit déjà être configuré ; suivre [les instructions officielles](https://brave.com/linux/) puis choisir une version via `apt-cache policy brave-browser`. Pour OpenCode, choisir une version via `npm view opencode-ai versions --json`. Ne pas réutiliser un condensat calculé sur un téléchargement non vérifié comme preuve d'origine. 

## ChatGPT et diagnostics

Le bureau Ubuntu utilise PRoot pour les programmes statiques. Le pack PRoot corrigé est embarqué. ChatGPT Web est disponible comme raccourci. ChatGPT Desktop en root demande explicitement confirmation avant de désactiver le sandbox Chromium ; hors root il essaie le lancement normal. PRoot n'est pas une frontière de sécurité, et la compatibilité Electron/Codex reste à valider sur le téléphone.

```bash
droi-doctor
chatgpt
```

Le diagnostic vérifie statx sur un chemin et un descripteur, openat, lecture/écriture, SQLite et, si installé, Codex avec un dossier temporaire isolé (aucune modification de ~/.codex). Le diagnostic CI ARM64 teste les appels système sur Linux natif ; il ne remplace pas le test de PRoot Android.

## Sources et builds

Le workflow principal récupère la révision DroidDeck d38fd373f0caf6574458abc574640f2d961d09d9, applique les patchs Android/Linux séparés, installe les sources Droi puis compile l'APK. Les scripts Linux, les deux classes Android propres à Droi et les tests sont maintenus en fichiers distincts. Le workflow de publication conserve le code de version monotone basé sur le numéro du build. Les anciens `desktop.patch`, `update.patch` et `build-rootfs.yml` concernent l'ancien chemin Debian/Fedora et ne sont pas utilisés par le workflow APK actuel ; ils sont conservés pour historique.

## Programmes Windows

Les EXE et MSI sont associés à Droi Wine / Proton dans KDE. Fermer Ubuntu pour installer ou choisir Proton dans Composants, puis redémarrer Ubuntu et double-cliquer dans Dolphin. En terminal : `droi-windows "/chemin/Programme.exe"`. `droi-windows --list` liste les moteurs détectés et `droi-windows --window "/chemin/Programme.exe"` utilise le mode fenêtre de compatibilité. Le lanceur réutilise les préfixes et options de DroidDeck ; Wine système sert de repli si aucun Proton utilisable n'est installé. Les titres Steam reconnus conservent leur chemin de lancement Steam. Aucun téléchargement de moteur n'est lancé par le gestionnaire Droi lui-même. Exécuter seulement des fichiers de confiance. La compatibilité x86/x64 dépend du moteur et des composants installés.

### Profils graphiques Windows

Geometry Dash utilise automatiquement le mode Compatibilite (llvmpipe, swrast), avec son propre préfixe normal. Le contournement SAS4 reste indépendant. Dans Dolphin, utiliser Ouvrir avec > Droi - Performance, Compatibilite ou Automatique pour enregistrer un profil par chemin d'exécutable. En terminal : `droi-windows --graphics compatibility "/chemin/jeu.exe"` (ou `performance`, `auto`). Le mode Compatibilite évite Gamescope et réapplique le rendu logiciel au dernier étage de lancement de Proton, y compris après la préparation du pont Vulkan. Le mode Automatique ne change pas les autres jeux. Aucun ancien préfixe n'est supprimé ni migré automatiquement ; la compatibilité réelle dépend de l'appareil.
