# AGENTS.md — Agent guide for Gestor-finances-V7

Canonical operating guide for AI coding agents. **OpenAI Codex** reads this file natively; **Claude Code** imports it via `CLAUDE.md`. Read it before making changes.

## Project

A personal finance app: offline-first, local-first. **Android (Kotlin/Compose)** is the primary surface and master DB; **Windows (C#/WinUI 3)** is the secondary surface. Single user, euros only, UI in **Catalan**. The shared contract exists under `shared/`; Android opens the local DB, and both platform test harnesses validate the shared golden vectors (see `docs/06-roadmap.md`).

## Documents — read the relevant one before touching related work

- `docs/00-Full_Spec.md` — functional spec (entities, rules, features). The "what."
- `docs/02-synchronization.md` — single-writer token + encrypted snapshot sync.
- `docs/03-architecture.md` — tech stack & the shared-logic strategy.
- `docs/04-data-model.md` — SQLite schema + canonical SQL (the cross-app contract).
- `docs/05-golden-tests.md` + `shared/golden/*.json` — the executable money-rule contract.
- `docs/06-roadmap.md` — phased plan.
- `docs/07-ui-ux.md` — UI scaffold (IA, navigation, screen inventory, flows, strings; structure only).
- `docs/08-design-system.md` — visual design system (tokens, typography, components, light/dark, mobile + desktop).
- `docs/09-app-architecture.md` — per-app layering, state management, DI, and shared wiring.
- `docs/10-core-ledger-ui.md` — Android Phase 1 ledger screen refinement.
- `docs/11-dashboard-analysis-ui.md` — Android Phase 2 dashboard/analysis screen refinement.
- `docs/12-sharing-debts-ui.md` — Android Phase 3 people/splits/debts screen refinement.
- `docs/13-recurring-refunds-budgets-ui.md` — Android Phase 4 recurring/refunds/budgets/notifications screen refinement.
- `docs/14-trips-tags-ui.md` — Android Phase 5 trips, tags, and trip-analysis screens.
- `docs/15-android-redesign-validation.md` — Phase 5R Android redesign + logic-validation rules, order, and definition of done.
- `shared/design/tokens/design-tokens.json` — machine-readable visual tokens consumed by both native apps.
- `shared/design/tokens/platform-mapping.md` — Compose and WinUI mapping for shared visual tokens.

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
- **Manual checks stay current:** when a slice adds behavior the user can exercise, include concrete manual test steps and expected results in the final handoff.
- **Stack discipline:** use the decided stack; don't add dependencies casually; justify any new one.
- **Design-system discipline:** platform UI values come from `shared/design/tokens/design-tokens.json`; update `docs/08-design-system.md` and the shared token file together when a visual token changes. New UI should conform to the design system as it is implemented whenever practical; if a screen remains intentionally rough, track the follow-up in `docs/06-roadmap.md`. Phase 5R is the dedicated Android redesign/consolidation pass before Windows starts.
- **Phase 5R redesign discipline:** during Phase 5R, read `docs/15-android-redesign-validation.md` before editing. Logic and UI validation happen iteratively per page/entity. Any logic issue discovered during redesign must be fixed at the deepest correct layer across docs, shared schema/SQL, repositories, ViewModels, UI, strings, tests, and Windows/shared harnesses where affected. Existing local data is disposable test data, so do not keep incorrect fields or compatibility placeholders just to preserve it.
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

- Android: from `android/`, run `.\gradlew.bat :app:assembleDebug` and `.\gradlew.bat :app:testDebugUnitTest` (Windows) / `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` (Unix). Kotlin, Jetpack Compose, SQLDelight, and the shared golden-vector JVM harness are wired under `android/`.
- Windows: from the repo root, run `dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj` (Windows) / `dotnet test ./windows/GestorFinances.Tests/GestorFinances.Tests.csproj` (Unix). Phase 0C currently has a .NET shared-SQL and golden-vector harness using Microsoft.Data.Sqlite + Dapper; the WinUI 3 shell waits until Phase 6A.
- Shared tests: both runners load `shared/golden/*.json` and assert against `expected`.

## Intended structure

```
/docs        design docs (source of truth)
/shared      schema + canonical SQL + golden/ fixtures   (the cross-app contract)
/android     Kotlin/Compose app
/windows     C#/WinUI app
AGENTS.md    this file        CLAUDE.md  (imports this)
```
