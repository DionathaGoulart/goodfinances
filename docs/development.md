# Development guide

## Requirements

- **Android Studio** (recommended), or JDK 17 plus the Android SDK (compileSdk 36, minSdk 26)
- A configured Firebase project — see [firebase-setup.md](firebase-setup.md)
- `local.properties` pointing at the SDK (Android Studio writes it for you)

## Commands

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests (JUnit)
./gradlew assembleRelease      # signed release APK (needs the keystore)
```

No Java on `PATH`? Point `JAVA_HOME` at the JBR that ships with Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

Setting up a toolchain from scratch, without Android Studio, is covered in
[release.md](release.md#build-environment-without-android-studio).

Dependency versions are centralized in `gradle/libs.versions.toml`. Annotation
processing uses **KSP** — do not add kapt.

## Release signing

Releases are signed with two files kept in the repository root and **out of
git**:

- `finapp-release.jks` — the keystore
- `key.properties` — `storeFile`, `storePassword`, `keyAlias`, `keyPassword`

To generate your own:

```bash
keytool -genkeypair -v -keystore finapp-release.jks \
  -alias finapp -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=GoodFinances"
```

> ⚠️ **Back up the keystore and key.properties.** Without them you cannot ship
> an update to an already-installed app — Android refuses to update across
> different signatures.

To actually publish (tag, attached APK, release marked as *latest*), use
`scripts/release.sh` or `scripts/release.ps1`; the full auto-update contract is
in [release.md](release.md).

## Tests

Unit tests cover the pure logic, with no Android dependencies:

- `ParserImportacaoTest` — CSV/JSON, BR and ISO dates, BR and US amounts, errors reported with their line
- `PeriodoFiltroTest` — week, month and year boundaries
- `FormatadoresTest` — currency in cents, compact formats
- `RecorrenciasTest` — the 12-month horizon, `diaMensal` across short months (31→28→31), deterministic occurrence uuids
- `VencimentosTest` — the warning window, invoice grouping per card, dedup per day and run
- `OnibusProcessamentoTest` — per-leg debits, cursor idempotency, balance never going negative
- `EspelhoCartaoTest` — deterministic mirror uuid and reconciliation with the household

The real `org.json` is added as a `testImplementation` dependency, because the
one in `android.jar` is a stub on the JVM.

## Conventions

- **Code and UI in Brazilian Portuguese** (`TransacaoViewModel.adicionarTransacao`). Documentation, commit messages and tooling comments are in English.
- **Money as cents (`Long`)** — never `Double`; display through `Formatadores.moeda`
- **Deletion is logical** (tombstone) through the repository; reads filter `deletado = 0`
- Changing an entity means bumping the database version and adding a migration in `AppDatabase` — the schema is at **v17**, and `MIGRACAO_1_2` is the template
- Persisted enum names (`Perfil`, `TipoTransacao`) **cannot be renamed** — they reach the database and literal SQL
- Adding a value to the `Perfil` enum needs **no** migration (the column is TEXT)
- No charting libraries — the charts are Compose Canvas
- No storage permissions — export and import go through the Storage Access Framework
- Bump `versionCode` and `versionName` for every release; `versionName` never carries a suffix such as `-beta`, because the update comparator only reads `X.Y.Z` and a beta would tie with the stable version

### Commit messages

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), in
English, imperative mood:

```
feat(recurrence): end a recurrence from the Home row

Explain why the change was needed and what breaks without it. Wrap at
72 columns.
```

Types in use: `feat`, `fix`, `refactor`, `docs`, `test`, `build`, `chore`.

## Project layout

```
app/src/main/kotlin/com/finapp/
├── MainActivity.kt / FinanApplication.kt
├── data/
│   ├── db/            # Room: 7 entities, DAOs, migrations (v17)
│   ├── repository/    # FinanceRepository (single write path)
│   ├── io/            # export, import, backup, receipts, Drive
│   ├── notif/         # financial triggers + a worker running 3x/day
│   └── sync/          # CasaManager + SyncManager (Firestore)
├── di/                # Hilt modules
├── ui/
│   ├── screen/        # Home, Analysis, Bus, Settings, profile selection
│   ├── component/     # modal, history row, card groups, charts
│   └── theme/         # dynamic dark theme (font and colour)
├── utils/             # formatters, periods, appearance, cards, buckets, time
└── viewmodel/         # one per tab + household, members and profile
```
