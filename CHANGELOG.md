# Journal des modifications

Toutes les modifications importantes de Collage Photos sont documentées ici.

## [Non publié]

- Remplace le fondu à 8 paliers par une animation de 300 ms synchronisée aux frames.
- Utilise le canvas matériel avec un repli vers le canvas logiciel.
- Précharge au maximum une prochaine photo avant son échéance.
- Annule et invalide les préchargements lorsque le dossier, la surface ou le cadrage
  change.
- Adapte les dispositions au format de l'écran et favorise les photos dont le ratio
  correspond à leur case.
- Agrandit la grande région à 60 % pour éviter les cases excessivement étroites.
- Ajoute les modes de cadrage « Remplir » et « Photo entière ».
- Corrige l'orientation EXIF sur le chemin de décodage Android 8.0/8.1.
- Ajoute des traces de performance sans URI ni nom de fichier.

## [0.2.2] — 2026-07-28

- Calcule les intervalles écoulés lorsque le fond d’écran redevient visible.
- Rattrape une, deux ou trois photos selon le temps écoulé.
- Limite le rattrapage à une seule mosaïque complète pour préserver la batterie.
- Termine correctement une mosaïque déjà partiellement renouvelée.
- Ajoute les tests unitaires correspondants.

## [0.2.1] — 2026-07-28

- Corrige l’écran noir avec certains fournisseurs de documents.
- Remplace l’aperçu réel de l’application par un schéma instantané sans décodage.
- Utilise uniquement une mosaïque asymétrique de trois photos : une grande et deux
  petites.
- Ajoute un intervalle réglable de 1 à 60 minutes.
- Charge les photos réelles uniquement dans l’aperçu officiel Android et dans le
  fond d’écran.

## [0.2.0] — 2026-07-28

- Introduit les mosaïques asymétriques aléatoires.
- Construit une nouvelle mosaïque au-dessus de l’ancienne, photo après photo.
- Conserve l’ancienne mosaïque jusqu’à ce que les trois nouvelles photos soient
  prêtes.

[0.2.2]: https://github.com/PollegGames/CollagePhotosAndroid/releases/tag/v0.2.2
[0.2.1]: https://github.com/PollegGames/CollagePhotosAndroid/releases/tag/v0.2.1
[0.2.0]: https://github.com/PollegGames/CollagePhotosAndroid/releases/tag/v0.2.0
