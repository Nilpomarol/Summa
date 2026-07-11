# Trips and Tags UI — Android P5 / P5R-7

> Originally scoped in P5-1 (Phase 5: trips/events, trip-scoped analysis, tags, trip attachment on movements, trip-as-block analysis behavior, trip budgets). **Rewritten in P5R-7** to describe what was actually built once trip detail was promoted to a full page, tags were redesigned around a 3-way scope plus optional category association, and a Dashboard active-trip card was added. See `docs/16-android-audit-findings.md` (F7) for the correctness bug found and fixed alongside this redesign. This doc still does not add product rules beyond `docs/00-Full_Spec.md`, `docs/04-data-model.md`, `docs/08-design-system.md`, or `docs/09-app-architecture.md`.

---

## 1. Phase Scope

Trips/events are a first-class analysis unit:

- create and manage trips/events with type, status, dates, default account, notes, icon, and color;
- attach movements and no-account external splits to one trip;
- assign at most one tag to a movement, only when the movement belongs to a trip;
- show a trip detail page with KPIs, a daily chart, category and tag breakdowns, an inline budget bar, and a scoped movement list;
- tags are the primary day-to-day categorization axis *within* a trip, optionally linked to a category for icon/color inheritance and rollup;
- surface the currently-active trip on the Dashboard with a quick link and a quick "add movement" action;
- group trip spend as a single block in normal analysis by default (unchanged from P5, `docs/11` §6);
- add trip-scoped budgets in the existing budget model.

Out of scope, unchanged from P5: person groups, multi-tag movements, many-to-many tags, desktop bulk editing, CSV import behavior, sync changes, forecasting. A movement may have a global category and one trip tag at the same time. Tags are global and reusable by default, with optional event-type or trip-specific scoping.

---

## 2. Navigation Shape

Trips/events live under **Gestió > Esdeveniments** (`ManagementDestination.EVENTS`), matching `docs/07-ui-ux.md` and spec section 5.9.

Trip detail is a **full page**, not a dialog: `AppOverlay.TripDetail(tripId)` (`ui/navigation/AppNavState.kt`) renders as a full-screen replacement the same way `AppOverlay.Tags`/`AppOverlay.Budgets` already do, with its own header row (Back arrow + overflow menu) rather than a system `TopAppBar`. Opened directly from a `TopLevelSection`/`ManagementDestination` (the two entry points below), it slots into the existing `back()` reducer for free (`overlay != null -> copy(overlay = null)`). Opened *from within* Trip Detail itself — its "Gestiona etiquetes"/"Pressupost del viatge" actions push `AppOverlay.Tags`/`AppOverlay.Budgets` with a `returnTo` field set to the current `TripDetail` overlay, so `back()` restores it instead of clearing to the underlying section; this keeps Trip Detail on the back-stack instead of losing it. Trip detail is reached from two places only:

- the Trips (Esdeveniments) list, tapping a trip row;
- the Dashboard's active-trip card, via its "Veure viatge" link.

Analysis rows and chart elements stay display-only per the Aggregate Interaction Contract (`docs/11` §6, unchanged) — tapping a trip block in Dashboard/Analysis never opens trip detail. That earlier §8 line describing a drill-through from an Analysis trip block was already inaccurate before this slice and has been dropped.

Tags are managed on their own page (`AppOverlay.Tags(tripId)`), reachable from the Trips list header ("manage tags" with no trip context) and from trip detail's "Etiquetes" action (with that trip pre-selected as the default scope for new tags). Tags are not a separate top-level Gestió tile.

---

## 3. Trips List

Purpose: show exceptional events as compact, scannable objects and make the current/active trips easy to resume.

Content (`TripsContent`/`TripRow`, `ui/trips/TripsScreen.kt`):

- title: Esdeveniments (`SectionHeader`), with a "Nou viatge" action;
- filter chips for all / planned / active / finished (`FinanceFilterChip`);
- each trip row is a `FinanceCard` with an `IconChip` (trip-type icon, trip color), name, a muted date-range line (falling back to the type label when the trip has no dates), a status `NeutralPill`, **total actual spend** (`MoneyText`) and **average spend per day** (`trip_row_avg_day`, derived from `TripSummary.totalActualCents` and `TripSummary.dayCount()`), plus a row overflow menu (edit/archive). The type is carried by the icon alone — no type pill or type text, keeping one glanceable metadata line per row;
- empty state with a New trip action and a short explanation.

Actions:

- New trip (opens the add form as a `ModalBottomSheet`);
- tap a row to open trip detail (the full page);
- edit trip (row overflow menu, or trip detail's own overflow menu) — opens the same form sheet;
- archive trip (row overflow menu, or trip detail's own overflow menu) — `AlertDialog` confirmation (destructive confirmations are exempt from the "no `AlertDialog`" rule, design system §8).

Data rules:

- archived trips are hidden by default;
- status is the persisted `trips.status` value (`planned`, `active`, `finished`);
- `total_actual_cents` and the day count are derived (`v_actual_expense` + the trip's date range), never stored on the trip.

---

## 4. Trip Detail (full page)

Purpose: answer what the trip cost, how spending evolved over trip days, and what it cost by category/tag, from a full page reached via the Trips list or the Dashboard active-trip card (`TripDetailScreen`/`TripDetailContent`, `ui/trips/TripsScreen.kt`).

Header (inline title row, no separate header card — the trip itself is the page title):

- back arrow + `IconChip` (trip-type icon, trip color) + trip name as the page title + overflow menu (Edit, Archive);
- one muted meta line under the name: date range (`dateRange()`) · day count (`trip_detail_days_count`, from `TripSummary.dayCount()` falling back to the daily-actual date range when the trip has no explicit start/end) · status label. No type/status pills;
- default account and notes are **not** shown on the detail page — they are edit-form facts (the default account still surfaces implicitly when adding a movement from the trip).

Hero card (`TripHeroSection`, replaces the former 2×2 KPI grid):

- actual trip spend (the user's own actual expense, net of refunds) as the single headline number;
- one muted secondary line combining average actual spend per day (`trip_row_avg_day`, or `trip_detail_avg_day_excluding` when the toggle is on; omitted entirely when the trip has no day count, since the average is undefined) and total account outlay/flow (`trip_detail_outflow`);
- an **"exclou despeses extraordinàries" toggle** (`trip_detail_exclude_one_time`) that reuses the existing `is_one_time` flag already used elsewhere in the app (Analysis, budgets) — **this is not a new flag**. When enabled, the hero numbers, the daily chart, and the breakdown are recomputed with `exclude_one_time = 1` threaded through `TripAnalysisRepository`'s queries, and the secondary line labels the avg/day as "sense extraordinàries".

Action row (directly under the hero, not at the bottom of the page): "Nou moviment" (`PrimaryButton`) plus "Etiquetes" and "Pressupost" (`OutlinedButton`s).

Budget: when an active TRIP-scope budget exists for the trip, `TripBudgetSection` renders a `BudgetProgressBar` inline, in addition to the existing "Pressupost del viatge" action that opens the full Budgets surface for editing.

Analysis blocks:

- daily chart (`TripDailySection`): a cumulative `IncomeExpenseChart` fed the trip's daily-actual series (income left at 0) — the old per-day/cumulative `SegmentedControl` toggle was **dropped**; the chart is always cumulative, matching how Analysis's own equivalent chart behaves;
- **one** breakdown section (`TripBreakdownSection`, replacing the former separate category and tag sections): a category/tag dimension `SegmentedControl` plus the Totals/Mitjana-per-dia mode toggle over the same percent-bar rows, so only one row list is on screen at a time. The category dimension gives the "no category" bucket the usual muted treatment; the tag dimension uses `TagSummary.effectiveIcon()`/`effectiveColor()` (falling back to an associated category's icon/color when the tag has none of its own, §6) for each tag's identity, with the untagged bucket (`trip_analysis_untagged`) muted the same way;
- scoped movement list capped at the **5 most recent** movements (`MovementListItem` rows, active movements with `trip_id` = this trip); tapping a row opens the same movement detail sheet used everywhere else (`MovementDialogHost` — trip detail wires `onMovementDetail` to the shared `movementsViewModel::onDetailClicked` path, no separate detail surface). When more exist, a "Veure tots (N)" action leaves trip detail and opens Moviments with the trip filter pre-applied (the same `MovementFilters(tripId)` drill-down the Dashboard uses).

Actions (the row under the hero, §above):

- "Nou moviment" preselects the trip and uses the trip's default account when present (unchanged from P5);
- "Etiquetes" opens the Tags page pre-scoped to this trip;
- "Pressupost" opens the Budgets surface pre-scoped to this trip.

Data rules:

- the KPIs and breakdowns read derived actual/flow data filtered by `trip_id` via `TripAnalysisRepository`, which calls the canonical `tripAnalysisSummary`/`tripActualByDay`/`tripActualByCategory`/`tripActualByTag` queries — never recomputed ad hoc;
- external friend-paid splits with `trip_id` count in actual spend and now also carry their own `tag_id` correctly into the tag breakdown (see §6, and `docs/16` F7 for the bug this fixes);
- transfers may appear in the movement list, but not in actual expense KPIs;
- refunds retain their normal actual behavior and are scoped by the refund's own `trip_id`.

---

## 5. Add/Edit Trip

Purpose: create the event container and define defaults for scoped entry.

The form is a `ModalBottomSheet` (`TripFormDialog`, `ui/trips/TripsScreen.kt`), not an `AlertDialog`, matching the rest of the redesigned app:

Fields:

- name (`OutlinedTextField`);
- type: trip / celebration / other (`SegmentedControl`);
- status: planned / active / finished (`SegmentedControl`);
- start date and optional end date (`FormDatePicker`, the same date-picker component the movement form uses);
- default account (optional, a `FormSelect` dropdown over active accounts — not a chip picker; many-item pill lists don't wrap well, so this and every other "pick one of many" field in the trips/tags forms use the same `ExposedDropdownMenuBox`-based dropdown the movement form already uses);
- icon (`IconPickerRow`) and color (`ColorPickerRow`);
- notes.

Validation (mirrored in `TripsViewModel.onSaveClicked`):

- hard errors only for schema-invalid data: missing name, invalid start/end date, end date before start date;
- default account is optional but must reference an active account when set;
- archiving a trip that still has movements referencing it is allowed (never blocks) — the movements simply keep their `trip_id`.

State: form state belongs in `TripsViewModel`'s `TripFormState`; trip detail (a full page, not a dialog) stays open underneath the edit form and reloads once the save succeeds, so its header reflects the just-saved name/dates without going stale.

---

## 6. Tags Management

Purpose: provide flexible, trip-oriented classification without polluting global categories, optionally linked to a category for icon/color inheritance.

Content (`TagsContent`, `ui/tags/TagsScreen.kt`), rebuilt into the same collapsible-section pattern `CategoriesScreen.kt` uses for Despeses/Ingressos:

- a "Globals" section for tags with neither a trip nor an event-type scope;
- one section per `TripType` that has event-type-scoped tags (title: "Tags de {tipus}");
- one section per specific trip that has trip-local tags (title: the trip's name);
- each section has a `CollapsibleTagSectionHeader` with a count badge and starts expanded;
- each tag row (`TagRow`) renders the tag's actual icon/color via `TagSummary.effectiveIcon()`/`effectiveColor()` (falling back to its associated category's icon/color when the tag has none of its own) plus a scope pill (Global / Tipus d'esdeveniment / Viatge concret).

Add/Edit tag form (`TagFormSheet`), a `ModalBottomSheet`:

- name, icon (`IconPickerRow`), color (`ColorPickerRow`);
- a 3-way scope `SegmentedControl`: **Global** / **Tipus d'esdeveniment** / **Viatge concret** (replacing the old two-pill global/trip-local toggle). Selecting "Tipus d'esdeveniment" reveals a `TripType` `SegmentedControl`. Selecting "Viatge concret" reveals a `FormSelect` dropdown over active trips (not a chip picker — replaced after the initial P5R-7 build, since a growing trip list doesn't wrap well as pills);
- an optional category association: a `FormSelect` dropdown over active categories (including a "Sense categoria" option), used only for icon/color inheritance and rollup — it does not change which movements the tag can be applied to. Also not a chip picker, for the same reason.

Actions: New tag; edit tag; archive tag (`AlertDialog` confirmation, destructive-confirmation exemption); create an event-type- or trip-scoped tag directly from the relevant context; pick a tag from the movement form only after a trip is selected.

Data rules:

- one tag max per movement (`movements.tag_id`);
- a tag is **global** (`tags.trip_id IS NULL AND tags.trip_type IS NULL`), **event-type-scoped** (`tags.trip_type` set, `tags.trip_id NULL`), or **trip-specific** (`tags.trip_id` set, `tags.trip_type NULL`) — never both type-scoped and trip-specific at once. This is enforced by a schema `CHECK (trip_id IS NULL OR trip_type IS NULL)` on fresh installs and mirrored as app-layer validation in `TagsViewModel.onSaveClicked` (`tag_validation_scope_exclusive`) for upgraders, per the project's precedent of mirroring CHECK constraints in the app layer (`AGENTS.md` invariant #11);
- a tag may optionally reference a category (`tags.category_id`) purely for icon/color inheritance (`effectiveIcon()`/`effectiveColor()`);
- a movement may only carry a tag when it also has a trip;
- when a movement's trip changes, clear any incompatible trip-local tag unless the user picks a valid replacement (unchanged from P5).

---

## 7. Movement Integration

Movement list:

- filters gain trip and tag selectors;
- rows with a trip show a subtle trip marker;
- tag is shown as a secondary marker only when useful and never replaces category.

Movement detail:

- shows trip and tag;
- trip opens trip detail (the full page);
- tag opens tag-filtered movement list or tag management depending on context.

Add/Edit movement:

- trip picker appears beside category/account fields;
- when a trip is selected, the tag picker enables and lists global tags plus event-type-scoped tags for that trip's type plus trip-local tags for that trip;
- when no trip is selected, the tag picker is disabled and any selected tag is cleared;
- if opened from trip detail or the Dashboard active-trip card, trip is preselected and the trip's default account takes precedence over the global default account.

External split:

- the no-account split form can carry `trip_id` and its own `tag_id` — both now correctly flow through `v_actual_expense` into the trip's tag breakdown (`docs/16` F7); category remains required for actual analysis.

---

## 8. Trips-As-Blocks In Analysis

Purpose: keep exceptional trip spend from contaminating routine category trends. Unchanged from P5.

Behavior:

- normal analysis groups trip movements into a single trip block by default;
- grouping can be toggled off, showing trip movements under their normal categories;
- tapping a trip block (like any other Analysis aggregate row or chart element) keeps the user on `Anàlisi` — it is **not** a navigation link into trip detail, per the Aggregate Interaction Contract (`docs/11` §6). Trip detail is reached only via the Trips list or the Dashboard active-trip card (§2);
- Dashboard category breakdown follows the same grouping behavior.

Data rules:

- the grouped block is an analysis presentation over `trip_id`, not a stored aggregate;
- the underlying actual/flow rows stay unchanged;
- tag breakdowns are only shown inside trip detail.

---

## 9. Trip Budgets

Scope: trip budget (`budgets.scope='trip'`, `period='one_off'`).

Content:

- trip detail shows an inline `BudgetProgressBar` when an active trip-scope budget exists for that trip (§4), in addition to the "Pressupost del viatge" action that opens the full Budgets surface for editing;
- trip budget form uses the existing budget component shape: limit, optional threshold, start date defaulting to trip start;
- progress compares the trip budget limit against the trip's whole-life derived actual spend (all expense movements tagged with that `trip_id`, unbounded by date — a TRIP-scope budget is one-off, not a calendar period, so advance-booking spend recorded before the trip's own `start_date` counts too); unlike the trip detail KPIs, the budget bar does **not** respect the `is_one_time` toggle — it always reflects total actual spend.

Data rules:

- budgets never block input;
- budget status uses green / amber / red semantic states;
- category-monthly budgets remain unchanged.

---

## 10. Dashboard Active-Trip Card

Purpose: surface the trip covering today, if any, without requiring a trip to open the Trips list first.

Content (`ActiveTripCard`, `ui/dashboard/DashboardScreen.kt`):

- rendered conditionally, only when `DashboardUiState.activeTrip` is non-null — populated via `TripRepository.activeToday(today)` (`WHERE archived_at IS NULL AND status = 'active' AND start_date <= :date AND (end_date IS NULL OR end_date >= :date) ORDER BY start_date DESC LIMIT 1`; if trips ever overlap, the most recently started one wins rather than building a carousel for a rare case);
- positioned after the hero KPI block, before the account grid;
- shows the trip's name/icon and a compact spend-so-far figure (`TripSummary.totalActualCents`);
- a "Veure viatge" link opens `AppOverlay.TripDetail(tripId)`;
- a quick "Afegeix moviment" action opens the movement form pre-filled with the trip's id and default account, the same `openMovementForm(tripId)` path used from the Trips list and from trip detail itself.

---

## 11. Visual Rules

Use `docs/08-design-system.md` without new tokens:

- Trips and trip detail use the standard `FinanceCard`/`IconChip`/`NeutralPill` shapes, `IncomeExpenseChart`, and `MovementListItem` — no bespoke bar-chart or KPI-grid composables remain (trip detail's hero card is a plain `FinanceCard` with one headline `MoneyText`, not a KPI grid).
- Tag chips use category-style muted color identity, but their color is identity only, not finance meaning.
- Forms (trip add/edit, tag add/edit) are `ModalBottomSheet`s with `IconPickerRow`/`ColorPickerRow`/`FormDatePicker`/`SegmentedControl`/`FormSelect` — no raw hex/icon text fields, and no chip-picker fields for a growing list of items (accounts, categories, trips): `FormSelect` (the same `ExposedDropdownMenuBox`-based dropdown the movement form uses, `ui/movements/FormControls.kt`) is used instead, since pills wrap poorly once there are many options. `SegmentedControl`/`FinanceFilterChip` stay for genuinely small, fixed-cardinality choices (type, status, the 3-way tag scope) and for filters (the Trips list status filter row) — not form fields with an open-ended item count. Destructive confirmations (archive) remain `AlertDialog`, per the design system's exemption.
- Empty states stay compact and action-oriented.
- Warnings are inline banners, never blocking modals.
- Amounts use IBM Plex Mono/tabular figures and are formatted as euros only at the UI edge.

---

## 12. String Coverage

Android resource names for:

- `trip_*` for trip list, detail, form, status/type labels, the hero numbers (including the exclude-one-time toggle and its avg/day-excluding secondary line), and validation;
- `tag_*` for tag list, form, the 3-way scope labels, category-association picker, section headers, and validation (including the scope-exclusivity error);
- `dashboard_active_trip_*` for the Dashboard active-trip card;
- movement additions for trip and tag fields;
- analysis additions for trips-as-blocks;
- budget additions for trip-scoped budgets where labels differ from category-monthly budgets.

All values are Catalan. Keep adding strings beside the slice that needs them; do not hardcode user-facing copy in Compose. Dead strings from superseded UI (e.g. the old two-pill tag scope toggle, the old per-day/cumulative trip chart toggle) are removed rather than left behind when they're replaced.

---

## 13. Acceptance Checklist

- Trips list shows statuses, date ranges, total spend, average/day, empty state, and New trip action.
- Trip detail is a full page (`AppOverlay.TripDetail`, not a dialog) with the trip as its own header, a hero spend card (incl. the exclude-one-time toggle), an action row under the hero, a cumulative daily chart, a single category/tag breakdown with a dimension switch (tags using effective icon/color inheritance), an inline budget bar when a trip-scope budget exists, and a scoped movement list capped at 5 with a "Veure tots" drill-down into Moviments.
- Add/Edit trip and tag forms are `ModalBottomSheet`s using `IconPickerRow`/`ColorPickerRow`/`FormDatePicker`/`SegmentedControl`/`FormSelect`; every "pick one of many" field (default account, category association, specific-trip picker) is a `FormSelect` dropdown, not a chip picker; archive stays a destructive `AlertDialog`.
- Tapping a movement row in trip detail's scoped movement list opens the shared movement detail sheet (`MovementDialogHost`), the same as everywhere else in the app.
- Tag management supports three scopes (global / event-type / specific-trip, schema-enforced exclusivity between the last two) plus an optional category association, grouped into collapsible sections.
- Movement integration covers trip and tag pickers, filters, detail display, and trip-default-account precedence.
- Trips-as-blocks analysis behavior stays a presentation layer over derived rows; Analysis aggregate rows never navigate into trip detail (Aggregate Interaction Contract, `docs/11` §6).
- Trip budgets are scoped without changing category-monthly budgets, and show inline in trip detail when active.
- The Dashboard shows an active-trip card only when a trip's date range covers today, with working "Veure viatge" and "Afegeix moviment" actions.
- The external-split tag bug (`docs/16` F7) is fixed: `v_actual_expense` exposes `tag_id` directly, and `tripActualByTag` no longer re-joins `movements`.
- Android resource strings exist for all of the above, in Catalan, with no leftover strings from superseded UI.
