# Android Phase 5R - Redesign and Logic Validation

> Scope: Phase 5R. This is the working contract for the Android redesign checkpoint before Windows starts. It combines two tasks that must happen together: validating app logic and redesigning the Android UI/UX into a coherent, design-system-aligned product. Read this before starting any `P5R-*` task.
>
> **Audit register:** Known logic/UX/maintainability findings live in `docs/16-android-audit-findings.md` with stable IDs (`C1`, `O4`, `M5`, …). Each slice in §4 lists the IDs it owns; the roadmap mirrors the same IDs in `docs/06-roadmap.md`. Treat slice work as redesign **plus** resolving its owned audit IDs at the deepest correct layer.

---

## 1. Purpose

Phase 5R is not a light polish pass. It is the point where the Android app is allowed to change deeply because the current data is test data and no user data needs compatibility preservation.

The goal is to leave Android as:

- logically correct against `docs/00-Full_Spec.md`, `docs/04-data-model.md`, and the golden vectors;
- simple and local-first, with the existing architecture principles preserved;
- visually coherent and ergonomic enough to become the source for the Windows implementation;
- free of dead fields, placeholder concepts, and UI-only patches that hide model problems.

---

## 2. Non-Negotiable Phase 5R Rules

### Deep Logic Fixes

When a logic decision changes a concept, fix it at the deepest correct layer. Do not patch around it in the UI.

If a field, entity, rule, or workflow is removed or reshaped, update every affected layer in the same change:

- docs and roadmap notes;
- shared schema, canonical SQL, migrations, and schema version when required;
- SQLDelight queries and Android generated bindings;
- repositories, ViewModels, UI state, and Compose screens;
- strings and manual checklists;
- Android tests, shared golden vectors, and Windows harness bindings where affected.

Examples:

- If a field is no longer needed, delete it everywhere instead of leaving it null or hidden.
- If a business rule changes, update the golden vector or canonical SQL first, then app code.
- If a screen reveals that the model is wrong, pause local UI polishing and correct the model.

### Test Data Is Disposable

During Phase 5R, existing local data is treated as disposable test data. Do not preserve bad shapes for compatibility with old test rows. Schema and migration changes still must be documented and consistent because the shared contract matters, but compatibility baggage is not a reason to keep incorrect concepts.

### Preserve Architecture and Simplicity

Do not use the redesign as a reason to add speculative abstractions, broad component libraries, or new dependencies. Keep the app local-first, single-user, euros-only, and Android-primary. Reuse the existing layers and helpers unless a change clearly removes duplication or fixes a real design problem.

### Derived Finance Truth Stays Derived

Balances, debts, account flow, actual income, and actual expense still come from canonical SQL views and shared queries. UI and ViewModels may present the data differently, but they must not become a second source of financial truth.

### Docs and Tests Move With Behavior

Any behavior change discovered during redesign must update the relevant docs and tests in the same slice. A completed slice should include a short manual checklist with expected results.

---

## 3. Working Loop

Logic and UI do not have to be separated cleanly. Some logic problems will only become visible while redesigning a screen.

For each page or entity, use this loop:

1. Read the relevant docs and current implementation.
2. Inspect the current logic, data shape, and tests.
3. Redesign enough of the UI/UX to expose whether the model feels right.
4. When a logic issue appears, stop local UI patching and fix the logic deeply.
5. Resume the UI on top of the corrected model.
6. Run focused tests plus the appropriate broader gates.
7. Record manual checks and any intentionally deferred follow-up.

The rule is not "logic first, UI second." The rule is: logic issues discovered at any point must be resolved deeply, not hidden locally.

---

## 4. Preferred Order

Phase 5R should proceed in dependency order, not purely visual navigation order.

1. **Global audit and shell**
   Validate navigation, top-level information architecture, global add flow, modal/sheet strategy, loading and empty states, and shared component gaps.

   Current Phase 5R shell decision: Android uses **Inici · Moviments · + · Anàlisi · Gestió** as the bottom bar. The centered FAB is always visible after onboarding and starts New movement, or routes to account creation when no account exists. Gestió is a 2-column × 3-row hub for Comptes, Categories, Persones, Esdeveniments, Recurrents, and Configuració. Back from Moviments, Anàlisi, or the Gestió hub returns to Inici; Back from a Gestió child returns to the Gestió hub. Pages inside Gestió keep Gestió highlighted.

2. **Accounts and categories**
   These are foundational reference data. Many later screens depend on account and category identity, color, ordering, and validity.

3. **Movements and ledger**
   This is the core write surface. Validate expense, income, transfer, category, trip, tag, split entry points, refund entry points, one-time flag, and account defaults.

   *Audit IDs owned here (P5R-3):* `C5` (atomic external-split edit), `F2` + `U2` (§2.6 form — group bill vs my share), `O1` (`Movements.sq` 4× duplication → `v_movement_summary`), `O4` (dead `archive` branch), `O5` (dead person split line), `M2` (`MovementsViewModel` split → `MovementDraftBuilder` + `MovementSaveCoordinator`), `M6` (`sl.archived_at` filter). `F1` (AutoCategorizer wiring) is **descoped** to P5R-6 / 6C prep — only the roadmap note is updated here. Start with the T2-1 design decision (`docs/16` §4 F2/O5) before any code.

4. **Dashboard and analysis**
   Once ledger behavior is clean, validate the derived reading surfaces, drill-down paths, trip grouping, budget entry points, and chart language.

   *Audit IDs owned here (P5R-4):* `O2` (`analysis_actual_breakdown` redundancy), `O3` (`analysis_net_worth` O(N²) self-join → window function), `O6` (`validate_shared_sql.py` list drift — do this first), `M2` (`AnalysisScreen` split).

5. **People, splits, debts, and settlements**
   These depend on movement correctness and include the most sensitive derived debt logic.

   *Audit IDs owned here (P5R-5):* `F4` (`debt_balance` golden edge cases), `F6` (settlement-exceeds-debt warn-not-block). The §2.6 person-page side of the flow finalized in P5R-3 is re-validated here end-to-end.

6. **Recurring, refunds, budgets, and notifications**
   These depend on movement, category, date, actual spend, and account-flow behavior.

   *Audit IDs owned here (P5R-6):* `C3` (recurring confirm atomicity), `C4` (quick-template atomicity), `F3` (`RecurringAdvancer` loop bound), `F6` (over-refund warn UX, now unblocked by C6), `F1` (AutoCategorizer form suggestion — descoped from P5R-3). Plus the P4 deferred follow-ups (orphan refunds, budget bar inside category detail).

7. **Trips and tags**
   Revisit after ledger and analysis settle so trip/tag UX matches the final app language and still respects scope rules.

8. **Settings, sync, and read-only states**
   Finish cross-cutting and secondary surfaces after the main product surfaces stabilize.

### Advantages

- Stabilizes dependencies before dependent screens.
- Reduces rework in analysis, budgets, trips, and Windows.
- Makes logic validation easier because each slice has a clear source of truth.

### Disadvantages

- The full final look will emerge gradually, not all at once.
- Some screens may be touched twice when a later slice reveals a shared component gap.
- The user may notice model issues during UI work; the loop above allows that and requires deep fixes.

---

## 5. Slice Definition of Done

Each Phase 5R slice is done only when:

- relevant docs were read and updated if behavior changed;
- logic issues discovered in the slice were fixed deeply across affected layers;
- UI uses `docs/08-design-system.md` and `shared/design/tokens/design-tokens.json` without adding casual tokens;
- Catalan strings are externalized and fit expected mobile widths;
- no dead fields, unused UI state, or one-off helpers remain from removed concepts;
- focused tests pass, plus Android unit/build gates when the slice touches app code;
- manual checks are recorded in the final handoff.

---

## 6. P5R-1 Manual Checklist

Run these after building the app to confirm the shell is correct.

| # | Step | Expected |
|---|------|----------|
| 1 | Cold-start on first launch (no DB) | App shows loading spinner centred on screen; after DB init, bottom bar appears immediately — no blank band at the top |
| 2 | Tap Moviments, Anàlisi, Gestió in the bottom bar | Active item highlights; FAB stays centred in the bar across all tabs; top-edge separator on the bottom bar is a thin line, no full border |
| 3 | Tap Gestió | Hub shows 6 tiles in a 2-column grid; each tile is ~88 dp tall with a coloured icon chip on the left and a title on the right; ripple starts from the tile surface |
| 4 | Tap any Gestió tile | Navigate to the child screen; Back returns to the Gestió hub; Gestió tab stays highlighted |
| 5 | Back from any top-level section | Returns to Inici |
| 6 | Press system Back while on Inici | No navigation action |
| 7 | Cold-start with a simulated DB failure | Error icon + message centred on screen; no blank band at top |
| 8 | App is in light mode | Background is white/surface, not dark |

---

## 7. P5R-2 Accounts Manual Checklist

Run these after building the app to confirm the accounts redesign is correct.

| # | Step | Expected |
|---|------|----------|
| 1 | Open Gestió → Comptes with ≥ 2 accounts | `PatrimoniHeroCard` shows net worth in `displayMedium`; stacked color bar segments proportional to each positive-balance account; per-account rows show colored 8 dp dot, name, %, and balance |
| 2 | One account has a negative balance | Balance is red (`FinanceTheme.colors.debt`) in both the hero breakdown row and the `AccountCard` |
| 3 | One account is below its low-balance threshold | `AccountCard` balance is red |
| 4 | Tap an account card | `ModalBottomSheet` opens; header shows account `IconChip`, name, movement count (e.g. "3 moviments"), and a `BarChart` icon + "Anàlisi" `TextButton` |
| 5 | Sheet has movements | `LazyColumn` of `MovementListItem` rows: type `IconChip`, title, date, signed amount in type color |
| 6 | Tap "Anàlisi" in the sheet header | Sheet dismisses; navigates to Anàlisi tab; "Compte: [name]" `FinanceFilterChip` appears below the section title |
| 7 | Tap the account filter chip in Anàlisi | Chip disappears (filter cleared) |
| 8 | Open account add/edit form | Sheet shows `ColorPickerRow`: 8 swatches + palette slot all in one row; `IconPickerRow`; live `AccountPreviewCard` |
| 9 | Tap the palette swatch | HSV picker expands: 2D saturation/value rect + hue slider + hex `OutlinedTextField` |
| 10 | Drag inside the 2D rect while the form sheet is open | Color updates live; sheet does not scroll or dismiss during the drag |
| 11 | Type a valid hex code in the hex field | Picker and swatch update to that color |

---

## 9. P5R-2 Categories Manual Checklist

Run these after building the app to confirm the categories redesign is correct.

| # | Step | Expected |
|---|------|----------|
| 1 | Open Gestió → Categories | Uncategorized card at top; "Despeses" and "Ingressos" collapsible section headers with count badge; tapping the header collapses/expands the section |
| 2 | A parent category has no spend this month but has yearly spend | Card shows the yearly amount and "Aquest any" period label in the spend block |
| 3 | A parent category has monthly spend | Spend block shows amount + "Aquest mes"; category-color progress bar proportional to section total; percentage of section total at the right |
| 4 | A parent category has subcategories | Secondary line shows e.g. "Despesa · 3 subcategories"; expand chevron visible; tapping chevron shows/hides child rows |
| 5 | A category has `FIXED` nature | `Fixa` pill tag (PushPin icon + text, surfaceVariant background) appears inline beside the name |
| 6 | Parent spend with subcategories | Card amount = parent own spend + all children's spend; section total uses same rollup |
| 7 | Tap a category card | `ModalBottomSheet` opens; header shows category `IconChip`, name, movement count, and "Anàlisi" `TextButton` |
| 8 | Sheet has movements | `LazyColumn` of `MovementListItem` rows with type chip, name, date, signed amount |
| 9 | Tap "Anàlisi" in the category flow sheet | Sheet dismisses; navigates to Anàlisi tab; "Categoria: [name]" `FinanceFilterChip` appears below the title |
| 10 | Tap the category filter chip in Anàlisi | Chip disappears (filter cleared) |
| 11 | Open category add/edit form | Sheet shows `ColorPickerRow` (20 swatches + HSV slot), `IconPickerRow` with `CategoryIconPalette`, live `CategoryPreviewCard`, kind segmented control, nature segmented control, parent selector |
| 12 | Save a category with icon and color set | Icon and color persist after re-opening the edit form |
| 13 | Tap "Anàlisi" in account flow sheet (from AccountsScreen) | Still works — account filter chip appears (regression check) |

---

## 8. Phase 5R Output

By the end of Phase 5R, Android should have:

- a validated app model and shared contract;
- a coherent visual system implemented in Compose;
- reusable components only where repetition has proven them useful;
- no temporary scaffolding that Windows would accidentally copy;
- a manual checklist for the main app flows.
