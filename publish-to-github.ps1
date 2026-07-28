[CmdletBinding()]
param(
    [string]$Repository = "PollegGames/CollagePhotosAndroid",
    [string]$Tag = "v0.2.2"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Require-Command {
    param(
        [string]$Name,
        [string]$InstallCommand
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Commande '$Name' introuvable. Installez-la avec: $InstallCommand"
    }
}

function Invoke-Checked {
    param(
        [scriptblock]$Command,
        [string]$FailureMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (code $LASTEXITCODE)."
    }
}

function Invoke-Probe {
    param([scriptblock]$Command)

    # Windows PowerShell 5.1 turns native stderr into an ErrorRecord. With
    # $ErrorActionPreference set to Stop, ordinary probes such as checking for a
    # missing remote, tag, commit, or release would otherwise abort the script.
    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        $OutputLines = @(& $Command 2>$null)
        $ExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $PreviousErrorActionPreference
    }

    [pscustomobject]@{
        Output = [string]($OutputLines -join [Environment]::NewLine)
        ExitCode = $ExitCode
    }
}

$Root = $PSScriptRoot
$ApkPath = Join-Path $Root "release\CollagePhotos-0.2.2-release.apk"
$CertificatePath = Join-Path $Root "certificates\CollagePhotos-signing-certificate.pem"
$ChecksumsPath = Join-Path $Root "SHA256SUMS.txt"
$ReleaseNotesPath = Join-Path $Root "RELEASE_NOTES.md"
$ExpectedApkSha256 = "9f9a4d518ba585610118af07e188f711d3829270bb7f27355d4bedbdd94e03f4"
$ExpectedCertificateSha256 = "680a6459214646f103ff6e8ac25a341eacb585084f4fb9aff7a76895a9338104"
$ExpectedChecksumsFileSha256 = "cbc159a5373e290ed7406a343663805c366075b70c62465fc23b20137667e2e3"

Push-Location $Root

try {
    Write-Step "Verification des outils"
    Require-Command "git" "winget install --id Git.Git -e"
    Require-Command "gh" "winget install --id GitHub.cli -e"

    $AuthProbe = Invoke-Probe { gh auth status --hostname github.com }
    if ($AuthProbe.ExitCode -ne 0) {
        Write-Host "Connexion a GitHub dans le navigateur..."
        Invoke-Checked {
            gh auth login --hostname github.com --git-protocol https --web
        } "La connexion GitHub a echoue"
    }

    Invoke-Checked {
        gh auth status --hostname github.com
    } "GitHub CLI n'est pas authentifie"

    Invoke-Checked {
        gh auth setup-git
    } "Impossible de configurer Git avec GitHub CLI"

    $RemoteProbe = Invoke-Probe {
        gh repo view $Repository --json nameWithOwner --jq ".nameWithOwner"
    }
    $RemoteName = $RemoteProbe.Output
    if ($RemoteProbe.ExitCode -ne 0 -or $RemoteName.Trim() -ne $Repository) {
        throw "Le depot GitHub '$Repository' est introuvable ou inaccessible avec ce compte."
    }

    Write-Step "Verification des fichiers et des signatures"
    foreach ($RequiredPath in @($ApkPath, $CertificatePath, $ChecksumsPath, $ReleaseNotesPath)) {
        if (-not (Test-Path -LiteralPath $RequiredPath -PathType Leaf)) {
            throw "Fichier requis absent: $RequiredPath"
        }
    }

    $ActualApkSha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ActualApkSha256 -ne $ExpectedApkSha256) {
        throw "La somme SHA-256 de l'APK ne correspond pas. Publication annulee."
    }

    $ActualCertificateSha256 = (Get-FileHash -LiteralPath $CertificatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ActualCertificateSha256 -ne $ExpectedCertificateSha256) {
        throw "La somme SHA-256 du certificat public ne correspond pas. Publication annulee."
    }

    $ActualChecksumsFileSha256 = (Get-FileHash -LiteralPath $ChecksumsPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ActualChecksumsFileSha256 -ne $ExpectedChecksumsFileSha256) {
        throw "Le fichier SHA256SUMS.txt a ete modifie. Publication annulee."
    }

    $SensitiveFiles = @(
        Get-ChildItem -LiteralPath $Root -Recurse -Force -File |
            Where-Object {
                $_.FullName -notmatch "[\\/]\.git[\\/]" -and (
                    $_.Extension -in @(".p12", ".jks", ".keystore") -or
                    $_.Name -ieq "keystore.properties" -or
                    $_.Name -like "*signing-info*"
                )
            }
    )

    if ($SensitiveFiles.Count -gt 0) {
        $SensitiveNames = ($SensitiveFiles | ForEach-Object { $_.FullName }) -join [Environment]::NewLine
        throw "Fichier prive detecte. Retirez-le avant de continuer:`n$SensitiveNames"
    }

    Write-Step "Preparation du depot Git local"
    if (-not (Test-Path -LiteralPath (Join-Path $Root ".git") -PathType Container)) {
        Invoke-Checked { git init } "git init a echoue"
    }

    Invoke-Checked { git branch -M main } "Impossible de definir la branche main"

    $Login = ""
    $GitNameProbe = Invoke-Probe { git config --get user.name }
    $GitName = $GitNameProbe.Output
    if ($GitNameProbe.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($GitName)) {
        $Login = [string](& gh api user --jq ".login")
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($Login)) {
            throw "Impossible de lire le nom du compte GitHub."
        }
        Invoke-Checked { git config user.name $Login.Trim() } "Impossible de configurer le nom Git"
    }

    $GitEmailProbe = Invoke-Probe { git config --get user.email }
    $GitEmail = $GitEmailProbe.Output
    if ($GitEmailProbe.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($GitEmail)) {
        if ([string]::IsNullOrWhiteSpace($Login)) {
            $Login = [string](& gh api user --jq ".login")
        }
        $NoReplyEmail = "$($Login.Trim())@users.noreply.github.com"
        Invoke-Checked { git config user.email $NoReplyEmail } "Impossible de configurer l'adresse Git"
    }

    $RemoteUrl = "https://github.com/$Repository.git"
    $OriginProbe = Invoke-Probe { git remote get-url origin }
    $ExistingOrigin = $OriginProbe.Output
    if ($OriginProbe.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($ExistingOrigin)) {
        Invoke-Checked { git remote add origin $RemoteUrl } "Impossible d'ajouter le depot distant"
    }
    elseif ($ExistingOrigin.Trim() -ne $RemoteUrl) {
        Invoke-Checked { git remote set-url origin $RemoteUrl } "Impossible de corriger le depot distant"
    }

    Invoke-Checked { git add -A } "Impossible de preparer les fichiers Git"
    $StagedFiles = @(& git diff --cached --name-only --diff-filter=ACMR)
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de verifier les fichiers prepares."
    }

    $SensitiveStaged = @(
        $StagedFiles |
            Where-Object {
                $_ -match "(?i)(^|/)(keystore\.properties|.*signing-info.*|.*\.(p12|jks|keystore))$"
            }
    )
    if ($SensitiveStaged.Count -gt 0) {
        throw "Un fichier prive serait ajoute a Git: $($SensitiveStaged -join ', ')"
    }

    $HeadProbe = Invoke-Probe { git rev-parse --verify HEAD }
    $HasHead = $HeadProbe.ExitCode -eq 0

    if ($StagedFiles.Count -gt 0) {
        $CommitMessage = if ($HasHead) { "Prepare release 0.2.2" } else { "Initial release 0.2.2" }
        Invoke-Checked { git commit -m $CommitMessage } "Le commit Git a echoue"
    }
    elseif (-not $HasHead) {
        throw "Aucun fichier a publier."
    }

    $LocalHead = [string](& git rev-parse HEAD)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($LocalHead)) {
        throw "Impossible de lire le commit local."
    }
    $LocalHead = $LocalHead.Trim()

    Write-Step "Publication de la branche main"
    $RemoteMainLine = [string](& git ls-remote --heads origin refs/heads/main)
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire la branche distante main."
    }

    if ([string]::IsNullOrWhiteSpace($RemoteMainLine)) {
        Invoke-Checked { git push -u origin main } "Le push de main a echoue"
    }
    else {
        $RemoteMainSha = ($RemoteMainLine.Trim() -split "\s+")[0]
        if ($RemoteMainSha -ne $LocalHead) {
            throw "La branche main distante contient un autre commit. Aucun ecrasement n'a ete effectue."
        }
        Write-Host "La branche main est deja publiee."
    }

    Write-Step "Creation du tag $Tag"
    $LocalTagProbe = Invoke-Probe { git rev-list -n 1 $Tag }
    $LocalTagCommit = $LocalTagProbe.Output
    if ($LocalTagProbe.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($LocalTagCommit)) {
        Invoke-Checked { git tag -a $Tag -m "Collage Photos 0.2.2" } "La creation du tag a echoue"
        $LocalTagCommit = $LocalHead
    }
    elseif ($LocalTagCommit.Trim() -ne $LocalHead) {
        throw "Le tag local $Tag vise un autre commit."
    }

    $RemoteTagLine = [string](& git ls-remote --tags origin "refs/tags/$Tag^{}")
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire le tag distant."
    }

    if ([string]::IsNullOrWhiteSpace($RemoteTagLine)) {
        Invoke-Checked { git push origin $Tag } "Le push du tag a echoue"
    }
    else {
        $RemoteTagCommit = ($RemoteTagLine.Trim() -split "\s+")[0]
        if ($RemoteTagCommit -ne $LocalHead) {
            throw "Le tag distant $Tag vise un autre commit."
        }
        Write-Host "Le tag $Tag est deja publie."
    }

    Write-Step "Creation de la release GitHub"
    $ReleaseProbe = Invoke-Probe {
        gh release view $Tag --repo $Repository --json url --jq ".url"
    }
    $ReleaseUrl = $ReleaseProbe.Output
    if ($ReleaseProbe.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($ReleaseUrl)) {
        Write-Host "La release existe deja: $($ReleaseUrl.Trim())"
    }
    else {
        Invoke-Checked {
            gh release create $Tag `
                $ApkPath `
                $ChecksumsPath `
                $CertificatePath `
                --repo $Repository `
                --verify-tag `
                --title "Collage Photos 0.2.2" `
                --notes-file $ReleaseNotesPath
        } "La creation de la release a echoue"

        $ReleaseUrl = [string](& gh release view $Tag --repo $Repository --json url --jq ".url")
        if ($LASTEXITCODE -ne 0) {
            throw "La release a ete creee, mais son adresse n'a pas pu etre lue."
        }
    }

    Write-Host ""
    Write-Host "Publication terminee avec succes." -ForegroundColor Green
    Write-Host $ReleaseUrl.Trim()
}
finally {
    Pop-Location
}
