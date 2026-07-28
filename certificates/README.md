# Certificat de signature public

`CollagePhotos-signing-certificate.pem` est le certificat public utilisé pour signer
les mises à jour officielles de Collage Photos. Il ne permet pas de signer une APK et
ne contient aucune clé privée.

Empreinte SHA-256 :

```text
AD:FB:26:90:60:4E:41:DC:36:B1:28:44:67:EC:87:BD:B9:5D:86:59:0B:EB:3E:2E:95:D9:76:F9:C0:38:28:C8
```

Afficher le certificat avec un JDK :

```bash
keytool -printcert -file CollagePhotos-signing-certificate.pem
```

Comparer également la somme SHA-256 de l’APK avec `SHA256SUMS.txt`. Android n’accepte
une mise à jour que si elle porte le même identifiant de paquet et la même signature
que l’application déjà installée.
