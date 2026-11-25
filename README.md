# Droi Desktop

Droi est une application Android qui permet de lancer un bureau Linux sur un appareil ARM64, sans root. Elle reprend le moteur Linux de [DroidDeck](https://github.com/Droid-Deck/DroidDeck).

## Bureau Ubuntu

Droi lance Ubuntu avec KDE Plasma. Au premier démarrage du bureau, une connexion Internet permet d’installer Brave depuis son dépôt officiel ARM64. Une fois Brave installé, Droi le configure comme navigateur par défaut et retire le paquet Firefox.

## Installer l’APK

1. Ouvre [GitHub Releases](https://github.com/Jfdedit3/Droi/releases) et télécharge `Droi-Desktop-arm64.apk` dans la dernière version publiée.
2. Ouvre l’APK sur ton téléphone. Si Android le demande, autorise l’installation depuis l’application qui a ouvert le fichier.
3. Lance **Droi Desktop** et choisis ton bureau Linux.

Droi nécessite un appareil **ARM64** sous **Android 8.0 ou ultérieur**. Le support graphique dépend de l’appareil et du pilote.

## Mises à jour

Dans Droi, ouvre **Paramètres → Télécharger automatiquement les nouveaux APK**. Cette option est désactivée par défaut. Quand elle est activée, Droi vérifie à l’ouverture si une nouvelle version est disponible, puis télécharge l’APK. **Vérifier maintenant** permet de lancer la vérification manuellement. Android te demandera de confirmer l’installation.

## Compilation et sources

Le [workflow GitHub Actions](.github/workflows/build-apk.yml) récupère la révision de DroidDeck utilisée par le projet, applique [les modifications de Droi](ubuntu.patch), puis compile l’APK ARM64.

Le code de base est celui de [DroidDeck](https://github.com/Droid-Deck/DroidDeck), distribué sous [GNU GPL v3](https://github.com/Droid-Deck/DroidDeck/blob/d38fd373f0caf6574458abc574640f2d961d09d9/LICENSE).
