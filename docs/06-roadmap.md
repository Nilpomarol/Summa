# Personal Finance App — Roadmap

> Phased plan from the current (design-complete, pre-code) state to a v1 release, then beyond. **No fixed dates** — solo personal project; phases are value-ordered, dependency-aware increments, each leaving the app usable. Tasks use checkboxes and short IDs (`P0A-1`, `P1-3`, …) for tracking and reference.

## Principles

- **Android-first** (the primary surface); Windows and sync come after the core works on mobile.
- **Vertical slices:** DB → canonical SQL → UI per feature, not broad half-built layers.
- **Golden vectors are the gate:** a money rule ships only when `shared/golden/` passes in both apps.
- **`shared/` is the contract:** schema + canonical SQL are single-sourced, never forked.
- **Foundational UI scaffold up front** (`docs/07-ui-ux.md`: IA, navigation, screen inventory, content blocks, flows, strings); **visual style/aesthetics are a separate design pass**; per-screen detail is refined just before building each phase.
- **One writing agent at a time:** use roadmap task IDs in prompts/commits; let the other agent review rather than edit the same dirty tree.
- **Simplest correct thing wins:** prefer the simplest implementation that satisfies the spec and the invariants — no speculative abstraction, premature generalization, or gold-plating. The deliberate safety nets (golden vectors, derived SQL views, `CHECK` constraints, the `shared/` contract) are *not* overengineering and stay.
- **Definition of done (every task):** *correct* — golden suite green and `spec-guardian` clean — **and** *simple* — `simplicity-guardian` clean.

## Current state ✅

Design complete: spec (`00`), sync (`02`), architecture (`03`), data model (`04`), golden tests (`05`), roadmap (`06`), agent tooling (`AGENTS.md`, `CLAUDE.md`, `.claude/agents/`). No application code yet.

---

## Phase 0 — Foundations & scaffolding
**Goal:** a small walking skeleton without turning the first milestone into two app builds.

### Phase 0A — Shared contract

- [x] **P0A-1** Commit the baseline docs/agent/golden setup so future agent work has a clean diff.
- [x] **P0A-2** Create the repo layout: `/android`, `/windows`, `/shared` (with `schema/`, `queries/`, `golden/`), top-level build/CI config.
- [x] **P0A-3** Extract `shared/schema/schema.sql` from `docs/04` — FK-ordered DDL (accounts, categories, people, trips, tags, templates, budgets, auto_cat_rules, import_batches, meta, movements, splits, split_lines), all indexes, PRAGMAs.
- [x] **P0A-4** Seed `meta` (`schema_version=1`, `snapshot_version=0`) and a v1 baseline migration.
- [x] **P0A-5** Extract the canonical views into `shared/queries/` (`v_movement_shared`, `v_account_flow`, `v_account_balance`, `v_actual_expense`, `v_actual_income`, `v_person_balance`).
- [ ] **P0A-6** Add validation for `shared/golden/*.json` envelopes and document the JSON schemas for `templates.split_config` and `auto_cat_rules.conditions`.
- [ ] **P0A-7** Lock the sync protocol shape early in `docs/02`: snapshot file naming/format, version metadata, token marker, key derivation choice, and authenticated-encryption primitive. Implementation still waits until Phase 7.
- [x] **P0A-8** Write the per-app internal-architecture note (`docs/08-app-architecture.md`): layering (data/domain/UI), state management (MVI/MVVM Android, MVVM WinUI), DI, how `shared/` is wired in.
- [x] **P0A-9** Foundational UI/UX scaffold (`docs/07-ui-ux.md`): IA, navigation map, screen inventory + per-screen content blocks, core flows, string-catalog structure. **Structure only — visual style/aesthetics are a separate design pass.**

**Exit:** `shared/` is the canonical contract and validates independently of either app.

### Phase 0B — Android walking skeleton

- [ ] **P0B-1** Scaffold the Android project (Gradle, Kotlin, Compose, SQLDelight).
- [ ] **P0B-2** Generate SQLDelight bindings from `shared/schema` and open the DB at runtime.
- [ ] **P0B-3** Implement Android procedural money rules: split rounding, percentage/exact, recurring advancement, dedup, auto-cat matching.
- [ ] **P0B-4** Golden-vector test harness on Android: load `shared/golden/*.json`, run, assert against `expected`.
- [ ] **P0B-5** CI: build Android and run Android unit tests + golden suite on every push.

**Exit:** Android launches, opens the DB, and passes the golden vectors.

### Phase 0C — Windows schema/golden smoke harness

- [ ] **P0C-1** Create a minimal Windows/.NET test project using Microsoft.Data.Sqlite + Dapper; defer full WinUI shell to Phase 6A.
- [ ] **P0C-2** Apply `shared/schema` and run `shared/queries/` verbatim against a test DB.
- [ ] **P0C-3** Implement C# procedural money rules needed by the golden vectors.
- [ ] **P0C-4** Golden-vector test harness on C#: load `shared/golden/*.json`, run, assert against `expected`.
- [ ] **P0C-5** Add an automated schema/query parity check: Android SQLDelight inputs and Windows SQLite inputs come from the same `shared/` files.
- [ ] **P0C-6** CI: build the Windows test project and run the C# golden suite on every push.

**Exit:** both platforms validate the shared contract, but only Android has an app shell.

---

## Phase 1 — Core ledger (Android) — *Goals #1, #3*
**Goal:** record money and see correct balances.

- [ ] **P1-1** UI/UX: design account list/detail, movement list, add/edit-movement screens; add their Catalan strings to the catalog.
- [ ] **P1-2** Data layer: repositories for accounts, categories, movements over SQLDelight.
- [ ] **P1-3** Accounts: CRUD, starting balance, type, single-default enforcement, display order, archive.
- [ ] **P1-4** Categories: CRUD, kind, nature, two-level parent, archive.
- [ ] **P1-5** Movements: add/edit/archive for `expense`, `income`, `transfer` (origin+dest); the one-time/extraordinary flag on expenses; app-layer validation mirroring the CHECK constraints (amount > 0, type⇔field).
- [ ] **P1-6** Wire `v_account_balance` / `v_account_flow` → per-account balance, net worth.
- [ ] **P1-7** Movement list with filtering (type, account, category, date range, text search) and drill-through to detail.
- [ ] **P1-8** First-run / onboarding: create first account + starting balance; seed default Catalan categories.
- [ ] **P1-9** Global "New movement" action; live refresh of related screens on change.
- [ ] **P1-10** Tests: repository unit tests; golden suite still green; basic UI smoke test.

**Exit:** record expenses/income/transfers on Android with correct, derived balances.

---

## Phase 2 — Analysis core (Android) — *Goal #2*
**Goal:** the dashboard and main spending analysis.

- [ ] **P2-1** UI/UX: design the dashboard + analysis screens; add strings.
- [ ] **P2-2** Analysis SQL in `shared/queries/`: parameterized actual-by-category, account-flow-over-time, income-vs-expense, period totals (built over the five views).
- [ ] **P2-3** Dashboard: KPI cards (net worth, month income/expense, net flow + savings %), accounts overview, category breakdown, latest movements, quick actions.
- [ ] **P2-4** Daily evolution chart (income vs. expense per day) — integrate the charts lib (Vico).
- [ ] **P2-5** Time scopes: month / year / all-time / custom; period comparison (this vs. last).
- [ ] **P2-6** Toggles: actual-vs-flow, averages-vs-totals, fixed-vs-variable, include/exclude one-time (extraordinary).
- [ ] **P2-7** Drill-down everywhere: each aggregate opens its underlying movements.
- [ ] **P2-8** Extra v1 widgets over current data: top merchants / most frequent expenses, largest expenses, spending heatmap, category trend lines, net-worth-over-time, and savings-rate by period.
- [ ] **P2-9** Tests: analysis-query correctness against fixed datasets; golden green.

**Exit:** the monthly dashboard, core breakdowns, and non-recurring v1 analysis widgets are usable and correct.

---

## Phase 3 — Sharing, people & debts — *Goal #4 (the differentiator)*
**Goal:** shared expenses and interpersonal debt.

- [ ] **P3-1** UI/UX: design people, the split editor, settle-up, and debt views; add strings.
- [ ] **P3-2** People: CRUD, archive (warn if balance ≠ 0).
- [ ] **P3-3** Split editor: equal / exact / percentage entry → absolute cents (uses the rounding rule); participant picker; live reconcile to total.
- [ ] **P3-4** User-fronted shared expense incl. §2.5 "paid by other" (user line = 0, one person at full).
- [ ] **P3-5** §2.6 external split (no movement): "a person paid, I owe my share" entry flow, carrying its own date/category/trip.
- [ ] **P3-6** Wire `v_person_balance` → per-person net balance + itemized breakdown (which splits/settlements compose it).
- [ ] **P3-7** Settlements: create with direction inferred from balance; settle-up helper (pre-fill full outstanding + account); partial settlements.
- [ ] **P3-8** Show `is_shared` (via `v_movement_shared`) in movement lists/detail; "paid by X" labeling.
- [ ] **P3-9** Tests: `debt_balance` + `refund_actual` golden green in-app; split-rounding edge cases.

**Exit:** shared expenses, debts, and settlements work; debt vectors green in-app.

---

## Phase 4 — Recurring, refunds & budgets
**Goal:** recurring movements, refunds, and budget tracking.

- [ ] **P4-1** UI/UX: design the recurring list, the recurring prompt, the refund flow, budgets, and notification settings; add strings.
- [ ] **P4-2** Templates: CRUD with schedule (frequency, anchor, `custom_unit`/`interval_count`), flexibility fields, `split_config` JSON for shared recurring.
- [ ] **P4-3** Recurring prompts (virtual): on open, generate due occurrences via the advancement rule; confirm (create movement + advance cursor) / skip (advance) / end / pause.
- [ ] **P4-4** Recurring list ordered by day with a monthly total (mobile).
- [ ] **P4-5** Refunds: create linked to an expense; `amount_cents` (cash) + optional `actual_refund_cents` (shared-expense share); inherit category; over-refund warning.
- [ ] **P4-6** Budgets: CRUD (category-monthly first), evaluation (limit vs. actual), progress bar with green/amber/red.
- [ ] **P4-7** Local notifications: recurring lead-time reminders, budget-threshold alerts, low-balance alerts (`account.low_balance_threshold`) — offline scheduling.
- [ ] **P4-8** Recurring-cost summary analysis ("you spend X/month on subscriptions") built from active templates.
- [ ] **P4-9** Tests: `recurring_advance` + `refund_actual` golden green; budget-evaluation tests.

**Exit:** recurring prompts, refunds, budgets, recurring-cost summary, and alerts function offline.

---

## Phase 5 — Trips & tags
**Goal:** trips as a first-class analysis unit.

- [ ] **P5-1** UI/UX: design trips list, trip-detail analysis, and tag management; add strings.
- [ ] **P5-2** Trips: CRUD (type, status, dates, default account); attach movements; per-trip default-account precedence.
- [ ] **P5-3** Tags: CRUD; global vs. trip-local; one tag per movement (only when `trip_id` set).
- [ ] **P5-4** Trip-detail analysis: KPIs (total, days, avg/day), stacked daily chart (per-day ↔ cumulative), breakdown by category & by tag (total ↔ avg/day), scoped movement list.
- [ ] **P5-5** Trips-as-blocks in normal analysis (toggle, on by default): roll-up into one line + drill-in.
- [ ] **P5-6** Trip budget (`budgets` scope = trip).
- [ ] **P5-7** Tests.

**Exit:** trips are fully functional and analyzable.

---

## Phase 6 — Windows app
**Goal:** desktop parity + the heavy tools, split so the desktop is never one giant porting phase.

### Phase 6A — Windows shell & shared DB

- [ ] **P6A-1** UI/UX: design the desktop shell (sidebar nav), app chrome, shared string catalog usage, and large-screen layout rules.
- [ ] **P6A-2** Scaffold the WinUI 3 app and wire Microsoft.Data.Sqlite + Dapper to the existing `shared/` schema/queries.
- [ ] **P6A-3** Data layer: repositories over the canonical SQL only (no EF Core); map core entities.
- [ ] **P6A-4** Port minimal core screens: accounts, categories, movement list/detail, add/edit movement.
- [ ] **P6A-5** Tests: C# golden suite green, schema/query parity check still green.

**Exit:** desktop launches, opens the real DB, and can perform basic ledger work.

### Phase 6B — Desktop parity

- [ ] **P6B-1** Port people, splits, debts, settlements, refunds, recurring, budgets, trips, tags, dashboard, analysis, and settings.
- [ ] **P6B-2** Recurring calendar: month grid, solid (instance exists) vs. greyed (pending), monthly total, month navigation.
- [ ] **P6B-3** Large-screen analysis: desktop-specific layouts for charts, comparisons, filters, and drill-downs.
- [ ] **P6B-4** Tests: desktop repository tests, UI smoke tests, golden green.

**Exit:** desktop can view/edit everything the Android app can.

### Phase 6C — CSV import

- [ ] **P6C-1** UI/UX: design the CSV wizard and import-review screens.
- [ ] **P6C-2** CSV import wizard: upload → auto-detect/confirm column mapping (multi-language header aliases) + pick account → review/edit drafts.
- [ ] **P6C-3** Draft processing: sign→type, auto-cat, dedup flags, per-row edit/exclude, split/refund/settlement bridge where applicable.
- [ ] **P6C-4** Bulk save as one atomic batch with `import_batch_id`.
- [ ] **P6C-5** Import batch review & rollback.
- [ ] **P6C-6** Tests: import-pipeline unit tests, atomic batch tests, dedup/auto-cat vectors green.

**Exit:** desktop has parity plus reliable bank CSV import.

---

## Phase 7 — Synchronization
**Goal:** mobile↔desktop sync per `docs/02`.

- [ ] **P7-1** Implement the protocol locked in P0A-7; update `docs/02` only for implementation-discovered details, not open-ended redesign.
- [ ] **P7-2** Snapshot production: `VACUUM INTO` consistent image → stamp version → encrypt with the user key.
- [ ] **P7-3** Snapshot apply: decrypt → reject if version ≤ local → write temp → fsync → atomic rename over the live DB.
- [ ] **P7-4** Token lifecycle: normal hand-off (mobile→desktop), intermediate checkpoint, normal return (desktop→mobile); device-state UI.
- [ ] **P7-5** Read-only UX: prominent indicator, disabled editing affordances, one-tap reclaim / discard (conservative case B).
- [ ] **P7-6** Failure handling: desktop open-session detection & recovery; manual discard of a lost session.
- [ ] **P7-7** Backup / export / import: whole-DB backup & restore; CSV-per-entity / JSON export; restore-from-export.
- [ ] **P7-8** Tests: snapshot round-trip, version-reject, atomic-apply crash test, import-during-session atomicity.

**Exit:** hand-off and checkpoints work; no data loss across the documented failure cases.

---

## Phase 8 — Hardening & v1 release
**Goal:** ship v1.

- [ ] **P8-1** Future forecasting: combine active recurring templates with historical averages / learned patterns; clearly label estimates.
- [ ] **P8-2** Global search & rich filtering across all movements/entities, with clear-all.
- [ ] **P8-3** Settings & system tools: recalculate balances, resync recurring, sync-key management, light/dark theme, app version / update check.
- [ ] **P8-4** Onboarding polish; empty states across screens.
- [ ] **P8-5** Privacy pass: confirm OS at-rest reliance; snapshot-encryption key UX (app lock stays deferred).
- [ ] **P8-6** Performance pass at scale (thousands–tens of thousands of movements): verify indexes, profile analysis queries.
- [ ] **P8-7** Accessibility & visual polish; theming pass on both apps.
- [ ] **P8-8** Packaging & distribution: Android (signed APK / Play Store), Windows (MSIX installer); signing.
- [ ] **P8-9** Final `spec-guardian` and `simplicity-guardian` review; golden suite green; docs ↔ code consistency check.
- [ ] **P8-10** v1 release.

**Exit:** v1.

---

## Post-v1 / v1.5 *(from spec §8)*

- [ ] Payee-as-entity (remembered merchant + default category).
- [ ] Person groups (split shortcuts).
- [ ] Desktop bulk multi-select edit.
- [ ] Quick-add capture surfaces (app shortcuts, home-screen widget, QS tile).
- [ ] Refund of other participants' shares.
- [ ] Later: savings goals, receipt/attachment storage, dedicated at-rest encryption + app lock, system-learned categorization, multi-currency (deferred).
