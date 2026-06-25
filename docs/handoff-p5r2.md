# Handoff — P5R-2 Accounts done, Categories next

**Created:** 2026-06-25  
**Branch:** main  
**Status:** Accounts redesign complete. Categories redesign pending.

---

## What was completed (accounts)

### New shared components (`ui/common/`)

| File | What it does |
|------|-------------|
| `HsvColorPicker.kt` | 2D saturation/value rect + rainbow hue slider + editable hex field. Seeded from `initialHex`, emits `#RRGGBB` via `onSelect`. Single-gesture `colorPickerDrag` modifier (consumes at Main pass — no scroll conflict). `snapshotFlow + conflate` throttles parent recomposition to one per frame. |
| `EntityPicker.kt` | `ColorPickerRow`: 8 fixed-palette swatches + custom slot that opens `HsvColorPicker`. `IconPickerRow`: flow grid of named icon options. `EntityColorPalette` / `AccountIconPalette` / `CategoryIconPalette` defined here. |
| `MovementListItem.kt` | Public `MovementListItem(entry: AccountFlowEntry)` composable — `IconChip` (type icon + type color) + title/date column + signed delta. Used by `AccountFlowSheet`; MovementsScreen will import it in P5R-3 instead of its private `MovementRow`. |

### Accounts screen (`ui/accounts/AccountsScreen.kt`)

- **`PatrimoniHeroCard`**: net worth hero + per-account colored stacked bar (proportional `weight`) + breakdown rows (8 dp colored dot, name, %, balance).
- **`AccountCard`**: balance red when `currentBalanceCents < 0 || belowThreshold`.
- **`AccountFlowDialog` removed → `AccountFlowSheet`** (ModalBottomSheet):
  - Header: single row — account `IconChip` + name + movement count below (`N moviments`) + `BarChart` icon + "Anàlisi" `TextButton`.
  - Body: `LazyColumn` of `MovementListItem`.
  - "Anàlisi" button calls `onViewAnalysis(accountId, accountName)` and dismisses.

### Analysis integration

- `AnalysisUiState`: added `filterAccountId: String?` + `filterAccountName: String?`.
- `AnalysisViewModel`: added `setAccountFilter(id, name)` + `clearAccountFilter()`.
- `AnalysisScreen`: shows a dismissible `FinanceFilterChip` "Compte: [name]" below the title when `filterAccountName != null`. Clearing it calls `clearAccountFilter()`.
- `MainActivity`: `AccountsScreen` gets `onViewAnalysis = { id, name -> analysisViewModel.setAccountFilter(id, name); showTopLevel(ANALYSIS) }`.

### Strings added (`values/strings.xml`)

```
account_flow_view_analysis  = "Anàlisi"
analysis_account_filter     = "Compte: %1$s"
plurals account_flow_movement_count  one="1 moviment" other="%1$d moviments"
```

---

## What is next: P5R-2 Categories

Categories redesign follows the same pattern as accounts. Suggested order:

### Step C1 — Category spending query

Wire period totals per category into `CategoriesViewModel` so the list can show a spend metric.

- The query already exists: `analysisActualByCategory` in `Analysis.sq` (generated from `shared/queries/analysis_actual_by_category.sql`). It takes `fromDate`, `toDate`, `oneTimeMode`, `categoryNature`.
- `CategoriesViewModel` currently only loads the category tree for CRUD. Add a `periodSpend: Map<String, Long>` (categoryId → actualExpenseCents for the current month) to `CategoriesUiState`, loaded in a coroutine alongside the category list.
- Use `AnalysisRepository.actualBreakdown(...)` (already wired in `AnalysisViewModel`) or call `analysisRepository` directly from `CategoriesViewModel` after injecting it.
- Key files: `CategoriesViewModel.kt`, `CategoriesUiState`, `AnalysisRepository.kt`.

### Step C2 — Categories list redesign

Redesign `CategoriesScreen.kt` to match the account screen quality:

- Two top-level sections: **Despeses** and **Ingressos** (filter by `CategoryKind`).
- Parent categories are collapsible rows; children are indented beneath them.
- Each category row: colored `IconChip` (category icon + color), name, spend metric for current month (from Step C1).
- Empty state per section.
- Same `SectionHeader` + section structure as other redesigned screens.
- The `CategoryIconPalette` (defined in `EntityPicker.kt`) and `EntityColorPalette` are ready to use.

### Step C3 — Category form → bottom sheet

Replace the current `CategoryFormDialog` (AlertDialog) with a `CategoryFormSheet` (ModalBottomSheet), matching `AccountFormSheet` in style:

- Live preview card (icon chip + name, like `AccountPreviewCard`).
- `ColorPickerRow` (already works — same as account form).
- `IconPickerRow` using `CategoryIconPalette`.
- Parent category picker (only show parents of the same kind, no nesting beyond one level).
- Kind chips (Despeses / Ingressos / Ambdós).

### Step C4 — Category tap → movements sheet

Same pattern as `AccountFlowSheet`:

- Tap a category row → `ModalBottomSheet`.
- Header: category `IconChip`, name, movement count, + "Anàlisi" link (navigates to Analysis with category filter — `AnalysisUiState` will need `filterCategoryId`/`filterCategoryName` analogous to the account filter).
- Body: `LazyColumn` of `MovementListItem` — needs movements filtered by category. `MovementRepository` has `accountFlowForAccount`; check whether a `movementsForCategory` query exists or needs to be added to the shared queries.
- Reuses `MovementListItem` from `ui/common/`.

### Step C5 — P5R-2 manual checklist + tests

Add category rows to `docs/15-android-redesign-validation.md` section 7, run `.\gradlew.bat :app:testDebugUnitTest`, verify golden tests pass.

---

## Key patterns to carry forward

| Pattern | Where established | Reuse in categories |
|---------|------------------|-------------------|
| `ColorPickerRow` + `IconPickerRow` | `EntityPicker.kt` | Category form (already uses `CategoryIconPalette`) |
| `MovementListItem` | `ui/common/MovementListItem.kt` | Category flow sheet |
| `ModalBottomSheet` form pattern | `AccountFormSheet` | `CategoryFormSheet` |
| `ModalBottomSheet` flow pattern | `AccountFlowSheet` | `CategoryFlowSheet` |
| `setAccountFilter` / `clearAccountFilter` | `AnalysisViewModel` | Add `setCategoryFilter` / `clearCategoryFilter` analogously |
| `FinanceFilterChip` dismissible in AnalysisScreen | `AnalysisScreen.kt` item block | Same item block for category filter chip |

---

## Build / test commands (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "C:\Personal\Gestor-finances-V7\android"
.\gradlew.bat :app:compileDebugKotlin       # quick compile check
.\gradlew.bat :app:testDebugUnitTest         # unit + golden tests
```
