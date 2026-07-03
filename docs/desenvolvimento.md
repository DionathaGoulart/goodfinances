# Guia de desenvolvimento

## Requisitos

- **Android Studio** (recomendado) ou JDK 17 + Android SDK (compileSdk 36, minSdk 26)
- Projeto Firebase configurado — ver [configuracao-firebase.md](configuracao-firebase.md)
- `local.properties` apontando para o SDK (o Android Studio cria sozinho)

## Comandos

Sem Java no PATH? Use o JBR do Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat assembleDebug        # APK debug -> app\build\outputs\apk\debug\
.\gradlew.bat testDebugUnitTest    # testes unitários (JUnit)
.\gradlew.bat assembleRelease      # APK release assinado (requer keystore)
```

Versões de dependências centralizadas em `gradle/libs.versions.toml`. Processamento de anotações via **KSP** (não usar kapt).

## Assinatura de release

O release é assinado via dois arquivos na raiz, **fora do git**:

- `finapp-release.jks` — o keystore
- `key.properties` — `storeFile`, `storePassword`, `keyAlias`, `keyPassword`

Para gerar os seus:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore finapp-release.jks `
  -alias finapp -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=FinanApp"
```

> ⚠️ **Faça backup do keystore e do key.properties.** Sem eles não é possível publicar atualizações do app já instalado.

## Testes

Os testes unitários cobrem a lógica pura (sem Android):

- `ParserImportacaoTest` — CSV/JSON, datas BR/ISO, valores BR/US, erros com linha
- `PeriodoFiltroTest` — limites de semana/mês/ano
- `FormatadoresTest` — moeda em centavos, formatos compactos

`org.json` real entra como `testImplementation` (a versão do android.jar é stub em testes JVM).

## Convenções do projeto

- Código e UI em **português brasileiro** (`TransacaoViewModel.adicionarTransacao`)
- **Dinheiro em centavos (`Long`)** — nunca `Double`; exibir via `Formatadores.moeda`
- **Deleção é lógica** (tombstone) via repository; leituras filtram `deletado = 0`
- Mudança em entidade ⇒ bump de versão do banco + migração no `AppDatabase` (as migrações 1→4 servem de modelo)
- Nomes dos enums persistidos (`Perfil`, `TipoTransacao`) **não podem ser renomeados** — vão para o banco e para SQL literal
- Sem bibliotecas de gráfico — os gráficos são Canvas do Compose
- Sem permissões de armazenamento — export/import via Storage Access Framework

## Estrutura

```
app/src/main/kotlin/com/finapp/
├── MainActivity.kt / FinanApplication.kt
├── data/
│   ├── db/            # Room: entidades, DAOs, migrações
│   ├── repository/    # FinanceRepository (porta única de escrita)
│   ├── io/            # export, import, backup
│   └── sync/          # CasaManager + SyncManager (Firestore)
├── di/                # módulos Hilt
├── ui/
│   ├── screen/        # Home, Análise, Transações, Config, PerfilSelecao
│   ├── component/     # modal, itens, cards, gráficos
│   └── theme/         # tema escuro dinâmico (fonte/cor)
├── utils/             # formatadores, períodos, aparência, tempo
└── viewmodel/         # 1 por tela + Casa e Perfil
```
