# Sync — the shared household

## Concept

The **House** is a wallet shared between people (a couple, a family). Each
member signs in with Google; everything in the `CASA` bucket syncs across all
their devices in real time. Personal buckets (individual, MEI, company) **never
leave the device** — unless the user opts into personal sync, which uploads to
their own private area.

The House **is not a tab**: it and Personal are a single list, and every entry
records **whose** it is (`Dono`). Inside a household everything is written to
`Perfil.CASA`, including entries assigned to a person — otherwise the assignment
would be neither mutual nor visible to the other member.

## Firestore layout

```
convites/{code}                    (6-char code -> casaId; GET only, never LIST)

casas/{casaId}
  ├── codigoConvite: "A3F7KP"      (6 chars, no 0/O/1/I)
  ├── membros: [uid1, uid2]        (uids only — identity)
  ├── nomes: {uid1: "Ana", ...}    (display names, written as a nested field)
  ├── criadoPor, criadoEm
  ├── transacoes/{uuid}            (one doc per transaction)
  │     valor, tipo, categoria, descricao, data (epochDay), pago,
  │     atualizadoEm, deletado, criadoPor/criadoPorUid, pessoaUid/pessoaNome
  ├── categorias/{uuid}
  │     nome, tipo, cor, arquivada, atualizadoEm, deletado
  ├── cartoes/{uuid}               (nome, diaFechamento, diaVencimento, cor, origemUuid, ...)
  ├── metas/{uuid}                 (nome, valorAlvo, valorGuardado, prazo, cor, ...)
  ├── contas/{uuid}                (descricao, valor, tipo, categoria, vencimento, pago, ...)
  └── membros/{uid}/transacoes/{uuid}
                                   (opt-in mirror of each member's personal spending)

usuarios/{uid}/backups/{profile}   (cloud backup, private per user)
  └── json, criadoEm
usuarios/{uid}/perfis/{profile}/{transacoes|categorias|cartoes|metas|contas}
                                   (opt-in personal cross-device sync)
```

`cartoes`, `metas` and `contas` are collective inside a household — any member
can edit them, with no author guard, unlike transactions. The rules live in
[`firestore.rules`](../firestore.rules); **when you add a collection, update
that file and republish it in the console**. Goals and bills use
`atualizadoEm` / `deletado` (tombstone) like everything else.

Each document id is the **uuid** of the local row, so the same transaction keeps
one identity across every device.

Display names go into the `nomes` map through a nested-field update
(`nomes.<uid>`), never by replacing the whole map — otherwise one member would
wipe the others' names. Every device publishes its own name on launch via
`registrarMeuNome`, which is why a person only shows up in the owner picker
after opening the new version once.

The `membros/{uid}/transacoes` mirror holds the **personal** spending a member
chose to show the household: it appears in lists (`baldesVisiveis`) but **never
in anyone else's balance**. It is the only case where the app physically deletes
a local row.

## Flow (SyncManager)

Started by `FinanApplication`; it switches itself on and off as the user joins
or leaves a household.

**PULL (cloud → device)** — snapshot listeners on `transacoes`, `categorias`,
`cartoes`, `metas` and `contas`:

1. The initial snapshot delivers every document; after that, only changes
2. For each document, look up the local row by uuid
3. Apply it only when the remote `atualizadoEm` is **greater** than the local one (last write wins; a tie keeps the local row, which avoids echoes)
4. The write goes **straight to the DAO**, preserving the remote stamp — never through the repository, which would re-stamp it

**PUSH (device → cloud)** — reactive, with a 1.5s debounce:

1. Watch `MAX(atualizadoEm)` across the CASA rows, tombstones included
2. When it changes, batch-upload rows with `atualizadoEm > watermark` (the watermark is stored in prefs, per household)
3. Writes are fire-and-forget: the Firestore offline queue is durable, so you can add entries with no connection and they upload later, even if the app is closed
4. After uploading, the watermark advances to the highest `atualizadoEm` sent

**Deletions** travel as tombstones (`deletado = true`) — documents are never
removed, otherwise deletions would not propagate reliably.

## Conflicts

Last-write-wins by `atualizadoEm` (epoch millis from the device that edited).
That is enough for household use: if two people edit the same transaction at the
same time, whoever saved last wins, and nothing beyond that one edit is lost.

## Seeding default categories without duplicates

Only **the person who creates the household** seeds the default categories
(`semearCategoriasCasa`); everyone else receives them through sync.
`garantirCategoriasPadrao` deliberately skips the CASA profile — if every device
seeded its own, everything would be duplicated.

## Cards mirrored into the household

Cards are global: register one and it serves personal, household and business.
For a household purchase to point at it, every personal card gets a **mirror**
row with `perfil = CASA` and a deterministic uuid (`origemUuid` keeps the
original). The mirror is one-way (original → mirror) and **read-only** on the
household side; it is reconciled whenever a household is created, joined or
loaded.

When **leaving a household**, the mirrors are tombstoned directly in Firestore
**before** the member `arrayRemove` — once you are out, the rules deny the write
and they would be orphaned forever on everyone else's device.

When grouping (invoices, the analysis pie), always canonicalize the mirror back
to the original card; otherwise the same invoice shows up split in two and
Personal plus Household double-count the slice.

## Cloud backup

Independent of household sync: with automatic backup enabled **and** the user
signed in, the weekly backup of **each profile** (personal ones included) is
also uploaded as JSON to `usuarios/{uid}/backups/{profile}`, subject to a 900 KB
per-document limit — above that only the local copy is kept. **Restore from
backup** compares local against cloud and takes the newer one. New phone?
Install, sign in, restore.

## Privacy and rules

The Firestore rules (see [firebase-setup.md](firebase-setup.md)) guarantee:

- Household data is readable and writable **only by that household's members**
- Household entries: any member reads, **only the author** edits or deletes
- Backups and personal sync: **only by the user themselves**
- Invites: whoever holds the code resolves `convites/{code}` with a direct GET, but **listing** codes is denied (otherwise households could be enumerated), and registering a code requires already being a member of the household it points at, which blocks squatting
- Joining through an invite may only **add your own uid** to the member list, keeping the existing ones and touching neither the code nor the creator
