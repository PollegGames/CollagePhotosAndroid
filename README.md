# Collage Photos — fond d’écran Android

[![Android CI](https://github.com/PollegGames/CollagePhotosAndroid/actions/workflows/android.yml/badge.svg)](https://github.com/PollegGames/CollagePhotosAndroid/actions/workflows/android.yml)
[![Version](https://img.shields.io/badge/version-0.2.2-blue)](https://github.com/PollegGames/CollagePhotosAndroid/releases/latest)
[![Licence](https://img.shields.io/badge/licence-Apache--2.0-green)](LICENSE)

`Collage Photos` est une application Android originale qui affiche les photos d’un
dossier sous forme de fond d’écran animé. Elle ne contient aucun code provenant de
Photo FX Live Wallpaper.

L’APK officiel est disponible dans les
[releases GitHub](https://github.com/PollegGames/CollagePhotosAndroid/releases).

## Version 0.2.2

Cette version comprend :

- choix d’un dossier avec le sélecteur Android (`ACTION_OPEN_DOCUMENT_TREE`) ;
- conservation de l’accès limité au dossier avec `takePersistableUriPermission` ;
- détection des fichiers JPG, JPEG, PNG, WEBP, HEIC et HEIF ;
- une mosaïque asymétrique de 3 photos : une grande et deux petites ;
- placement aléatoire de la grande case en haut, en bas, à gauche ou à droite ;
- photos aléatoires sans doublon quand le dossier contient assez d’images ;
- rendu `centerCrop`, sans déformation ;
- intervalle réglable de 1 à 60 minutes par nouvelle photo, avec 5 minutes par défaut ;
- construction de la nouvelle mosaïque par-dessus l’ancienne, une photo à la fois ;
- remplacement de l’ancienne mosaïque seulement lorsque les 3 nouvelles photos sont
  prêtes ;
- fondu local à la nouvelle photo, désactivable ;
- espace réglable et fond noir, blanc ou couleur RGB personnalisée ;
- schéma de composition instantané dans l’application, sans lecture des photos ;
- photos réelles chargées uniquement dans l’aperçu officiel Android et le wallpaper ;
- aucun décodage lorsque le wallpaper n’est pas visible ; le temps écoulé est calculé
  seulement lorsqu’il redevient visible ;
- au retour, rattrapage de 1, 2 ou 3 photos selon le temps écoulé, sans jamais
  construire plus d’une mosaïque ;
- aucune permission générale de stockage et aucune permission Internet.

La version 0.2.1 a corrigé le fond noir observé avec certains fournisseurs de
fichiers. La sélection ne dépend plus d’une lecture préalable des dimensions : chaque
photo est essayée directement par le décodeur redimensionné, et les fichiers illisibles
sont ignorés. Un écran de composition neutre reste visible pendant le premier chargement,
avec plusieurs nouvelles tentatives contrôlées si le fournisseur répond tardivement.

La version 0.2.2 ajoute le rattrapage au retour sur l’écran. Avec un intervalle d’une
minute, une absence d’une minute change une photo, deux minutes en changent deux, et
trois minutes ou davantage terminent au maximum une seule mosaïque de trois photos.

L’index des milliers de fichiers est obtenu par une seule requête au fournisseur de
documents et conservé 30 minutes en mémoire. Une seule nouvelle photo est ensuite décodée
à chaque intervalle. La disposition et les URI de la dernière mosaïque complète sont
enregistrées dans DataStore afin de la retrouver rapidement après un redémarrage.

## Prérequis

- Android Studio récent ;
- JDK 17 ;
- Android SDK Platform 36 ;
- Android SDK Build-Tools 36.x ;
- téléphone sous Android 8.0 (API 26) ou plus récent.

Le projet utilise Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21,
Jetpack Compose et AndroidX. Il ne contient aucune bibliothèque native obligatoire et
fonctionne sur les téléphones `arm64-v8a`, notamment le Sony Xperia XQ-FE54.

## Ouvrir le projet

1. Décompresser l’archive.
2. Ouvrir Android Studio.
3. Choisir **Open** et sélectionner le dossier `PhotoCollageWallpaper`.
4. Laisser Android Studio synchroniser Gradle.
5. Installer la plateforme Android SDK 36 si Android Studio le propose.

## Compiler et tester

Linux/macOS :

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

Windows PowerShell :

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

APK produit :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Les tests couvrent les quatre dispositions à 3 photos, la progression photo par photo,
le rattrapage plafonné à une mosaïque, la sélection aléatoire sans doublon, le calcul
`centerCrop`, le filtrage des formats, les dossiers vides ou invalides et les limites
de l’intervalle.

## Produire un APK release signé

Ne place jamais le fichier de clé ni ses mots de passe dans Git.

Créer une clé une seule fois :

```bash
keytool -genkeypair -v \
  -keystore photo-collage-release.jks \
  -alias photo-collage \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

Linux/macOS :

```bash
export PHOTO_COLLAGE_KEYSTORE="/chemin/absolu/photo-collage-release.jks"
export PHOTO_COLLAGE_STORE_PASSWORD="mot-de-passe-du-keystore"
export PHOTO_COLLAGE_KEY_ALIAS="photo-collage"
export PHOTO_COLLAGE_KEY_PASSWORD="mot-de-passe-de-la-cle"
./gradlew clean testReleaseUnitTest assembleRelease
```

Windows PowerShell :

```powershell
$env:PHOTO_COLLAGE_KEYSTORE="C:\chemin\photo-collage-release.jks"
$env:PHOTO_COLLAGE_STORE_PASSWORD="mot-de-passe-du-keystore"
$env:PHOTO_COLLAGE_KEY_ALIAS="photo-collage"
$env:PHOTO_COLLAGE_KEY_PASSWORD="mot-de-passe-de-la-cle"
.\gradlew.bat clean testReleaseUnitTest assembleRelease
```

APK produit :

```text
app/build/outputs/apk/release/app-release.apk
```

Sans ces quatre variables, Gradle peut produire un APK release non signé.

## Installation sur le Sony Xperia

Installation directe :

1. Copier l’APK release sur le téléphone.
2. L’ouvrir avec l’application **Fichiers**.
3. Autoriser temporairement l’installation depuis cette source.
4. Installer l’APK.

Pour mettre à jour la version 0.2.0 ou 0.2.1, installer simplement la 0.2.2 par-dessus, sans
désinstaller l’application : le paquet et la clé de signature sont identiques, donc le
dossier et les réglages sont conservés.

Installation avec ADB :

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Utilisation

1. Ouvrir **Collage Photos**.
2. Appuyer sur **Choisir le dossier de photos**.
3. Confirmer **Utiliser ce dossier** dans le sélecteur Android.
4. Choisir l’intervalle, l’espace, la couleur et le fondu.
5. Appuyer sur **Aperçu et définition du fond d’écran**.
6. Vérifier les photos dans l’aperçu officiel Android/Sony, puis appliquer le live
   wallpaper à l’écran d’accueil ou à l’écran de verrouillage.

Android peut afficher **Aucune autorisation demandée** dans la fiche de l’application :
c’est normal. Le Storage Access Framework accorde uniquement l’accès à l’URI du dossier
choisi ; ce n’est pas une permission générale de stockage.

## Limites actuelles

- le renouvellement explicite à chaque déverrouillage n’a pas encore son propre réglage ;
- la sélection reste aléatoire ;
- seuls les fichiers directement présents dans le dossier choisi sont parcourus, pas les
  sous-dossiers.

## Vie privée et sécurité

L’application fonctionne entièrement sur le téléphone : aucun compte, publicité,
suivi, collecte de données ou accès Internet. Elle accède uniquement au dossier choisi
avec le sélecteur Android. Voir [PRIVACY.md](PRIVACY.md) pour les détails.

Chaque release contient l’APK, ses sommes SHA-256 et le certificat public servant à
vérifier l’identité de signature. La clé privée de signature n’est jamais enregistrée
dans GitHub. Voir [certificates/README.md](certificates/README.md) et
[SECURITY.md](SECURITY.md).

## Publication

Le paquet de publication contient un script Windows qui initialise Git, pousse le
projet dans `PollegGames/CollagePhotosAndroid`, crée le tag `v0.2.2` et publie l’APK
dans une release GitHub. La procédure est décrite dans [PUBLISHING.md](PUBLISHING.md).

## Licence

Copyright 2026 Polleg Games. Code publié sous licence
[Apache License 2.0](LICENSE).
