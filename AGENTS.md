# AGENTS.md — Agent guide for Gestor-finances-V7

Canonical operating guide for AI coding agents. **OpenAI Codex** reads this file natively; **Claude Code** imports it via `CLAUDE.md`. Read it before making changes.

## Project

A personal finance app: offline-first, local-first. **Android (Kotlin/Compose)** is the primary surface and master DB; **Windows (C#/WinUI 3)** is the secondary surface. Single user, euros only, UI in **Catalan**. The shared contract exists under `shared/`; Android currently has a minimal Gradle/Compose scaffold (see `docs/06-roadmap.md`).

## Documents — read the relevant one before touching related work

- `docs/00-Full_Spec.md` — functional spec (entities, rules, features). The "what."
- `docs/02-synchronization.md` — single-writer token + encrypted snapshot sync.
- `docs/03-architecture.md` — tech stack & the shared-logic strategy.
- `docs/04-data-model.md` — SQLite schema + canonical SQL (the cross-app contract).
- `docs/05-golden-tests.md` + `shared/golden/*.json` — the executable money-rule contract.
- `docs/06-roadmap.md` — phased plan.
- `docs/09-manual-tests.md` — manual smoke checks for implemented slices.

## Non-negotiable invariants

Never violate these unless an explicit decision is recorded in `docs/`:

1. **Money is integer euro cents** (`*_cents`, INTEGER). Never float/decimal. Format to euros only at the UI edge.
2. **Balances, debts, account flow, and "actual" spend are DERIVED** via the canonical SQL views (`v_account_balance`, `v_person_balance`, `v_actual_expense`, `v_actual_income`, `v_account_flow`). Never store them as truth; never recompute them ad-hoc in app code — call the views.
3. **`shared/` is the single source of truth** for schema + canonical SQL. Both apps consume the *same* SQL verbatim (SQLDelight on Android; Microsoft.Data.Sqlite + Dapper on Windows). **No EF Core / ORM query logic** for the finance layer.
4. **Procedural money rules must match `shared/golden/`.** To change a rule (split rounding, recurring advance, dedup, auto-cat, refund math), update the golden vector **first**, then both apps. A red golden test blocks release.
5. **Dates:** a movement `date` is a local calendar date (`'YYYY-MM-DD'`, `LocalDate`); audit timestamps (`*_at`) are UTC instants. Never collapse them into one ambiguous `DateTime`.
6. **Soft-delete only** (`archived_at`); archived = treated as absent. No hard deletes in normal flows.
7. **Never block — warn.** Duplicates, over-refunds, archiving with live deps, settlements exceeding debt: dismissible warning, never a hard stop.
8. **Scope discipline:** single user (no auth/profiles/PIN), euros only (no currency field), offline/local (no cloud DB), CSV import is desktop-only. Do not add these.
9. **Catalan UI, English code.** All user-facing strings externalized (i18n-ready). Code, comments, identifiers, docs in English.
10. **Sync:** the control-token holder is the only writer; the other device is read-only. Snapshots are consistent single-file images (`VACUUM INTO`/backup API), encrypted with the user key, applied atomically. Never merge.
11. **Movement integrity:** `amount_cents > 0` always; direction comes from `type`; respect the type⇔field CHECK constraints (transfer⇔dest_account, settlement⇔person+direction, refund⇔refunds_expense, …).

## Workflow rules

- **Docs are law.** If a change contradicts a doc, don't do it — or update the doc in the same change. Keep `docs/`, `AGENTS.md`, and `CLAUDE.md` consistent.
- **Schema changes are atomic across artifacts:** update `shared/` DDL + a migration (bump `meta.schema_version`) + affected canonical SQL + affected golden vectors + both apps' bindings, together.
- **Vertical slices:** prefer a thin end-to-end feature (DB → view → UI) over broad half-built layers.
- **Tests gate behavior:** both apps run the golden vectors; never weaken a vector to make code pass.
- **Manual checks stay current:** when a slice adds behavior the user can exercise, update `docs/09-manual-tests.md` with concrete steps and expected results.
- **Stack discipline:** use the decided stack; don't add dependencies casually; justify any new one.
- **Simplicity is a requirement, not a nicety.** Write the simplest *correct* code that satisfies the spec and these invariants: no speculative abstraction, premature generalization, gold-plating, or unjustified dependencies; clarity over cleverness; match existing patterns. The golden vectors, derived SQL views, `CHECK` constraints, and the `shared/` contract are deliberate safety nets — keep them; simplicity is sought *within* them. `simplicity-guardian` reviews for this. **Definition of done:** a change is done only when it is *correct* (golden green + `spec-guardian` clean) **and** *simple* (`simplicity-guardian` clean).
- **Small, scoped commits** referencing the relevant doc section. Branch off `main`; don't commit/push unless asked.

## Roles

Adopt the matching role for a task (Claude Code exposes these as subagents in `.claude/agents/`):

- **schema-steward** — `shared/` schema, canonical SQL, golden vectors, migrations.
- **android-engineer** — Kotlin/Compose/SQLDelight feature work.
- **windows-engineer** — C#/WinUI/Microsoft.Data.Sqlite feature work.
- **ui-ux-designer** — screens, flows, Catalan strings, mobile vs desktop layouts.
- **spec-guardian** — review changes against these invariants and the docs (correctness).
- **simplicity-guardian** — review changes for simplicity and clarity: flag overengineering, speculative abstraction, dead code, needless indirection or dependencies. Correct *and* simple.

## Build / run / test

*Concrete commands should be filled in as each scaffold lands.*

- Android: from `android/`, run `.\gradlew.bat :app:assembleDebug` (Windows) / `./gradlew :app:assembleDebug` (Unix), Kotlin, Jetpack Compose. SQLDelight wiring starts in P0B-2.
- Windows: .NET (`dotnet …`), WinUI 3 (Windows App SDK), Microsoft.Data.Sqlite + Dapper.
- Shared tests: both runners load `shared/golden/*.json` and assert against `expected`.

## Intended structure

```
/docs        design docs (source of truth)
/shared      schema + canonical SQL + golden/ fixtures   (the cross-app contract)
/android     Kotlin/Compose app
/windows     C#/WinUI app
AGENTS.md    this file        CLAUDE.md  (imports this)
```
