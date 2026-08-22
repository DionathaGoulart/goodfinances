# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The app checks for new releases once a day and offers the update with a single
tap. Publishing a release is described in [docs/release.md](docs/release.md).

## [1.3.1] - 2026-08-22

### Fixed

- **A transfer made inside a household now comes out of the shared money.** It
  used to be written to the private bucket of the active tab, so the other
  member's phone never saw the deduction — even though the household balance on
  screen mixes both. Both legs now resolve through the same rule as every other
  entry (`PerfilManager.baldeDeContexto`), so the outgoing leg lands in the
  household wallet and syncs to everyone. The dialog says so explicitly.

### Added

- **Choose whether a transfer touches the shared money.** Inside a household
  the transfer dialog now offers *Da casa* (default — the leg lands in the
  shared wallet and every member sees it) or *Meu* (the leg stays in your
  private bucket, invisible to the others). Useful for a pro-labore that is
  not meant to go into the common pot. Outside a household nothing changes.

## [1.3.0] - 2026-08-16

First **stable** release of the batch that started in 1.2.0-beta: personal and
household spending unified, global cards and the Cards tab leave beta, now with
the recurrence control that was missing.

### Added

- **End a recurrence straight from the entry row.** Long-press any entry that
  came from a fixed bill and pick *Encerrar recorrência*: the rule stops
  generating new occurrences and every unpaid entry is removed at once. The
  confirmation says how many will disappear before you commit.

### Changed

- Ending a recurrence no longer cuts off at today's date — **overdue unpaid
  entries are removed too**. Previously the past-due instalment survived, which
  left behind exactly the red overdue row the user wanted gone.
- Entries you already **paid stay in the history** when a recurrence ends; only
  unpaid ones are removed.
- Documentation reviewed end to end (architecture at schema v17, sync with
  invites and mirrors, development guide) and moved to English.

### Fixed

- `docs/firebase-setup.md` inlined an **outdated, insecure copy of the Firestore
  rules** and told you to publish it. It now points at `firestore.rules` as the
  single source of truth.

## [1.2.0-beta] - 2026-07-16

> ⚠️ **This version required uninstalling the app before installing.** The
> signing key changed, and Android will not update an app in place when the
> signature differs. **It was a one-off** — from 1.3.0 on, updates install over
> the top.
>
> If you are still on something older than 1.2.0, before uninstalling: write
> down the **House invite code** (Settings › Casa Compartilhada), turn on
> **cross-device sync** and wait for it to finish, and take a **JSON export**
> stored off the phone. Receipts and invoices only come back if the Google Drive
> backup is enabled.

### Added

- **Personal and household became a single view.** The House is no longer a tab.
  Every entry says whose it is — the household (default), you, or the other
  person — and you can log an expense on their behalf, and they on yours. The
  assignment syncs along. On Home, the `Tudo · Casa · <names>` chips slice the
  list and every row carries an owner badge. Only Business stays separate.
- **Cards became global.** Register a card once and it works for personal,
  household and business; what stays separate is the spending, by the entry's
  bucket.
- **Cards tab** (inside Analysis): everything that went through each card, with
  the share of each context and every item labelled — *"Nubank R$ 260 — Meu 250
  · Casa 10"*.
- **Upcoming months** strip on Home with what is already scheduled for the next
  6 months, payable and receivable. Not an estimate: it comes from fixed bills
  and instalments already posted.

### Fixed

- Notifications did not cover household entries — since "household" became the
  default, that silenced nearly every due-date alert.
- Recurring expenses and household categories had no screen in Settings.
- CSV/JSON/PDF export only carried the personal bucket, leaving out the household.
- History rows overflowed on narrow screens.

### Changed

- Database at schema version 17 (migrated automatically).

## [1.1.0-beta] - 2026-07-09

### Added

- **Bus tab**: a transit card balance that **debits itself** on routine days,
  with off-routine trips logged by hand.
- **Recurring expenses created from the entry modal** ("repeat every month",
  with an end date).
- **Pending entries**: credit purchases, recurring occurrences and future
  instalments only count towards the balance once marked paid — with payable,
  receivable and an **overdue** state.
- **Multi-context analysis** (combinable Personal/Business chips), tappable pie
  slices and automatic monthly insights.
- **Due-date reminders three times a day** (morning, afternoon, evening), plus
  MEI ceiling, DAS tax and budget alerts.
- **Home-screen widget**, search across every month, per-category budgets and a
  period selector for exports.
- **Receipts and invoices** attached to entries (images converted to PDF), with
  Google Drive backup.

### Security

- Hardened the Firestore rules around invites, joining a house and author-only
  editing.

## [1.0.0] - 2026-07-06

### Added

- First release: entries with a Brazilian currency mask, categories, a dashboard
  with balance/income/expenses, charts in plain Canvas, usage profiles (personal,
  MEI, company), CSV/JSON/PDF export with deduplicated import, automatic weekly
  backups, and the **shared household** with Google sign-in and real-time
  Firestore sync.

[1.3.0]: https://github.com/DionathaGoulart/goodfinances/releases/tag/v1.3.0
[1.2.0-beta]: https://github.com/DionathaGoulart/goodfinances/releases/tag/v1.2.0-beta
[1.1.0-beta]: https://github.com/DionathaGoulart/goodfinances/releases/tag/v1.1.0-beta
[1.0.0]: https://github.com/DionathaGoulart/goodfinances/releases/tag/v1.0.0
