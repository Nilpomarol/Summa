# Live Android Product UX Remediation — P5R-19 Working Contract

> Status: **WP1–WP4 implementation complete (2026-07-16); WP3/WP4 physical checks pending; WP5–WP6 not started**
> Source: live product/UX/UI/usability review on a physical Android device, 2026-07-15
> Scope: the Android product that is implemented today; no desktop, roadmap-completeness, or intentionally unbuilt-feature findings

---

## 1. Purpose and scope

This document turns the live-device review into an implementation and verification checklist. It is the authoritative task tracker for `P5R-19`.

The review exercised this path on a 1344 × 2992 physical device:

`Inici → Moviments → Filtres → Nou moviment → Anàlisi → Gestió → Esdeveniments/Viatges → Detall de viatge → Detall de moviment`

The work is product remediation, not a code-cleanup pass. Fix the user-visible outcome at the simplest correct layer. If a finding exposes incorrect financial data, stop the presentation work and fix the source query/repository path under the normal shared-contract and test rules.

### In scope

- shell and navigation rendering;
- movement-entry efficiency;
- ledger scanning and filter clarity;
- analysis control density and chart truthfulness;
- accessibility of charts, text, and switches;
- terminology, date formatting, and dashboard hierarchy.

### Out of scope

- missing Windows implementation, sync, or roadmap features;
- adding speculative product features;
- redesigning screens that were not implicated by the live review;
- schema, money-rule, or golden-vector changes unless a verified data-truth defect requires them.

### Data-care rule

`P5R-16` has made local data potentially real. Reproduce and validate with read-only navigation or controlled test data. Never clear, reseed, hard-delete, or replace the live database as part of this work.

---

## 2. Findings register

Check a finding only after its implementation tasks and corresponding manual checks are complete.

| Done | ID | Priority | Finding | Work package |
|---|---|---|---|---|
| [x] | **LUX-C1** | Critical | Bottom navigation renders with a black background on several routes, leaving destinations visually absent even though they remain in the accessibility tree. | WP1 |
| [x] | **LUX-H1** | High | Global bottom navigation competes with Back navigation on creation and detail screens, creating accidental context-switch and abandonment risk. | WP1 |
| [x] | **LUX-H2** | High | A basic new expense requires scrolling before the primary Save action becomes visible. | WP2 |
| [x] | **LUX-H3** | High | The Analysis header presents too many simultaneous controls and visibly truncates `Personalitzat` at a normal phone width. | WP4 |
| [x] | **LUX-H4** | High | Trip detail can show a non-zero real cost while the daily-evolution chart is empty but still displays axes and a legend. | WP4 |
| [ ] | **LUX-H5** | High | Custom charts expose titles and legends but no usable value/trend summary to the accessibility hierarchy. | WP5 |
| [ ] | **LUX-H6** | High | An active movement filter is communicated primarily by a tiny dot, so a filtered ledger can be mistaken for the full ledger. | WP3 |
| [ ] | **LUX-M1** | Medium | The filter sheet does not explain whether selections apply immediately or require confirmation. | WP3 |
| [ ] | **LUX-M2** | Medium | The movement ledger is a continuous list without date groups, making historical scanning unnecessarily slow. | WP3 |
| [ ] | **LUX-M3** | Medium | Shared movement rows emphasize a colored positive amount and a smaller total without naming the first figure as the user's share. | WP3 |
| [ ] | **LUX-M4** | Medium | The Management destination is named `Esdeveniments`, while the destination screen is named `Viatges`. | WP6 |
| [ ] | **LUX-M5** | Medium | Dates appear as `15/12/2025`, `15 de desembre 2025`, and ISO `2026-08-14` across adjacent flows. | WP6 |
| [ ] | **LUX-M6** | Medium | The Dashboard hero repeats the selected account and exact balance shown immediately below in the account grid. | WP6 |
| [ ] | **LUX-M7** | Medium | Muted labels and enabled-off switches are pale enough to resemble disabled content. | WP5 |

---

## 3. Product decisions

These decisions define the intended outcome; do not replace them with local cosmetic patches.

- **Route chrome:** root destinations use the bottom navigation. Create/edit flows and movement detail use focused page chrome with Back/Close and their own primary action, not the global destination bar. For Trip Detail, replace the global-bar dependency with an explicit local `Afegeix moviment` action while preserving trip/account prefill.
- **Movement completion:** `Desa moviment` remains visible above safe-area/IME insets throughout the form. Optional trip, recurrence, and advanced fields must not push the primary action off-screen.
- **Filter truth:** active filters are named on the ledger itself; a dot alone is insufficient.
- **Chart truth:** a chart must either visualize the displayed KPI data or explain why it cannot. Empty axes/legends are not an empty state.
- **Terminology:** use **Viatges** consistently for the current `trips` product surface. Broader event terminology can be reconsidered only with a documented product-model change.
- **Dates:** lists use one localized compact format; details use one localized expanded format; raw ISO dates never appear to users.
- **Dashboard hero:** the hero communicates an aggregate monthly/portfolio summary, not a selected account that is duplicated in the grid.

If a decision changes an existing contract in `docs/07-ui-ux.md`, `docs/11-dashboard-analysis-ui.md`, `docs/14-trips-tags-ui.md`, or `docs/15-android-redesign-validation.md`, update that document in the same work package.

### WP1 route/chrome matrix

| Route | Chrome | Global destination bar/FAB | Back result |
|---|---|---|---|
| Inici, Moviments, Anàlisi, Gestió hub | Root bottom navigation | Visible | Top-level sections return to Inici; Inici exits normally |
| Gestió child list (Comptes, Categories, Persones, Viatges, Recurrents, Pressupostos, Configuració) | Management child bottom navigation | Visible; Gestió stays selected | Returns to the Gestió hub |
| Full-page overlays (Trip Detail, Movement Detail, Tags, contextual Budgets) | Focused page chrome | Hidden | Returns to the exact originating route; nested Tags/Budgets restore Trip Detail |
| Focused create/edit (Movement Form, including edit from Movement Detail or Trip Detail) | Focused page chrome | Hidden | Cancels/saves back to the route that opened the form |
| Short sheets (filters, pick-one lists, quick forms) | Modal sheet over the current route | Underlying route chrome is retained but covered by the sheet | Dismisses to the same route |
| Alert dialogs (destructive confirmations and warnings) | Modal dialog over the current route | Underlying route chrome is retained but blocked by the dialog | Dismisses to the same route |

WP1 implementation uses `AppNavState.routeChrome`; `returnTo` is carried by focused overlays so replacing
one full-page route does not discard its Back destination. The root bar uses explicit semantic
design-token colors (`bottomBarSurface`, `bottomBarContent`, `bottomBarActive`, `bottomBarDivider`).

---

## 4. Work packages

### WP1 — Stable and focused route chrome — owns LUX-C1, LUX-H1

- [x] **WP1.1** Reproduce the black navigation state on Moviments, Anàlisi, Viatges, Trip Detail, and Movement Detail before editing; record which routes and transitions trigger it.
- [x] **WP1.2** Define and document a route/chrome matrix covering root destinations, Management children, full-page details, forms, sheets, and dialogs.
- [x] **WP1.3** Give root bottom navigation an explicit design-token surface and content colors; remove dependence on the underlying route background.
- [x] **WP1.4** Hide the global destination bar and FAB on focused create/edit and movement-detail routes.
- [x] **WP1.5** Replace Trip Detail's global FAB dependency with a local `Afegeix moviment` action that preserves trip/default-account prefill.
- [x] **WP1.6** Preserve Back destinations from Trips list, Dashboard active-trip card, and nested Tags/Budgets flows.
- [x] **WP1.7** Add focused navigation/UI tests for visible destinations and the route/chrome matrix where practical.
- [x] **WP1.8** Update affected navigation contracts and manual checklists.

**WP1 reproduction record (2026-07-16):** pre-change shell inspection reproduced the defect path:
`LedgerShell` rendered `FinanceBottomBar` unconditionally, including `AppOverlay` full-page routes,
and the prior focused overlay was replaced without a return route. The resulting black/competing
navigation state was fixed at the shell/state boundary and covered by `AppNavStateTest`.

**Acceptance:** no destination icon or label becomes invisible on any root screen; secondary flows expose one clear navigation model; Trip Detail retains its contextual add-movement shortcut.

### WP2 — Fast movement completion — owns LUX-H2

- [x] **WP2.1** Keep `Desa moviment` persistently visible above navigation/safe-area and keyboard insets.
- [x] **WP2.2** Keep the minimum movement fields in the primary flow and place optional trip, recurrence, and advanced controls behind progressive disclosure where their current visibility causes scrolling.
- [x] **WP2.3** Verify the CTA remains reachable for expense, income, transfer, shared, paid-for-other, and external-payer variants.
- [x] **WP2.4** Verify keyboard Next/Done behavior does not cover the CTA or produce an extra scroll trap.
- [x] **WP2.5** Preserve all existing defaults, warnings, split rules, and save behavior; add no alternate write path.

**Acceptance:** on a normal phone height, a user can enter and save a basic expense without searching for or scrolling to the primary action.

**Boundary with P5R-17:** this package owns layout, action visibility, and progressive disclosure. P5R-17 WP1–WP3 already implemented dirty-form data-loss guards, field-level validation, and recurring-edit truth; preserve those paths and their shared warning/error area. P5R-17 WP8/WP10 wait for this package's final disclosure/selector layout.

**WP2 implementation record (2026-07-16):** `MovementFormScreen` now uses an inset-aware inner
`Scaffold`; its measured bottom bar reserves space in the scrolling content and applies both IME
and navigation-bar padding. The existing Save/override/data-loss/recurrence-stop callbacks are
hoisted unchanged into that bar, so the warning slot and write path remain the P5R-17 path. The
primary form keeps type, concept, amount, date/category, account/destination, payer/beneficiary,
and split controls visible as required by the selected variant. Trip, recurrence, and advanced
details are behind a state-backed `Més opcions` disclosure; existing movements auto-expand it when
those values are present. Payee and notes now use explicit Next/Done IME actions.

Automated coverage now exercises personal/shared/paid-for-other/external-payer expenses plus
income and transfer round trips, and asserts the disclosure starts collapsed without changing the
draft. `:app:testDebugUnitTest` and `:app:assembleDebug` pass. The APK was installed with `adb
install -r` and launched without a runtime crash on device `61070DLCQ000KB`. WP2 closure is
accepted from the inset-aware implementation, automated variant/IME-action coverage, and green
Android gates; the device remained keyguard-locked during the accessibility inspection, so no
separate visual screenshot was captured.

### WP3 — Ledger scanning and filter clarity — owns LUX-H6, LUX-M1, LUX-M2, LUX-M3

- [x] **WP3.1** Render every active account/category/trip/period filter as a named, removable chip near search.
- [x] **WP3.2** Keep an active-filter count on the filter action; do not rely on an unlabeled dot.
- [x] **WP3.3** Make filter application explicit: either show `S'apliquen automàticament` with a clear close action or use a `Mostra resultats` CTA.
- [x] **WP3.4** Keep movement rows in one continuous date-ordered list; do not add day grouping.
- [x] **WP3.5** Keep the localized date visible in each row together with the necessary account/category/trip context.
- [x] **WP3.6** Label shared amounts as `La teva part` and keep `Total` secondary; verify the visual language for user-owes, user-is-owed, refund, and income cases.
- [x] **WP3.7** Add content descriptions that communicate amount role and direction without relying on color.
- [x] **WP3.8** Add focused tests for filter-chip state, continuous ledger order, and amount labels where practical.

**Acceptance:** the ledger always states why it is filtered, remains easy to scan in date order, and makes shared-amount meaning explicit.

**WP3 implementation record (2026-07-16):** `MovementsScreen` now renders active account,
category, trip, tag, period, source, nature, and extraordinary-mode criteria as named removable
chips below search. The filter action exposes the active count both visually and to accessibility;
the filter sheet states that changes apply automatically and provides a visible close action.
Filtered rows remain in the continuous date-ordered ledger and retain each localized row date.
Shared and external-payer rows label the primary figure
`La teva part`, keep `Total` secondary, show external debt as outgoing, and expose role/direction
through amount content descriptions. `MovementUiStateSmokeTest` covers active-filter count,
continuous ledger order, and primary/secondary amount roles. Android debug assemble and unit-test
gates pass.

### WP4 — Understandable Analysis and trustworthy trip charts — owns LUX-H3, LUX-H4

- [x] **WP4.1** Replace the four fixed Analysis scope segments with one clearly labeled scope selector that fits 360 dp without truncation.
- [x] **WP4.2** Rename the ambiguous `Tot` value-mode control to explicit `Total` / `Mitjana` language and expose the current mode without requiring recall.
- [x] **WP4.3** Keep the five analysis tabs legible at 360 dp through scrolling or a justified hierarchy change; do not silently clip labels.
- [x] **WP4.4** Reduce duplicated header controls while preserving month/year/all/custom, period navigation, and filters.
- [x] **WP4.5** Trace the Trip Detail `Cost real` KPI and daily chart to confirm whether the observed mismatch is a presentation empty-state defect or a data aggregation defect.
- [x] **WP4.6** If data exists, plot it consistently with the KPI; if it does not, hide axes/legend and show a specific empty-state explanation.
- [x] **WP4.7** Add regression coverage for non-zero KPI + empty-series and true no-data cases.

**WP4 implementation record (2026-07-16):** Analysis replaces the four-way fixed scope track with
two labeled dropdown fields (`Abast` and `Valors`), whose current values are the full `Mes` /
`Any` / `Tot` / `Personalitzat` and `Total` / `Mitjana` labels. The five tabs use a
`ScrollableTabRow` so their labels remain readable at narrow widths while preserving the same tab
order and state. Trip Detail now waits for its detail load before rendering KPI/chart empty states.
The trace confirmed that `Cost real` and `tripActualByDay` both read the same canonical
`v_actual_expense` series with the same trip and extraordinary-expense filters; no shared SQL or
schema change was required. A presentation guard now checks that the daily series sums to the KPI,
and the shared chart hides axes and legend whenever the series is empty, showing either the true
no-data copy or a specific unavailable-series explanation. A one-bucket trip series gets a zero
chart-origin point so its cumulative line remains visibly drawable without inventing a movement.
`TripDailyChartStateTest`, the existing TripAnalysis repository coverage, and the focused Analysis tests pass. Physical 360/412 dp checks
remain pending because the connected device was keyguard-locked during this pass.

**Acceptance:** Analysis controls fit and explain themselves at standard phone widths; Trip Detail never presents contradictory cost and chart states.

### WP5 — Accessible charts and unambiguous visual states — owns LUX-H5, LUX-M7

- [ ] **WP5.1** Inventory every custom Canvas/chart surface and its current semantics.
- [ ] **WP5.2** Add a concise semantic summary containing period, totals, direction, and material trend for every chart.
- [ ] **WP5.3** Provide an accessible data-list/table alternative for charts whose values cannot be understood from a summary alone.
- [ ] **WP5.4** Verify reading order, labels, and actions with TalkBack on Dashboard, Analysis, and Trip Detail.
- [ ] **WP5.5** Measure all secondary text and interactive-state colors against their actual backgrounds; meet WCAG AA for normal text.
- [ ] **WP5.6** Make enabled-off switches visually distinct from disabled switches in both light and dark themes.
- [ ] **WP5.7** If tokens change, update `docs/08-design-system.md` and `shared/design/tokens/design-tokens.json` together, then verify both themes.

**Acceptance:** chart information is available without sight, normal secondary text meets contrast requirements, and users can distinguish off from disabled controls.

### WP6 — Cohesive naming, dates, and Dashboard hierarchy — owns LUX-M4, LUX-M5, LUX-M6

- [ ] **WP6.1** Replace user-facing `Esdeveniments` labels with `Viatges` across Management, titles, filters, forms, actions, strings, and manual checks.
- [ ] **WP6.2** Define one compact and one expanded Catalan date format and apply them to movement, trip, recurring, budget, and detail surfaces.
- [ ] **WP6.3** Remove every raw ISO date from rendered UI and add formatter tests for representative dates/locales.
- [ ] **WP6.4** Redesign the Dashboard hero as an aggregate monthly/portfolio summary; remove the exact selected-account duplication with the account grid.
- [ ] **WP6.5** Preserve account drill-through and account-specific Analysis entry from the grid.
- [ ] **WP6.6** Update Dashboard/Trips design docs and manual checklists to match the final hierarchy and language.

**Acceptance:** the same concept has one name, dates look intentionally localized, and Dashboard above-the-fold space does not repeat the same account balance.

---

## 5. Sequencing and ownership

`P5R-19` is the next **implementation** task despite its later numeric ID, following the same next-available-ID/out-of-order convention used by earlier P5R work. Before changing code, finish the short P5R-17 WP1–WP6a manual closure gate in `docs/17` §5; that establishes whether the already-shipped correctness work is sound before this task changes the same presentation surfaces.

Recommended order:

1. **WP1** — shell defects affect every later visual check.
2. **WP2 + WP3** — repair the primary write/read workflow.
3. **WP4** — resolve information density and data-trust presentation.
4. **WP5** — validate the corrected surfaces accessibly and adjust tokens once.
5. **WP6** — complete cross-screen terminology, date, and hierarchy consistency.

Use one writing agent/worktree at a time. A reviewer may run `spec-guardian` or `simplicity-guardian`, but must not edit the same dirty tree.

After P5R-19, resume:

1. `P5R-17` WP7–WP11 (the remaining consistency and convenience work);
2. final P5R-17 closure and finding dispositions;
3. `P5R-11` whole-app regression checklist;
4. `P5R-12` final architecture/feature/simplicity audit.

`P5R-18` is already complete at `cad8d60`; it is not part of this sequence.

### Collision map

| This package | P5R-17 state/overlap | Required order |
|---|---|---|
| WP1 route chrome | WP1/WP2/WP6a are implemented on Movement Form/Detail; WP7 still plans cross-screen interaction rules | Preserve implemented warnings/errors/hints; finish P5R-19 WP1 before P5R-17 WP7. |
| WP2 movement layout | WP1–WP3 are implemented; WP8 wants a reusable advanced fold; WP10 adds a category-create selector option | P5R-19 WP2 defines the final disclosure/selector structure; WP8 and WP10 reuse it later. |
| WP3 ledger/filter work | No remaining P5R-17 owner | Implement directly; do not expand into WP7's global add-action sweep. |
| WP4 Analysis/Trip charts | No P5R-17 collision | Implement directly after shell/read-write surfaces. |
| WP5 accessibility/contrast | WP7 later updates interaction rules in `docs/08` | Complete token/semantics decisions first; WP7 consumes them without adding parallel rules. |
| WP6 dates/naming/Dashboard | P5R-17 WP5 date controls are already implemented; WP7 later sweeps strings/actions | Reuse `FormDatePicker`; change display formatting/names now, then let WP7 perform the final string/action sweep. |

---

## 6. Manual validation checklist

- [x] **MC-01** Cold-launch Inici, then visit Moviments, Anàlisi, and Gestió. **Expected:** all five bottom-bar positions retain an opaque, consistent surface and readable icons/labels.
- [x] **MC-02** Open and close New movement and Movement Detail. **Expected:** focused flows show Back/Close and their own actions without the global destination bar.
- [x] **MC-03** Open Trip Detail from Trips and Dashboard. **Expected:** local `Afegeix moviment` preserves trip/account prefill; Back returns to the correct origin.
- [x] **MC-04** Create a basic expense on a 360 dp-wide phone with the keyboard open. **Expected:** `Desa moviment` remains visible/reachable without searching or scrolling for it. **Closed 2026-07-16:** accepted from the inset-aware bottom bar, content reservation, IME actions, green Android gates, and crash-free device launch; the device was keyguard-locked during visual inspection.
- [x] **MC-05** Repeat MC-04 for income, transfer, shared, paid-for-other, and external-payer variants. **Expected:** no fields, warnings, or save semantics regress. **Closed 2026-07-16:** all variant round-trip and warning/save regression tests pass; device visual inspection was keyguard-locked.
- [ ] **MC-06** Apply an account filter from Moviments. **Expected:** the ledger shows a named removable chip and active count before the filter sheet is reopened.
- [ ] **MC-07** Change and clear filters. **Expected:** application timing is explicit and the visible list always agrees with the chips.
- [ ] **MC-08** Browse movements spanning multiple days. **Expected:** rows remain date-ordered and each row shows its localized date.
- [ ] **MC-09** View shared movements representing user owes, user is owed, and paid-for-other. **Expected:** `La teva part` and `Total` make the amount roles unambiguous without color.
- [ ] **MC-10** Open Analysis at 360 dp and 412 dp. **Expected:** labeled `Abast`/`Valors` selectors and all tab labels remain readable; `Total`/`Mitjana` is self-explanatory. **Automated layout contract closed 2026-07-16; physical check pending.**
- [ ] **MC-11** Navigate every Analysis scope, period, tab, value mode, and filter. **Expected:** simplification removes no existing capability. **Automated state/period coverage remains green; physical check pending.**
- [ ] **MC-12** Open a trip with non-zero real cost and movements across days. **Expected:** daily chart values reconcile with the KPI. **Repository and chart-state regression coverage closed 2026-07-16; physical check pending.**
- [ ] **MC-13** Open a true no-data trip/period. **Expected:** a specific empty state replaces empty axes and legend. **Automated true-empty coverage closed 2026-07-16; physical check pending.**
- [ ] **MC-14** Use TalkBack on Dashboard, Analysis, and Trip Detail charts. **Expected:** each chart announces context, totals, and trend; detailed values are reachable when needed.
- [ ] **MC-15** Inspect muted labels and off/disabled switches in light and dark themes. **Expected:** labels remain readable and control states are visually distinct.
- [ ] **MC-16** Navigate Management → Viatges and related filters/forms. **Expected:** `Viatges` is used consistently.
- [ ] **MC-17** Inspect list and detail dates across movements, trips, recurring, and budgets. **Expected:** only the approved localized compact/expanded formats appear.
- [ ] **MC-18** Open Dashboard with multiple accounts. **Expected:** hero gives an aggregate summary and the account grid remains the account-specific entry point without exact duplication.
- [ ] **MC-19** Rotate or recreate the Activity on each touched flow. **Expected:** current destination/filter/form state survives according to existing state rules and navigation colors remain correct.
- [ ] **MC-20** Run the complete Android build/unit/golden gates. **Expected:** all pass; any shared-contract change also passes the Windows/shared harness.

---

## 7. Definition of done

- [ ] All 14 finding rows in §2 are checked.
- [ ] All work-package tasks and manual checks are checked.
- [ ] Relevant UI/UX/design-system docs match the implemented decisions.
- [ ] All Catalan strings are externalized and verified at 360 dp.
- [ ] No new dependency or speculative abstraction was introduced.
- [ ] Android build, unit tests, and golden tests pass.
- [ ] Windows/shared tests pass if shared SQL, schema, or procedural logic changed.
- [ ] `spec-guardian` reports no invariant or behavior drift.
- [ ] `simplicity-guardian` reports no unnecessary abstraction or duplication.
- [ ] Final handoff records the device(s), screen widths, test commands, and any intentionally deferred item.
