#!/usr/bin/env bash
# Publica uma versão nova no GitHub Releases (Linux/WSL).
#
# O app instalado detecta a release sozinho (checagem 1x/dia) e oferece o
# download com um toque — mas só se a release respeitar o contrato do
# AtualizacaoManager. Este script existe para não deixar nenhum item de fora;
# ver docs/release.md para o porquê de cada um.
#
# Uso:
#   1. Atualize versionCode (+1) e versionName em app/build.gradle.kts
#   2. Escreva a seção da versão no CHANGELOG.md (o corpo da release sai daqui)
#   3. Commite e:  ./scripts/release.sh 1.3.0
#
# Requisitos: gh autenticado (gh auth login), JDK 17, Android SDK e
# key.properties + finapp-release.jks na raiz.

set -euo pipefail

VERSAO="${1:-}"
if [[ -z "$VERSAO" ]]; then
    echo "Uso: ./scripts/release.sh <versao>   (ex.: ./scripts/release.sh 1.3.0)" >&2
    exit 1
fi

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

: "${JAVA_HOME:=$HOME/androidtools/jdk17}"
: "${ANDROID_HOME:=$HOME/androidtools/sdk}"
export JAVA_HOME ANDROID_HOME
PATH="$PATH:$HOME/androidtools/gh/bin"

erro() { echo "ERRO: $*" >&2; exit 1; }

# ---------- Verificações que impedem uma release que não atualiza ninguém ----------

# O AtualizacaoManager compara só X.Y.Z: um sufixo tipo "-beta" empata com o
# estável e quem está no beta nunca receberia o aviso.
[[ "$VERSAO" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || erro "versao deve ser X.Y.Z sem sufixo (recebi '$VERSAO')."

grep -q "versionName = \"$VERSAO\"" app/build.gradle.kts \
    || erro "versionName em app/build.gradle.kts nao e $VERSAO."

CODIGO=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
ANTERIOR=$(git show HEAD~1:app/build.gradle.kts 2>/dev/null | grep -oP 'versionCode = \K[0-9]+' || echo 0)
[[ "$CODIGO" -gt "$ANTERIOR" ]] \
    || echo "AVISO: versionCode ($CODIGO) nao subiu desde o commit anterior ($ANTERIOR) — o Android recusa instalar por cima."

[[ -f key.properties && -f finapp-release.jks ]] \
    || erro "key.properties/finapp-release.jks ausentes — sem eles o APK sai sem assinatura e nao instala por cima do app existente."

[[ -z "$(git status --porcelain)" ]] \
    || erro "arvore suja — commite tudo antes de publicar."

git rev-parse "v$VERSAO" >/dev/null 2>&1 \
    && erro "a tag v$VERSAO ja existe."

command -v gh >/dev/null || erro "gh CLI nao encontrado."
gh auth status >/dev/null 2>&1 || erro "gh nao autenticado — rode 'gh auth login'."

# ---------- Notas: a seção desta versão no CHANGELOG ----------

# Para no separador "---" que antecede a próxima versão, para o corpo da
# release não terminar com uma linha horizontal solta.
NOTAS=$(awk -v v="## $VERSAO" '
    $0 == v {ok=1; next}
    ok && (/^## / || /^---[[:space:]]*$/) {exit}
    ok {print}
' CHANGELOG.md)
[[ -n "${NOTAS// /}" ]] || erro "CHANGELOG.md nao tem a secao '## $VERSAO'."

# ---------- Build ----------

echo "==> Testes e APK release..."
./gradlew testDebugUnitTest assembleRelease --console=plain

APK="GoodFinances-$VERSAO.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

# O app procura o primeiro asset terminado em .apk; sem ele, o dialog só abre
# a página da release no navegador.
[[ "$APK" == *.apk ]] || erro "o asset precisa terminar em .apk."

# ---------- Publicação ----------

echo "==> Tag e push..."
git push origin main
git tag "v$VERSAO"
git push origin "v$VERSAO"

echo "==> Criando a release..."
# --latest e ausência de --prerelease/--draft são obrigatórios: o app consulta
# /releases/latest, que ignora rascunhos e pre-releases.
gh release create "v$VERSAO" "$APK" \
    --title "GoodFinances $VERSAO" \
    --notes "$NOTAS" \
    --latest

rm -f "$APK"

echo
echo "Release v$VERSAO publicada. Os apps instalados avisam ao abrir a Home, na"
echo "primeira vez apos 24h da ultima checagem."
