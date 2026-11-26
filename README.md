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

### Démarrage rapide

Les lancements via Droi désactivent par défaut les traces Wine/Proton détaillées et conservent les erreurs. Pour un rapport complet : `droi-windows --debug "/chemin/jeu.exe"` ou Ouvrir avec > Droi - Diagnostic. La préparation facultative `droi-windows --prepare "/chemin/jeu.exe"` initialise le préfixe exact du jeu via Proton et cmd.exe, sans exécuter le jeu ni lancer Gamescope. Elle déplace le coût du premier lancement, ne le supprime pas. Les préfixes, caches et réglages graphiques sont conservés ; le contrôle des DLL et des composants reste actif. Le gain de temps doit être mesuré sur Android.

### Lancements bloqués

Droi refuse un deuxième lancement du même fichier (chemin résolu, verrou libéré à la fin). Un indicateur KDE indéterminé est affiché pendant le démarrage si KDialog et QDBus sont disponibles ; il ne mesure pas l'avancement de Wine. Le menu Ouvrir avec > Droi - Annuler le lancement, ou `droi-windows --cancel "/chemin/jeu.exe"`, demande l'arrêt du groupe de processus de ce lancement uniquement. Les processus Wine détachés peuvent nécessiter la fermeture de la session Ubuntu. Aucun préfixe ni sauvegarde n'est supprimé. NFS Carbon (NFSC.exe) utilise désormais le profil OpenGL logiciel par défaut, avec son préfixe existant. Le test `droi-windows --sync esync "/chemin/jeu.exe"` est facultatif : il demande ESYNC sans NTSYNC/FSYNC, mais le moteur peut conserver la synchronisation d'un préfixe déjà actif. Aucun changement global de synchronisation.

### Gestionnaire de processus Wine

Le menu KDE > Système > Droi - Processus Wine ouvre le gestionnaire. Il permet d'actualiser la liste, de fermer un processus, un préfixe, ou tous les processus Wine visibles, avec confirmation. Dolphin propose aussi Ouvrir avec > Droi - Fermer les processus Wine de ce programme. En terminal : `droi-windows --processes`, `droi-windows --stop-wine PID`, `droi-windows --stop-wine all`, ou `droi-windows --stop-prefix "/chemin/jeu.exe"`. Ajouter `--yes` pour confirmer explicitement sans dialogue. Le gestionnaire vérifie le même UID réel et l'identité du processus avant signal, puis utilise TERM et KILL si nécessaire. Il ne supprime aucun fichier, mais un arrêt forcé peut perdre les données non enregistrées. Les processus que /proc ne permet pas d'identifier ne sont pas arrêtés ; dans ce cas fermer la session Ubuntu reste le dernier recours.

### Accélération générale des jeux

Le choix PRoot Fast Path utilise désormais le mode réel de la session, pas la préférence de mode natif. Le bureau reste sous PRoot pour les exécutables statiques ; lorsque le pack PRoot compatible, seccomp et la préférence d'accélération le permettent, le fast path est activé. Les lancements Droi rapides évitent également le miroir des traces Wine, tout en conservant stderr et le mode diagnostic. Le premier démarrage d'un préfixe et la traduction x86/ARM64 restent nécessaires. Vérifier la ligne `proot: fast path on` dans app.log, puis exécuter `droi-doctor` après installation. Aucun délai universel de démarrage n'est garanti.

### Choix de l'environnement de bureau

Au lancement d'Ubuntu, choisir KDE Plasma (Wayland, par défaut), XFCE ou LXQt (X11 dans Xwayland plein écran). Les deux alternatives sont installées via APT au premier lancement, sans supprimer KDE, et partagent la même racine Ubuntu, le même /root, les logiciels et préfixes Wine. Fermer la session en cours avant de changer. L'installation nécessite Internet et de l'espace disque ; les journaux d'installation sont conservés. XFCE et LXQt utilisent Xwayland avec présentation shm et sont expérimentaux sur Android ; si leur démarrage retourne une erreur, Droi affiche l'échec de la session et conserve le choix. Xwayland essaie le plein écran puis une fenêtre rootful simple ; les raisons sont inscrites dans desktop.log. LXQt installé par Droi est protégé contre l'ancien nettoyage de migration. Le mode bureau secondaire conserve Plasma.

Le rapport de Droi 1053 montre que Xwayland échoue sur le lien physique /tmp/.X3-lock avec EPERM, sous l'UID réel Android. Le lancement utilise maintenant -displayfd avec un écran explicite, un verrou flock propre à Droi, une authentification X11 privée et sans écoute TCP. La vérification de démarrage attend le numéro d'écran retourné par Xwayland puis une réponse à xdpyinfo.

### Huit interfaces Ubuntu

Le sélecteur déroulant propose désormais KDE Plasma, XFCE, LXQt, MATE, Cinnamon (session de rendu logiciel), LXDE, Enlightenment et IceWM (gestionnaire de fenêtres avec PCManFM pour le bureau). Les sept alternatives passent par le serveur Xwayland authentifié avec -displayfd. APT installe les composants nécessaires à la demande ; aucun environnement existant n'est supprimé. Les cinq nouvelles interfaces sont expérimentales, en particulier Cinnamon et Enlightenment. La fermeture d'une session alternative ne relance plus Steam Big Picture. La validation CI vérifie que chaque identifiant du sélecteur possède une branche de lancement explicite.

### Taille de l’interface X11

Le menu Ubuntu propose 100, 125, 150 (par défaut) et 200 %. La préférence est transmise aux sessions X11 uniquement ; Qt, GTK, EFL et Xft reçoivent des réglages de taille au démarrage. Le framebuffer et la résolution des jeux ne sont pas modifiés. GTK utilise un facteur entier pour les éléments graphiques ; à 150 %, certains éléments sont donc agrandis à 200 % tandis que le texte suit le DPI demandé. Les panneaux GTK2 et les réglages propres aux bureaux peuvent ignorer ces paramètres. Plasma conserve son comportement d'origine. Le résultat doit être testé sur l'appareil.

### Correction XFCE sur écran de téléphone

XFCE utilise désormais son DPI natif au lieu du facteur GTK entier (qui doublait la taille à 150 %). Les icônes et les panneaux sont dimensionnés séparément selon la préférence de taille. Le compositeur XFWM est désactivé pour cette session afin de limiter la charge de rendu. Un fond local PNG est utilisé uniquement lorsqu'aucune image lisible n'est configurée ou que l'image est désactivée. La résolution de Xwayland, les fichiers, les autres bureaux et les préfixes Wine restent inchangés. La configuration XFCE est ajustée au démarrage, sans effacer ses autres préférences.

### Ajustements des huit bureaux

Le facteur GTK entier est désormais 1 pour tous les bureaux X11, et le DPI X11 est ajusté sans changer le mode vidéo. XFCE conserve son profil dédié ; LXQt adapte la taille logique des panneaux et de PCManFM-Qt ; MATE et Cinnamon utilisent les clés GSettings disponibles ; LXDE et IceWM ajustent leurs fichiers de configuration et le bureau PCManFM. Enlightenment reçoit le DPI X11 et ELM_SCALE, son thème et ses paramètres binaires natifs sont conservés. Plasma utilise kscreen-doctor pour appliquer l'échelle native Wayland lorsqu'un seul écran actif est détecté ; si ce mécanisme est indisponible, il conserve son échelle existante. Les fonds existants lisibles sont préservés. Un PNG local remplace les images manquantes pour LXQt, MATE, Cinnamon, LXDE et IceWM ; les autres bureaux gardent leurs mécanismes natifs. Les réglages modifiés dans les fichiers utilisateur ont une sauvegarde .before-droi-ui. La validation sur téléphone reste nécessaire.

### Droi Desktop — interface unique

Le sélecteur des huit bureaux est remplacé par Droi Desktop, un shell GTK3 indépendant inspiré de Windows 11. Openbox gère les fenêtres X11 (déplacement, redimensionnement, focus et fermeture) ; Droi dessine le fond abstrait original, la barre des tâches centrée, le menu Démarrer, la recherche et la liste des fenêtres actives via libwnck. Les applications sont lues via Gio et les raccourcis pointent vers les logiciels installés. Les composants GTK3/Openbox/Wnck manquants sont installés au premier lancement. Le pack de compatibilité historique et les anciens paquets ne sont pas purgés : ils peuvent contenir des dépendances, Dolphin, Konsole et les outils Wine. Aucun ancien bureau n'est proposé ni lancé par le chemin principal. La taille 100–200 % est conservée. Le shell n'est pas Windows et ne contient aucun asset Windows. Le rendu et l'intégration EWMH restent à valider sur Android.
