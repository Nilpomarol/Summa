# Data-Entry UX Remediation — P5R-17 Working Contract

> Companion to `docs/15-android-redesign-validation.md` (whose Phase 5R rules all still apply), `docs/16-android-audit-findings.md` (findings register — this slice owns IDs **C9, F12–F14, U8–U16, M16**), and `docs/06-roadmap.md` (`P5R-17`). Read this before starting any WP below.
>
> **Source:** dedicated UX audit of every add/edit flow (2026-07-10): movement form + detail, accounts, categories, recurring templates + due prompts, trips/events, tags, people/settlements, refunds, budgets, and the navigation shell. Evidence per finding is summarized in `docs/16` §4; this doc is the remediation plan.

---

## Status audit — 2026-07-16

`P5R-17` is **partially complete**, not unstarted and not ready to close. Commit `2cf829e` implemented WP1–WP5 and WP6a. The 2026-07-16 controlled-data run manually closed WP1 and WP3–WP6a; WP2 C17-03 remains open because its exact invalid-state setup is unreachable from the current UI. The current `main` passes `:app:testDebugUnitTest` and `:app:assembleDebug`, WP7–WP11 are not implemented, and optional WP6b remains deliberately deferred.

| Package | Implementation | Automated evidence | Manual closure | Status |
|---|---|---|---|---|
| WP1 — movement data-loss guards | [x] | [x] | [x] | Implemented and manually closed (C17-01/C17-02) |
| WP2 — field-level validation | [x] | [x] | [ ] | Automated coverage complete; C17-03 remains open because the current form has no clear affordance for the required account/date invalid-state setup |
| WP3 — recurring truth | [x] | [x] | [x] | Implemented and manually closed (C17-04) |
| WP4 — budget discovery/safe archive | [x] | [x] | [x] | Implemented and manually closed (C17-05) |
| WP5 — unified date controls | [x] | [x] | [x] | Implemented and manually closed (C17-06) |
| WP6a — no-edit explanation | [x] | [x] | [x] | Implemented and manually closed (C17-07) |
| WP6b — settlement/refund editing | [ ] | [ ] | [ ] | Optional, deferred; does not block P5R-17 |
| WP7 — consistency/string pass | [ ] | [ ] | [ ] | Not started |
| WP8 — template cleanup/split visibility | [ ] | [ ] | [ ] | Not started |
| WP9 — split remainder helper | [ ] | [ ] | [ ] | Not started |
| WP10 — inline category creation | [ ] | [ ] | [ ] | Not started |
| WP11 — account reorder mode | [ ] | [ ] | [ ] | Not started |

**Immediate closure gate:** before new implementation, execute and record the existing manual checks for WP1–WP6a using controlled data. This is verification only; do not redesign those flows during the gate. Any failure reopens the owning WP. The 2026-07-16 run closed WP1 and WP3–WP6a; WP2 remains open only for C17-03's unreachable exact invalid-state setup.

## 1. Purpose & scope

Fix the data-entry UX problems found in the audit **without changing any money rule**. The problems fall into three groups:

1. **Silent data loss inside the movement form** (C9) and features that silently don't work (F12 recurrence edits, F13 budget discovery).
2. **Feedback rendered where the user isn't looking** (U8 validation, U10 unconfirmed budget archive).
3. **Inconsistency between flows that should feel identical** (U9 dates, U12 interaction grammar, U13 template form, M16 hardcoded strings) plus friction removers (U11, U14, U15, U16, F14).

Out of scope: anything touching split rounding, recurring advancement, dedup, auto-cat, or refund math; the golden vectors; rules-CRUD UI for auto-categorization; full split *editing* in the template form (F14 covers visibility + removal only — full editing stays the P4-2 deferred item).

**Data-care note:** this slice runs after `P5R-16` — local data is **real**, not disposable test data. No WP plans a schema change; the one optional item that might touch the write path for existing row types (WP6b) requires `schema-steward` sign-off before any code.

---

## 2. Global conventions (every WP)

- **Branching:** one branch per WP off `main` (`p5r17/wp1-form-state`, …), small scoped commits referencing `P5R-17`, the WP, and the finding IDs. Do not commit/push without being asked.
- **Strings:** all new user-facing text in `android/app/src/main/res/values/strings.xml`, Catalan, existing key naming (`movement_*`, `template_*`, `budget_*`, `common_*`). Never hardcode (M16 exists because of exactly this).
- **Build/test:** from `android/`: `.\gradlew.bat :app:assembleDebug` and `.\gradlew.bat :app:testDebugUnitTest` (JDK 17+ / Android Studio JBR).
- **Definition of done (per WP):** build + full unit suite (incl. golden) green · new regression tests green (destructive-bug fixes must be verified failing against pre-fix code first, per the P5R-15 precedent) · WP manual checks executed and listed in the handoff · `spec-guardian` clean · `simplicity-guardian` clean · affected docs updated in the same branch.
- **No new dependencies.** No speculative abstractions — each WP names the exact components it may create; anything beyond that needs justification in the handoff.
- **Docs that move with behavior:** WP3/WP4/WP6/WP7 update `docs/13`, `docs/12`, and/or `docs/08` as listed in each WP; every WP appends its manual checklist to `docs/15` (continuing the §10+/checklist convention) or references this doc's checklist section.

---

## 3. Work packages

### WP1 — Movement form: stop silent data loss — owns **C9** — CRITICAL

**Status:** implementation and automated tests complete (`2cf829e`); manual closure complete for C17-01/C17-02.

**Goal:** switching type/payer, or re-tapping a selected segment, never destroys form input; destructive edits warn (never block, never silently proceed).

**Files:** `ui/common/DesignComponents.kt` (`SegmentedControl`, `LabeledSegmentedControl`) · `ui/movements/MovementsViewModel.kt` (`normalizeForm`, `onSharedToggled`, `attemptSave`, `MovementFormState`) · `ui/movements/ExpenseFormSection.kt`, `MovementFormScreen.kt` · `MovementsViewModelTest.kt`.

**Steps:**
1. **Segment re-tap guard.** In both segmented controls, skip the callback when the tapped option is already selected (`if (!isSelected) onSelect(option)`). This alone fixes "tap *Jo* while SHARED wipes the split", because `ExpenseFormSection`'s level-1 handler maps any non-DEBT tap to `PERSONAL`.
2. **Non-destructive normalization.** `MovementFormState` retains all entered values across type/kind switches for the life of the form. `normalizeForm` keeps: error clearing, suggestion computation, category/tag *compatibility* nulling. It stops nulling `splitEditor`, `forOtherPersonId`, `settlementPersonId`, `payee`, `notes`, `isOneTime`, `accountId` on type change, and stops latching `removeExistingSplit`. The UI already renders only type-relevant sections, so retained-but-hidden state is invisible; incompatible fields are stripped at **draft-build time** in `attemptSave` (most already are via `takeIf { form.type == … }` — extend to `splitWrite`: `Remove` only when the *final* kind cannot carry the split **and** the user explicitly disabled sharing, or per step 3's accepted warning).
3. **Pre-save destructive-edit warning.** Add `pendingDataLossWarning` (mirroring `duplicateWarning`). In `attemptSave`, when `!form.isNew` and the save would (a) remove a stored split, (b) archive-and-recreate across the movement/external-split boundary (payer switch — the C8 path), or (c) drop payee/notes/recurrence in the external-split conversion: set the flag and return. `MovementFormScreen` renders an `InlineBanner(Alert)` **adjacent to the save button** naming what will be lost; the button becomes "Guarda igualment" (same mechanism as `duplicateWarning`, e.g. an `acceptDataLoss` param on the save path). Strings: `movement_warning_split_removed`, `movement_warning_payer_switch_drops_fields`.
4. *(Optional, simplicity-guardian judges)* remember `lastExpenseCategoryId`/`lastIncomeCategoryId` so an accidental type round-trip restores the category.
5. **Regression tests** (verify failing against pre-fix code where the bug is destructive):
   - shared expense → TRANSFER → EXPENSE → save ⇒ split preserved (never `Remove`);
   - edit shared expense → switch payer to "Un altre" → save ⇒ first save warns, accepted save archives+creates;
   - explicit un-share → save ⇒ warns, accepted ⇒ `Remove`.

**Manual checks:** edit an existing 3-person shared expense; tap every type segment and back; save; split intact in detail. Explicitly un-share → warning → accept → split removed.

---

### WP2 — Field-level validation & error placement — owns **U8** — CRITICAL

**Status:** implementation and automated tests complete (`2cf829e`); C17-03 manual closure remains open because the exact invalid-state setup is unreachable from the current UI.

**Goal:** validation feedback appears in the viewport and identifies the offending field.

**Files:** every form ViewModel (`Movements`, `Accounts`, `Categories`, `Recurring`, `Trips`, `Tags`, `Budgets`, `People` incl. settlement/refund states) · matching form screens · `ui/movements/FormControls.kt` (`FieldFrame`) · one small helper in `ui/common/`.

**Steps:**
1. **Error-field identity.** Add an `errorField` key alongside the existing single `errorRes` in each form state (per-form enum or shared string key). Each validation branch already produces one error — have it also set the field key. **Keep the single-error model**; do not build a multi-error framework.
2. **Field rendering.** `OutlinedTextField`s: `isError` + `supportingText` when matching. `FieldFrame`-based controls (`FormSelect`, `FormDatePicker`): add `isError: Boolean` → error border + caption line.
3. **Scroll-to-error.** One helper in `ui/common` (`BringIntoViewRequester` + `LaunchedEffect`, e.g. `Modifier.scrollToWhen(condition)`), applied to the erroring field. Top-of-form text remains **only** for `errorMessage` (repository failures).
4. **Duplicate-warning placement.** Move the duplicate banner adjacent to the save button (shared slot with WP1's data-loss banner — build once); add a secondary `TextButton("Revisa")` that clears the warning and restores the normal Save label.
5. **Rollout order:** movement form → account → category → template → trip → tag → budget → person → settlement → refund. Mechanical; keep diffs minimal.
6. **Tests:** ViewModel assertions on `(errorRes, errorField)` for every movement-form validation branch; smoke coverage for the rest.

**Manual checks:** long expense form with split editor open, clear the account, save from the bottom → view scrolls to the highlighted account select with its message.

**Depends on:** WP1 (shared save-path/banner area).

---

### WP3 — Recurring truth in the movement form — owns **F12** — CRITICAL

**Status:** implementation and automated tests complete (`2cf829e`); manual closure complete for C17-04.

**Goal:** the form never misrepresents or silently ignores recurrence.

**Files:** `ui/movements/MovementsViewModel.kt` (`onEditClicked`, `toFormState`, `attemptSave`, `createQuickTemplate`) · `ui/movements/MovementFormCommon.kt` (`FormRecurringSection`) · section files · `docs/13-recurring-refunds-budgets-ui.md`.

**Steps:**
1. **Load real template data on edit.** When `movement.templateId != null`, fetch the template alongside the split draft and thread `recurringFrequency` + a new `templateStatus` into the form state (`templateRepository` is already a constructor dep).
2. **Read-only recurrence when linked.** `FormRecurringSection` gains a linked mode: instead of the frequency select, a muted line "Recurrent · {freqüència} — gestionat a Recurrents" (string `movement_recurring_managed`). *Decision recorded:* no navigation link from the form to the template editor in this slice — a label is honest and sufficient; cross-overlay nav would add real complexity.
3. **Toggle-off semantics.** Turning the toggle OFF with `templateId != null` sets `pendingRecurrenceStop`; the pre-save banner (WP1/WP2 slot) offers "Finalitza la plantilla" (default — template ended inside the same save transaction, reusing the existing end path) vs "Només desvincula aquest moviment" (current behavior). Strings: `movement_recurring_stop_title/end/unlink`.
4. **Quick templates carry notes:** `createQuickTemplate` uses `form.notes.nullIfBlank()` instead of `null`.
5. **Tests:** weekly-template movement edit carries WEEKLY; toggle-off + "end" ⇒ template ENDED and movement unlinked atomically; quick-create carries notes.

**Manual checks:** create a weekly recurring expense via the form; edit it → shows "Setmanal", frequency not editable; toggle off → choose end → template listed under Finalitzades, no more due prompts.

**Depends on:** WP1/WP2 banner infrastructure.

---

### WP4 — Budgets: discovery, safe delete, form polish — owns **F13, U10** (+ part of U12's form quality) — CRITICAL

**Status:** implementation and automated tests complete (`2cf829e`); manual closure complete for C17-05.

**Files:** `ui/management/ManagementScreen.kt` (+ `ManagementDestination`) · `MainActivity.kt` · `ui/budgets/BudgetsScreen.kt`, `BudgetsViewModel.kt` · `ui/categories/CategoriesScreen.kt`, `CategoriesViewModel.kt` · `notifications/` destination routing · `docs/13`.

**Steps:**
1. **Gestió entry.** Add `BUDGETS` to `ManagementDestination` (string `management_budgets_title` = "Pressupostos", icon `Icons.Filled.Savings`, a `FinanceTheme` accent). Rework the tile grid for 7 tiles (keep Settings last). Route it in `MainActivity`'s `MANAGEMENT` branch to `BudgetsScreen(contextTripId = null)`. Keep `AppOverlay.Budgets` for trip context; retarget the budget notification deep-link to `showManagement(BUDGETS)`.
2. **Category-flow CTA.** In `CategoryFlowScreen`, when no budget evaluation exists and the category supports expenses: `TextButton("Defineix un pressupost")` → Budgets with the category preseeded (`budgetsViewModel.onAddClicked(categoryId)` overload seeding `BudgetFormState(scope = CATEGORY, categoryId)`; callback plumbed via `CategoriesScreen` → `MainActivity`).
3. **Archive confirmation (U10).** Add `archiveCandidate: BudgetSummary?` + the standard `AlertDialog` (strings `budget_archive_confirm_title`, `budget_archive_warning`), exactly the `TagsScreen` pattern; `onDeleteClicked` only sets the candidate.
4. **Form polish.** Threshold defaults to `"80"` for new budgets with a `%` suffix; category/trip chip floods replaced by `FormSelect` (icons/colors leading, expense-supporting categories only); back arrow `contentDescription` fixed to `common_back`. (Date picker lands in WP5.)
5. **Tests:** archive-candidate flow; seeded form state from the category CTA.

**Manual checks:** Gestió → Pressupostos → create a category budget end-to-end; archive one → dialog; category flow screen → "Defineix un pressupost" → pre-scoped form; tap a budget notification → lands on the Gestió Budgets page.

**No dependencies** — parallel with WP1–3.

---

### WP5 — One date control everywhere — owns **U9** — HIGH, quick win

**Status:** implementation and automated tests complete (`2cf829e`); manual closure complete for C17-06.

**Files:** `ui/movements/MovementDetailScreen.kt` (refund) · `ui/people/PeopleScreen.kt` (settlement) · `ui/recurring/RecurringScreen.kt` (`ConfirmPromptDialog`) · `ui/budgets/BudgetsScreen.kt`.

**Steps:**
1. Replace the four free-text `YYYY-MM-DD` fields with `FormDatePicker`. Do **not** relocate the component — it is already imported cross-package by recurring/trips.
2. Budget start date is optional: add an optional `onClear: (() -> Unit)?` to `FormDatePicker` (trailing clear icon when non-blank; blank renders the muted placeholder). Only the budget form passes it.
3. Drop now-dead `movement_date_format_hint` usages (delete the string only if fully orphaned).
4. Keep the ViewModel "invalid date" validation branches (defense in depth).

**Manual checks:** record a refund, a settlement, confirm a due recurring payment, set + clear a budget start date — all via the calendar dialog.

**No dependencies.**

---

### WP6 — Settlements & refunds: explain, then (optionally) edit — owns **U11** — HIGH

**Status:** WP6a implementation is complete (`2cf829e`) with manual closure complete for C17-07. WP6b remains an optional, separately approved enhancement and does not block this slice.

**WP6a (do now, UI-only):** in `MovementDetailScreen`, where Edit is suppressed for SETTLEMENT/REFUND, render a muted caption `movement_detail_no_edit_hint` ("Les liquidacions i devolucions no es poden editar. Arxiva-la i registra'n una de nova.").

**WP6b (optional follow-up, separate branch, `schema-steward` sign-off required before code):** editing amount/date/account/notes for settlements and refunds. Same-row updates on `movements` respecting the type⇔field CHECK constraints — **no schema change expected**; steward confirms no canonical-SQL/golden implication (vectors test rules, not app edit paths — confirm, never weaken). UI reuses `SettlementScreen` (prefilled from the movement) and `RefundFormContent` (add `refundId`; `remainingCents` excludes the edited refund itself). Over-refund/over-settle stay dismissible warnings. Update `docs/12`/`docs/13`.

**Depends on:** WP5 (same forms' date fields) merged first.

---

### WP7 — Consistency pass — owns **U12, M16** — HIGH

**Status:** not started. Run after P5R-19 because both tasks touch route chrome, list actions, movement-form presentation, Trips naming, and design-system interaction rules.

`ui-ux-designer` locks two rules first (recorded in `docs/08-design-system.md` §patterns); `android-engineer` then applies them.

**Rule A — card-tap semantics.** One written rule applied everywhere. Recommended: *ledger/inspectable entities* (movement, account, category, person, trip) → tap = inspect (detail/flow); *simple config entities* (budget, template) → tap = edit. Whichever variant the designer picks, budgets and templates must end up consistent with it and the rule must be written down.

**Rule B — add-action placement.** Header `TopBarIconButton(Add)` on every entity list (the Categories pattern); bottom `PrimaryButton` remains only inside empty-state cards. Tags loses its duplicated top text-button.

**Implementation:**
1. Apply Rules A/B across Accounts, Categories, People, Trips, Tags, Recurring, Budgets screens.
2. **Person form → full page:** convert `PersonFormSheet` to the standard full-page swap in `PeopleScreen` (`PageHeaderRow`, Cancel/Save row, `BackHandler`) matching every sibling form.
3. **M16 — externalize hardcoded strings:** movement-form titles ("Nova despesa"/"Nou ingrés"/"Nova transferència"/"Nou moviment"/"Edita moviment") → `movement_form_new_*` / `movement_form_edit_title`; `AccountsScreen`'s "1 compte"/"N comptes" → `plurals/account_count`. Sweep for any others in touched files.
4. **Minor items folded in:** tag-cleared-on-trip-change notice (`movement_tag_cleared_notice` as transient supporting text); DEBT/FOR_OTHER person pickers gain a "+ Nova persona" option reusing `CreatePersonDialog` (generalize `onCreatePersonInSplit` → `onCreatePerson`, routing the new id to `forOtherPersonId` when invoked from those pickers).
5. Update `docs/08` (rules) and note screen conformance in `docs/15`.

**Depends on:** WP4 and WP5 merged (both churn screens this WP touches).

---

### WP8 — Template form cleanup — owns **U13, F14** — MEDIUM

**Status:** not started. Run after P5R-19 WP2 so the template form reuses the final advanced-options/disclosure pattern instead of extracting a competing component.

**Files:** `ui/recurring/RecurringScreen.kt` (`TemplateFormScreen`) · `RecurringViewModel.kt` · `ui/common/` (one extraction) · `docs/13`.

**Steps:**
1. **Advanced fold (U13):** add `showAdvanced` to `TemplateFormState`; move amount-flex, date-flex, lead-days, payee, notes under the divider pattern. Extract the advanced-divider row from `MovementFormCommon` into `ui/common/` (the one justified relocation in this slice — two packages now need it). Auto-expand when editing a template with any of those set.
2. **Drop the status field:** new templates are ACTIVE; the list's pause/resume/end/reactivate actions are the single status surface (verify `onResumeClicked` covers ENDED — it does). Remove the control from the form entirely; document in `docs/13`.
3. **Split visibility (F14):** when editing a template with `splitConfig != null`, render the existing `SplitPreviewCard` (read-only, fed by the template amount) + `DestructiveTextButton("Treu la compartició")` setting a `removeSplitConfig` flag → save passes `splitConfig = null` instead of carrying forward. Full split editing stays deferred (P4-2 note). **Careful:** this is adjacent to the C7 fix — regression tests must prove untouched saves still carry `splitConfig` forward.
4. **Tests:** create ⇒ ACTIVE; edit + remove-split ⇒ null persisted; edit without touching ⇒ carried forward (existing C7 test still green).

**Depends on:** WP2 (field mapping in this form), WP5.

---

### WP9 — Split editor helpers — owns **U14** — MEDIUM

**Status:** not started. No direct P5R-19 behavior overlap, but schedule after the movement-form layout settles to avoid parallel churn in the same flow.

**Files:** `ui/movements/SplitEditorCard.kt`, `SplitEditorState.kt`, tests.

**Steps:**
1. **Assign-remainder:** when EXACT and `deltaCents != 0`, the reconcile banner gains `TextButton("Assigna la resta")` → `SplitEditorState.withRemainderAssigned(totalCents)` adds the delta to the payer's line (user's line if the payer has no entry). Percentage method: same, in basis points. Pure UI-state string-map arithmetic — **`SplitCalculator` untouched** (golden territory).
2. **Payer reset on edit:** stored splits carry no payer column (verify against `docs/04-data-model.md`); if confirmed, `toFormState`'s reset to USER is data-correct — record the limitation in the handoff, change nothing. Do not invent schema.
3. **Tests:** `withRemainderAssigned` incl. negative delta and blank payer entry.

**Schedule after WP1** (same files).

---

### WP10 — Inline category creation — owns **U15** — MEDIUM

**Status:** not started. Run after P5R-19 WP2 and P5R-17 WP7 because all three touch movement-form selectors and creation affordances.

**Files:** `ui/movements/MovementFormCommon.kt` (`CategorySelect`) · `MovementsViewModel.kt` · strings.

**Steps:**
1. Trailing `SelectOption(id = CREATE_SENTINEL, label = "+ Nova categoria")` in `CategorySelect`, gated by an optional `onCreate: (() -> Unit)?` param so call sites opt in explicitly (movement + template forms yes; filters no).
2. Minimal dialog: name only; kind from the current movement type; nature VARIABLE; no parent; default color/icon. Reuse `CategoriesViewModel`'s draft construction shape — duplicate the ~10-line draft if extraction isn't clearly simpler; no abstraction layer.
3. `MovementsViewModel.onCreateCategoryInForm(name)` mirroring `onCreatePersonInSplit`: create → reload categories → select it.
4. **Tests:** create-selects-it; blank-name no-op.

**Depends on:** WP1, and WP7 step 4 (shares the create-dialog generalization).

---

### WP11 — Account reordering — owns **U16** — LOW

**Status:** not started. No material P5R-19 dependency; keep after higher-priority navigation, entry, accessibility, and consistency work.

**Files:** `ui/accounts/AccountsScreen.kt`, `AccountsViewModel.kt`.

**Steps:**
1. **Ship the reorder-mode fallback first:** a header "Ordena" action toggles a mode where each card shows inline up/down arrow buttons (1 tap per step vs today's 3-taps-via-menu). Long-press gesture drag is a *possible later refinement* only if it needs no new dependency; do not block on it.
2. Generalize the move plumbing to `onReordered(fromIndex, toIndex)` writing display orders in one transaction; keep the ⋮ menu items as the accessibility path.
3. **Tests:** reorder persistence.

**No dependencies.**

---

## 4. Sequencing

The original implementation waves are historical for WP1–WP6a. From the 2026-07-16 audit onward, use this order:

| Order | Work | Reason |
|---|---|---|
| 0 | **P5R-17 closure gate for WP1–WP6a** | Confirm already-shipped correctness before later layout work obscures whether a regression was pre-existing. |
| 1 | **P5R-19 WP1–WP6** | Contains the only current Critical live-product defect (navigation rendering) and establishes the final shell/movement/interaction presentation. |
| 2 | **P5R-17 WP7** | Apply one consistency/string pass to the final P5R-19 routes, names, and controls rather than polishing the old layout first. |
| 3 | **P5R-17 WP8 + WP9** | Reuse the settled advanced-disclosure pattern; finish template and split-editor friction. |
| 4 | **P5R-17 WP10 + WP11** | Add the remaining convenience features after movement selectors and account presentation settle. |
| Optional | **WP6b** | Only with explicit scope approval and `schema-steward` sign-off; never blocks P5R-17 closure. |
| Final | **P5R-11 + P5R-12** | Whole-app checklist and final correctness/simplicity audit. |

### Collision map with P5R-19

| P5R-17 work | P5R-19 overlap | Resolution |
|---|---|---|
| Completed WP1/WP2 | P5R-19 WP1/WP2 touch the movement route and save area | Preserve the existing warnings/error slot; P5R-19 owns chrome, sticky CTA, and layout only. |
| Completed WP5 | P5R-19 WP6 standardizes rendered date formats | Reuse `FormDatePicker`; P5R-19 changes display formatting, not the date-entry control. |
| Completed WP6a | P5R-19 WP1 changes Movement Detail chrome | Keep the no-edit explanation while replacing the competing global navigation. |
| Remaining WP7 | P5R-19 WP1/WP6 change route chrome, Viatges naming, and Dashboard hierarchy | P5R-19 first; WP7 then performs one final cross-screen interaction/string sweep. |
| Remaining WP8 | P5R-19 WP2 changes progressive disclosure in movement entry | P5R-19 defines/extracts the shared disclosure pattern; WP8 reuses it in templates. |
| Remaining WP10 | P5R-19 WP2 changes movement form layout/selectors | P5R-19 first, then WP7's create-person generalization, then WP10 inline category creation. |
| Remaining WP9/WP11 | No material behavior collision | Keep after higher-severity work to minimize simultaneous churn. |

One writing agent at a time per the roadmap principle; a WP is the unit of assignment.

## 5. Manual checklist

### Closure gate for implemented packages

- [x] **C17-01 / WP1:** Edit an existing three-person shared expense, re-tap selected segments, switch through every movement type and back, then save; the split remains intact.
- [x] **C17-02 / WP1:** Explicitly remove sharing and save; the warning appears adjacent to Save, and only the accepted second action removes the split.
- [ ] **C17-03 / WP2:** From the bottom of a long invalid form, save with account/category/date errors; the page scrolls to and labels the offending field.
- [x] **C17-04 / WP3:** Edit a weekly linked movement; the real cadence is read-only, and turning recurrence off offers end-template versus unlink-only.
- [x] **C17-05 / WP4:** Open Gestió → Pressupostos, create a budget, archive it through confirmation, and open a pre-scoped budget from a category with no budget.
- [x] **C17-06 / WP5:** Record a refund, settlement, due occurrence, and optional budget start date using calendar controls only.
- [x] **C17-07 / WP6a:** Open settlement and refund details; the non-edit explanation is visible and archive/recreate remains possible.
- [x] **C17-08 / automated gate:** `:app:testDebugUnitTest` and `:app:assembleDebug` pass after the controlled-data run and verified wording fix (2026-07-16).

### Controlled-data execution record — 2026-07-16

The closure run used the isolated debug package `com.gestorfinances.app.manual` on device `61070DLCQ000KB`, with the existing debug fixture plus disposable records created under the `Controlled` onboarding account. It exercised the shared-expense round trip/removal warning, linked weekly recurrence and end-template choice, budget creation/archive/category pre-scoping, refund/settlement/due-occurrence calendars, and settlement/refund no-edit details. The real `com.gestorfinances.app` package was not installed, cleared, seeded, or edited.

C17-03 remains open: the current `AccountSelect` has no clear option and the required movement date picker has no clear option, so the exact manual invalid-state setup cannot be reached from the UI. The corresponding ViewModel validation/error-field paths remain covered by automated tests. During C17-07 verification, the action and hint were confirmed to archive/recreate; their Catalan wording was corrected from `Elimina`/`Elimina-la` to `Arxiva`/`Arxiva-la`.

### Remaining-package closure

- [ ] WP7 manual checks completed and recorded.
- [ ] WP8 manual checks completed and recorded.
- [ ] WP9 manual checks completed and recorded.
- [ ] WP10 manual checks completed and recorded.
- [ ] WP11 manual checks completed and recorded.
- [x] All resolved P5R-17 finding dispositions are updated in `docs/16`.
- [ ] P5R-17 is checked complete in `docs/06-roadmap.md` only after every required item above is complete.

The per-WP "Manual checks" above remain the detailed acceptance scripts. P5R-11 executes them again as part of the phase-close regression pass.
