# 💰 GoodFinances

**Current version: 1.3.0**

Android app for personal and household finances, with a **shared wallet synced in real time** across phones. Built with Kotlin and Jetpack Compose, dark mode only.

> The app itself — UI strings and code identifiers (`adicionarTransacao`, `perfilDados`) — is in **Brazilian Portuguese**, because that is who it is built for. Everything written for developers is in English.

## 📲 Install and updates

Grab the latest APK from the [**Releases**](https://github.com/DionathaGoulart/goodfinances/releases/latest) page. The app **checks for new versions on its own** (once a day) and downloads the update with a single tap — publish a release here and every phone gets notified.

See the [**CHANGELOG**](CHANGELOG.md) for what changed in each version.

> Coming from a version older than 1.2.0? That update required **uninstalling the app first** (the signing key changed). It was a one-off — from 1.3.0 on, updates install over the top. The migration steps are in the [CHANGELOG](CHANGELOG.md#120-beta---2026-07-16).

## ✨ Features

**Day-to-day finances**
- Dashboard with total and monthly balance, income and expenses in real time — plus a **closing summary** of the previous month at the start of each month
- Entries with category, description and date, using a Brazilian currency mask (type `1234`, get `R$ 12,34`)
- **A single list for personal and household spending**: every entry says **whose it is** — the household (default), yours, or the other person's — and the `Tudo · Casa · <names>` chips slice the list. Only Business stays separate
- **Installment purchases** (the total is split into one entry per month) and **transfers between contexts** (Personal ↔ Business) with both legs linked — delete one and the other goes with it
- **Credit card purchases** grouped by card on Home, with **"Pay invoice"** in one tap — the pending amount only hits the balance once you pay
- **Cards are global, not per context**: register one and it works for personal, household and business. The **Cards** tab breaks down everything that went through each card by context — *"Nubank R$ 260 — Meu 250 · Casa 10"*
- **Upcoming months** on Home: what is already scheduled to be paid or received over the next 6 months (fixed bills and installments already materialized), one tap to open the month
- History grouped by date — **tap to edit, long-press for the menu** (edit / hide / delete / end recurrence)
- **Fixed bills** that post themselves every month as pending, with **"End recurrence"** in one tap: it stops repeating and clears everything still unpaid, overdue included — what you already paid stays in the history
- **Fixed salary posted automatically** on the configured day
- Charts drawn with plain Canvas (categories with an income/expense toggle, 6-month line and bars), **per-category budgets** and quick stats
- **Tap a pie slice** to see the entries behind it — debit and cash split by category, credit split by card
- **Automatic monthly insights**: notable changes against the previous month ("you spent 32% more on Food")
- **Open card invoices**, grouped by due date
- **Home-screen widget** for quick entry and optional **biometric lock**

**Planning and alerts**
- **Notifications** for budgets running over, DAS tax due, MEI revenue ceiling, bills coming due, recurring income for the day, and a nudge when you go days without recording anything
- **In-app updates** — the app downloads and installs the new version by itself

**Receipts and invoices**
- Attach a photo, image or PDF to any entry — **images are converted to PDF automatically**
- Back those files up to **Google Drive** (free, app-private folder)
- ZIP export organized by **year/month plus a yearly CSV** — ready for tax season

**Usage modes** — data fully isolated from each other:
- 👤 **Personal only** — day-to-day spending
- 💼 **Personal + Business** — separate tabs, with mirrored owner's pay
- 🏢 **Business only** — revenue × expenses plus profit, tax panel with the MEI ceiling and DAS reminder
- 🏠 **Household** — lives inside Personal as a **shared wallet synced** across members (no longer a separate tab)

**Shared household (sync)**
- Google sign-in and a "House" with a 6-character invite code
- Entries show up on the other phones within seconds, tagged with who created them — and **only the author can edit or delete** (enforced server-side too)
- **Mutual assignment**: you can log an expense as belonging to the other person, and they can do the same for you — the assignment syncs along
- Each member can also choose to share their own personal spending with the household
- Works offline (syncs when the connection returns); conflicts resolved last-write-wins

**Data**
- CSV, JSON and PDF report export · import with preview and deduplication
- **Cross-device sync** for the same account (opt-in) and an automatic weekly backup — local **and in the cloud**
- Money stored as **cents (`Long`)** — no floating-point drift

## 🛠 Built with

Kotlin 2.1 · Jetpack Compose (Material 3) · Room (SQLite) · Hilt + KSP · Coroutines/Flow · WorkManager · Firebase Auth + Firestore · Canvas API for charts · JUnit

## 🚀 Getting started

Requirements: Android Studio, or JDK 17 plus Android SDK 36.

```bash
git clone git@github.com:DionathaGoulart/goodfinances.git
```

Google sign-in and sync need your own Firebase project (`google-services.json` is not versioned) — follow [docs/firebase-setup.md](docs/firebase-setup.md).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
```

More detail (signed releases, code conventions) in [docs/development.md](docs/development.md).

## 📚 Documentation

| Document | Contents |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | What changed in each version |
| [docs/architecture.md](docs/architecture.md) | Layers, contexts and buckets, entry ownership, data model (v17), design decisions |
| [docs/sync.md](docs/sync.md) | How the shared household works internally (push/pull, conflicts, tombstones, cloud backup) |
| [docs/firebase-setup.md](docs/firebase-setup.md) | Full Firebase setup (Auth, Firestore, security rules) |
| [docs/development.md](docs/development.md) | Build, tests, release signing and conventions |
| [docs/release.md](docs/release.md) | How to publish a version and the contract that keeps auto-updates working |
| [docs/roadmap.md](docs/roadmap.md) | Known gaps, accepted trade-offs and pending work |

## 📄 License

**Personal, non-commercial use** is free — use, modify and build it for yourself and your family. **Commercial use** (selling it, publishing it to a store for money or with ads, using it in a paid service) requires written permission from the author. See [LICENSE](LICENSE).

## 👤 Author

**Dionatha Goulart** — dgoulart.work@gmail.com
