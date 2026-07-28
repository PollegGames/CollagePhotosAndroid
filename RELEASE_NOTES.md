# Collage Photos 0.2.2

Cette mise à jour améliore le comportement lorsque le fond d’écran n’était pas visible.

Avec un intervalle d’une minute :

- retour après une minute : une photo est remplacée ;
- retour après deux minutes : deux photos sont remplacées ;
- retour après trois minutes ou davantage : les trois photos sont remplacées ;
- même après une longue absence, une seule mosaïque est renouvelée au retour.

Le changement reste progressif, photo après photo. Le fond d’écran ne décode pas
d’images inutilement lorsqu’il n’est pas visible.

## Installation

Télécharger `CollagePhotos-0.2.2-release.apk`, puis l’installer par-dessus la version
précédente sans désinstaller l’application. Le dossier choisi et les réglages sont
conservés grâce au même identifiant de paquet et au même certificat.

## Vérification

Somme SHA-256 de l’APK :

```text
9f9a4d518ba585610118af07e188f711d3829270bb7f27355d4bedbdd94e03f4
```

Empreinte SHA-256 du certificat Android :

```text
AD:FB:26:90:60:4E:41:DC:36:B1:28:44:67:EC:87:BD:B9:5D:86:59:0B:EB:3E:2E:95:D9:76:F9:C0:38:28:C8
```

Validation : 35 tests debug et 35 tests release réussis, lint Android sans erreur,
signature APK v2 valide.
