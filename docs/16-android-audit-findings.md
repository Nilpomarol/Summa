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
| `P5R-3` / `P5R-4` / `P5R-5` / `P5R-6` | Owned by that Phase 5R slice (redesign + logic validation). |
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
| **C2** | `DataSeeder` hard-deletes 9 tables, user-reachable | Critical | P8 | Release-prep (remove); DEBUG gate applied when WIP merges (HEAD has no DataSeeder) | High |
| **C3** | Recurring confirm non-atomic | Critical | P5R-6 | Recurring | High |
| **C4** | Quick-template-create non-atomic | Critical | P5R-6 | Recurring | High |
| **C5** | External-split edit non-atomic | Critical | **RESOLVED** P5R-3 | `SplitRepository.replaceExternalSplit` archives + inserts in one TX; rollback test green | High |
| **C6** | ~~Over-refund CHECK vs "never block"~~ **INVALID — false positive** | — | INVALID | Not a defect; CHECK is correct (see §4) | High |
| **F1** | `AutoCategorizer` built but unwired | Functional | DESCOPE | P5R-6 / 6C prep | High |
| **F2** | §2.6 form can't express group bill | Functional | **WONTFIX-by-design** P5R-3 | T2-1 decided: type-4 `total = user share`; group-bill case out of scope v1 | High |
| **F3** | `RecurringAdvancer` while-loop unbounded | Functional | P5R-6 | Recurring | Medium |
| **F4** | `debt_balance` golden vector has one case | Functional | P5R-5 | Debts | Medium |
| **F5** | No undo affordance despite spec §5.9 | Functional | P8 | Release polish | Medium |
| **F6** | Inconsistent warn-vs-block across rules | Functional | P5R-5 / P5R-6 | Split | Medium |
| **U1** | "Load demo data" destructive trap | Usability | P8 | Release-prep (remove); same WIP-merge timing as C2 | High |
| **U2** | External-payer form missing share field | Usability | **RESOLVED** P5R-3 | 4-type cascade UI built; DEBT path = type-4 (someone else paid, user owes); WONTFIX-by-design on separate share field (F2) | High |
| **U3** | No P5R-3 manual checklist yet | Usability | P5R-3 | This slice | High |
| **U4** | No instrumentation/UI tests | Usability | P8 | Optional | Medium |
| **U5** | Spec §5.9 nav drift (Recurring placement) | Usability | T1 | Doc-only | High |
| **O1** | `Movements.sq` 4× SQL duplication | Bloat | **RESOLVED** P5R-3 | `v_movement_summary` view; all 4 queries now `SELECT * FROM v_movement_summary` | High |
| **O2** | `analysis_actual_breakdown` redundancy | Bloat | P5R-4 | Analysis | Medium |
| **O3** | `analysis_net_worth` O(N²) self-join | Bloat | P5R-4 | Analysis | Medium |
| **O4** | Dead branch in `MovementRepository.archive` | Bloat | **RESOLVED** P5R-3 | Dead branch removed from `MovementRepository.archive` | High |
| **O5** | Dead person split line written, never read | Bloat | **RESOLVED** P5R-3 | `createExternalPaidByPerson` now writes one user line only; no person line | High |
| **O6** | `validate_shared_sql.py` analysis list drift | Bloat | P5R-4 | Analysis | High |
| **M1** | C#↔Kotlin procedural-rule drift risks | Maintainability | P8 | Final pass | Medium |
| **M2** | `MovementsViewModel` / `AnalysisScreen` size | Maintainability | P5R-3 / P5R-4 | Split | Medium |
| **M3** | No write serialization in ViewModels | Maintainability | WONTFIX | — | Medium |
| **M4** | Coroutines dep / sqlite-jdbc / proguard | Maintainability | P8 | Release | Medium |
| **M5** | Untested multi-write paths | Maintainability | T1 + per-fix | Harness + slices | High |
| **M6** | `external_expense` missing `sl.archived_at` filter | Maintainability | **RESOLVED** P5R-3 | Fixed in `v_movement_summary` external branch | Medium |

---

## 4. Per-finding detail

### Critical

#### C1 — No DB migration runner
- **Evidence:** `android/app/src/main/java/com/gestorfinances/app/data/db/DatabaseDriverFactory.kt:12-24` constructs `AndroidSqliteDriver(schema = GestorDatabase.Schema, …)` with no `migrations = …` vararg. `shared/migrations/001_initial.sql` is only a build-time input (the `syncSharedSqlForSqlDelight` task generates `.sq` from it); no runtime migration runner consumes `0NN_*.sql`. `MetaRepository.load()` reads `schema_version` for display only.
- **Impact:** SQLDelight 2.x throws `IllegalStateException("Inconsistent schema, missing migration?")` the moment `GestorDatabase.Schema.version` rises on an installed DB. The first schema change after release crashes every existing user on next launch; their data is stranded. Violates `AGENTS.md` ("Schema changes are atomic across artifacts … together").
- **Fix:** Add a `Migrations` consumer that reads `shared/migrations/0NN_*.sql` in order; wire it into `AndroidSqliteDriver(…, migrations = …)`. Add a JVM regression test that opens a DB at version N and upgrades to N+1.
- **Disposition note:** Deferred from Tier 1 to **P5R-3**. Tier 1 has no real schema change to migrate (C6, the original trigger, is invalid), so building the runner now would be empty scaffolding against the project's "no speculative abstraction" rule. P5R-3's external-split finality (F2/O5) will be the first real schema change; the runner is built and proven there against a migration that's actually needed. The audit's M5 multi-write test harness (committed in Tier 1) is the testing foundation the migration regression test will build on.
- **Confidence:** High.

#### C2 — `DataSeeder` hard-deletes 9 tables, user-reachable
- **Evidence:** `DataSeeder.kt` runs `PRAGMA foreign_keys=OFF` then `DELETE FROM` over `split_lines, splits, movements, templates, tags, trips, people, categories, accounts`. `SettingsViewModel.onSeedDataRequested` calls it directly. This is the only hard-delete surface in the codebase.
- **Impact:** A user who taps "load demo data" on real data irrecoverably destroys everything. Soft-delete/`archived_at` is bypassed.
- **Disposition note:** This is a development seeding helper, not a production feature. It will be **removed** for the release build. **HEAD status:** `DataSeeder` and the `SettingsViewModel.onSeedDataRequested` entry point exist only in the in-progress external-split WIP branch, not in committed `main`; therefore Tier 1 cannot gate code that isn't there. The `BuildConfig.DEBUG` gate is applied at the **WIP-merge moment** (a ~2-line change in `SettingsViewModel`/`SettingsScreen` plus enabling `buildFeatures.buildConfig`), and the path is removed entirely in P8.
- **Fix:** At WIP merge — gate behind `BuildConfig.DEBUG`. At P8 — remove the path entirely.
- **Confidence:** High.

#### C3 — Recurring confirm non-atomic
- **Evidence:** `RecurringViewModel.onConfirmSaveClicked`: `movementRepository.create(draft, …)` and `templateRepository.advanceCursor(…)` run in **two separate** `queries.transaction {}` blocks. If the cursor advance throws after the movement insert succeeds, the movement is persisted but the cursor is unchanged; the next refresh re-presents the same prompt and a second tap creates a duplicate movement.
- **Impact:** Silent duplicate movements — the worst defect for a ledger. Golden vectors don't catch it (advancer is tested in isolation).
- **Fix:** One repository method (`confirmRecurring(template, draft)`) wrapping both writes in a single TX. The Tier 2 `MovementSaveCoordinator` extraction (M2) should make this a one-liner.
- **Confidence:** High.

#### C4 — Quick-template-create non-atomic
- **Evidence:** `MovementsViewModel.save` calls `createQuickTemplate(…)` (ends with `repo.create(draft)` — its own TX) and then `movementRepository.create(draft, …)` (another TX). A code comment claims the link is atomic; it is not.
- **Impact:** A failure between the two TXs leaves an orphaned active template generating due prompts for a movement that doesn't exist.
- **Fix:** One repository method creating template + movement in a single TX.
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

### Functional

#### F1 — `AutoCategorizer` built but unwired
- **Evidence:** `AutoCategorizer.kt` exists and is golden-tested, but grep for `auto_cat|autoCat|AutoCatRule|auto_cat_rules` in `android/app/src/main` returns zero files outside the engine + test. No `AutoCatRuleRepository`, no CRUD screen, no integration with `MovementFormSheet`/`MovementsViewModel.save`. The `auto_cat_rules` table is unused at runtime.
- **Impact:** The whole §3.15/§4.4 feature has no user-facing existence. Phase 6C (CSV import) cannot function as specified without it.
- **Disposition note:** Descope from P5R-3. Engine + golden tests stay as-is. Wiring (suggestion in movement form, no CRUD UI yet) moves to P5R-6, or to a Phase 6C prep line if that lands first. The misleading P5R-3 roadmap note is updated.
- **Confidence:** High.

#### F2 — §2.6 form can't express group bill — **WONTFIX-by-design (P5R-3)**
- **Evidence:** The external-payer branch of `MovementsViewModel.save` constructs `ExternalSplitDraft` with `totalAmountCents = amount` and `userShareCents = amount` (the same value). `MovementFormSheet` exposes only `externalPayerPersonId` — no separate "total group bill" vs "my share" inputs. Spec §2.6 / §3.7 / data-model `splits` comment explicitly model `total_amount_cents > user share`.
- **Impact:** The user can only record "X paid, I owe the full amount" — the group-bill-with-my-smaller-share scenario (the point of §2.6) is unreachable. This also makes the O5 person line always 0 cents through the UI.
- **Resolution (P5R-3, T2-1):** Design decision: type-4 ("Deute") stores `total_amount_cents = user_share` always. The group-bill-larger-than-user-share scenario is deliberately out of scope for v1. The 4-type cascade (U2) makes this explicit in the UI: when "Una altra persona" paid, the amount field is labelled as "what you owe." `docs/04-data-model.md` §3 and §5 updated accordingly.
- **Confidence:** High.

#### F3 — `RecurringAdvancer` while-loop unbounded
- **Evidence:** `RecurringAdvancer.advance`: `while (!next.isAfter(today)) { dueDates += next; next = nextDate(rule, next) }`. No upper bound. If a future change to `nextDate` ever fails to advance (e.g. a `clampDay` regression, or `intervalCount=0` slipping past the `>0` guard), this loops forever on the IO dispatcher.
- **Impact:** Latent hang freezing background data load.
- **Fix:** Add a hard ceiling (e.g. break the loop past 10000 iterations, or `require` a sane upper bound).
- **Confidence:** Medium.

#### F4 — `debt_balance` golden vector has one case
- **Evidence:** `shared/golden/debt_balance.json` contains exactly one mixed scenario. No archived split lines, no multiple/partial settlements, no settlement exceeding debt.
- **Impact:** The differentiator feature (spec goal #4) is the least-tested money rule; the never-block over-settle behavior is invisible to CI.
- **Fix:** Add 3-4 cases (archived line, multi-settlement, partial settlement, over-settle).
- **Confidence:** Medium.

#### F5 — No undo affordance despite spec §5.9
- **Evidence:** Spec §5.9: "Undo last action: after a create / edit / delete, a brief undo affordance reverts it." No `undo`/`Snackbar` state in any ViewModel. Soft-delete makes this trivial but it isn't implemented.
- **Fix:** Track last-archived record id in ViewModel state; show a `Snackbar` with an undo action that nulls `archived_at`. P8 polish.
- **Confidence:** Medium.

#### F6 — Inconsistent warn-vs-block across "warn" rules
- **Evidence:** Manual-entry duplicate detection (`MovementsViewModel.save` `isDuplicate`) implements the never-block warning correctly. But the analogous settlement-exceeds-debt (§3.9) and over-refund (§3.3b) are either schema-hard-blocked (C6) or not surfaced as dismissible banners.
- **Fix:** C6 unblocks the refund side; P5R-5 audits the settlement side. Both must warn, not block.
- **Confidence:** Medium.

### Usability

#### U1 — "Load demo data" destructive trap
- **Evidence:** See C2. Whatever dialog guards the Settings action, the underlying operation is unconditional and unrecoverable at the VM layer.
- **Disposition note:** Dev helper; gated at the WIP-merge moment, removed in P8. Same lifecycle as C2 (the WIP-only-in-HEAD caveat applies).
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

### Over-engineering / Bloat

#### O1 — `Movements.sq` 4× SQL duplication
- **Evidence:** The same ~30-line `paid_by_person_name` correlated subquery and the same `external_expense` UNION block are duplicated across `activeMovementSummaries`, `activeMovementSummariesForAccount`, `movementById`, `activeMovementSummariesForCategory` (22 grep matches). Each list row also pays 3 correlated subqueries (`paid_by_person_name`, `user_share_cents`, `LEFT JOIN v_movement_shared`).
- **Impact:** Any fix or filter change must be applied 4× — drift surface. Tied to the shared/external WIP, so owned by P5R-3.
- **Fix:** Define `v_movement_summary` in `shared/queries/`; refactor the four queries onto it. Pulls M6 in with it.
- **Confidence:** High.

#### O2 — `analysis_actual_breakdown` redundancy
- **Evidence:** `shared/queries/analysis_actual_breakdown.sql` joins `categories` three times (once per UNION branch + once in `active_groups`) and GROUPs BY denormalized columns (`category_name, kind, nature, icon, color, trip_name`) functionally dependent on the id.
- **Fix:** Join once in `active_groups`; GROUP BY ids only. P5R-4.
- **Confidence:** Medium.

#### O3 — `analysis_net_worth` O(N²) self-join
- **Evidence:** `shared/queries/analysis_net_worth_over_time.sql` computes the running balance via a correlated self-join: `SELECT SUM(prior.delta_cents) FROM bucket_flow prior WHERE prior.bucket <= bf.bucket`.
- **Impact:** Quadratic in number of buckets; fine at monthly granularity, slow at daily over years (spec §4.8 calls for daily evolution).
- **Fix:** Window function `SUM(delta_cents) OVER (ORDER BY bucket)` (SQLite ≥ 3.25; minSdk 26 is fine). P5R-4.
- **Confidence:** Medium.

#### O4 — Dead branch in `MovementRepository.archive`
- **Evidence:** `MovementRepository.archive` calls `splitQueries?.archiveSplitLines(split_id = id, …)` and `archiveMovementSplit(id = id, …)` passing the **movement's** id as a `splits.id` PK. Movement-backed splits get their own UUID at creation (not the movement id); external splits get a fresh UUID too. The comment "id matches split_id in UNION" is wrong — the UNION selects `s.id` (the split PK), which never equals a movement id. The preceding `archiveMovementSplit(id)` already handles movement-backed splits correctly via `WHERE movement_id = :id`.
- **Fix:** Delete the dead lines. P5R-3 (movements slice).
- **Confidence:** High.

#### O5 — Dead person split line written, never read — **RESOLVED (P5R-3)**
- **Evidence:** `SplitRepository.createExternalPaidByPerson` inserts a `person` split_line with `owed_amount_cents = totalAmountCents − userShareCents`. No canonical view reads `participant_kind='person'` lines for `payer_person_id IS NOT NULL` splits (`v_person_balance.sql` and `v_actual_expense.sql` both filter `participant_kind='user'`). Through the UI (F2) the line is always 0 anyway.
- **Resolution (P5R-3):** `createExternalPaidByPerson` now writes exactly one user line; the dead person line is gone. The single-user app has no use for other participants in a §2.6 split.
- **Confidence:** High.

#### O6 — `validate_shared_sql.py` analysis list drift
- **Evidence:** `tools/validate_shared_sql.py` lists 8 analysis query files (missing `analysis_actual_breakdown.sql`); `tools/validate_sql_parity.py` enforces 9; `windows/…/SharedSql.cs` has 9.
- **Impact:** The Python runner doesn't execute the breakdown query — the very query the trips-as-blocks toggle (P5-5) depends on. CI is green but coverage is silently incomplete.
- **Fix:** Add the missing file to the Python list; ideally derive all three lists from one source. P5R-4.
- **Confidence:** High.

### Maintainability & Architecture

#### M1 — C#↔Kotlin procedural-rule drift risks
- **Evidence:** The four money rules are hand-translated between `android/…/domain/rules/*.kt` and `windows/GestorFinances.Tests/Domain/Rules/*.cs` (intentional — `shared/` is SQL + golden vectors, not shared code). Golden vectors are the drift safety net. Two latent drift points no vector covers: (a) `RecurringAdvancer.cs` narrows `long IntervalCount → int` for `AddDays/AddMonths/AddYears` where Kotlin keeps `Long` (silent overflow on extreme values; schema only enforces `> 0`); (b) `DuplicateDetector.cs` `ToLowerInvariant()` vs Kotlin `lowercase(Locale.ROOT)` — diverge on non-ASCII.
- **Fix:** Add bounds on `interval_count` at the schema; add a non-ASCII dedup golden vector. P8 final pass.
- **Confidence:** Medium.

#### M2 — `MovementsViewModel` / `AnalysisScreen` size
- **Evidence:** `MovementsViewModel.kt` ~1145 lines mixing validation, draft building, recurring/template logic, external splits, refresh, and 9 constructor deps. `AnalysisScreen.kt` ~1442 lines.
- **Fix:** P5R-3 extracts `MovementDraftBuilder` (pure) + `MovementSaveCoordinator` (TX orchestration) out of `MovementsViewModel`. The coordinator shape must make the deferred C3/C4 fixes (P5R-6) one-liners — i.e. it should accept a lambda that runs within its transaction so `advanceCursor`/`createQuickTemplate` can later be pulled inside. P5R-4 splits `AnalysisScreen`.
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

---

## 5. Cross-references

- **Tier 1 (Foundation) — shipped:** M5 (multi-write test harness `RepositoryTestSupport` + `MovementRepositoryAtomicityTest`, the atomicity-test pattern the C3/C4/C5 fixes will reuse), U5 (spec §5.9 nav reconciled to the implemented IA). **Deferred out of Tier 1:** C1 → P5R-3 (no real schema change to migrate without C6; building the runner then proves it against the first real migration). **Invalidated:** C6 (false positive; CHECK is correct). **Deferred to WIP-merge:** C2/U1 DEBUG gate (the `DataSeeder` code is WIP-only, not in `main`); full removal stays at P8.
- **Tier 2 (P5R-3) scope:** C1 (migration runner, built with the first real migration), C5, F2, U2, U3, O1, O4, O5, M2 (MovementsViewModel), M6, plus F1 descope (roadmap note only). Starts with the T2-1 design decision (§4 F2/O5).
- **Tier 3 mapping:**
  - **P5R-4** (dashboard + analysis): O2, O3, O6, M2 (AnalysisScreen).
  - **P5R-5** (people/splits/debts): F4, F6 (settlement side), §2.6 person-page flow.
  - **P5R-6** (recurring/refunds/budgets): C3, C4, F1 (wiring — or 6C prep), F3, F6 (refund side), plus P4 deferred items (orphan refunds, budget bar in category detail).
  - **P5R-7** (trips/tags): none.
  - **P5R-8** (settings/sync): DeviceAccessState placeholder (pre-existing).
  - **P8** (release): C2/U1 full removal, F5, U4 (optional), M4, M1.

When a new issue is discovered during a slice, append it here with a new ID and a disposition before fixing it, so this doc stays the living register.
