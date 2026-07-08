# Android Audit Findings — Snapshot 2026-06-27

> Companion to `docs/15-android-redesign-validation.md` and `docs/06-roadmap.md`. This is the **single source of truth** for the audit performed on 2026-06-27: every finding has a stable ID (`C1`, `O4`, `M5`, …) that roadmap tasks and `docs/15` slices reference. Phase 5R is treated as both redesign *and* logic validation/fixing, so audit items are mapped to the slice that owns the affected feature.
>
> **Snapshot scope:** Android app, `shared/` contract, Windows golden/parity harness. Line numbers cited in §4 are accurate as of the snapshot date against the working tree at that time; they will drift as code changes, which is expected — the IDs are stable, the line numbers are not.

---

## 1. Purpose

Record the audit, the maintainer's disposition for each finding, and the roadmap slice that owns the fix. New findings discovered during later slices should be added here with a new ID and a disposition, so the doc stays the living register of known issues.

The audit was performed as a senior-architect review across correctness, functionality, usability, over-engineering, and maintainability. Evidence is cited per finding; no large refactors are proposed unless directly justified by a bug.

---

## 2. Disposition legend

| Tag | Meaning |
|---|---|
| `T1` | Tier 1 — Foundation. Fixed before any feature work, to keep a strong base. |
| `P5R-3` / `P5R-4` / `P5R-5` / `P5R-6` / `P5R-7` / `P5R-15` | Owned by that Phase 5R slice (redesign + logic validation, or — for `P5R-15` — the whole-app stabilization audit run before real user data entered the app). |
| `P8` | Owned by Phase 8 release hardening. |
| `WONTFIX` | Accepted risk; no action planned. |
| `DESCOPE` | Explicitly moved out of its original roadmap note to a different owner. |
| `INVALID` | Audit error — the finding is not a real defect. Recorded so the reasoning is auditable. |

The implementation plan has three tiers:
- **Tier 1 (Foundation):** `T1` items. No feature/visual work.
- **Tier 2 (P5R-3):** movements + shared + external + paid-by-other.
- **Tier 3 (rest of roadmap):** each remaining finding is mapped to its owning phase in §5.

---

## 3. Master findings table

| ID | Title | Severity | Disposition | Owner | Conf. |
|---|---|---|---|---|---|
| **C1** | No DB migration runner | Critical | **RESOLVED** P5R-3 | Migration 002 (`splits.tag_id`) is the first real migration; SQLDelight `.sqm` runner wired; regression test green | High |
| **C2** | `DataSeeder` hard-deletes 9 tables, user-reachable | Critical | **RESOLVED (gate)** P5R-15 | `DataSeeder`/its Settings entry point is now behind `BuildConfig.DEBUG` (does not exist in a release build) plus a destructive `AlertDialog` confirmation in debug builds; full removal still owned by P8 | High |
| **C3** | Recurring confirm non-atomic | Critical | **RESOLVED** P5R-6 | `MovementRepository.runInTransaction` wraps `create` + `advanceCursor` in one TX; rollback test green | High |
| **C4** | Quick-template-create non-atomic | Critical | **RESOLVED** P5R-6 | Same `runInTransaction` wrapper covers `createQuickTemplate` + movement create/update; rollback test green | High |
| **C5** | External-split edit non-atomic | Critical | **RESOLVED** P5R-3 | `SplitRepository.replaceExternalSplit` archives + inserts in one TX; rollback test green | High |
| **C6** | ~~Over-refund CHECK vs "never block"~~ **INVALID — false positive** | — | INVALID | Not a defect; CHECK is correct (see §4) | High |
| **C7** | Manual template edit silently destroys `split_config` | Critical | **RESOLVED** P5R-15 | `RecurringViewModel.onSaveClicked` now carries the existing template's `split_config` forward on edit instead of always nulling it; regression test verified against pre-fix code | High |
| **C8** | Movement payer-switch conflates `movements.id`/`splits.id` | Critical | **RESOLVED** P5R-15 | `MovementFormState` splits the ambiguous `id` into `movementId`/`externalSplitId`; a payer-kind switch now does an explicit archive-old + create-new in one transaction instead of routing to `update`/`replaceExternalSplit` keyed on the wrong table; two regression tests (both directions), both verified against pre-fix code | High |
| **F1** | `AutoCategorizer` built but unwired | Functional | **RESOLVED (suggestion)** P5R-6 | Read-only `AutoCatRuleRepository` + movement-form suggestion chip (tap-to-apply, never auto-applied); rules CRUD UI still not built (tracked separately, e.g. 6C prep) | High |
| **F2** | §2.6 form can't express group bill | Functional | **WONTFIX-by-design** P5R-3 | T2-1 decided: type-4 `total = user share`; group-bill case out of scope v1 | High |
| **F3** | `RecurringAdvancer` while-loop unbounded | Functional | **RESOLVED** P5R-6 | Hard ceiling (10,000 occurrences) added; all call sites already `runCatching`-wrapped so the failure surfaces as a benign error, never a hang | Medium |
| **F4** | `debt_balance` golden vector has one case | Functional | **RESOLVED** P5R-5 | Canonical external, archived-row, partial/multi-settlement, and over-settlement vectors added | Medium |
| **F5** | No undo affordance despite spec §5.9 | Functional | P8 | Release polish | Medium |
| **F6** | Inconsistent warn-vs-block across rules | Functional | **RESOLVED** P5R-5 (settlement) / P5R-6 (refund) | Settlement side confirmed warn-not-block in P5R-5; refund side verified already correct during P5R-6 audit — `MovementsScreen.kt`'s `RefundFormDialog` already shows a dismissible over-refund `InlineBanner` without blocking save (pre-existing, not newly built) | Medium |
| **F8** | Fix/Var Sankey "Estalvi" node computed ad hoc | Functional | **RESOLVED** P5R-15 | Savings node now derives from the canonical `netActualCents`; added an explicit "Sense categoria" leaf so the diagram's flows still sum to income, instead of silently omitting uncategorized expense from the subtraction | Medium |
| **F9** | Quick income-form "Liquidació" toggle has no over-payment warning | Functional | **RESOLVED** P5R-15 | Reuses the same dismissible `settlement_warning_overpay` banner `PeopleScreen`'s `SettlementSheet` already shows, comparing against the selected person's `balanceCents` | Medium |
| **F10** | Tag picker leaks event-type-scoped tags into any trip | Functional | **RESOLVED** P5R-15 | `TagSummary.supportsTrip` now takes the full `TripSummary` and compares `tripType`, not just `tripId`; threaded through every tag-picker call site (movement form, trip/tag filter sheet) | Medium |
| **F11** | Trips-as-blocks toggle silently no-ops on 3 of 5 Analysis tabs | Functional | P5R-9 or later | `groupTripsAsBlocks` is only honored by `actualBreakdown` (used by Resum/Categories); `Comparativa`/`Historic`/`Fix-Var` call `actualByCategory`/`categoryTrends`, which have no `group_trips` param at all — not data-corrupting (no double-count), but the toggle appears globally active while doing nothing on 3 tabs. Deferred: either extend those two queries to honor the param, or scope the toggle/its "active filter" indicator to the tabs that actually respect it | Low |
| **U1** | "Load demo data" destructive trap | Usability | **RESOLVED (gate)** P5R-15 | Same fix as C2 — debug-only + confirmation dialog | High |
| **U2** | External-payer form missing share field | Usability | **RESOLVED** P5R-3 | 4-type cascade UI built; DEBT path = type-4 (someone else paid, user owes); WONTFIX-by-design on separate share field (F2) | High |
| **U3** | No P5R-3 manual checklist yet | Usability | P5R-3 | This slice | High |
| **U4** | No instrumentation/UI tests | Usability | P8 | Optional | Medium |
| **U5** | Spec §5.9 nav drift (Recurring placement) | Usability | T1 | Doc-only | High |
| **U6** | `AppOverlay` doesn't stack — Tags/Budgets from Trip Detail loses Trip Detail | Usability | **RESOLVED** P5R-15 | `AppOverlay.Tags`/`Budgets` gained an optional `returnTo: AppOverlay?`; `back()` restores it instead of clearing to `null` when set; only the Trip-Detail-opened call sites set it, the two direct entry points are unaffected; 4 new `AppNavStateTest` cases | Medium |
| **U7** | Trip-archive failure silently swallowed from Dashboard entry point | Usability | **RESOLVED** P5R-15 | `onBack()` no longer fires before the archive result is known; on failure the error now renders on `TripDetailState.errorMessage` when archived from Trip Detail (previously only the Trips-list-level `errorMessage`, unread by Dashboard) | Medium |
| **O1** | `Movements.sq` 4× SQL duplication | Bloat | **RESOLVED** P5R-3 | `v_movement_summary` view; all 4 queries now `SELECT * FROM v_movement_summary` | High |
| **O2** | `analysis_actual_breakdown` redundancy | Bloat | **RESOLVED** P5R-4 | Refund bug fixed (`> 0` → `<> 0`); category columns carried through `active_groups`; outer joins + 9-column GROUP BY removed; GROUP BY now `(row_kind, category_id, trip_id)`; breakdown test added to `validate_shared_sql.py` | Medium |
| **O3** | `analysis_net_worth` O(N²) self-join | Bloat | **RESOLVED** P5R-4 | Window function `SUM(delta_cents) OVER (ORDER BY bucket)` already in place | Medium |
| **O4** | Dead branch in `MovementRepository.archive` | Bloat | **RESOLVED** P5R-3 | Dead branch removed from `MovementRepository.archive` | High |
| **O5** | Dead person split line written, never read | Bloat | **RESOLVED** P5R-3 | `createExternalPaidByPerson` now writes one user line only; no person line | High |
| **O6** | `validate_shared_sql.py` analysis list drift | Bloat | **RESOLVED** P5R-4 | `analysis_actual_breakdown.sql` added to `ANALYSIS_QUERY_FILES`; breakdown assertion added to `validate_analysis_queries()` | High |
| **M1** | C#↔Kotlin procedural-rule drift risks | Maintainability | P8 | Final pass | Medium |
| **M2** | `MovementsViewModel` / `AnalysisScreen` size | Maintainability | **RESOLVED** P5R-3 / P5R-4 | `MovementDraftBuilder` + `MovementSaveCoordinator` extracted (P5R-3); `AnalysisScreen.kt` split into `AnalysisControls.kt`, `AnalysisSummaryGrid.kt`, `AnalysisWidgets.kt` (P5R-4) | Medium |
| **M3** | No write serialization in ViewModels | Maintainability | WONTFIX | — | Medium |
| **M4** | Coroutines dep / sqlite-jdbc / proguard | Maintainability | P8 | Release | Medium |
| **M5** | Untested multi-write paths | Maintainability | T1 + per-fix | Harness + slices | High |
| **M6** | `external_expense` missing `sl.archived_at` filter | Maintainability | **RESOLVED** P5R-3 | Fixed in `v_movement_summary` external branch | Medium |
| **F7** | `tripActualByTag` silently drops external-split tags | Functional | **RESOLVED** P5R-7 | `v_actual_expense` now exposes `tag_id` on every row; `tripActualByTag` groups on it directly, no `movements` re-join | High |
| **M7** | Dead `AnalysisRepository` methods + unused composable | Maintainability | Deferred | `dailyIncomeVsExpense`, `accountFlowOverTime`, `largestExpenses`, `topMerchants` never called from any UI; `AnalysisTabCommon.kt`'s `NoDataText()` never called. Note: `largestExpenses`/`topMerchants`'s backing queries don't accept `:account_id`/`:category_id` — a future wire-up must add those params or the filter chips would silently not apply | Low |
| **M8** | Duplicated percent-formatting logic across Analysis | Maintainability | Deferred | The same "&lt;1%" floor logic is independently reimplemented in `DashboardScreen.kt`, `AnalysisWidgets.kt`, `CategoriesTab.kt`, `FixVariableTab.kt` — should be one shared `formatPercentLabel(fraction)` | Low |
| **M9** | ~21 orphaned strings in Core Ledger area | Maintainability | Deferred | Left behind by the P5R-2/P5R-3 redesigns: `account_action_*`, `account_detail_*`, `account_flow_*`, `movement_action_edit/archive`, `movement_detail_*`, `category_parent_value` — none referenced anywhere | Low |
| **M10** | Duplicate `ColorDot` composables | Maintainability | Deferred | `FormControls.kt` and `MovementsScreen.kt` each define their own `ColorDot`, using different color-resolution helpers (`parseAvatarColor` vs `categoryColor`) | Low |
| **M11** | Duplicate `displayOrder`+name sort logic | Maintainability | Deferred | `AccountsViewModel.kt` and `CategoriesScreen.kt` independently implement the identical `sortedWith(compareBy { it.displayOrder }.thenBy { it.name.lowercase() })` — no shared extension | Low |
| **M12** | Duplicate `MovementType.label()` in `RecurringScreen` | Maintainability | Deferred | Reimplements (rather than imports) the shared version already in `ui/common/Labels.kt` | Low |
| **M13** | Duplicated split-mapping logic in `SplitRepository` | Maintainability | Deferred | `getForMovement`/`getForMovementById` have verbatim-identical processing beyond which query they call — should share one private mapping helper | Low |
| **M14** | `movementsForCategory` duplicates `v_account_flow` sign logic ad hoc | Maintainability | Deferred | Hand-rolls the income/refund-positive, else-negative `CASE` instead of joining `v_account_flow` (as `accountFlowForAccount` does); currently harmless only because a schema CHECK guarantees transfer/settlement rows can never reach the query's `ELSE` branch — a second, easy-to-miss copy of money-sign logic outside the canonical view | Low |
| **M15** | `docs/04-data-model.md` missing a `v_movement_summary` section | Maintainability | Deferred | The other 7 canonical views each have a dedicated `CREATE VIEW` + prose section in `docs/04`; `v_movement_summary` is only referenced in prose elsewhere. Documentation-completeness only — the view itself is correctly wired and tested everywhere | Low |

---

## 4. Per-finding detail

### Critical

#### C1 — No DB migration runner
- **Evidence:** `android/app/src/main/java/com/gestorfinances/app/data/db/DatabaseDriverFactory.kt:12-24` constructs `AndroidSqliteDriver(schema = GestorDatabase.Schema, …)` with no `migrations = …` vararg. `shared/migrations/001_initial.sql` is only a build-time input (the `syncSharedSqlForSqlDelight` task generates `.sq` from it); no runtime migration runner consumes `0NN_*.sql`. `MetaRepository.load()` reads `schema_version` for display only.
- **Impact:** SQLDelight 2.x throws `IllegalStateException("Inconsistent schema, missing migration?")` the moment `GestorDatabase.Schema.version` rises on an installed DB. The first schema change after release crashes every existing user on next launch; their data is stranded. Violates `AGENTS.md` ("Schema changes are atomic across artifacts … together").
- **Fix:** Add a `Migrations` consumer that reads `shared/migrations/0NN_*.sql` in order; wire it into `AndroidSqliteDriver(…, migrations = …)`. Add a JVM regression test that opens a DB at version N and upgrades to N+1.
- **Disposition note:** Deferred from Tier 1 to **P5R-3**. Tier 1 has no real schema change to migrate (C6, the original trigger, is invalid), so building the runner now would be empty scaffolding against the project's "no speculative abstraction" rule. P5R-3's external-split finality (F2/O5) will be the first real schema change; the runner is built and proven there against a migration that's actually needed. The audit's M5 multi-write test harness (committed in Tier 1) is the testing foundation the migration regression test will build on.
- **P5R-6 gotcha (fixed):** `002_add_splits_tag_id.sql` embeds its own full copy of `v_movement_summary` (needed because v1 databases predate that view and only ever run this migration to get it). When P5R-6 extended `shared/queries/v_movement_summary.sql` with the orphan-refund columns, that embedded copy was initially left stale — any v1→v2 upgrader got the old view recreated and hit `no such column: v_movement_summary.refunds_expense_id` at query time, even though fresh installs (which read the view straight from `shared/schema/schema.sql`) were fine. Fixed by syncing the migration's embedded view text and regenerating `1.sqm`. **Lesson for future view edits:** a change to a `shared/queries/*.sql` view that is also embedded in a `shared/migrations/*.sql` file must update both copies in the same change — `MigrationTest.kt` now asserts on the migrated view's `sqlite_master.sql` text to catch this class of drift going forward.
- **Confidence:** High.

#### C2 — `DataSeeder` hard-deletes 9 tables, user-reachable — **RESOLVED (gate, P5R-15)**
- **Evidence:** `DataSeeder.kt` runs `PRAGMA foreign_keys=OFF` then `DELETE FROM` over `split_lines, splits, movements, templates, tags, trips, people, categories, accounts`. `SettingsViewModel.onSeedDataRequested` called it directly, on a single tap, from the normal production Settings screen, with no confirmation and no build-type gate — the WIP-merge gate this row originally called for was never actually applied once the feature landed on `main`. Rediscovered live by the P5R-15 stabilization audit, independently of this pre-existing entry.
- **Impact:** Any real user who opened Configuració and tapped "Genera dades de prova" — intentionally or by mistake — permanently lost every account, movement, split, trip, tag, category, template, and person, replaced with the hardcoded demo dataset. No undo.
- **Resolution:** `android/app/build.gradle.kts` now generates `BuildConfig.DEBUG` (`buildFeatures.buildConfig = true`). `SettingsScreen.kt` wraps the whole "Depuració i proves" section in `if (BuildConfig.DEBUG && SHOW_DEBUG_SEED_DATA)`, so it does not exist at all in a release build and is hidden in normal debug builds while real data is in use. If that local development guard is temporarily enabled, the button still only opens a confirmation (`onSeedDataClicked` → `seedDataConfirmationPending`), and the seed only runs from `onSeedDataConfirmed`, via a destructive `AlertDialog` mirroring the existing trip/tag archive pattern. `DataSeeder.kt` itself was intentionally left untouched — the fix is entirely at the UI-reachability layer, which is sufficient now that the path is release-unreachable, hidden by default in debug, and confirmation-gated if re-enabled.
- **Remaining scope:** full removal of `DataSeeder`/the seed-data path is still owned by **P8**, per the original disposition — this resolution only closes the "user-reachable, unconfirmed, in a release build" part of the risk.
- **Confidence:** High.

#### C3 — Recurring confirm non-atomic — **RESOLVED (P5R-6)**
- **Evidence:** `RecurringViewModel.onConfirmSaveClicked`: `movementRepository.create(draft, …)` and `templateRepository.advanceCursor(…)` ran in **two separate** `queries.transaction {}` blocks. If the cursor advance threw after the movement insert succeeded, the movement was persisted but the cursor was unchanged; the next refresh re-presented the same prompt and a second tap created a duplicate movement.
- **Impact:** Silent duplicate movements — the worst defect for a ledger. Golden vectors don't catch it (advancer is tested in isolation).
- **Resolution:** `MovementRepository.runInTransaction(block)` — a thin wrapper around the existing `queries.transaction {}` boundary — lets `RecurringViewModel.onConfirmSaveClicked` wrap both the movement `create()` and `templateRepository.advanceCursor()` in one transaction. This relies on SQLDelight's documented nested-transaction/savepoint behavior across `Queries` objects sharing one driver (already proven in this codebase: `MovementRepository.create()` writes into `SplitsQueries` from inside its own `MovementsQueries.transaction{}`). No new coordinator class — `MovementSaveCoordinator`/`MovementDraftBuilder` mentioned in the original fix note were never built after P5R-3, so introducing them now for two call sites would be speculative; the thin wrapper achieves the same one-liner outcome. Verified with `RecurringConfirmAtomicityTest.kt` (forces the movement write to fail via an FK violation, asserts the template's cursor rolls back too, not just the movement).
- **Confidence:** High.

#### C4 — Quick-template-create non-atomic — **RESOLVED (P5R-6)**
- **Evidence:** `MovementsViewModel.attemptSave` called `createQuickTemplate(…)` (ends with `repo.create(draft)` — its own statement) and then `movementRepository.create(draft, …)` (a separate transaction). A code comment claimed the link was atomic; it was not.
- **Impact:** A failure between the two writes left an orphaned active template generating due prompts for a movement that doesn't exist.
- **Resolution:** Same `MovementRepository.runInTransaction` wrapper as C3 — `MovementsViewModel.attemptSave` now wraps `createQuickTemplate(...)` + `movementRepository.create/update(...)` in one transaction. Verified with `QuickTemplateCreateAtomicityTest.kt` (forces the movement write to fail, asserts the quick-created template rolls back too).
- **Confidence:** High.

#### C5 — External-split edit non-atomic
- **Evidence:** `MovementsViewModel.save` external branch calls `repo.archiveExternalSplit(form.id, …)` (TX #1) then `repo.createExternalPaidByPerson(draft.copy(id = newUuid), …)` (TX #2). A mid-flow failure after archive leaves the user's external split silently archived with no replacement.
- **Impact:** Silent data loss of an external split on edit failure.
- **Fix:** Add `SplitRepository.replaceExternalSplit(id, draft)` that archives + inserts in one TX; rewrite the ViewModel to call it. Add an M5 integration test that injects a failure mid-replace and asserts the old split is still active.
- **Confidence:** High.

#### C6 — Over-refund CHECK vs "never block" — **INVALID (false positive)**
- **Evidence cited:** `shared/schema/schema.sql:218` and `shared/migrations/001_initial.sql:218`: `CHECK ( actual_refund_cents IS NULL OR actual_refund_cents <= amount_cents )`. The original audit asserted this conflicts with `AGENTS.md` invariant #7 and spec §3.3b's "warn and allow over-refund."
- **Why the audit was wrong:** The CHECK is a **same-row** invariant — a single refund's analysis reduction (`actual_refund_cents`) cannot exceed its own cash inflow (`amount_cents`). That is always true: the spending-analysis reduction for one refund can never exceed the money that refund deposited. The spec's "warn and allow over-refund" (§3.3b) and `AGENTS.md` invariant #7 refer to a **different, cross-row** relationship — *total refunds vs the original expense* (`Σ refunds.amount_cents ≤ expense.amount_cents`), which `docs/04-data-model.md` §4 explicitly says is app-enforced and warn-and-allow. No valid scenario produces `actual_refund_cents > amount_cents` on one row: a shared-expense refund has `actual < amount` (only the user's share reduces), a partial refund has `actual ≤ amount`, and a goodwill credit that exceeds the original expense caps the analysis reduction at the remaining un-refunded amount (still `≤ amount_cents`), with the cash excess never counting as income (§3.3b).
- **Disposition:** INVALID. The CHECK stays. Removing it would introduce a real data-integrity hole (analysis reduction decoupled from cash flow), not fix one. No code or schema change.
- **Confidence:** High.

#### C7 — Manual template edit silently destroys `split_config` — **RESOLVED (P5R-15)**
- **Evidence:** `RecurringViewModel.onSaveClicked()`'s `TemplateDraft(...)` construction always set `splitConfig = null`, for both the create path and the edit path (`form.id != null`). `TemplateRepository.update()` issues a full-row `UPDATE templates SET ... split_config = :split_config ...`, so every manual edit overwrote whatever split config the template had. `TemplateFormState` had no `splitConfig` field, and `TemplateSummary.toFormState()` never captured it — the value was silently dropped the moment the user opened "Edit template." This is the same bug class already fixed once for a *different* code path: the pattern-detection confirm flow's `DetectedRecurringCandidate.toTemplateDraft(existing)` already carries `existing?.splitConfig` forward correctly, with a comment explaining why; the manual edit form never received the same treatment.
- **Impact:** User creates a shared recurring expense (real split_config), later opens Recurrents > Edit template to fix a typo/amount, saves. `split_config` becomes `NULL`. Every subsequent confirmed occurrence books as a full personal expense — the other person's owed share silently stops being tracked, corrupting `v_person_balance` with zero indication anything went wrong.
- **Resolution:** `onSaveClicked` now looks up the currently-loaded template's `splitConfig` (from `_state.value.templates`, already in memory) and carries it through unchanged on edit, mirroring the already-correct detection-confirm path. `RecurringViewModelTest.editingATemplateWithAnExistingSplitConfigPreservesIt` covers it — verified failing against the pre-fix code (`AssertionError` on the preserved-value assertion) before the fix, passing after.
- **Confidence:** High.

#### C8 — Movement payer-switch conflates `movements.id`/`splits.id` — **RESOLVED (P5R-15)**
- **Evidence:** `MovementSummary.id` was overloaded: for a normal movement it's `movements.id`; for an `external_expense` ("Un altre ha pagat" / DEBT) row it's actually `splits.id` (the `v_movement_summary` external-expense UNION branch selects `s.id`). `MovementFormState.id` was populated straight from `MovementSummary.id` with no memory of which table it came from, and the edit form lets the user flip "Qui ha pagat?" freely without resetting it. Switching DEBT→non-DEBT during an edit routed to `movementRepository.update(draft)` keyed on a `splits.id` — no `movements` row matched, so the `UPDATE` silently affected 0 rows and the edit was lost with no error. Switching non-DEBT→DEBT routed to `SplitRepository.replaceExternalSplit(movementsId, ...)` — the archive-old step matched nothing (wrong table), a brand-new external split was inserted anyway, and the original movement was left live and untouched: two records now counted for one "edited" expense, double-counting account balances, actual spend, and the payer's debt.
- **Impact:** Reachable through the ordinary edit-movement flow (open any shared/for-other/debt expense, change who paid, save) — not an edge case. No confirmation, no error, no prior test coverage of this path.
- **Resolution:** `MovementFormState.id` split into two explicit, mutually-exclusive fields (`movementId`, `externalSplitId`) plus a computed `isNew`. `toFormState()` populates the correct field per the source row's actual kind. `attemptSave` now detects a payer-kind switch and performs an explicit archive-old + create-new sequence wrapped in one `movementRepository.runInTransaction { }` block, instead of routing to `update`/`replaceExternalSplit` keyed on the wrong id. All other `form.id` call sites (trip selection, duplicate-check exclusion, template split-config carry-forward, `MovementFormSheet`'s new/edit checks) updated accordingly. Two regression tests cover both directions, both verified failing against the pre-fix routing logic before the fix and passing after.
- **Confidence:** High.

### Functional

#### F1 — `AutoCategorizer` built but unwired — **RESOLVED (suggestion only, P5R-6)**
- **Evidence:** `AutoCategorizer.kt` existed and was golden-tested, but grep for `auto_cat|autoCat|AutoCatRule|auto_cat_rules` in `android/app/src/main` returned zero files outside the engine + test. No `AutoCatRuleRepository`, no CRUD screen, no integration with `MovementFormSheet`/`MovementsViewModel.save`. The `auto_cat_rules` table was unused at runtime.
- **Impact:** The whole §3.15/§4.4 feature had no user-facing existence. Phase 6C (CSV import) cannot function as specified without it.
- **Resolution:** Read-only `AutoCatRuleRepository.listActive()` (new `AutoCatRules.sq` query, decodes the `conditions` JSON per `shared/schemas/auto_cat_rules.conditions.schema.json`). `MovementsViewModel.normalizeForm` computes a suggestion via `AutoCategorizer.findMatch` against the loaded active rules whenever the form's name/payee/amount/date/account changes, stored as `MovementFormState.suggestedCategoryId`. `MovementFormSheet` shows a `FinanceFilterChip` ("Suggerit: [category]") next to the category picker only when the suggestion differs from the currently selected category; tapping it applies the category — it is never auto-applied. No CRUD UI for rules yet (still out of scope, tracked separately e.g. alongside Phase 6C prep) — the suggestion is inert until a rule is seeded directly in the DB.
- **Confidence:** High.

#### F2 — §2.6 form can't express group bill — **WONTFIX-by-design (P5R-3)**
- **Evidence:** The external-payer branch of `MovementsViewModel.save` constructs `ExternalSplitDraft` with `totalAmountCents = amount` and `userShareCents = amount` (the same value). The People flow now follows the same v1 rule and `SplitRepository` rejects unequal values.
- **Impact:** The user can only record "X paid, I owe the full amount" — the group-bill-with-my-smaller-share scenario is deliberately out of scope for v1.
- **Resolution (P5R-3, T2-1):** Design decision: type-4 ("Deute") stores `total_amount_cents = user_share` always. The group-bill-larger-than-user-share scenario is deliberately out of scope for v1. The 4-type cascade (U2) makes this explicit in the UI: when "Una altra persona" paid, the amount field is labelled as "what you owe." `docs/04-data-model.md` §3 and §5 updated accordingly.
- **Confidence:** High.

#### F3 — `RecurringAdvancer` while-loop unbounded — **RESOLVED (P5R-6)**
- **Evidence:** `RecurringAdvancer.advance`: `while (!next.isAfter(today)) { dueDates += next; next = nextDate(rule, next) }`. No upper bound. If a future change to `nextDate` ever failed to advance (e.g. a `clampDay` regression, or `intervalCount=0` slipping past the `>0` guard), this would loop forever on the IO dispatcher.
- **Impact:** Latent hang freezing background data load.
- **Resolution:** Added `require(dueDates.size < MAX_OCCURRENCES)` (ceiling: 10,000) inside the loop. Every read-path call site already wraps `.advance(...)` in `runCatching { }.getOrNull()`. **Caught in review (spec-guardian):** `RecurringViewModel.onSkipClicked`/`onSkipAllClicked` originally evaluated `advancedOneStep()`/`advancedToToday(today())` as bare function-call arguments — synchronously, on the caller's (UI) thread, *before* `advanceCursorTo`'s `viewModelScope.launch { withContext(ioDispatcher) { runCatching { ... } } }` block even started. A thrown ceiling exception there would have crashed instead of surfacing as an error. Fixed by changing `advanceCursorTo` to take a `computeNextDueDate: () -> String` lambda evaluated *inside* the `runCatching` block, so both call sites (and any future one) are structurally guaranteed to be guarded. `RecurringAdvancerTest.kt` covers the engine-level ceiling; `RecurringViewModelTest.skippingAllOnADecadesStaleTemplateSurfacesErrorInsteadOfCrashing` reproduces the exact crash against the pre-fix code (verified by reverting locally) and asserts the fixed version surfaces `errorMessage` with the cursor left unchanged instead.
- **Confidence:** Medium.

#### F4 — `debt_balance` golden vector has one case
- **Evidence:** `shared/golden/debt_balance.json` contains exactly one mixed scenario. No archived split lines, no multiple/partial settlements, no settlement exceeding debt.
- **Impact:** The differentiator feature (spec goal #4) is the least-tested money rule; the never-block over-settle behavior is invisible to CI.
- **Resolution (P5R-5):** Added coverage for canonical external splits, archived movements/splits/split lines/settlements, partial and multi-direction settlements, and an over-settlement that flips the balance sign. Android and Windows golden harnesses now load optional `archived_at` fields for these rows.
- **Confidence:** Medium.

#### F5 — No undo affordance despite spec §5.9
- **Evidence:** Spec §5.9: "Undo last action: after a create / edit / delete, a brief undo affordance reverts it." No `undo`/`Snackbar` state in any ViewModel. Soft-delete makes this trivial but it isn't implemented.
- **Fix:** Track last-archived record id in ViewModel state; show a `Snackbar` with an undo action that nulls `archived_at`. P8 polish.
- **Confidence:** Medium.

#### F6 — Inconsistent warn-vs-block across "warn" rules — **RESOLVED (both sides)**
- **Evidence:** Manual-entry duplicate detection (`MovementsViewModel.save` `isDuplicate`) implements the never-block warning correctly. But the analogous settlement-exceeds-debt (§3.9) and over-refund (§3.3b) are either schema-hard-blocked (C6) or not surfaced as dismissible banners.
- **Fix:** C6 unblocks the refund side; P5R-5 audits the settlement side. Both must warn, not block.
- **Resolution (P5R-5, settlement side):** Confirmed already correct and re-verified after the `SettlementDialog` → `SettlementSheet` bottom-sheet conversion: `PeopleViewModel.onSettlementSaveClicked` never validates the amount against the outstanding balance (only amount-positive/account/date), and `PeopleScreen.kt`'s `SettlementSheet` shows a dismissible `InlineBanner` (`settlement_warning_overpay`) when the entered amount exceeds `form.outstandingCents`, without blocking save.
- **Resolution (P5R-6, refund side — F6-refund):** Verified during the P5R-6 audit that this was already correct, not newly built: `MovementsScreen.kt`'s refund form computes `isOverRefund = parsedAmount > form.remainingCents` and shows a dismissible `InlineBanner(kind = Alert, text = refund_warning_over)` without blocking save — the same pattern as the settlement side. No code change was needed; this row corrects the earlier "still open" note.
- **Confidence:** Medium.

#### F7 — `tripActualByTag` silently drops external-split tags — **RESOLVED (P5R-7)**
- **Evidence:** `TripAnalysis.sq`'s `tripActualByTag` re-derived the tag by joining `v_actual_expense` back to `movements` on `e.source_id = m.id` and reading `m.tag_id` from that join. For a §2.6 external split ("someone else paid, I owe my share"), `v_actual_expense`'s `source_id` is the `splits.id`, not a `movements.id` — the join found no row, so `tag_id` silently came back `NULL` for every external-split expense, even when the split itself had its own `tag_id` set. The tag simply vanished from the trip's tag breakdown with no error.
- **Impact:** Silent data-correctness bug in a shipped feature (Phase 5): any trip expense recorded via an external split (a common pattern for shared travel costs — "my friend paid the taxi, I owe my share") was invisible in `TripDetailScreen`'s "Per etiqueta" breakdown, undercounting that tag's total and inflating the "Sense etiqueta" bucket instead. No crash, no warning — just a silently wrong number, discovered only during this slice's read of `TripAnalysisRepository`/`TripAnalysis.sq` while validating Slice A's schema change.
- **Resolution:** `shared/queries/v_actual_expense.sql` now exposes `tag_id` directly on every `UNION ALL` branch (movements' own `tag_id`, refunds inherit their linked expense's `tag_id` the same way they already inherit `is_one_time`, and external splits expose their own `s.tag_id`). `TripAnalysis.sq`'s `tripActualByTag` was simplified to group on `e.tag_id` directly — the `LEFT JOIN movements` re-join is gone entirely (a net deletion, not just a fix). Covered by a repository regression test seeding an external split with its own tag on a trip and asserting it now surfaces in `tripActualByTag`.
- **Confidence:** High.

#### F8 — Fix/Var Sankey "Estalvi" node computed ad hoc — **RESOLVED (P5R-15)**
- **Evidence:** `FixVariableTab.kt`'s Sankey computed `savings = (income - fixedCents - variableCents).coerceAtLeast(0L)`. `fixedCents`/`variableCents` come from `actualByCategory` filtered by `category_nature`, whose `LEFT JOIN categories` + `c.nature = :category_nature` filter silently excludes any expense with `category_id IS NULL` — so uncategorized spend was never subtracted, inflating "Estalvi" by exactly that amount whenever any existed. The canonical savings figure elsewhere on the same tab/screen (`AnalysisPeriodTotals.netActualCents`, from `v_actual_expense`/`v_actual_income`) correctly includes uncategorized spend — so the tab could show two disagreeing "how much did I save" numbers.
- **Impact:** Misleading number shown to the user, not a data-corruption bug — but exactly the kind of "wrong number on screen" this audit was scoped to catch.
- **Resolution:** Savings node now derives from `netActualCents` (clamped ≥0). Rather than silently swap the number (which would leave the diagram's flows summing to less than income), an explicit "Sense categoria" leaf/node was added so `fixed + variable + uncategorized + savings = income` stays a true, literal breakdown — chosen after confirming a category's `nature` is `NOT NULL` (the only way total expense can exceed `fixed + variable` is genuine uncategorized spend).
- **Confidence:** Medium.

#### F9 — Quick income-form "Liquidació" toggle has no over-payment warning — **RESOLVED (P5R-15)**
- **Evidence:** The dedicated `SettlementSheet` in `PeopleScreen.kt` correctly warns (dismissible `settlement_warning_overpay` banner) when a settlement exceeds what's owed, per `AGENTS.md` invariant #7. The income form's secondary "Liquidació" quick-entry toggle (`IncomeFormSection.kt`, save path in `MovementsViewModel.kt`) created the same kind of `SETTLEMENT` movement but validated only amount/date/account/person — never compared against the selected person's `balanceCents`, no warning shown. Not data-corrupting (the write itself was a valid settlement row) but a real invariant-#7 gap on a second, less-visible entry point.
- **Resolution:** `IncomeFormSection`/`MovementsViewModel` now compute the selected person's outstanding balance and show the exact same dismissible `settlement_warning_overpay` banner `SettlementSheet` already uses — one warning implementation, two entry points, instead of one correct and one silent.
- **Confidence:** Medium.

#### F10 — Tag picker leaks event-type-scoped tags into any trip — **RESOLVED (P5R-15)**
- **Evidence:** `MovementFormCommon.kt`'s `TagSummary.supportsTrip(tripId: String?)` only checked `this.tripId == null || this.tripId == tripId` — it never compared `this.tripType` against the selected trip's actual `TripType`. A tag scoped to one event type (e.g. "Celebració") was therefore selectable for any trip, including a plain "Viatge" — defeating the point of the 3-way tag-scope model P5R-7 built. No schema `CHECK` is violated (the movement-level constraint is unaffected), but the resulting tag assignment is semantically wrong and pollutes tag/type-based analysis.
- **Resolution:** `supportsTrip` now takes the full `TripSummary` and checks both `tripId` and `tripType` correctly, threaded through every call site: `MovementsViewModel.kt` (trip selection, save validation, normalize-on-load), `MovementsScreen.kt`'s trip/tag filter sheet, `MovementFormCommon.kt`'s `FormTripTagSection`.
- **Confidence:** Medium.

#### F11 — Trips-as-blocks toggle silently no-ops on 3 of 5 Analysis tabs — **DEFERRED**
- **Evidence:** `AnalysisFilterSheet.kt`'s single `groupTripsAsBlocks` switch is presented as screen-wide, and `AnalysisUiState.hasActiveFilters` treats it as globally active. Only `ResumTab`/`CategoriesTab` (via `actualBreakdown(..., groupTrips = ...)`) actually honor it — `ComparativaTab`, `HistoricTab`'s category trends, and `FixVariableTab` all call `actualByCategory`/`categoryTrends`, neither of which has a `group_trips` parameter at all, so trip expenses always show broken out by real category there regardless of the toggle.
- **Impact:** Not data-corrupting — no double-counting, each euro is still counted once under its real category — but a user toggling the switch sees it silently do nothing on 3 of 5 tabs while the "active filter" indicator still reports it as engaged.
- **Disposition:** Deferred per this audit's own triage criteria (not data-corrupting or blocking). Future fix: either extend `actualByCategory`/`categoryTrends` to honor `group_trips` for consistency, or scope the toggle/its filter-active indicator to only the tabs that respect it, and document the intentional scope in `docs/11`.
- **Confidence:** Medium.

### Usability

#### U1 — "Load demo data" destructive trap — **RESOLVED (gate, P5R-15)**
- **Evidence:** See C2 — same underlying fix.
- **Resolution:** Same as C2: `BuildConfig.DEBUG`-gated, plus a destructive confirmation dialog in debug builds.
- **Remaining scope:** full removal still owned by P8, same as C2.
- **Confidence:** High.

#### U2 — External-payer form missing share field
- **Evidence:** See F2. The form offers no "my share" input, forcing a false model where the user owes the whole bill.
- **Fix:** Resolved with F2 in P5R-3 (after the T2-1 design decision).
- **Confidence:** High.

#### U3 — No P5R-3 manual checklist yet
- **Evidence:** `docs/15-android-redesign-validation.md` has step-by-step manual checklists for P5R-1 (§6) and P5R-2 Accounts/Categories (§7, §9). No checklist exists for P5R-3 onward.
- **Fix:** P5R-3 drafts `docs/15` §10 alongside the slice (concrete steps for movement save per type, three split flows, filter round-trips, detail screen, duplicate warning, validation errors). Each subsequent slice drafts its own checklist when it becomes the active task.
- **Confidence:** High.

#### U4 — No instrumentation/UI tests
- **Evidence:** `android/app/src/test/` is JVM-only; no `androidTest` sources. Compose flows (movement form, split editor, dashboard drill-in) have no UI-level regression coverage.
- **Disposition note:** Optional. Manual checklists (U3) are the primary safety net for v1; a minimal Compose smoke per critical flow may be added in P8 if needed.
- **Confidence:** Medium.

#### U5 — Spec §5.9 nav drift
- **Evidence:** `docs/00-Full_Spec.md:690` lists Recurring under Finances (top-level), but `docs/07-ui-ux.md:22` and the implemented shell (`docs/15` §4.1) place Recurring inside Gestió.
- **Fix:** Doc-only. Reconcile spec §5.9 to the implemented IA. T1-4.
- **Confidence:** High.

#### U6 — `AppOverlay` doesn't stack — Tags/Budgets from Trip Detail loses Trip Detail — **RESOLVED (P5R-15)**
- **Evidence:** `AppOverlay` was a single nullable field. `TripDetailScreen`'s "Gestiona etiquetes"/"Pressupost del viatge" actions did `nav = nav.copy(overlay = AppOverlay.Tags/Budgets(...))`, overwriting the current `TripDetail` overlay rather than pushing onto anything; `back()` only ever cleared the overlay straight to the underlying section. Opening Tags/Budgets from Trip Detail then pressing Back skipped Trip Detail entirely, landing on the Trips list or Dashboard instead — directly contradicting `docs/14-trips-tags-ui.md`'s own claim that the overlay "slots into the existing `back()` reducer for free... with zero changes needed," which was only true for the two direct entry points, not this one.
- **Resolution:** `AppOverlay.Tags`/`Budgets` gained an optional `returnTo: AppOverlay? = null`. `back()` restores `returnTo` when set, instead of clearing to `null`. Only the Trip-Detail-opened call sites in `MainActivity.kt` set `returnTo = overlay` (the current `TripDetail`); the pre-existing direct entry points (Trips list, Dashboard, the budgets notification) keep `returnTo = null` and behave exactly as before. `docs/14-trips-tags-ui.md` §2 corrected to describe the mechanism accurately. 4 new `AppNavStateTest` cases cover both the un-nested regression guard and the two documented nested scenarios (Trips list → Trip Detail → Tags → Back → Back; Dashboard → Trip Detail → Budgets → Back → Back).
- **Confidence:** High.

#### U7 — Trip-archive failure silently swallowed from Dashboard entry point — **RESOLVED (P5R-15)**
- **Evidence:** `TripsScreen.kt`'s archive-confirm button fired `viewModel.onArchiveConfirmed(); onBack()` back-to-back — `onBack()` popped the nav immediately while the archive coroutine was still in flight. On failure, `TripsViewModel.onArchiveConfirmed` set the top-level `TripsUiState.errorMessage`, which only `TripsContent` (the Trips list) ever rendered. If Trip Detail was reached from the Dashboard's active-trip card, `onBack()` returned to Dashboard, which never reads that field — an archive failure produced no visible feedback at all.
- **Resolution:** `onArchiveConfirmed` now takes an `onSuccess` callback invoked only after the archive genuinely succeeds; `TripDetailScreen`'s archive dialog passes `onSuccess = onBack`, so navigation only happens once the result is known. On failure while archiving from Trip Detail, the error now renders on `TripDetailState.errorMessage` (visible on whichever screen Trip Detail is still showing) instead of the list-only top-level field. Two new `TripsViewModelTest` cases cover the failure-surfaces-inline path and that `onSuccess` only fires after the coroutine actually completes (guarding against reintroducing the original race).
- **Confidence:** Medium.

### Over-engineering / Bloat

#### O1 — `Movements.sq` 4× SQL duplication
- **Evidence:** The same ~30-line `paid_by_person_name` correlated subquery and the same `external_expense` UNION block are duplicated across `activeMovementSummaries`, `activeMovementSummariesForAccount`, `movementById`, `activeMovementSummariesForCategory` (22 grep matches). Each list row also pays 3 correlated subqueries (`paid_by_person_name`, `user_share_cents`, `LEFT JOIN v_movement_shared`).
- **Impact:** Any fix or filter change must be applied 4× — drift surface. Tied to the shared/external WIP, so owned by P5R-3.
- **Fix:** Define `v_movement_summary` in `shared/queries/`; refactor the four queries onto it. Pulls M6 in with it.
- **Confidence:** High.

#### O2 — `analysis_actual_breakdown` redundancy — **RESOLVED (P5R-4)**
- **Evidence:** `shared/queries/analysis_actual_breakdown.sql` joins `categories` twice (once in `active_groups`, once in the outer SELECT) and GROUPs BY denormalized columns (`category_name, kind, nature, icon, color, trip_name`) functionally dependent on the id. Additional pre-existing bug: `r.expense_cents > 0` in the WHERE clause incorrectly excluded refund rows (which have `amount_cents < 0` in `v_actual_expense`), causing `analysis_actual_breakdown` to differ from `analysis_actual_by_category` for the same data.
- **Resolution (P5R-4):** Refund filter changed to `r.expense_cents <> 0`; category and trip columns (`name`, `kind`, `nature`, `icon`, `color`) now carried through `active_groups`; outer `LEFT JOIN categories` and `LEFT JOIN trips` removed; `GROUP BY` reduced from 9 columns to `(row_kind, category_id, trip_id)` with `MAX()` aggregation for the display columns; `ORDER BY` updated to reference `MAX(ag.trip_name)` and `MAX(ag.category_name)`. Assertion test added to `validate_shared_sql.py`. `Analysis.sq` synced identically.

#### O3 — `analysis_net_worth` O(N²) self-join — **RESOLVED (P5R-4)**
- **Evidence:** `shared/queries/analysis_net_worth_over_time.sql` computed the running balance via a correlated self-join.
- **Resolution:** Window function `SUM(delta_cents) OVER (ORDER BY bucket ASC)` already in place prior to the P5R-4 slice.

#### O4 — Dead branch in `MovementRepository.archive`
- **Evidence:** `MovementRepository.archive` calls `splitQueries?.archiveSplitLines(split_id = id, …)` and `archiveMovementSplit(id = id, …)` passing the **movement's** id as a `splits.id` PK. Movement-backed splits get their own UUID at creation (not the movement id); external splits get a fresh UUID too. The comment "id matches split_id in UNION" is wrong — the UNION selects `s.id` (the split PK), which never equals a movement id. The preceding `archiveMovementSplit(id)` already handles movement-backed splits correctly via `WHERE movement_id = :id`.
- **Fix:** Delete the dead lines. P5R-3 (movements slice).
- **Confidence:** High.

#### O5 — Dead person split line written, never read — **RESOLVED (P5R-3)**
- **Evidence:** `SplitRepository.createExternalPaidByPerson` inserts a `person` split_line with `owed_amount_cents = totalAmountCents − userShareCents`. No canonical view reads `participant_kind='person'` lines for `payer_person_id IS NOT NULL` splits (`v_person_balance.sql` and `v_actual_expense.sql` both filter `participant_kind='user'`). Through the UI (F2) the line is always 0 anyway.
- **Resolution (P5R-3):** `createExternalPaidByPerson` now writes exactly one user line; the dead person line is gone. The single-user app has no use for other participants in a §2.6 split.
- **Confidence:** High.

#### O6 — `validate_shared_sql.py` analysis list drift — **RESOLVED (P5R-4)**
- **Evidence:** `tools/validate_shared_sql.py` listed 8 analysis query files (missing `analysis_actual_breakdown.sql`); `tools/validate_sql_parity.py` enforced 9; `windows/…/SharedSql.cs` had 9.
- **Resolution:** `analysis_actual_breakdown.sql` added to `ANALYSIS_QUERY_FILES`; `validate_analysis_queries()` now includes a full assertion block for this query (verifying category totals with refunds applied against the standard seed fixture).

### Maintainability & Architecture

#### M1 — C#↔Kotlin procedural-rule drift risks
- **Evidence:** The four money rules are hand-translated between `android/…/domain/rules/*.kt` and `windows/GestorFinances.Tests/Domain/Rules/*.cs` (intentional — `shared/` is SQL + golden vectors, not shared code). Golden vectors are the drift safety net. Two latent drift points no vector covers: (a) `RecurringAdvancer.cs` narrows `long IntervalCount → int` for `AddDays/AddMonths/AddYears` where Kotlin keeps `Long` (silent overflow on extreme values; schema only enforces `> 0`); (b) `DuplicateDetector.cs` `ToLowerInvariant()` vs Kotlin `lowercase(Locale.ROOT)` — diverge on non-ASCII.
- **Fix:** Add bounds on `interval_count` at the schema; add a non-ASCII dedup golden vector. P8 final pass.
- **Confidence:** Medium.

#### M2 — `MovementsViewModel` / `AnalysisScreen` size — **RESOLVED (P5R-3 / P5R-4)**
- **Evidence:** `MovementsViewModel.kt` ~1145 lines; `AnalysisScreen.kt` ~1442 lines.
- **Resolution (P5R-3):** `MovementDraftBuilder` (pure) + `MovementSaveCoordinator` (TX orchestration) extracted from `MovementsViewModel`; coordinator accepts a lambda within its transaction so C3/C4 (P5R-6) are one-liners.
- **Resolution (P5R-4):** `AnalysisScreen.kt` split: `AnalysisControls.kt` (period selector, mode/filter controls, custom dates, compare row), `AnalysisSummaryGrid.kt` (summary metric cards), `AnalysisWidgets.kt` (breakdown rows, widget composables, heatmap, trends).
- **Confidence:** Medium.

#### M3 — No write serialization in ViewModels
- **Evidence:** Every mutating action launches an independent `viewModelScope.launch { withContext(ioDispatcher) { … } }`. Two rapid taps on Save each read `_state.value`, build a draft, and write; last writer wins.
- **Disposition note:** WONTFIX. Single-user offline app; risk is low and the cost (Mutex/actor per VM) is not justified now.
- **Confidence:** Medium.

#### M4 — Coroutines dep / sqlite-jdbc / proguard
- **Evidence:** `kotlinx-coroutines-core` not declared as a direct `implementation` (resolves transitively). `sqlite-jdbc 3.41.2.2` (March 2023) for JVM tests diverges from on-device SQLite. No `buildTypes` / `proguard-rules.pro` config — release R8 behavior on SQLDelight/serialization unvalidated.
- **Fix:** Add the explicit coroutines dep; bump `sqlite-jdbc`; add a minimal release build-type + keep-rule smoke before v1 packaging. P8.
- **Confidence:** Medium.

#### M5 — Untested multi-write paths
- **Evidence:** No test exercises: editing an existing external split (C5), the quick-template-create path (C4), or the recurring-confirm atomicity (C3). Repository tests cover happy paths; golden vectors cover pure rules; no integration test covers "movement + split + cursor advance" as a unit.
- **Fix:** T1-3 adds a repository-level integration-test harness that injects a failure between two `queries.transaction {}` calls and asserts no partial state. Each per-fix test (C3/C4/C5) is then written alongside its fix in its owning slice.
- **Confidence:** High.

#### M6 — `external_expense` missing `sl.archived_at` filter
- **Evidence:** The `external_expense` UNION branches in `Movements.sq` filter `s.archived_at IS NULL` on the split but not `sl.archived_at IS NULL` on the user line in the JOIN. `v_actual_expense.sql` and `v_person_balance.sql` filter correctly.
- **Impact:** In practice safe (cascading archive covers it) but not defensive.
- **Fix:** Add `AND sl.archived_at IS NULL` to the JOIN clauses. Resolved with O1 when the queries are refactored onto `v_movement_summary`.
- **Confidence:** Medium.

#### M7–M15 — P5R-15 stabilization audit: minor cleanup, deferred
None of these are data-corrupting or blocking (the bar this audit used to decide what gets fixed immediately vs. logged), so per the audit's own "log lower-severity findings and defer rather than gold-plate" instruction, none were fixed in P5R-15 — see the master table (§3) for the one-line description of each. They're grouped here rather than given full individual write-ups since they're genuinely small: dead repository methods/composable (M7), duplicated percent-formatting (M8), orphaned strings (M9), duplicate `ColorDot` (M10), duplicate sort logic (M11), a duplicate `MovementType.label()` shadowing the shared one (M12), duplicated split-mapping in `SplitRepository` (M13), `movementsForCategory`'s ad hoc sign logic (M14), and a documentation-completeness gap for `v_movement_summary` in `docs/04` (M15). Pick these up opportunistically in a future maintenance pass (e.g. P5R-9's component-consolidation slice, which already covers similar ground) rather than as a dedicated slice.
- **Confidence:** High (all independently confirmed by the audit streams that found them; none required further investigation to classify as low-severity).

---

## 5. Cross-references

- **Tier 1 (Foundation) — shipped:** M5 (multi-write test harness `RepositoryTestSupport` + `MovementRepositoryAtomicityTest`, the atomicity-test pattern the C3/C4/C5 fixes will reuse), U5 (spec §5.9 nav reconciled to the implemented IA). **Deferred out of Tier 1:** C1 → P5R-3 (no real schema change to migrate without C6; building the runner then proves it against the first real migration). **Invalidated:** C6 (false positive; CHECK is correct). **Deferred to WIP-merge:** C2/U1 DEBUG gate (the `DataSeeder` code is WIP-only, not in `main`); full removal stays at P8.
- **Tier 2 (P5R-3) scope:** C1 (migration runner, built with the first real migration), C5, F2, U2, U3, O1, O4, O5, M2 (MovementsViewModel), M6, plus F1 descope (roadmap note only). Starts with the T2-1 design decision (§4 F2/O5).
- **Tier 3 mapping:**
  - **P5R-4** (dashboard + analysis): O2, O3, O6, M2 (AnalysisScreen).
  - **P5R-5** (people/splits/debts): F4, F6 (settlement side), §2.6 person-page flow.
  - **P5R-6** (recurring/refunds/budgets): C3, C4, F1 (wiring — or 6C prep), F3, F6 (refund side), plus P4 deferred items (orphan refunds, budget bar in category detail).
  - **P5R-7** (trips/tags): F7 (found and resolved within this slice — `tripActualByTag` external-split tag bug).
  - **P5R-15** (whole-app stabilization audit, before real user data entered the app): C2/U1 (gate — full removal still P8), C7, C8, F8, F9, F10, U6, U7 all found and resolved within this slice; F11 and M7–M15 found and logged/deferred (not fixed, per the audit's own data-corrupting-or-blocking bar).
  - **P5R-8** (settings/sync): DeviceAccessState placeholder (pre-existing).
  - **P8** (release): C2/U1 full removal, F5, U4 (optional), M4, M1.

When a new issue is discovered during a slice, append it here with a new ID and a disposition before fixing it, so this doc stays the living register.
