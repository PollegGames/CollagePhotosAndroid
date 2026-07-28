# Validation de la version 0.2.2

Validation exécutée le 28 juillet 2026 avec JDK 17, Gradle 8.13, Android Gradle
Plugin 8.13.2, Android SDK Platform 36 et Build-Tools 36.0.0.

## Résultats

| Vérification | Résultat |
| --- | --- |
| `testDebugUnitTest` | 35 tests, 0 échec, 0 erreur |
| `testReleaseUnitTest` | 35 tests, 0 échec, 0 erreur |
| `lintDebug` | aucune anomalie |
| `assembleDebug` | réussi |
| `assembleRelease` | réussi avec R8 et réduction des ressources |
| Signature release | APK Signature Scheme v2 valide, clé RSA 3072 bits |
| Compatibilité de mise à jour | même paquet et même certificat que la 0.2.0 |
| Permissions | aucune permission Internet ni accès général au stockage |
| ABI du Xperia | `arm64-v8a` présente |
| Archive APK | structure ZIP vérifiée, aucune erreur |

Les tests couvrent notamment :

- les quatre dispositions asymétriques à 3 photos ;
- la conservation de l’ancienne mosaïque jusqu’à l’arrivée des 3 nouvelles photos ;
- le rattrapage de 1, 2 ou 3 photos avec un plafond d’une seule mosaïque ;
- la fin d’une mosaïque partielle sans démarrage d’un deuxième cycle ;
- la sélection aléatoire sans doublon lorsqu’il y a assez de photos ;
- le repli sûr lorsqu’un dossier ne contient qu’une seule photo lisible ;
- les limites de l’intervalle de 1 à 60 minutes et la valeur par défaut de 5 minutes ;
- le calcul `centerCrop`, le filtrage des formats et les dossiers vides ou invalides.

## Correction du fond noir

La version 0.2.0 ouvrait les images une première fois pour lire leurs dimensions avant de
les autoriser dans la sélection. Certains fournisseurs ou formats ne renvoyaient pas ces
dimensions par ce chemin, ce qui pouvait éliminer toutes les photos et produire un écran
noir.

La version 0.2.1 et les versions suivantes :

- ne lit plus les dimensions pendant la sélection ;
- essaie directement un petit groupe de candidates avec le décodeur redimensionné ;
- ignore individuellement les fichiers illisibles ;
- exige trois images décodées avant de remplacer une mosaïque complète ;
- conserve l’ancienne mosaïque pendant les changements ;
- affiche une composition neutre au lieu d’un écran noir lors du premier chargement ;
- réessaie jusqu’à trois fois si le fournisseur de documents répond tardivement.

## Rattrapage au retour

La version 0.2.2 calcule le nombre d’intervalles écoulés lorsque le wallpaper redevient
visible. Le résultat est limité au nombre de cellules restant dans la mosaïque en cours,
ou à trois cellules si une nouvelle mosaïque commence. Le temps supplémentaire est
abandonné : après dix intervalles, une seule mosaïque est renouvelée.

Le paquet final est `ch.rex.photocollagewallpaper`, avec `versionCode` 5 et
`versionName` 0.2.2. Le certificat SHA-256 est :

```text
adfb2690604e41dc36b1284467ec87bdb95d86590beb3e2e95d976f9c03828c8
```

Il correspond à la clé stable de la version 0.2.0 ; l’APK peut donc être installé
directement par-dessus sans perdre le dossier ni les réglages.

## Paquet de publication GitHub

La préparation GitHub a également été vérifiée :

- syntaxe du script PowerShell validée avec le parseur PowerShell 7.6.4 ;
- parcours complet du script simulé localement jusqu’à la création de la release ;
- fichiers YAML de GitHub Actions, Dependabot et du formulaire de bug valides ;
- APK et certificat public contrôlés par SHA-256 avant toute publication ;
- checksum de la distribution Gradle 8.13 ajouté au wrapper ;
- checksum du `gradle-wrapper.jar` identique à la référence officielle Gradle ;
- APK présent dans le paquet de transfert mais exclu du commit Git ;
- aucune clé privée, aucun mot de passe et aucun fichier de signature privé dans le
  paquet public.
