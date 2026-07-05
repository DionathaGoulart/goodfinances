# Publica uma versão nova no GitHub Releases.
# O app instalado detecta a release sozinho (checagem diária) e oferece o download.
#
# Uso:
#   1. Atualize versionCode e versionName em app/build.gradle.kts
#   2. Commite tudo
#   3. .\scripts\release.ps1 -Versao 1.1.0 -Notas "O que mudou nesta versão"
#
# Requisitos: gh CLI autenticado (gh auth login) e key.properties/finapp-release.jks na raiz.

param(
    [Parameter(Mandatory = $true)][string]$Versao,
    [string]$Notas = ""
)

$ErrorActionPreference = "Stop"

# Confere se o versionName do build bate com a versão pedida
$gradle = Get-Content app\build.gradle.kts -Raw
if ($gradle -notmatch [regex]::Escape("versionName = `"$Versao`"")) {
    Write-Host "ERRO: versionName em app/build.gradle.kts nao e $Versao." -ForegroundColor Red
    Write-Host "Atualize versionCode e versionName antes de publicar." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) { exit 1 }

$apk = "FinanApp-$Versao.apk"
Copy-Item app\build\outputs\apk\release\app-release.apk $apk -Force

git push origin main
git tag "v$Versao"
git push origin "v$Versao"

if ($Notas -eq "") {
    gh release create "v$Versao" $apk --title "FinanApp $Versao" --generate-notes
} else {
    gh release create "v$Versao" $apk --title "FinanApp $Versao" --notes $Notas
}

Remove-Item $apk
Write-Host ""
Write-Host "Release v$Versao publicada! Os apps instalados vao avisar da atualizacao." -ForegroundColor Green
