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

   *Audit IDs owned here (P5R-3):* `C1` (migration runner — built here with the first real migration, since external-split finality needs a schema change), `C5` (atomic external-split edit), `F2` + `U2` (§2.6 form — group bill vs my share), `O1` (`Movements.sq` 4× duplication → `v_movement_summary`), `O4` (dead `archive` branch), `O5` (dead person split line), `M2` (`MovementsViewModel` split → `MovementDraftBuilder` + `MovementSaveCoordinator`), `M6` (`sl.archived_at` filter). `F1` (AutoCategorizer wiring) is **descoped** to P5R-6 / 6C prep — only the roadmap note is updated here. Start with the T2-1 design decision (`docs/16` §4 F2/O5) before any code.

4. **Dashboard and analysis**
   Once ledger behavior is clean, validate the derived reading surfaces, non-navigating Analysis aggregates, trip grouping, budget entry points, and chart language.

   *Audit IDs owned here (P5R-4):* `O2` (`analysis_actual_breakdown` redundancy), `O3` (`analysis_net_worth` O(N²) self-join → window function), `O6` (`validate_shared_sql.py` list drift — do this first), `M2` (`AnalysisScreen` split).

   **Dashboard + analysis redesign — complete** (see `docs/15` §11 for manual checklist). Dashboard: combined account+KPI hero card (dark `heroSurface`, account icon chip, 40sp balance); `HeroKpiBlock` two rows — (1) Ingressos / Despeses (`FinanceTheme.colors.debt`, red) / Patrimoni; (2) savings `LinearProgressIndicator` (`drawStopIndicator = {}`) + "Flux net ±Y" (`netActualCents`); 2-column `AccountGrid` (FinanceCard cells: icon, name, type, balance) replaces `PatrimoniCard`; category % toggle; `MovementListItem`. Analysis: `analysis_actual_breakdown.sql` refund bug fixed + redundant joins removed + GROUP BY reduced to ids + test assertion added; expense/negative colors use `FinanceTheme.colors.debt` throughout summary cards and breakdown rows; `AnalysisScreen.kt` split into `AnalysisControls.kt`, `AnalysisSummaryGrid.kt`, `AnalysisWidgets.kt`; aggregate cards, charts, treemaps, rows, and waterfall bars are display-only on the top-level Analysis page.

   **Follow-up — Analysis rebuilt as 5 tabs** (`Resum` · `Categories` · `Comparativa` · `Històric` · `Fix/Var`, each its own file under `ui/analysis/components/`): *Comparativa* gained an in-tab comparison-period picker, 4 KPI cards with real-period-name deltas, a comparative cumulative chart (current solid / comparison desaturated), Notable Swings chips, a dual-bar category list, and the waterfall — `QuadrantCard` removed. *Històric* gained a buffer/runway hero, net-worth trend, savings-rate trend, heatmap, weekday radar, and category trends. *Fix/Var* was rebuilt as a cost-structure dashboard: the buffer card moved to `Històric`; the gauge gained a fixed-%-of-income line with a 50/30/20-style status pill; a new "Recurrents més antics" section lists the oldest active fixed-cost templates with monthly amount, active-since month, and real movement count (`MovementRepository.countsByTemplate()`); the Sankey diagram was redesigned (rounded nodes, app typography, per-node euro amounts, collision-safe labels — name always shown, amount dropped first under pressure); new Fixes/Variables category-breakdown cards show a magnitude bar per category (bar length = share of the group total) plus a header total+percentage; "Cost dels periòdics" got the same bar treatment plus each item's next due date; "Propers periòdics" was removed (now owned by the Recurring/Periòdics page). See `docs/06-roadmap.md` P5R-4 for the full file-level breakdown and the new shared chart components (`SankeyDiagram`, `RadarChart`, `SavingsRateChart`, `Sparkline`, `SpendingHeatmap`, `Treemap`, `WaterfallChart`). Manual checklist updated in `docs/15` §11.

5. **People, splits, debts, and settlements**
   These depend on movement correctness and include the most sensitive derived debt logic.

   *Audit IDs owned here (P5R-5):* `F4` (`debt_balance` golden edge cases), `F6` (settlement-exceeds-debt warn-not-block). The §2.6 person-page side of the flow finalized in P5R-3 is re-validated here end-to-end.

6. **Recurring, refunds, budgets, and notifications**
   These depend on movement, category, date, actual spend, and account-flow behavior.

   *Audit IDs owned here (P5R-6):* `C3` (recurring confirm atomicity), `C4` (quick-template atomicity), `F3` (`RecurringAdvancer` loop bound), `F6` (over-refund warn UX, now unblocked by C6), `F1` (AutoCategorizer form suggestion — descoped from P5R-3). Plus the P4 deferred follow-ups (orphan refunds, budget bar inside category detail).

   **Recurring/refunds/budgets/notifications — complete** (see `docs/06-roadmap.md` P5R-6 and `docs/15` §13 for the manual checklist). All five owned audit IDs resolved: `MovementRepository.runInTransaction` fixed C3/C4 (recurring-confirm and quick-template atomicity, each covered by a dedicated rollback test); `RecurringAdvancer` gained a 10,000-occurrence ceiling (F3); F6-refund was found already correct (the refund form's over-refund banner predates this slice); F1 shipped as a read-only movement-form suggestion chip (`AutoCatRuleRepository` + `AutoCategorizer.findMatch`, tap-to-apply, no CRUD UI yet). Both P4 follow-ups landed: orphan-refund banner + linked-expense label (new `v_movement_summary` columns) and a budget progress bar embedded in `CategoryFlowSheet`. `RecurringScreen.kt`'s and `BudgetsScreen.kt`'s remaining `AlertDialog` forms, plus `MovementsScreen.kt`'s `RefundFormDialog`, converted to `ModalBottomSheet`. Notifications (`SettingsScreen.kt`) needed no redesign — already design-system compliant.

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

## 10. P5R-3 Manual Checklist — Movement form (U3)

Run these after building the app to confirm all four expense types work correctly.

| # | Step | Expected |
|---|------|----------|
| 1 | New expense, "Jo" → "Només per a mi", save | Movement list shows it; account balance drops by amount; no split row in DB |
| 2 | "Jo" → "Compartida", add a person, equal split, save | Movement + split created; person's balance = their share; your actual expense = your share |
| 3 | "Jo" → "Per a un altre", pick a person, save | Movement + split created; **that person owes the full amount** (`v_person_balance`); your `actual` expense = 0; your account balance drops by full amount |
| 4 | "Una altra persona" → pick a person, enter 30€, save | No row is inserted into `movements`; the movement summary shows a synthetic `external_expense` row; **you owe that person 30€** (`v_person_balance`); `actual` expense = 30€; `account_flow` = 0 |
| 5 | Type-4 with trip + tag selected, save | Appears in trip analysis with the correct tag; `external.tripId` and `external.tagId` populated |
| 6 | Edit the type-3 expense (Per a un altre), change amount | The person's debt updates; form reloads as "Per a un altre" (round-trip) |
| 7 | Edit the type-4 expense (Deute), change amount | Your debt updates atomically; **kill the app mid-save and reopen** — old record still intact (C5) |
| 8 | Duplicate type-1 expense (same account/amount/date/name) | Warning banner appears; tapping "Guarda igualment" saves without block |
| 9 | Cold-launch on an existing v1 DB (schema_version=1) | App upgrades to v2 (`splits.tag_id` added); existing data intact; Settings shows `schema_version=2` |

---

## 11. P5R-4 Manual Checklist — Dashboard and Analysis

Run these after building the app to confirm the redesigned dashboard and analysis screens are correct.

### Dashboard

| # | Step | Expected |
|---|------|----------|
| 1 | Open Inici with ≥ 1 account and transactions this month | Hero card shows account name, big balance, and three KPI cells: Ingressos (green), Despeses (red), Patrimoni |
| 2 | Hero card has income and expenses | Second row shows "Estalvi X%" label + savings `LinearProgressIndicator` (no trailing green dot) and "Flux net ±Y" in green or red |
| 3 | No income this month | Savings label shows no percentage; progress bar at 0 |
| 4 | Tap the Ingressos KPI cell | Navigates to Moviments with actual-income filter for the current month |
| 5 | Tap the Despeses KPI cell | Navigates to Moviments with actual-expense filter for the current month |
| 6 | Two or more accounts exist | Account section shows a 2-column grid; each cell shows colored icon, account name, type label, and balance |
| 7 | Account with negative balance | Balance shown in red (`FinanceTheme.colors.debt`) in its grid cell |
| 8 | Tap an account cell | Navigates to Anàlisi with account filter pre-applied |
| 9 | Category section | Despeses/Ingressos toggle chips; each category row has icon, name, % progress bar, amount |
| 10 | Latest movements section | Shows recent movements as `MovementListItem` rows; tapping navigates to detail |

### Analysis

Analysis is 5 tabs — `Resum` · `Categories` · `Comparativa` (Compara) · `Històric` · `Fix/Var` — sharing one header (scope, period, filters). Every top-level aggregate, chart, and row across all 5 tabs is display-only (§6 Aggregate Interaction Contract, `docs/11`): tapping never opens a movement list or trip detail, only filters/controls respond.

| # | Step | Expected |
|---|------|----------|
| 1 | Open Anàlisi | 5 tabs render (`Resum`, `Categories`, `Compara`, `Històric`, `Fix/Var`); shared header (scope, period, filters) stays visible across tab switches |
| 2 | **Resum** — open with data | Summary grid shows Despeses (red) and Ingressos (green) cards top row; Net + Estalvi % bottom row; main chart shows daily (month) or per-bucket (year/all-time) income vs. expense |
| 3 | **Resum** — Net is negative | Net card amount is red |
| 4 | **Categories** — expense-heavy category row | Row shows colored icon, name, signed net amount, a progress bar, and a sparkline; a category with refunds this period nets them out (not gross expense) |
| 5 | **Comparativa** — pick a comparison period | In-tab "Compara amb" picker (scope-matched: month/year/custom date pair); KPI cards show current large + comparison small + a colored delta chip (abs + %, or "Nou"/"-100%" edge cases); comparative chart overlays current (solid) vs. comparison (desaturated ghost), index-aligned |
| 6 | **Comparativa** — step the main period | Comparison period stays put (does not auto-follow); "Restableix" appears once the comparison has been manually touched, and resets it to the period immediately preceding the current main period |
| 7 | **Comparativa** — Despeses/Ingressos toggle | Category comparison list switches mode; each row shows a thick current bar + thin comparison bar + delta %; Notable Swings chips above the list show the top-3 categories by absolute impact; waterfall renders at the bottom, always visible |
| 8 | **Històric** — open with ≥ 2 months of data | Buffer/runway hero card (days of net-worth runway at average expense, or "Sense dades" when unavailable); net-worth trend; savings-rate trend with progress bars; spending heatmap (bounded spans only); weekday radar; category trend lines |
| 9 | **Fix/Var** — open with fixed and variable expenses this period | Gauge shows fixed/variable amounts + % of expenses, plus a one-line fixed-%-of-income status (icon + percentage + a "Dins del marge" or "Supera el 50%" pill, colored by threshold); no separate "Dies de marge" card (moved to `Històric`) |
| 10 | **Fix/Var** — "Recurrents més antics" section | Lists the oldest active fixed-cost templates (by creation date) with monthly amount, "Actiu des de: [mes any]", and real movement count; empty state when none exist |
| 11 | **Fix/Var** — Sankey diagram | Income → Fix/Variable/Estalvi → top categories, each node showing its name and euro amount; non-interactive (no tap/selection); small/crowded nodes still show a name even when the amount is dropped for space |
| 12 | **Fix/Var** — Fixes/Variables category cards | Each category row shows a magnitude bar sized to its share of the group total (bar length = the % shown); section header shows the group's total and % of total expenses |
| 13 | **Fix/Var** — "Cost dels periòdics" | Header shows the total monthly recurring cost; each item shows a magnitude bar, its % share, and its next due date; no "Propers periòdics" section anywhere in the tab |
| 14 | Filter by account (from AccountsScreen "Anàlisi" tap) | `FinanceFilterChip` "Compte: [name]" appears; data scoped to that account across all tabs; tapping chip clears it |

---

## 12. P5R-5 Manual Checklist — People, splits, debts, and settlements

Run these after building the app to confirm the redesigned People screen is correct.

| # | Step | Expected |
|---|------|----------|
| 1 | Open Gestió → Persones with ≥ 6 people, mixed positive/negative/zero balances | Dark hero card shows the net balance headline, two tinted stat chips ("Et deuen" green / "Deus" red), and up to 5 per-person rows (colored initial-avatar, name, signed amount) sorted by absolute balance, with a "+N més" line if more than 5 have a non-zero balance; colors read as bright/legible on the dark surface, not muddy |
| 2 | All people are settled (balance 0) | Hero card shows only the net balance headline — no chips, no person list |
| 3 | Below the hero, each person row is a `FinanceCard` | Round color avatar, name, latest context, balance + direction label, overflow menu |
| 4 | Tap a person row | `PersonDetailSheet` (`ModalBottomSheet`) opens with header (avatar, name, notes, balance/direction), then — only when balance ≠ 0 — a full-width Settle up button on its own (tightly spaced) row, followed by one row with Person paid for me / Copy message / Edit at equal width; when balance is 0, only that second row appears |
| 5 | Debt breakdown below the actions | A single chronological history (most recent first) rendered as `MovementListItem` rows — same visual density as the Moviments list — showing this person's owed share (not the logged-in user's own share) for shared expenses |
| 6 | Tap a history row | Opens the underlying movement detail, or the synthetic external-expense detail when the row has no backing movement (regression check on `onOpenDebtSource`) |
| 7 | A settlement appears in the history | Its row uses the settlement type color/icon, visually distinct from the expense-colored debt rows |
| 8 | Tap "Person paid for me" (from the list row's overflow menu or the detail sheet) | The real Movement form opens as an overlay on top of the current screen (Persones stays visible underneath, no tab switch) pre-seeded with "Qui ha pagat" = Una altra persona and this person already selected as payer; saving returns you to the same screen |
| 9 | Tap Edit (from the sheet or the row's overflow menu), pick a color, save | `PersonFormSheet` `ColorPickerRow` selection persists; the row, hero list, and detail-sheet avatar all update to the new color |
| 10 | Create a new person and leave color unset | Avatar shows a deterministic fallback color (not the app's primary/indigo) and the first-initial letter |
| 11 | Tap Copy message on a person with no settlement history | Clipboard receives every open item plus a "Total pendent" line; snackbar confirms the copy |
| 12 | Tap Copy message after a settlement that fully clears the balance, followed by a new expense | Only the new expense is listed — older, now-settled items are omitted, no "Saldo pendent anterior" line |
| 13 | Tap Copy message after a partial settlement, followed by a new expense | New expense listed, plus a "Saldo pendent anterior" line for the pre-settlement remainder, then the total |
| 14 | Tap Copy message when the balance is exactly 0 | Copies a short settled message instead of an itemized breakdown |
| 15 | Enter a settlement amount greater than the outstanding balance | Dismissible `InlineBanner` warning appears; Save still succeeds (never-block) |
| 16 | Archive a person with a non-zero balance | Confirmation is a centered `AlertDialog` (never a bottom sheet), with a warning banner for the non-zero balance |

---

## 13. P5R-6 Manual Checklist — Recurring, refunds, budgets, and notifications

Run these after building the app to confirm the P5R-6 redesign and logic fixes are correct.

| # | Step | Expected |
|---|------|----------|
| 1 | Open Gestió → Recurrents, tap a due prompt's "Afegeix pagament" | Confirm form opens as a `ModalBottomSheet` (not a centered dialog); amount + next-due date fields, Cancel/Save buttons at the bottom |
| 2 | Confirm a due occurrence | Movement is created and the template's cursor advances in one step; the prompt disappears; re-opening Recurrents does not re-present the same occurrence |
| 3 | Open Gestió → Recurrents → "+" to add a template | Template form opens as a `ModalBottomSheet` with the full scrollable field set (type, amount, account, category, schedule, status, notes); Cancel/Save row at the bottom |
| 4 | From a movement form, toggle "Fes-ho recurrent" and save a new expense | A new active template is created alongside the movement; both appear (template in Recurrents, movement in Moviments) |
| 5 | Open a refund form (from an expense's detail → "Afegeix reemborsament") | Form opens as a `ModalBottomSheet` (not a centered dialog) |
| 6 | Enter a refund amount greater than the remaining refundable amount | Dismissible `InlineBanner` (`refund_warning_over`) appears; Save still succeeds (never-block) — this behavior is unchanged, now on the bottom-sheet form |
| 7 | Archive the original expense of an existing refund, then open the refund's own detail | A "Retorn de: [expense name]" grid item still shows the linked expense's name; a dismissible orphan-refund `InlineBanner` appears |
| 8 | Open the same refund's detail when its original expense is NOT archived | No orphan banner; "Retorn de" label still shows the expense name |
| 9 | Open Gestió → Categories, tap a category with an active category-scope budget for the current month | `CategoryFlowSheet` shows a budget progress bar (green/amber/red) and a "spent / limit" + remaining-or-over label between the header and the movement list |
| 10 | Same category, but with no active budget | No budget block shown — just the header and movement list, unchanged from before |
| 11 | Open Gestió → Pressupostos, tap "+" or an existing budget | Form opens as a `ModalBottomSheet` (scope segmented control, category/trip chips, limit/threshold/start-date fields, Cancel/Save row) |
| 12 | Open Configuració → notification preferences | Unchanged from before this slice (already design-system compliant): `FinanceCard` sections with `Switch` toggles and lead-time input |
| 13 | In a movement form, type a name/payee that matches a manually-seeded `auto_cat_rules` row (no CRUD UI — seed directly in the DB for this check) | A `FinanceFilterChip` "Suggerit: [category]" appears next to the category picker once name/amount/date/account are filled in |
| 14 | Tap the suggestion chip | The suggested category is applied to the form; the chip disappears (selected category now matches the suggestion) |
| 15 | Manually pick a different category instead of tapping the suggestion | The suggestion chip disappears once the selected category differs from the match — never overridden automatically |

---

## 8. Phase 5R Output

By the end of Phase 5R, Android should have:

- a validated app model and shared contract;
- a coherent visual system implemented in Compose;
- reusable components only where repetition has proven them useful;
- no temporary scaffolding that Windows would accidentally copy;
- a manual checklist for the main app flows.
