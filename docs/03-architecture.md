# Personal Finance App — Architecture & Tech Stack

> Companion to `00-Full_Spec.md` (*what* to build) and `02-synchronization.md` (sync design). This document records *how* the app is built: platform topology, per-platform stack, local storage, and — critically — how shared logic stays single-sourced. UI is Catalan; technical artifacts are English.

---

## 1. Topology

- **Dual native**, two UI codebases:
  - **Android — primary surface, master DB:** Kotlin + Jetpack Compose.
  - **Windows — secondary surface:** C# + WinUI 3 (Windows App SDK).
- Each app is fully offline and self-contained over its own local database (spec §7.1).
- Mobile holds the master DB; synchronization is the single-writer control-token + encrypted versioned snapshots specified in `02-synchronization.md`.
- **Rationale:** native fidelity and longevity on both platforms were the priority. The cost — two UI codebases — is accepted; the one risk it creates (forking the money logic) is mitigated by §4.

## 2. Local storage

- **SQLite** on both platforms (single-file embedded DB).
  - Android: **SQLDelight** (SQL-first; generates typed Kotlin from the canonical SQL).
  - Windows: **Microsoft.Data.Sqlite** running the canonical SQL directly, with **Dapper** for lightweight result mapping.
- Both sides are deliberately **SQL-first** (SQLDelight on Android, Microsoft.Data.Sqlite on Windows) so the canonical SQL of §4 runs **verbatim** on each. **EF Core is avoided** for the finance layer — its ORM/LINQ abstraction fights verbatim SQL (you'd bypass it with raw SQL anyway); sqlite-net is a workable fallback but still nudges toward its own query patterns.
- **Identical schema and migrations** on both apps — the schema is a shared artifact (§4), never redefined per platform.
- A **snapshot is a consistent single-file image of the database** plus a monotonically increasing version number, **encrypted with the user-held key** before it leaves the device (spec §7.4). It is *not* a naive copy of the live file — see §2.2 for the mechanics. Backup/export/restore and sync all use this same primitive.

### 2.1 Core value representations — **[DECIDED]**

- **Money:** integer **euro cents** everywhere (`amount_cents`); euros are a UI formatting concern only (cents ÷ 100). Guarantees exact sums, splits, and rounding with no floating-point drift (spec §2.8).
- **Movement `date`:** a **local calendar date** — no time, no time zone (it is the *value date*, spec §3.0). Modeled as `LocalDate` (kotlinx-datetime / NodaTime); stored as TEXT `YYYY-MM-DD`.
- **`created_at` / `updated_at`:** **instants** (timestamps), modeled as `Instant` (kotlinx-datetime / NodaTime); stored as ISO-8601 UTC text (or epoch millis) — pinned in the data-model phase.
- Calendar-date and instant are kept as **distinct types**, never a single ambiguous `DateTime`, so a movement dated the 31st never shifts across time zones. This is why **NodaTime is preferred over `System.DateTime`** on C#: it makes the distinction explicit and mirrors kotlinx-datetime. (Modern .NET `DateOnly` + UTC `DateTimeOffset` is a lighter alternative if you'd rather avoid the dependency.)

### 2.2 Snapshot mechanics — **[DECIDED]**

The live DB runs in WAL mode (with `-wal`/`-shm` sidecar files and possibly active writes), so a snapshot is **not** a raw copy of the file. Producing one:
1. Generate a **consistent single-file image** via SQLite's online **backup API** or **`VACUUM INTO`** (both checkpoint the WAL and yield a clean, standalone DB).
2. The **version number lives inside the DB** (a `meta` table), so it travels atomically with the data — no sidecar to lose.
3. **Encrypt** that stable image with the user-held key (spec §7.4).

Applying a received snapshot:
1. Decrypt; **reject** if its version ≤ the local version (rule in `02-synchronization.md`).
2. Write to a temp file, fsync, then **atomically replace** the local DB (atomic rename), so a crash mid-apply can never leave a half-written database.

## 3. Per-platform components

| Concern | Android (Kotlin) | Windows (C#) |
|---|---|---|
| UI | Jetpack Compose | WinUI 3 |
| DB access | SQLDelight / SQLite | Microsoft.Data.Sqlite + Dapper / SQLite |
| Dates | kotlinx-datetime (`LocalDate` + `Instant`) | NodaTime (`LocalDate` + `Instant`) |
| Charts | e.g. Vico | e.g. LiveCharts / ScottPlot |
| Notifications | Android local notifications | Windows toast/local notifications |
| CSV import | — (desktop-only, spec §4.6b) | full import pipeline |

*(Charting and date libraries are non-binding suggestions — choose during build.)*

## 4. Keeping shared logic single-sourced — **[DECIDED]**

The one rule: **the money logic must behave identically on both platforms.** Strategy chosen: **shared SQL + a golden-test contract.**

### 4.1 Single source of truth = schema + canonical SQL
Because balances, debts, and most analysis are **derived** (spec §2.9, §4.1, §4.2), they are expressed as a **versioned library of canonical SQL** — views/queries for flow, actual spent/earned, per-person debt, category/period breakdowns, and account-flow-over-time. Both apps execute this **same SQL verbatim**:
- Android consumes it directly through SQLDelight (which is SQL-first).
- Windows runs the same query strings against the same schema.

This keeps the largest and most error-prone surface defined exactly once.

### 4.2 Procedural remainder = duplicated, but locked by golden tests
A small set of rules can't be pure SQL and is implemented in each language:
- equal-split **rounding** — remainder cents to the payer (spec §3.7);
- recurring **date-advancement** and pending-occurrence generation (spec §3.10, §4.3);
- **auto-categorization** rule matching and tie-break — newer rule wins (spec §3.15, §4.4);
- **duplicate-detection** heuristic (spec §4.6).

Each is pinned by a **shared, language-neutral set of golden test vectors** (input → expected output, e.g. JSON fixtures). Both apps run the same vectors in their test suites; divergence is a failing test, not a silent money bug.

### 4.3 Desktop-only logic lives once anyway
The CSV import pipeline — parse, column-map, dedup, auto-categorize, batch-commit (spec §4.6b) — is desktop-only, so it exists solely in the C# app. No sharing needed.

## 5. The shared artifacts

Three things are the contract both apps build against, and should live in one shared location:
1. **SQLite schema + migrations.**
2. **The canonical SQL library** (all derivations from §4.1).
3. **The golden test-vector fixtures** (§4.2).

Result: two thin UI codebases over a shared data/derivation layer. Money-affecting logic is either shared SQL (single-sourced) or duplicated-with-golden-tests (behavior-locked); pure UI/presentation is free to differ per platform.

## 6. Open implementation choices (defer to build)

- Exact charting libraries per platform.
- Migration tooling — SQLDelight migrations on Android and a matching approach on C#, kept schema-identical.
- Snapshot encryption primitive (e.g. AES-GCM with a key derived from the user passphrase) — fix in the data-model / implementation phase.
- Test-vector format and how the shared folder is wired into both build systems.
