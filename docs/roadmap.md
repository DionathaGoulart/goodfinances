# Roadmap and known gaps

What is mapped but has not shipped. Nothing here blocks a build. Last reviewed
at 1.3.0.

## Manual action (critical)

- [ ] **Publish `firestore.rules` in the Firebase console.** The rules in the
  repository are only a reference — self-invites, member-list tampering, invite
  code squatting and the author guard on transactions only take effect once
  deployed. Republish whenever the file changes.

## Pending UX (minor)

- [ ] **Unify money input.** The entry modal and the Bus tab use a cents mask
  ("type 1234, get R$ 12,34"), but Settings still uses free decimal text
  (`reaisParaCentavos`) for salary, DAS, budgets and recurrence editing. Extract
  `CampoMoeda` from `OnibusScreen` into `ui/component/` and reuse it.
- [ ] **No Y axis scale on the line chart** (`GraficoLinha.kt`). The grid has no
  values; label at least top, middle and zero with `moedaCompacta`. The
  tap-to-inspect tooltip also has no discovery hint.
- [ ] **The Analysis "Personalizado" chip does not show its range**
  (`AnaliseScreen.kt`). After the `DateRangePicker`, show "12/05 – 20/06" in the
  chip label.
- [ ] **`PerfilSelecaoScreen` does not scroll.** A centred `Column` with no
  `verticalScroll` — it can clip in landscape or at large font scales.
- [ ] Cosmetic (low): ad-hoc spacing scale (2..24dp, should consolidate to
  4/8/12/16/24); the UPDATE button is red when editing an expense, the same
  semantic colour as DELETE (move the CTA to `primary`); the pie slice detail
  shifts the legend (reserve height the way `GraficoLinha` does); the Analysis
  `subAbaIndice` is stored as an Int and desyncs when the Fiscal tab disappears.

## Known limitations (design decisions, not oversights)

- **Category dedup after a rename in personal sync.** Two devices seed
  "Alimentação" with different uuids; renaming on one creates a duplicate on the
  other (an empty "Alimentação" plus "Comida"). Fixing it means redesigning
  category identity, which is by name today. Rare — waiting for a real report.
- **Import dedup by date + amount + category** discards legitimate duplicates
  (two identical bus fares on the same day when merging a CSV without uuids).
  An accepted trade-off of the importer.
- **`ContaAgendada` and `Meta` have no UI.** The entities, sync, backup and
  notifications are still live, but the screens were removed — do not recreate
  them without an explicit request. The daily evaluation serves restored or
  synced data and costs almost nothing.
- **Ending a recurrence cannot be undone.** It tombstones every unpaid
  occurrence at once; the UI confirms first and states how many will go. An undo
  would mean storing the ended batch.

## Infrastructure

- [ ] **Tests for the logic that still lives in ViewModels and the repository**:
  `transacoesParaExport` (period slicing) and salary/DAS materialization. The
  project only tests pure classes (`src/test`), so this means extracting them or
  adding an in-memory Room. Already covered: recurrences, due dates, bus fares,
  card mirroring, the import parser, periods and formatters.

## Manual test checklist (per release)

- [ ] `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`
- [ ] Open with existing data (the migration preserves recurrences, pending entries and the monthly day)
- [ ] Household flow against the published rules: create, join by invite, assign an expense to the other person, leave
- [ ] Home: single scroll, card groups, "Pagar fatura", search, budget card, "Próximos meses" strip
- [ ] Recurrence: create from the modal, edit in Settings, **end it from the row** and confirm only paid entries remain
- [ ] The "Saldo" widget on the Android home screen, and the in-app update pointing at the new release
