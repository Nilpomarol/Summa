# Redesign Notes

Status: planning context, not a current requirement. These are observations from a usability and simplicity audit of the Android app (September 2026), grouped by the screen they affect. When a screen is redesigned, read its section and decide what still applies. Nothing here is a task, an order, or a commitment.

Code references name files and functions rather than lines, because lines drift.

## Cross-cutting

**Sub-pages that are state swaps rather than navigation destinations.** Person detail, the account ledger, the settlement form, the account/contribution forms, and the recurring template form replace a page's content through ViewModel state, each with its own `BackHandler`. Any action that leaves one of them has to dismiss it first, so Back returns to the list rather than to the person or account the user was on (person Edit and "paid by person", account ledger → Analysis). A redesign that settles the page model — real routes versus in-page swaps — resolves this class of problem everywhere at once.

**"Movements for X" exists in several forms.** A category flow sheet, the account ledger page, the trip Moviments tab, the person history list, and the recurring history sheet each list movements with their own layout and behaviour. The global Movements list already filters by account, category, trip, and period. The account ledger has a genuine reason to differ: it leads with the account's signed physical delta so it reconciles with the balance (see `design.md`). The others are candidates for one list with a filter applied.

**An open product question shapes several screens:**

- Should Analysis drill into the movements behind its numbers? Answering yes keeps several unused filter fields alive (see Movements list).

## Global navigation (`MainActivity.kt`, `ui/navigation`)

- `openSection` clears whatever is on top of Inici, and `openManagement` pops everything down to the nearest root page. Contextual flows that call these lose their origin. Prefer opening sheets above the current page, as "add expense paid by person" and the debt-source detail already do.
- The bottom bar hides while the movement detail sheet is open, which resizes the page behind the sheet.
- Startup: `DatabaseState.Ready.meta` and its message are never shown, and `Failed.message` is discarded with no Retry.

## Home (`ui/dashboard`)

- The selected account lives in a ViewModel that is recreated on every visit, so tapping Inici or returning from another tab resets it to the default account.
- The balance changes with the selected account, but recent movements stay global, so the page mixes two contexts.
- Tapping the balance opens that account's Analysis, with no visual or accessibility hint. It also isn't the account ledger, which is probably what users expect; that is reachable only through Més → Comptes.
- Budget alerts are introduced by a `TextButton` acting as a section header.

## Movements list (`MovementsScreen.kt`, `MovementsViewModel.kt`)

- The list is flat, with a date on each row and no day grouping, and filtered results show no total.
- `MovementFilters.paidByPersonOnly`, `categoryNature`, `oneTimeMode`, and drill-down `tagId` are never set by any screen. They were built for drill-downs that no longer exist. Delete them or reconnect them, depending on the Analysis drill-down decision.
- Tags are reachable only through free-text search (it matches `tagName`).
- Recurring is a plain text button in the header.

## Movement form (`MovementFormScreen.kt`, `MovementFormCommon.kt`, `ExpenseFormSection.kt`, `MovementEditor.kt`)

- **Chained save warnings.** Saving can raise split removal, then recurrence stop, then duplicate, one after another, each relabelling the same Save button.
- **Recurrence has two creation paths with different depth.** The movement form's recurrence toggle offers frequency only; the Recurring page's template form offers the full schedule.

## Movement detail (`MovementDetailScreen.kt`, `MovementSheets.kt`)

- Editing a contribution leaves the sheet and navigates to Accounts, while every other type edits in place.
- The refund form is a nested swap inside the detail sheet, with its own `BackHandler`.

## Analysis (`ui/analysis`)

- It is a dead end. Category and trip rows can't be tapped, and the screen receives no navigation callbacks, so users can see a total but not the movements behind it.

## People (`ui/people`)

- Person detail and the settlement form are in-page swaps (see Cross-cutting). "Add paid by person" and "Edit" dismiss the detail first, so after saving you land on the list rather than the person.
- The detail has one primary action (settle up) and three equal secondary buttons (paid by person, copy message, edit) in a row, which is tight at narrow widths and with large text.

## Accounts and goals (`ui/accounts`, `ui/goals`)

- The account ledger is an in-page swap. Ledger → Analysis dismisses it, so Back returns to the account list.
- Accounts hosts the account form, contribution/withdrawal form, ledger, reordering, and the entry to goals. It is the densest management page.

## Trips and tags (`ui/trips`, `ui/tags`)

- Trip detail now has two tabs, Resum and Moviments. The per-tag and per-day breakdowns were removed as unreachable, but `TripAnalysisRepository.actualByTag` and `actualByDayByCategory` (with their `.sq` queries and tests) remain if tag or day analysis returns.
- Tags are managed from the Trips header and have no other read surface.

## Recurring (`ui/recurring`)

- "Detect recurring" is an unlabelled sparkle icon in the header.
- The Add button comes after every template section, so users scroll to the bottom to add one.
- The due-reminders sheet appears above any page once per cold start.
- Frequency and status labels are defined three times: `label()` in `RecurringScreen`, `formLabel()` in `RecurringFormScreen`, and `cadenceLabel()` in `MovementFormCommon`.
- The template form reuses the income-ownership section but otherwise duplicates much of the movement form's structure.

## Budgets (`ui/budgets`)

- Two entry composables serve two routes: `BudgetsPage` for global budgets, which also mounts a Categories ViewModel for its category flow sheet, and `BudgetsScreen` for trip budgets.
