# Feuille de route performances et cadrage

## Objectif

Cette feuille de route prépare l'évolution du pipeline photo pour une prochaine version
de Collage Photos. Elle couvre les trois symptômes observés :

- délai perceptible avant certains changements de photo ;
- fondu visible par paliers ;
- photos parfois trop coupées ou mal adaptées à leur case.

Le travail doit conserver les garanties actuelles :

- aucune permission Internet ou permission générale de stockage ;
- aucun décodage lorsque le wallpaper n'est pas visible ;
- conservation de l'ancienne mosaïque tant que la nouvelle n'est pas complète ;
- rattrapage limité à une seule mosaïque ;
- compatibilité avec Android 8.0 et les réglages déjà enregistrés ;
- repli sûr lorsqu'une image ou un fournisseur de documents est illisible.

## État global

| Lot | État | Résultat attendu |
| --- | --- | --- |
| 0. Outillage et baseline | CI validée, mesures Xperia à faire | Mesures fiables avant modification |
| 1. Instrumentation | Validation automatique réussie | Temps de scan, décodage et rendu observables |
| 2. Fondu synchronisé | Validation automatique réussie | Animation régulière à 60/90/120 Hz |
| 3. Préchargement borné | Validation automatique réussie | Changement lancé sans attente perceptible |
| 4. Cadrage intelligent | Validation automatique réussie | Moins de contenu important coupé |
| 5. Mode photo entière | Validation automatique réussie | Choix explicite entre remplissage et image complète |
| 6. Optimisations secondaires | À mesurer sur Xperia | Moins d'allocations et meilleure réutilisation mémoire |
| 7. Validation et release | Essai Xperia requis | APK testé, documenté et réversible |

## Lot 0 — Outillage et baseline

### Prérequis

Le JDK 17.0.12 est installé localement. Le poste ne possède pas de SDK Android local,
mais GitHub Actions fournit Android SDK Platform 36 et Build-Tools 36.0.0. La première
validation distante de la branche a réussi le 30 juillet 2026.

Commandes de référence :

```powershell
java -version
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

### Baseline à relever

Sur le téléphone de référence, relever :

- délai entre l'échéance de l'intervalle et la première frame du fondu ;
- durée réelle du fondu ;
- intervalle entre deux frames dessinées ;
- durée d'un scan de dossier à froid et depuis le cache ;
- durée de décodage par format ;
- mémoire avant et après 30 changements ;
- absence de scan et de décodage lorsque le wallpaper est invisible.

Le corpus de test reste hors du dépôt et contient :

- JPEG, PNG, WEBP et HEIC/HEIF ;
- portraits, paysages et images presque carrées ;
- photos de petite et très grande résolution ;
- au moins une image corrompue ou inaccessible ;
- un petit dossier et un dossier contenant plusieurs milliers de fichiers.

### Première validation automatique

Le run GitHub Actions `30578828972` a réussi :

- `testDebugUnitTest` ;
- `testReleaseUnitTest` ;
- `lintDebug` ;
- `assembleDebug` ;
- publication de l'APK debug comme artefact téléchargeable.

Cette validation confirme la compilation et les tests automatisés. Elle ne remplace pas
les mesures de fluidité, de cadrage, de mémoire et de batterie sur le Xperia.

## Lot 1 — Instrumentation sans changement visuel

Ajouter des mesures légères autour des opérations suivantes :

- `FolderImageRepository.scan` ;
- `ScaledBitmapDecoder.decode` ;
- préparation d'une mosaïque ;
- attente entre l'échéance et la première frame ;
- temps passé dans chaque dessin de frame.

Les traces doivent être consultables avec Logcat ou Perfetto et ne doivent contenir
aucun nom de fichier, URI ou donnée personnelle.

### Critères d'acceptation

- aucune modification du résultat visuel ;
- aucune information privée dans les traces ;
- coût négligeable lorsque les traces ne sont pas collectées ;
- tests existants toujours verts.

## Lot 2 — Fondu synchronisé à l'écran

Remplacer le couple `FADE_FRAME_COUNT`/`FADE_FRAME_DELAY_MILLIS` par une animation basée
sur le temps.

### Architecture

- introduire une fonction pure qui transforme le temps écoulé en progression `0..1` ;
- attendre la prochaine frame avec `Choreographer` ;
- calculer la progression depuis `frameTimeNanos`, et non depuis un compteur ;
- terminer à une durée cible initiale de 300 ms ;
- sauter directement à la progression correspondant au temps réel si une frame est
  retardée ;
- conserver l'annulation immédiate lorsque le wallpaper devient invisible.

Passer au canvas matériel avec `SurfaceHolder.lockHardwareCanvas()`. Prévoir un repli
sur `lockCanvas()` si le verrouillage matériel échoue sur un appareil particulier.

### Tests

- progression à 0 %, 50 % et 100 % ;
- progression bornée avant et après la durée ;
- durée nulle ou invalide ;
- annulation pendant le fondu ;
- perte et recréation de la surface pendant le fondu ;
- fondu désactivé : une seule frame finale.

### Critères d'acceptation

- aucune liste fixe de 8 frames ;
- durée réelle égale à 300 ms, avec une tolérance d'une frame ;
- pas de prolongation du fondu lorsque le dessin d'une frame prend du retard ;
- aucune frame produite après la perte de visibilité ou de surface ;
- cadence sans palier visible sur le téléphone de référence.

## Lot 3 — Préchargement d'une seule photo

Séparer les responsabilités actuellement regroupées dans `workJob` :

- planification de l'échéance ;
- préchargement ;
- transition visible.

### État proposé

Un résultat préchargé contient :

- la génération du dossier et des réglages ;
- la taille de surface et l'espace entre les cases ;
- la mosaïque préparée et l'index de cellule ;
- le bitmap décodé et son URI.

Le résultat n'est consommé que si toutes ces valeurs correspondent encore à l'état
courant. Un résultat ancien est ignoré.

### Règles

- une seule cellule préchargée au maximum ;
- décodage uniquement sur `Dispatchers.IO` ;
- aucun préchargement si le wallpaper est invisible ;
- annulation lors d'un changement de dossier, de surface ou de réglage affectant le
  rendu ;
- consommation unique du résultat ;
- repli vers le chargement normal si le préchargement échoue ou n'est pas terminé ;
- l'ancienne mosaïque reste visible dans tous les cas.

Le préchargement est lancé avant l'échéance. La marge exacte sera choisie avec les
mesures du lot 1 afin d'éviter de conserver inutilement un bitmap pendant plusieurs
minutes.

Le renouvellement du cache du dossier est également déplacé hors du chemin critique
de l'animation. Les URI qui échouent au décodage peuvent être mémorisées négativement
pour la génération courante du dossier afin de ne pas les essayer à chaque cycle.

### Tests

- résultat prêt avant l'échéance ;
- résultat encore en cours à l'échéance ;
- échec de décodage et candidate suivante ;
- résultat devenu obsolète après changement de dossier ;
- changement de dimensions de surface ;
- passage invisible puis visible ;
- rattrapage de deux ou trois cellules ;
- jamais plus d'une cellule en attente.

### Critères d'acceptation

- délai échéance-première frame inférieur à 100 ms lorsque le résultat est prêt ;
- aucune hausse non bornée de la mémoire ;
- aucune image provenant d'un ancien dossier affichée ;
- aucune régression du rattrapage ou de la mosaïque progressive.

## Lot 4 — Cadrage intelligent

Le `centerCrop` reste disponible, mais la sélection cesse d'ignorer complètement le
format des photos et de l'écran.

### Sélection de disposition

- écran nettement portrait : privilégier les dispositions grande case en haut ou en bas ;
- écran nettement paysage : privilégier les dispositions grande case à gauche ou à
  droite ;
- écran proche du carré : autoriser les quatre dispositions ;
- ajuster la proportion de séparation pour éviter les cases extrêmement étroites.

### Association photo/case

Ajouter un score de compatibilité basé sur la proportion réellement conservée par le
recadrage :

```text
score = min(ratioPhoto / ratioCase, ratioCase / ratioPhoto)
```

Un score proche de `1` signifie que peu de contenu sera coupé.

La lecture des dimensions doit rester bornée à un petit groupe de candidates. Une
métadonnée absente ou illisible ne doit jamais éliminer définitivement une photo :
elle reste une candidate de repli et le décodeur reste la source de vérité. Cette règle
évite de réintroduire l'ancien problème d'écran noir.

### Compatibilité d'orientation

Vérifier l'orientation EXIF sur Android 8.0 et 8.1, où le chemin `BitmapFactory` ne
bénéficie pas automatiquement du comportement d'`ImageDecoder`.

### Tests

- portrait vers case portrait ;
- paysage vers case paysage ;
- score identique pour deux ratios égaux ;
- métadonnée absente ;
- toutes les métadonnées illisibles ;
- écran portrait, paysage et carré ;
- restauration d'une mosaïque enregistrée.

### Critères d'acceptation

- aucune déformation ;
- moins de surface coupée que la sélection aléatoire sur le corpus de référence ;
- aucun scan des dimensions des milliers de fichiers du dossier ;
- aucun écran noir lorsque les métadonnées ne sont pas disponibles.

## Lot 5 — Mode « Photo entière »

Ajouter un réglage persistant avec deux valeurs :

- `REMPLIR` : comportement `centerCrop`, avec cadrage intelligent ;
- `PHOTO_ENTIERE` : comportement `fitCenter`, avec la couleur de fond dans les zones
  libres.

`REMPLIR` reste la valeur par défaut pour préserver le comportement des installations
existantes. Un arrière-plan flouté pourra être étudié séparément après mesure de son
coût GPU ; il ne fait pas partie du premier lot.

### Fichiers concernés

- `AppSettings.kt` ;
- `SettingsRepository.kt` ;
- `WallpaperViewModel.kt` ;
- `MainActivity.kt` ;
- `CollageRenderer.kt` ;
- calculateur géométrique et tests associés.

### Critères d'acceptation

- migration transparente des préférences existantes ;
- aperçu de composition cohérent avec le mode choisi ;
- aucune coupe en mode `PHOTO_ENTIERE` ;
- aucun étirement dans les deux modes.

## Lot 6 — Optimisations secondaires guidées par les mesures

Appliquer uniquement les optimisations confirmées par les traces :

- pré-calcul des rectangles de destination et de recadrage ;
- suppression des allocations de `Rect`, `RectF` et listes à chaque frame ;
- rendu limité à la région modifiée si le canvas matériel seul ne suffit pas ;
- réutilisation d'un bitmap suffisamment grand au lieu d'exiger une clé de cache aux
  dimensions exactement identiques ;
- partage contrôlé du cache entre l'aperçu système et le wallpaper réel ;
- redimensionnement final des bitmaps sur Android 8.0/8.1 après l'échantillonnage par
  puissance de deux.

Chaque optimisation doit être mesurée séparément et supprimée si elle complexifie le
code sans gain observable.

## Lot 7 — Validation finale

### Tests automatiques

Exécuter :

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testReleaseUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Ajouter des tests unitaires pour :

- progression du fondu ;
- état et invalidation du préchargement ;
- sélection de disposition ;
- score photo/case ;
- `centerCrop` et `fitCenter` ;
- restauration et migration des réglages.

### Matrice manuelle

- premier lancement et restauration du dernier collage ;
- aperçu officiel Android puis application du wallpaper ;
- intervalle d'une minute ;
- retour après un, deux et trois intervalles ;
- verrouillage/déverrouillage ;
- changement de dossier pendant un préchargement ;
- petit dossier, dossier massif et fournisseur lent ;
- JPEG, PNG, WEBP, HEIC/HEIF et fichier corrompu ;
- fondu activé et désactivé ;
- modes `REMPLIR` et `PHOTO_ENTIERE` ;
- contrôle mémoire après au moins 30 transitions.

### Définition de terminé

La version n'est considérée comme terminée que si :

- tous les tests automatiques réussissent en debug et release ;
- lint ne signale aucune anomalie ;
- l'APK debug et l'APK release sont construits ;
- les critères de performance sont relevés avant et après ;
- aucun travail photo n'est observé lorsque le wallpaper est invisible ;
- aucun écran noir, ancienne photo de dossier ou croissance mémoire continue n'est
  observé ;
- `VALIDATION.md`, `CHANGELOG.md`, `RELEASE_NOTES.md` et la version Android sont mis à
  jour avec uniquement des résultats réellement vérifiés.

## Découpage des commits

1. `docs: add performance roadmap and baseline protocol`
2. `perf: add private timing traces`
3. `perf: make fade time-based and frame-synchronized`
4. `perf: use hardware canvas with safe fallback`
5. `perf: prefetch one upcoming mosaic cell`
6. `perf: move folder refresh out of the transition path`
7. `feat: select layouts and photos by aspect ratio`
8. `feat: add fill and whole-photo display modes`
9. `perf: remove measured rendering allocations`
10. `test: complete performance and framing coverage`
11. `docs: record final validation and release notes`

Chaque commit fonctionnel doit compiler et conserver les tests précédents afin de
permettre un retour arrière ciblé.

## Risques surveillés

| Risque | Prévention |
| --- | --- |
| Bitmap obsolète après changement de dossier | Clé de génération vérifiée à la consommation |
| Surconsommation mémoire | Une seule cellule préchargée et cache borné |
| Batterie consommée écran masqué | Annulation sur invisibilité et surface détruite |
| Régression vers un écran noir | Métadonnées seulement indicatives, décodeur comme repli |
| Différence entre aperçu et wallpaper | Cache et état séparés par taille de surface |
| Animation bloquant le thread principal | Décodage IO, dessin matériel et mesure de chaque frame |
| Préférences existantes incompatibles | Valeurs par défaut et tests de migration |
