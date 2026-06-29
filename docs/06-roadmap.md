# Personal Finance App — Roadmap

> Phased plan from the current (design-complete, pre-code) state to a v1 release, then beyond. **No fixed dates** — solo personal project; phases are value-ordered, dependency-aware increments, each leaving the app usable. Tasks use checkboxes and short IDs (`P0A-1`, `P1-3`, …) for tracking and reference.

## Principles

- **Android-first** (the primary surface); Windows and sync come after the core works on mobile.
- **Vertical slices:** DB → canonical SQL → UI per feature, not broad half-built layers.
- **Golden vectors are the gate:** a money rule ships only when `shared/golden/` passes in both apps.
- **`shared/` is the contract:** schema + canonical SQL are single-sourced, never forked.
- **Foundational UI scaffold up front** (`docs/07-ui-ux.md`: IA, navigation, screen inventory, content blocks, flows, strings); **visual style lives in the design system** (`docs/08-design-system.md`); per-screen detail is refined just before building each phase.
- **Design-system alignment is not final-only.** Each UI slice should follow `docs/08-design-system.md` and shared tokens as it is built. Do not knowingly ship utilitarian UI when the design-system path is straightforward; if a slice must stay rough to protect momentum, track the follow-up explicitly.
- **Phase-end gates:** every implementation phase ends with tests, a focused UI polish/design-system pass, and an architecture/feature/code quality audit before the next phase starts.
- **Android redesign checkpoint before Windows:** after the Android feature phases and before the Windows app begins, run a dedicated Android redesign/consolidation pass so the desktop app ports a stable visual language instead of copying temporary scaffolding. Phase 5R also validates app logic; follow `docs/15-android-redesign-validation.md`.
- **One writing agent at a time:** use roadmap task IDs in prompts/commits; let the other agent review rather than edit the same dirty tree.
- **Simplest correct thing wins:** prefer the simplest implementation that satisfies the spec and the invariants — no speculative abstraction, premature generalization, or gold-plating. The deliberate safety nets (golden vectors, derived SQL views, `CHECK` constraints, the `shared/` contract) are *not* overengineering and stay.
- **Definition of done (every task):** *correct* — golden suite green and `spec-guardian` clean — **and** *simple* — `simplicity-guardian` clean.
- **Manual checks:** include concrete manual test steps in the final handoff whenever a completed slice adds behavior the user can exercise directly.

## Current state ✅

Phase 0 foundations, Android Phase 1 core ledger, Android Phase 2 analysis, Android Phase 3 sharing/people/debts, Android Phase 4 recurring/refunds/budgets, and Android Phase 5 trips/tags are complete (`P5-1` through `P5-9`). `Phase 5R` (Android redesign & design-system consolidation) is next unless the roadmap is updated. Deferred follow-ups from Phase 4: editing a shared split *in the template form*, cross-template confirm-all/skip-all, surfacing orphaned refunds when an original expense is archived, and embedding the budget bar inside category breakdown/detail.

---

## Phase 0 — Foundations & scaffolding
**Goal:** a small walking skeleton without turning the first milestone into two app builds.

### Phase 0A — Shared contract

- [x] **P0A-1** Commit the baseline docs/agent/golden setup so future agent work has a clean diff.
- [x] **P0A-2** Create the repo layout: `/android`, `/windows`, `/shared` (with `schema/`, `queries/`, `golden/`), top-level build/CI config.
- [x] **P0A-3** Extract `shared/schema/schema.sql` from `docs/04` — FK-ordered DDL (accounts, categories, people, trips, tags, templates, budgets, auto_cat_rules, import_batches, meta, movements, splits, split_lines), all indexes, PRAGMAs.
- [x] **P0A-4** Seed `meta` (`schema_version=1`, `snapshot_version=0`) and a v1 baseline migration.
- [x] **P0A-5** Extract the canonical views into `shared/queries/` (`v_movement_shared`, `v_account_flow`, `v_account_balance`, `v_actual_expense`, `v_actual_income`, `v_person_balance`).
- [x] **P0A-6** Add validation for `shared/golden/*.json` envelopes and document the JSON schemas for `templates.split_config` and `auto_cat_rules.conditions`.
- [x] **P0A-7** Lock the sync protocol shape early in `docs/02`: snapshot file naming/format, version metadata, token marker, key derivation choice, and authenticated-encryption primitive. Implementation still waits until Phase 7.
- [x] **P0A-8** Visual design system (`docs/08-design-system.md`): tokens (incl. finance-semantic colour roles), typography (Geist + tabular mono, decimal-comma locale), spacing/radius/elevation, components, light/dark, mobile + desktop. Living visual reference in the `.dc.html` files.
- [x] **P0A-9** Foundational UI/UX scaffold (`docs/07-ui-ux.md`): IA, navigation map, screen inventory + per-screen content blocks, core flows, string-catalog structure. **Structure only — visual style/aesthetics are a separate design pass.**

- [x] **P0A-10** Per-app internal-architecture note (`docs/09-app-architecture.md`): layering (data/domain/UI), state management (MVI/MVVM Android, MVVM WinUI), DI, how `shared/` is wired in.
- [x] **P0A-11** Extract the design tokens from `docs/08-design-system.md` into a shared, machine-consumable form (e.g. `shared/design/tokens`) + a Compose↔WinUI mapping, so both native UIs implement identical values without drift.

**Exit:** `shared/` is the canonical contract and validates independently of either app.

### Phase 0B — Android walking skeleton

- [x] **P0B-1** Scaffold the Android project (Gradle, Kotlin, Compose, SQLDelight).
- [x] **P0B-2** Generate SQLDelight bindings from `shared/migrations/001_initial.sql` + `shared/queries` and open the DB at runtime.
- [x] **P0B-3** Implement Android procedural money rules: split rounding, percentage/exact, recurring advancement, dedup, auto-cat matching.
- [x] **P0B-4** Golden-vector test harness on Android: load `shared/golden/*.json`, run, assert against `expected`.
- [x] **P0B-5** CI: build Android and run Android unit tests + golden suite on every push.

**Exit:** Android launches, opens the DB, and passes the golden vectors.

### Phase 0C — Windows schema/golden smoke harness

- [x] **P0C-1** Create a minimal Windows/.NET test project using Microsoft.Data.Sqlite + Dapper; defer full WinUI shell to Phase 6A.
- [x] **P0C-2** Apply `shared/migrations/001_initial.sql` and run `shared/queries/` verbatim against a test DB.
- [x] **P0C-3** Implement C# procedural money rules needed by the golden vectors.
- [x] **P0C-4** Golden-vector test harness on C#: load `shared/golden/*.json`, run, assert against `expected`.
- [x] **P0C-5** Add an automated schema/query parity check: Android SQLDelight inputs and Windows SQLite inputs come from the same `shared/` files.
- [x] **P0C-6** CI: build the Windows test project and run the C# golden suite on every push.

**Exit:** both platforms validate the shared contract, but only Android has an app shell.

---

## Phase 1 — Core ledger (Android) — *Goals #1, #3*
**Goal:** record money and see correct balances.

- [x] **P1-1** UI/UX: design account list/detail, movement list, add/edit-movement screens; add their Catalan strings to the catalog.
- [x] **P1-2** Data layer: repositories for accounts, categories, movements over SQLDelight.
- [x] **P1-3** Accounts: CRUD, starting balance, type, single-default enforcement, display order, delete via soft-delete.
- [x] **P1-4** Categories: CRUD, kind, nature, two-level parent, delete via soft-delete.
- [x] **P1-5** Movements: add/edit/delete via soft-delete for `expense`, `income`, `transfer` (origin+dest); the one-time/extraordinary flag on expenses; app-layer validation mirroring the CHECK constraints (amount > 0, type⇔field).
- [x] **P1-6** Wire `v_account_balance` / `v_account_flow` → per-account balance, net worth.
- [x] **P1-7** Movement list with filtering (type, account, category, date range, text search) and drill-through to detail.
- [x] **P1-8** First-run / onboarding: create first account + starting balance; seed default Catalan categories.
- [x] **P1-9** Global "New movement" action; live refresh of related screens on change.
- [x] **P1-10** Tests: repository unit tests; golden suite still green; basic UI smoke test.
- [x] **P1-11** Design-system alignment / UI polish for the completed Android core-ledger surfaces: app shell/nav/FAB, onboarding, accounts, categories, movements, dialogs, empty states, typography, spacing, cards, icons, and light/dark token usage. No new finance behavior.
- [x] **P1-12** Architecture, feature, and code quality audit for the Android core ledger: verify docs/spec/design-system alignment, derived-view usage, test coverage, simplicity, and absence of speculative abstractions.

**Exit:** record expenses/income/transfers on Android with correct, derived balances, and core ledger screens aligned to the design system.

---

## Phase 2 — Analysis core (Android) — *Goal #2*
**Goal:** the dashboard and main spending analysis.

- [x] **P2-1** UI/UX: design the dashboard + analysis screens; add strings.
- [x] **P2-2** Analysis SQL in `shared/queries/`: parameterized actual-by-category, account-flow-over-time, income-vs-expense, period totals (built over the five views).
- [x] **P2-3** Dashboard: KPI cards (net worth, month income/expense, net flow + savings %), accounts overview, category breakdown, latest movements, quick actions.
- [x] **P2-4** Daily evolution chart (income vs. expense per day) — integrate the charts lib (Vico).
- [x] **P2-5** Time scopes: month / year / all-time / custom; period comparison (this vs. last).
- [x] **P2-6** Toggles: actual-vs-flow, averages-vs-totals, fixed-vs-variable, include/exclude one-time (extraordinary).
- [x] **P2-7** Drill-down everywhere: each aggregate opens its underlying movements.
- [x] **P2-8** Extra v1 widgets over current data: top merchants / most frequent expenses, largest expenses, spending heatmap, category trend lines, net-worth-over-time, and savings-rate by period.
- [x] **P2-9** Tests: analysis-query correctness against fixed datasets; golden green.
- [x] **P2-10** Design-system alignment / UI polish for dashboard and analysis after the core analysis surface is usable: KPI cards, charts, breakdown rows, filter controls, comparison states, empty states, and drill-down affordances. No new analysis behavior.
- [x] **P2-11** Architecture, feature, and code quality audit for the Android analysis surface: verify canonical-query usage, feature completeness against docs, chart/filter simplicity, performance risk, and test coverage.

**Exit:** the monthly dashboard, core breakdowns, and non-recurring v1 analysis widgets are usable, correct, and visually aligned with the design system.

---

## Phase 3 — Sharing, people & debts — *Goal #4 (the differentiator)*
**Goal:** shared expenses and interpersonal debt.

- [x] **P3-1** UI/UX: design people, the split editor, settle-up, and debt views; add strings.
- [x] **P3-2** People: CRUD, delete via soft-delete (warn if balance ≠ 0).
- [x] **P3-3** Split editor: equal / exact / percentage entry → absolute cents (uses the rounding rule); participant picker; live reconcile to total.
- [x] **P3-4** User-fronted shared expense incl. §2.5 "paid by other" (user line = 0, one person at full).
- [x] **P3-5** §2.6 external split (no movement): "a person paid, I owe my share" entry flow, carrying its own date/category/trip.
- [x] **P3-6** Wire `v_person_balance` → per-person net balance + itemized breakdown (which splits/settlements compose it).
- [x] **P3-7** Settlements: create with direction inferred from balance; settle-up helper (pre-fill full outstanding + account); partial settlements.
- [x] **P3-8** Show `is_shared` (via `v_movement_shared`) in movement lists/detail; "paid by X" labeling.
- [x] **P3-9** Tests: `debt_balance` + `refund_actual` golden green in-app; split-rounding edge cases.
- [x] **P3-10** Design-system alignment / UI polish for people, split editor, debt, settlement, and shared-expense surfaces. No new sharing/debt behavior.
- [x] **P3-11** Architecture, feature, and code quality audit for sharing/people/debts: verify golden-rule parity, derived debt views, warning-not-blocking behavior, and simple state flows.

**Exit:** shared expenses, debts, and settlements work; debt vectors green in-app.

---

## Phase 4 — Recurring, refunds & budgets
**Goal:** recurring movements, refunds, and budget tracking.

- [x] **P4-1** UI/UX: design the recurring list, the recurring prompt, the refund flow, budgets, and notification settings; add strings. *(`docs/13-recurring-refunds-budgets-ui.md`)*
- [x] **P4-2** Templates: CRUD with schedule (frequency, anchor, `custom_unit`/`interval_count`), flexibility fields, `split_config` JSON for shared recurring. *(Shared-recurring split editing in the template form is deferred to P4-3 where carry-forward prefill is implemented; the data layer persists `split_config` already.)*
- [x] **P4-3** Recurring prompts (virtual): on open, generate due occurrences via the advancement rule; confirm (create movement + advance cursor) / skip (advance) / end / pause. *(Confirm prefills an editable amount/date dialog; split carry-forward is wired at the confirm/data level. Cross-template confirm-all/skip-all convenience is deferred; per-template skip-all is implemented.)*
- [x] **P4-4** Recurring list ordered by day with a monthly total (mobile).
- [x] **P4-5** Refunds: create linked to an expense; `amount_cents` (cash) + optional `actual_refund_cents` (shared-expense share); inherit category; over-refund warning. *(Started from expense detail, which also lists linked refunds. Surfacing orphaned refunds when the original is archived, and the refund→original label on the refund's own detail, are deferred refinements.)*
- [x] **P4-6** Budgets: CRUD (category-monthly first), evaluation (limit vs. actual), progress bar with green/amber/red. *(Reached from the Analysis header; surfacing the same bar inside category breakdown/detail is a deferred follow-up once a category-detail surface exists.)*
- [x] **P4-7** Local notifications: recurring lead-time reminders, budget-threshold alerts, low-balance alerts (`account.low_balance_threshold`) — offline scheduling.
- [x] **P4-8** Recurring-cost summary analysis ("you spend X/month on subscriptions") built from active templates.
- [x] **P4-9** Tests: `recurring_advance` + `refund_actual` golden green; budget-evaluation tests.
- [x] **P4-10** Design-system alignment / UI polish for recurring, refund, budget, alert, and notification surfaces. No new recurring/refund/budget behavior.
- [x] **P4-11** Architecture, feature, and code quality audit for recurring/refunds/budgets: verify rule parity, local scheduling boundaries, warning UX, and implementation simplicity.

**Exit:** recurring prompts, refunds, budgets, recurring-cost summary, and alerts function offline.

---

## Phase 5 — Trips & tags
**Goal:** trips as a first-class analysis unit.

- [x] **P5-1** UI/UX: design trips list, trip-detail analysis, and tag management; add strings. *(`docs/14-trips-tags-ui.md`)*
- [x] **P5-2** Trips: CRUD (type, status, dates, default account); attach movements; per-trip default-account precedence. *(Android: `Trips.sq`, `TripRepository`, `TripsScreen`, movement `trip_id` save/read path.)*
- [x] **P5-3** Tags: CRUD; global vs. trip-local; one tag per movement (only when `trip_id` set). *(Android: `Tags.sq`, `TagRepository`, `TagsScreen`, movement tag picker/filter/detail display.)*
- [x] **P5-4** Trip-detail analysis: KPIs (total, days, avg/day), stacked daily chart (per-day ↔ cumulative), breakdown by category & by tag (total ↔ avg/day), scoped movement list. *(Android: `TripAnalysis.sq`, `TripAnalysisRepository`, trip detail KPIs/chart/breakdowns/scoped movement list.)*
- [x] **P5-5** Trips-as-blocks in normal analysis (toggle, on by default): roll-up into one line + drill-in. *(Shared: `analysis_actual_breakdown.sql`; Android Analysis toggle + trip-block drill-through.)*
- [x] **P5-6** Trip budget (`budgets` scope = trip). *(Android: shared `budgets` table scope support, trip budget form/list evaluation, trip detail Budget action.)*
- [x] **P5-7** Tests. *(Android: trip-block analysis grouping, trip-budget context ViewModel, repository coverage for trips/tags/trip analysis/trip budgets, movement trip/tag integration.)*
- [x] **P5-8** Design-system alignment / UI polish for trips, tags, trip detail, trip analysis, and trip-budget surfaces. No new trip/tag behavior. *(Android: identity icon chips, neutral status/scope pills, overflow row actions, inline validation banners, and neutral trip-block analysis color.)*
- [x] **P5-9** Architecture, feature, and code quality audit for trips/tags: verify scope rules, analysis correctness, simple navigation/state, and test coverage. *(Audit complete; fixed active tag queries so trip-local tags from archived trips are treated as absent, with regression coverage.)*

**Exit:** trips are fully functional and analyzable.

---

## Phase 5R — Android redesign & design-system consolidation
**Goal:** before switching to Windows, make the Android app feel like the intended product, not accumulated implementation scaffolding.

Read `docs/15-android-redesign-validation.md` before any `P5R-*` work. Phase 5R combines logic validation and UI/UX redesign. Logic issues discovered during UI work must be fixed deeply across the affected model, SQL, repositories, UI, docs, and tests; existing local data is disposable test data.

- [x] **P5R-1** Full Android audit and shell plan: validate navigation hierarchy, app shell, global new-flow behavior, screen titles, top/bottom bars, loading states, empty states, modal/sheet behavior, and reusable component gaps against `docs/07-ui-ux.md`, `docs/08-design-system.md`, shared design tokens, and the implemented screens. *(Audit complete. Navigation: single `AppNavState` value replaces 6 overlapping state vars; `docs/09` §4.2–4.3, §5.1, §11 updated to match as-built repository-orchestration + ViewModel-warning pattern. Shell fixes: removed nested Scaffold double-inset bug (outer Scaffold replaced with `Surface`; each screen now owns its top bar); set `darkTheme = false` as default; replaced full `BorderStroke` with top-only `HorizontalDivider` on the bottom bar; redesigned loading/error screen (`Box + statusBarsPadding`, error icon, `CircularProgressIndicator`). Gestió hub: compact 88 dp tiles with `IconChip` accent colors per destination and `Surface(onClick)` for correct ripple; icons updated to `Sell` (categories), `CalendarMonth` (events), `Tune` (navbar). Modal/sheet strategy: prefer bottom sheets; movement form migration deferred to P5R-3. Deferred items also logged: AutoCategorizer wire-in → P5R-6 (descoped from P5R-3 per audit F1), dead §2.6 payer split line → P5R-3 (audit O5, moved from P5R-5), read-only seam → P5R-8.)*
- [x] **P5R-2** Accounts and categories redesign + logic validation. *(Accounts: `PatrimoniHeroCard` with per-account colored stacked bar + breakdown rows; `HsvColorPicker` + `ColorPickerRow` 20-color palette + custom HSV slot; account card balance red when negative or below threshold; account tap → `ModalBottomSheet` with `MovementListItem` rows + "Anàlisi" link wired to `AnalysisViewModel.setAccountFilter`; `MovementListItem` extracted to `ui/common/`. Categories: collapsible Despeses/Ingressos sections with count badges; `CategoryParentCard` mirroring AccountCard pattern — top row (icon + name + `FixedNatureTag` pill + kind·child-count subtitle + expand chevron + MoreVert menu) + spend block (amount over period label · category-color bar · % of section total); monthly spend falls back to yearly; parent amount rolls up own + all children's spend; section totals use same rollup; `CategoryChildRow` with parent name as subtitle; `movementsForCategory` SQL query + category flow sheet with `MovementListItem` rows + "Anàlisi" link wired to `AnalysisViewModel.setCategoryFilter`.)*
- [x] **P5R-3** Movements and ledger redesign + logic validation: validate expense/income/transfer entry, account defaults, categories, trip/tag attachment, one-time flag, split/refund entry points (incl. shared / §2.5 paid-by-other / §2.6 external flows), filters, detail, and list ergonomics. *(Audit IDs C1, C5, F2, U2, U3, O1, O4, O5, M2-Movements, M6 all resolved — see `docs/16`. `splits.tag_id` via migration 002 (C1); `v_movement_summary` view (O1, M6); 4-type cascade UI (U2); dead split-editor paths removed (O4, O5); F2 WONTFIX-by-design; 99 tests green; manual checklist in `docs/15` §10.)*
- [ ] **P5R-4** Dashboard and analysis redesign + logic validation: validate derived SQL usage, period controls, drill-downs, trips-as-blocks, chart language, budget entry points, and empty states. *(Audit IDs owned: O2, O3, O6, M2-Analysis — see `docs/16`. Dashboard redesign + polish complete: hero card has Ingressos/Despeses (debt-red on dark surface)/Patrimoni KPI row (`HeroKpiBlock` row 1) plus savings `LinearProgressIndicator` (`drawStopIndicator = {}`) + flux net (`netActualCents`) row; 2-column `AccountGrid` (FinanceCard cells: colored icon, name, type label, balance) replaces `PatrimoniCard`; category % toggle; `MovementListItem` for latest movements; quick actions and daily flow chart removed. Analysis redesign is next.)*
- [ ] **P5R-5** People, splits, debts, and settlements redesign + logic validation: verify debt derivation, split-entry UX, settlement warnings, archive behavior, and scoped movement/debt navigation. *(Audit IDs owned: F4, F6-settlement — see `docs/16`. The dead §2.6 payer split line previously tracked here is now audit O5, owned by P5R-3.)*
- [ ] **P5R-6** Recurring, refunds, budgets, and notifications redesign + logic validation: verify date rules, recurring advancement, refund actual math, budget evaluation, alert thresholds, and notification surfaces. *(Audit IDs owned: C3, C4, F3, F6-refund, F1 (AutoCategorizer wiring, descoped from P5R-3) — see `docs/16`. Plus P4 deferred follow-ups: orphan-refund surfacing, budget bar in category detail.)*
- [ ] **P5R-7** Trips and tags redesign + logic validation: revisit trip/tag UX after ledger and analysis settle; verify scope rules, trip analysis, trip budgets, tag locality, and movement integration.
- [ ] **P5R-8** Settings, sync, and read-only states redesign: finish cross-cutting surfaces, sync/read-only placeholders, and app-level settings once the core surfaces are stable. *(Deferred from P5R-1: add a no-op `DeviceAccessState` read in `LedgerShell` and a read-only banner slot in the scaffold so the shell is ready for Phase 7 without wiring real sync logic now.)*
- [ ] **P5R-9** Component consolidation: extract only shared Compose UI pieces that are repeatedly used and clearly stable; remove one-off visual hacks and dead UI helpers. No speculative component library.
- [ ] **P5R-10** Accessibility and density pass: touch targets, text overflow, contrast, Catalan string fit, small-screen behavior, dark mode, and keyboard/focus basics.
- [ ] **P5R-11** Visual regression/manual checklist: document concrete manual checks for the redesigned Android app and run Android build/unit/golden tests.
- [ ] **P5R-12** Architecture, feature, and code quality audit for the redesigned Android UI: verify no behavior drift, no overengineered UI abstractions, and docs/tokens/code consistency before Windows starts.

**Exit:** Android has a coherent, design-system-aligned UI that can act as the visual source for the Windows implementation.

---

## Phase 6 — Windows app
**Goal:** desktop parity + the heavy tools, split so the desktop is never one giant porting phase. Starts after Phase 5R stabilizes the Android visual language.

### Phase 6A — Windows shell & shared DB

- [ ] **P6A-1** UI/UX: design the desktop shell (sidebar nav), app chrome, shared string catalog usage, and large-screen layout rules.
- [ ] **P6A-2** Scaffold the WinUI 3 app and wire Microsoft.Data.Sqlite + Dapper to the existing `shared/` schema/queries.
- [ ] **P6A-3** Data layer: repositories over the canonical SQL only (no EF Core); map core entities.
- [ ] **P6A-4** Port minimal core screens: accounts, categories, movement list/detail, add/edit movement.
- [ ] **P6A-5** Tests: C# golden suite green, schema/query parity check still green.
- [ ] **P6A-6** Design-system alignment / UI polish for the Windows shell and minimal core screens using shared tokens. No new desktop ledger behavior.
- [ ] **P6A-7** Architecture, feature, and code quality audit for the Windows shell/shared-DB slice: verify canonical SQL usage, no ORM query logic, simple MVVM wiring, and parity with Android core behavior.

**Exit:** desktop launches, opens the real DB, and can perform basic ledger work.

### Phase 6B — Desktop parity

- [ ] **P6B-1** Port people, splits, debts, settlements, refunds, recurring, budgets, trips, tags, dashboard, analysis, and settings.
- [ ] **P6B-2** Recurring calendar: month grid, solid (instance exists) vs. greyed (pending), monthly total, month navigation.
- [ ] **P6B-3** Large-screen analysis: desktop-specific layouts for charts, comparisons, filters, and drill-downs.
- [ ] **P6B-4** Tests: desktop repository tests, UI smoke tests, golden green.
- [ ] **P6B-5** Design-system alignment / UI polish for desktop parity screens and large-screen analysis. No new desktop parity behavior.
- [ ] **P6B-6** Architecture, feature, and code quality audit for desktop parity: verify Android/Windows behavior parity, shared-query usage, desktop-only layout simplicity, and test coverage.

**Exit:** desktop can view/edit everything the Android app can.

### Phase 6C — CSV import

- [ ] **P6C-1** UI/UX: design the CSV wizard and import-review screens.
- [ ] **P6C-2** CSV import wizard: upload → auto-detect/confirm column mapping (multi-language header aliases) + pick account → review/edit drafts.
- [ ] **P6C-3** Draft processing: sign→type, auto-cat, dedup flags, per-row edit/exclude, split/refund/settlement bridge where applicable.
- [ ] **P6C-4** Bulk save as one atomic batch with `import_batch_id`.
- [ ] **P6C-5** Import batch review & rollback.
- [ ] **P6C-6** Tests: import-pipeline unit tests, atomic batch tests, dedup/auto-cat vectors green.
- [ ] **P6C-7** Design-system alignment / UI polish for CSV wizard, mapping, review, batch history, and rollback surfaces. No new import behavior.
- [ ] **P6C-8** Architecture, feature, and code quality audit for CSV import: verify atomicity, warning UX, desktop-only boundaries, and importer simplicity.

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
- [ ] **P7-9** Design-system alignment / UI polish for sync, read-only, backup/export/import, conflict/failure, and device-state surfaces. No new sync behavior.
- [ ] **P7-10** Architecture, feature, and code quality audit for synchronization: verify token single-writer semantics, snapshot atomicity, encryption boundaries, read-only UX, and no merge logic.

**Exit:** hand-off and checkpoints work; no data loss across the documented failure cases.

---

## Phase 8 — Hardening & v1 release
**Goal:** ship v1.

**Release-prep items from the audit (see `docs/16-android-audit-findings.md`):** remove the development-only `DataSeeder` path entirely (C2/U1) and confirm the `BuildConfig.DEBUG` gate (applied when the external-split WIP merges back into `main`) is gone; add release `buildTypes` + ProGuard keep-rule smoke, declare `kotlinx-coroutines-core` explicitly, and bump `sqlite-jdbc` (M4); add undo affordance (F5); add `interval_count` bounds + a non-ASCII dedup golden vector (M1). These fold into the relevant P8 tasks below rather than carrying their own IDs.

- [ ] **P8-1** Future forecasting: combine active recurring templates with historical averages / learned patterns; clearly label estimates.
- [ ] **P8-2** Global search & rich filtering across all movements/entities, with clear-all.
- [ ] **P8-3** Settings & system tools: recalculate balances, resync recurring, sync-key management, light/dark theme, app version / update check.
- [ ] **P8-4** Onboarding polish; empty states across screens.
- [ ] **P8-5** Privacy pass: confirm OS at-rest reliance; snapshot-encryption key UX (app lock stays deferred).
- [ ] **P8-6** Performance pass at scale (thousands–tens of thousands of movements): verify indexes, profile analysis queries.
- [ ] **P8-7** UI polish/design-system/accessibility pass on both apps; verify light/dark theming, touch targets, keyboard/focus behavior, and empty states.
- [ ] **P8-8** Packaging & distribution: Android (signed APK / Play Store), Windows (MSIX installer); signing.
- [ ] **P8-9** Final architecture, feature, and code quality audit: `spec-guardian` and `simplicity-guardian` clean, golden suite green, docs ↔ code consistency check.
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
