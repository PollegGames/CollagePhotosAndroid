# Publier la première version sur GitHub depuis Windows

Le script `publish-to-github.ps1` publie le projet dans :

<https://github.com/PollegGames/CollagePhotosAndroid>

Il effectue les opérations suivantes :

1. vérifie Git, GitHub CLI, l’authentification et le dépôt cible ;
2. vérifie la somme SHA-256 de l’APK signé et du certificat public ;
3. refuse de continuer s’il trouve une clé ou un fichier de mots de passe ;
4. initialise le dépôt Git local et crée le commit initial ;
5. pousse la branche `main` sans écraser une branche distante différente ;
6. crée et pousse le tag annoté `v0.2.2` ;
7. crée la release GitHub et lui joint l’APK, le certificat public et les sommes
   SHA-256.

Le script peut être relancé si la connexion s’interrompt : il reconnaît les étapes
déjà terminées.

## 1. Préparer le PC

Ouvrir PowerShell et installer Git et GitHub CLI si nécessaire :

```powershell
winget install --id Git.Git -e
winget install --id GitHub.cli -e
```

Fermer puis rouvrir PowerShell afin que les nouvelles commandes soient disponibles.

## 2. Extraire le paquet

Décompresser complètement `CollagePhotos-GitHub-Publish-0.2.2.zip`. Ne pas exécuter
le script directement à l’intérieur de l’archive.

Le dossier extrait doit notamment contenir :

```text
publish-to-github.ps1
README.md
app\
release\CollagePhotos-0.2.2-release.apk
```

## 3. Lancer la publication

Dans l’Explorateur Windows, ouvrir le dossier extrait. Cliquer dans la barre d’adresse,
saisir `powershell`, puis appuyer sur Entrée.

Exécuter :

```powershell
Set-ExecutionPolicy -Scope Process Bypass
Unblock-File .\publish-to-github.ps1
.\publish-to-github.ps1
```

Si GitHub CLI n’est pas encore connecté, le script ouvre la procédure de connexion
GitHub dans le navigateur. Se connecter au compte qui possède
`PollegGames/CollagePhotosAndroid`, puis revenir dans PowerShell.

À la fin, le script affiche l’adresse de la release publiée.

## Sécurité de la signature

Le paquet contient uniquement :

- l’APK release déjà signé ;
- le certificat public ;
- les sommes de contrôle.

Il ne contient ni la clé privée `.p12`, ni ses mots de passe. Ne jamais copier l’archive
privée de signature dans ce dossier. La CI GitHub compile et teste une APK debug
uniquement ; elle n’a accès à aucun secret de signature.

Conserver séparément et sauvegarder la clé privée existante. Toutes les futures mises à
jour Android devront être signées avec cette même clé.

## Après la publication

Vérifier :

- l’onglet **Actions** : le workflow Android doit réussir ;
- la page **Releases** : la version `v0.2.2` doit contenir trois fichiers ;
- l’onglet **Security** : activer **Private vulnerability reporting** si proposé ;
- **Settings → General → Features** : laisser les issues activées pour les rapports de
  bugs.
