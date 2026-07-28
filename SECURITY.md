# Politique de sécurité

## Version prise en charge

Seule la dernière version publiée dans les releases GitHub reçoit des correctifs de
sécurité.

## Signaler un problème

Pour une vulnérabilité, utiliser de préférence le signalement privé de vulnérabilité
GitHub du dépôt. Si cette fonction n’est pas activée, contacter le propriétaire du
dépôt via son profil GitHub avant d’ouvrir un ticket public.

Ne jamais joindre une photo privée, une clé de signature, un mot de passe, un fichier
`.p12`, `.jks` ou `.keystore` à un ticket.

Pour un simple bug sans donnée sensible, ouvrir une issue GitHub avec la version
d’Android, le modèle du téléphone, la version de Collage Photos et les étapes de
reproduction.

## Signature des APK

Les APK officiels sont signés avec la même clé stable. Le certificat public et son
empreinte sont disponibles dans le dossier `certificates`. La clé privée n’est pas
stockée dans ce dépôt ni dans GitHub Actions.
