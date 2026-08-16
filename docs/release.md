# Publishing a release (and keeping auto-updates working)

The app updates itself: when Home opens it queries
`api.github.com/repos/DionathaGoulart/goodfinances/releases/latest` (at most
**once a day**, unauthenticated), compares the result against the installed
`versionName` and, if a newer version exists, shows a dialog with the release
notes and a button that **downloads the APK and opens the system installer**
(`AtualizacaoManager` plus `REQUEST_INSTALL_PACKAGES` and a FileProvider over
`cache/atualizacoes/`).

That only works if the release honours the contract below. Every way of getting
it wrong is silent: the release publishes fine, GitHub reports no error, and the
app simply never sees it. Both [`scripts/release.sh`](../scripts/release.sh)
(Linux/WSL) and [`scripts/release.ps1`](../scripts/release.ps1) (Windows) check
whatever can be checked automatically.

## The contract

| Requirement | Why |
|---|---|
| **Tag `vX.Y.Z`** | The app reads `tag_name` and strips the leading `v`. A bare `1.3.0` works; `release-1.3.0` does not |
| **`versionName` is `X.Y.Z`, no suffix** | `ehMaisNova` does `split('.') + toIntOrNull`, so `"1.3.0-beta"` becomes `[1,3,0]` and **ties** with the final `1.3.0` — anyone on the beta would never be told about the stable release |
| **`versionCode` always higher** | Android refuses to install over an equal or lower `versionCode`. The user would download the APK and the install would fail |
| **APK attached as an asset ending in `.apk`** | The app takes the **first** `.apk` asset on the release. With no asset it falls back to opening the release page in a browser, losing the one-tap install |
| **Neither draft nor pre-release** | `/releases/latest` **ignores drafts and pre-releases**. A release flagged as a pre-release is invisible to the app no matter how new it is |
| **Signed with the same keystore** | A different signature means Android refuses the update — that is what forced the manual reinstall in 1.2.0. `finapp-release.jks` plus `key.properties` live in the repository root, out of git — **keep a backup** |
| **`applicationId` stays `com.finapp`** | Change the id and it becomes a different app: installed alongside instead of updating |

## Steps

1. **Bump the versions** in `app/build.gradle.kts`: `versionCode` + 1 and `versionName` to the new `X.Y.Z`.
2. **Write the version's section in `CHANGELOG.md`** (`## [X.Y.Z] - YYYY-MM-DD`) — the release body comes from it, so write it for the end user.
3. **Commit everything** (the working tree must be clean).
4. Run:

   ```bash
   ./scripts/release.sh 1.3.0            # Linux/WSL
   ```
   ```powershell
   .\scripts\release.ps1 -Versao 1.3.0   # Windows
   ```

The script runs the tests, builds the signed APK, creates the `vX.Y.Z` tag,
publishes the release with the APK attached and marks it as *latest*.

## Publishing by hand

If you would rather use the website, this is what cannot be missing:

```bash
./gradlew testDebugUnitTest assembleRelease
cp app/build/outputs/apk/release/app-release.apk GoodFinances-1.3.0.apk
git tag v1.3.0 && git push origin v1.3.0
gh release create v1.3.0 GoodFinances-1.3.0.apk \
  --title "GoodFinances 1.3.0" --notes-file notes.md --latest
```

Through the GitHub UI: **Releases → Draft a new release**, tag `v1.3.0`, attach
the APK, leave **"Set as a pre-release" UNCHECKED** and tick "Set as the latest
release".

## Build environment without Android Studio

The build only needs JDK 17 and the Android SDK (compileSdk 36):

```bash
# JDK 17
curl -L -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/androidtools && tar xzf jdk.tar.gz -C ~/androidtools && mv ~/androidtools/jdk-17* ~/androidtools/jdk17

# Android SDK (command line tools)
curl -L -o cli.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p ~/androidtools/sdk/cmdline-tools && unzip -q cli.zip -d ~/androidtools/sdk/cmdline-tools
mv ~/androidtools/sdk/cmdline-tools/cmdline-tools ~/androidtools/sdk/cmdline-tools/latest

export JAVA_HOME=~/androidtools/jdk17 ANDROID_HOME=~/androidtools/sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$HOME/androidtools/sdk" > local.properties
```

`local.properties` is per-machine and git-ignored. With Android Studio
installed, all you need is `JAVA_HOME` pointing at its JBR
(`C:\Program Files\Android\Android Studio\jbr` on Windows).

Publishing also needs the [GitHub CLI](https://cli.github.com), authenticated
with `gh auth login`.

## After publishing

The prompt reaches users **when they open Home**, the first time more than 24h
after the previous check — it is not instant. There is no "check now" button in
the UI, so to test it immediately you have to clear the app's data or wait out
the interval.
