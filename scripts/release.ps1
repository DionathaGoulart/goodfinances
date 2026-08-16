# Publishes a new version to GitHub Releases (Windows).
# The installed app finds the release on its own (checked once a day) and offers
# a one-tap update. See docs/release.md for the full contract.
#
# Usage:
#   1. Bump versionCode and versionName in app/build.gradle.kts
#   2. Commit everything
#   3. .\scripts\release.ps1 -Versao 1.3.0 -Notas "What changed in this version"
#
# Requires: an authenticated gh CLI (gh auth login) and key.properties plus
# finapp-release.jks in the repository root.

param(
    [Parameter(Mandatory = $true)][string]$Versao,
    [string]$Notas = ""
)

$ErrorActionPreference = "Stop"

# The app only compares X.Y.Z, so a suffix such as "-beta" would tie with the
# stable release and never notify anyone on the beta.
if ($Versao -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "ERROR: version must be X.Y.Z with no suffix." -ForegroundColor Red
    exit 1
}

# Make sure the build's versionName matches the requested version
$gradle = Get-Content app\build.gradle.kts -Raw
if ($gradle -notmatch [regex]::Escape("versionName = `"$Versao`"")) {
    Write-Host "ERROR: versionName in app/build.gradle.kts is not $Versao." -ForegroundColor Red
    Write-Host "Bump versionCode and versionName before publishing." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) { exit 1 }

$apk = "GoodFinances-$Versao.apk"
Copy-Item app\build\outputs\apk\release\app-release.apk $apk -Force

git push origin main
git tag "v$Versao"
git push origin "v$Versao"

# --latest, and the absence of --prerelease/--draft, are mandatory: the app
# queries /releases/latest, which ignores drafts and pre-releases.
if ($Notas -eq "") {
    gh release create "v$Versao" $apk --title "GoodFinances $Versao" --generate-notes --latest
} else {
    gh release create "v$Versao" $apk --title "GoodFinances $Versao" --notes $Notas --latest
}

Remove-Item $apk
Write-Host ""
Write-Host "Release v$Versao published. Installed apps will prompt when Home opens." -ForegroundColor Green
