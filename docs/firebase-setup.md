# Firebase setup

Google sign-in, the shared household and cloud backup all depend on a Firebase
project. `google-services.json` is **not versioned** — anyone cloning the
repository needs their own project (free, Spark plan).

## 1. Create the project

1. Go to https://console.firebase.google.com → **Create project** (e.g. `finapp`)
2. Google Analytics can stay disabled

## 2. Register the Android app

1. In the project: **+ Add app → Android**
2. Package name: `com.finapp`
3. Add the **debug SHA-1** (required for Google sign-in):

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android | grep SHA1
```

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android | Select-String "SHA1:"
```

4. Shipping releases too? Add the SHA-1 of your release keystore as well
   (⚙️ Project settings → General → the app card → *Add fingerprint*)

## 3. Enable Google sign-in

- **Security → Authentication** → *Get started* → **Sign-in method** tab → enable **Google**

> ⚠️ **Order matters:** enable Google **before** downloading
> `google-services.json`. Enabling the provider is what creates the OAuth
> clients inside the file. If you downloaded it first, download it again
> (⚙️ Project settings → General → the app card).

Put the file at **`app/google-services.json`**.

## 4. Create Firestore

- **Databases & Storage → Firestore Database** → *Create database*
- **Production** mode, region `southamerica-east1` (São Paulo)

## 5. Publish the security rules

In the Firestore **Rules** tab, paste the contents of
**[`firestore.rules`](../firestore.rules)** (repository root) and publish. That
file is the single source of truth — do not copy rules from anywhere else, and
republish whenever it changes.

What they guarantee:

| Resource | Who can |
|---|---|
| Create a household | any signed-in user, as long as they include themselves in `membros` and are the `criadoPor` |
| Read a household | **members only**; listing households is denied (blocks enumeration) |
| Resolve an invite code | any signed-in user, by direct GET — **listing** codes is denied |
| Register an invite code | **only a member** of the household it points at (blocks squatting) |
| Join a household | someone who is not yet a member, **adding only their own uid** (without replacing the list, the code or the creator) |
| Read household entries | **members only** |
| Edit or delete an entry | **the author only** (`criadoPorUid`) |
| Household categories, cards, goals and bills | any member (they are collective) |
| Personal mirror (`membros/{uid}`) | every member reads, **only the owner writes** |
| Backups and personal sync (`usuarios/{uid}`) | **the user themselves only** |

## 6. Verify

Build and run the app: Configurações → Casa Compartilhada → *Entrar com Google*.
If it says "Login Google não configurado", `google-services.json` was downloaded
before the Google provider was enabled — go back to step 3.

## Cost

The free Spark plan allows 50k reads and 20k writes per day on Firestore — a
couple using the app daily consumes well under 1% of that.
