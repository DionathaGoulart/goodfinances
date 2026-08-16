# Architecture

> Code identifiers are in Brazilian Portuguese by convention (see
> [development.md](development.md)); they are quoted here verbatim.

## Overview

MVVM plus a repository, reactive end to end: the UI collects Room `Flow`s and
**never reloads data by hand** — any write to the database refreshes every
screen on its own.

```
UI (Compose) ── collectAsState ──> ViewModel (StateFlow)
                                        │ flatMapLatest(context buckets)
                                        ▼
                                  FinanceRepository  ◄── the only write path
                                        │
                                        ▼
                                  Room (DAOs + Flow) ◄──► SyncManager ◄──► Firestore
```

## Layers

| Package | Responsibility |
|---|---|
| `ui/screen` | Home, Analysis, Bus, Settings, plus profile selection |
| `ui/component` | Reusable pieces: entry modal, history row, card groups, cards, Canvas charts |
| `viewmodel` | One per tab, plus `CasaViewModel`, `MembrosViewModel` and `PerfilViewModel`; they expose `StateFlow` and a `SharedFlow<String>` of messages that surface as snackbars or toasts |
| `data/repository` | `FinanceRepository` — the single data gateway; stamps `atualizadoEm` on every write |
| `data/db` | Room: 7 entities, DAOs, migrations. Aggregations (balance, sums) are done **in SQL** |
| `data/io` | CSV/JSON/PDF export, import parser (pure, testable), backups, receipts, Google Drive backup |
| `data/notif` | Financial triggers (`NotificacaoManager`, `Vencimentos`) and `NotificacaoWorker`, which runs three times a day |
| `data/sync` | `CasaManager` (auth and household) and `SyncManager` (the sync engine) |
| `utils` | pt-BR formatting, period filters, appearance, cards, bucket merging, `fluxoDataAtual()` |

## Contexts and buckets (`PerfilManager`)

The central concept. The selected profile decides the layout; each row's
`perfil` column decides **which bucket the data lives in** (one database
partitioned by column, not one database per profile).

- **`perfilAtivo`** — what the user picked (individual / MEI / company). Drives the dashboard layout and how many tabs exist.
- **`perfilDados`** — the **private** bucket of the active context (`MEI_PESSOAL` or `MEI_NEGOCIO` depending on the tab). The default write target and the anchor for single-bucket screens (settings, backup, export).
- **`baldesFinanceiros`** — what counts towards **balance, sums and pending totals**: the private bucket plus `CASA`.
- **`baldesVisiveis`** — what the **lists** read: `baldesFinanceiros` plus the `CASA_MEMBROS` mirror. The difference is exactly that mirror: another member's personal spending shows up in the list (so you know what happened) but **never touches your balance**.

**The household is not a tab.** Personal and household are one view; what
separates entries is their *owner*. There are at most two tabs, `Pessoal` and
`Empresa`.

`utils/Fluxos.kt` (`mesclarListas` / `somarBaldes`) merges buckets — and
short-circuits the empty collection deliberately: `combine` over an empty list
never emits and would freeze the UI on its initial value.

## Entry ownership

`Dono` is **whose** an entry is (`Dono.Casa` or `Dono.Pessoa(uid)`), stored in
`Transacao.pessoaUid` / `pessoaNome`. Not to be confused with `criadoPor*`,
which is **who typed it** — the whole point of the feature is being able to log
someone else's expense.

Inside a household **everything is written to `Perfil.CASA`**, including entries
assigned to a person: in the typist's private bucket the other person would
never see the row, and the assignment would not be mutual. Explicit consequence:
in a household, an expense assigned to someone is visible to both.
Outside a household there is no choice (private bucket, empty `pessoaUid`).

`Dono` and `FiltroDono` are always compared **by uid** (`mesmoQue`), never with
`==`: the name stored on the transaction can diverge from the one published by
the household.

## Data model (schema v17)

Entities: `Transacao`, `Categoria`, `ConfiguracaoPerfil`, `TransacaoRecorrente`,
`Cartao`, `Meta`, `ContaAgendada`.

Sync fields, present on every synced entity:

| Field | Role |
|---|---|
| `uuid` | Global identity (unique index) — autoincrement ids collide across devices |
| `atualizadoEm` | Epoch millis of the last change — last write wins on conflict |
| `deletado` | **Tombstone**: deleting is marking, never removing, so deletions propagate |
| `criadoPor` / `criadoPorUid` | (Transacao) who created it — shown in the household and used by the edit guard |
| `pessoaUid` / `pessoaNome` | (Transacao) whose entry it is, when assigned to a household member |

Rules the whole codebase follows:

- **Deleting means `deletado = true`** through the repository. Not even
  `deletarTodasTransacoes` uses `@Delete` — sync would resurrect the row. Only
  the local `CASA_MEMBROS` mirror is deleted physically.
- **Every write goes through the repository**, which stamps `atualizadoEm` —
  except `SyncManager`, which writes straight to the DAOs to **preserve** the
  remote stamp.
- **Every read filters `deletado = 0`.**
- Undoing a deletion clears the tombstone; it does not re-insert.
- In a household, **only the author can edit or delete** (`podeSerEditadaPor`,
  also enforced by the Firestore rules).

## Pending entries, recurrences and cards

- **`Transacao.pago = false` is a pending entry** (a credit purchase until the
  invoice is paid, a recurring expense occurrence, a future instalment). It does
  not count in `observarSaldoTotal` until marked paid. Home shows payable and
  receivable separately, plus **overdue** — a derived state, not a column: a
  pending expense dated in the past.
- **Monthly recurrences materialize 12 months ahead** (`Recorrencias.kt`, pure
  and tested). Each occurrence is born `pago = false` on its due date, with a
  **deterministic uuid** (reprocessing never duplicates; the tombstone blocks
  resurrection) and a `recorrenciaUuid` linking back to the rule. The intended
  day lives in `diaMensal` — a short month posts on the last day and **returns**
  to the requested day next month. Never reschedule with chained `plusMonths`,
  which truncates 31→28 permanently.
- **Ending a recurrence** (`encerrarRecorrenteComOcorrencias`) deactivates the
  rule and tombstones **every** unpaid occurrence, overdue ones included; what
  was already paid remains as history.
- **Cards are global**, not owned by a context: what separates spending is the
  transaction's bucket. Personal cards are **mirrored into the household**
  (`Cartao.origemUuid`, deterministic uuid), one-way original→mirror and
  read-only on the household side. `utils/Cartoes.kt` centralizes
  canonicalization — grouping without canonicalizing splits one invoice into two.

## Design decisions

- **Money as cents (`Long`)** — `R$ 12,34` is `1234`. No floating-point drift in
  sums. Always displayed through `Formatadores.moeda(Long)`. In charts, convert
  with `toDouble()` before dividing (integer division!).
- **Categories are never deleted** — they are *archived* (gone from new entries,
  preserved in history). Renaming propagates the new name to transactions and
  recurrences, since the reference is by name.
- **Charts in plain Canvas** — no external library (MPAndroidChart and friends):
  smaller APK, native dark mode and Compose animations.
- **Fixed salary is materialized as a recurrence** — configuring a salary
  creates or updates a monthly `TransacaoRecorrente`; due recurrences are posted
  when the app opens, which recovers days the app stayed closed.
- **Dates** — `LocalDate` is stored as epoch day (`Long`) in SQLite, so `BETWEEN`
  works directly. Screens that depend on "today" use `fluxoDataAtual()`, which
  re-emits at midnight, so they do not show yesterday's month after the rollover.
- **The bus tab debits itself** on routine days (`calcularDescontosOnibus`, pure
  and tested) with a last-processed-day cursor — idempotent by design and never
  negative.
- **Storage Access Framework** for export and import — zero storage permissions
  on any Android version.
