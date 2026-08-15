# Publicar uma release (e manter a auto-atualização funcionando)

O app se atualiza sozinho: ao abrir a Home ele consulta
`api.github.com/repos/DionathaGoulart/goodfinances/releases/latest` (no máximo
**1x por dia**, sem autenticação), compara com o `versionName` instalado e, se
houver versão nova, mostra o dialog com o que mudou e um botão que **baixa o APK
e abre o instalador** (`AtualizacaoManager` + `REQUEST_INSTALL_PACKAGES` +
FileProvider em `cache/atualizacoes/`).

Isso só funciona se a release respeitar o contrato abaixo. O script
[`scripts/release.sh`](../scripts/release.sh) (Linux/WSL) e
[`scripts/release.ps1`](../scripts/release.ps1) (Windows) verificam o que dá para
verificar automaticamente.

## O contrato

| Item | Por quê |
|---|---|
| **Tag `vX.Y.Z`** | O app lê `tag_name` e remove o `v`. Uma tag `1.3.0` sem o `v` funciona, mas `release-1.3.0` não |
| **`versionName` = `X.Y.Z`, sem sufixo** | `ehMaisNova` faz `split('.') + toIntOrNull`: `"1.3.0-beta"` vira `[1,3,0]` e **empata** com o `1.3.0` final — quem estivesse no beta nunca receberia o aviso do estável |
| **`versionCode` sempre maior** | O Android recusa instalar por cima de um `versionCode` igual ou menor. O usuário baixaria o APK e a instalação falharia |
| **APK anexado como asset, terminando em `.apk`** | O app pega o **primeiro** asset `.apk` da release. Sem asset ele só abre a página no navegador (fallback), sem instalação com um toque |
| **Nem draft, nem pre-release** | `/releases/latest` **ignora rascunhos e pre-releases**. Uma release marcada como pre-release é invisível para o app, por mais nova que seja |
| **Assinado com o mesmo keystore** | Assinatura diferente = o Android recusa a atualização (foi o que forçou a reinstalação manual no 1.2.0). `finapp-release.jks` + `key.properties` na raiz, fora do git — **mantenha backup** |
| **`applicationId` sempre `com.finapp`** | Mudou o id, vira outro app: instala do lado em vez de atualizar |

## Passo a passo

1. **Suba as versões** em `app/build.gradle.kts`: `versionCode` +1 e `versionName` para o novo `X.Y.Z`.
2. **Escreva a seção da versão no `CHANGELOG.md`** (`## X.Y.Z`) — é dela que sai o corpo da release, então escreva para o usuário final.
3. **Commite tudo** (a árvore precisa estar limpa).
4. Rode:

   ```bash
   ./scripts/release.sh 1.3.0        # Linux/WSL
   ```
   ```powershell
   .\scripts\release.ps1 -Versao 1.3.0   # Windows
   ```

O script roda os testes, gera o APK assinado, cria a tag `vX.Y.Z`, publica a
release com o APK anexado e marca como *latest*.

## Publicando na mão

Se preferir pelo site, o que não pode faltar:

```bash
./gradlew testDebugUnitTest assembleRelease
cp app/build/outputs/apk/release/app-release.apk GoodFinances-1.3.0.apk
git tag v1.3.0 && git push origin v1.3.0
gh release create v1.3.0 GoodFinances-1.3.0.apk \
  --title "GoodFinances 1.3.0" --notes-file notas.md --latest
```

Pela interface do GitHub: **Releases → Draft a new release**, tag `v1.3.0`,
anexar o APK, **deixar "Set as a pre-release" DESMARCADO** e marcar "Set as the
latest release".

## Ambiente de build sem Android Studio

O build precisa só de JDK 17 + Android SDK (compileSdk 36):

```bash
# JDK 17
curl -L -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/androidtools && tar xzf jdk.tar.gz -C ~/androidtools && mv ~/androidtools/jdk-17* ~/androidtools/jdk17

# SDK (command line tools)
curl -L -o cli.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p ~/androidtools/sdk/cmdline-tools && unzip -q cli.zip -d ~/androidtools/sdk/cmdline-tools
mv ~/androidtools/sdk/cmdline-tools/cmdline-tools ~/androidtools/sdk/cmdline-tools/latest

export JAVA_HOME=~/androidtools/jdk17 ANDROID_HOME=~/androidtools/sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$HOME/androidtools/sdk" > local.properties
```

Com Android Studio instalado, basta `JAVA_HOME` apontando para o JBR
(`C:\Program Files\Android\Android Studio\jbr` no Windows).

## Depois de publicar

O aviso chega **ao abrir a Home**, na primeira vez depois de 24h da última
checagem — não é instantâneo. Para testar na hora, limpe os dados do app ou
espere o intervalo; não existe botão "verificar agora" na UI.
