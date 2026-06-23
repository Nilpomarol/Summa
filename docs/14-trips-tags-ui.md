# Trips and Tags UI - Android P5

> Scope: P5-1. This refines the Android screens needed for Phase 5: trips/events, trip-scoped analysis, tags, trip attachment on movements, trip-as-block analysis behavior, and trip budgets. It does not add product rules beyond `docs/00-Full_Spec.md`, `docs/04-data-model.md`, `docs/08-design-system.md`, or `docs/09-app-architecture.md`. It is documentation and string preparation only - no repository or production screen is implemented in P5-1.

---

## 1. Phase Scope

P5 makes trips/events a first-class analysis unit:

- create and manage trips/events with type, status, dates, default account, notes, icon, and color;
- attach movements and no-account external splits to one trip;
- assign at most one tag to a movement, only when the movement belongs to a trip;
- show a trip detail analysis with KPIs, chart, category and tag breakdowns, and a scoped movement list;
- group trip spend as a single block in normal analysis by default, with drill-through into trip detail;
- add trip-scoped budgets in the existing budget model.

P5 must not introduce person groups, multi-tag movements, many-to-many tags, desktop bulk editing, CSV import behavior, sync changes, or forecasting. A movement may have a global category and one trip tag at the same time. Tags are global and reusable by default, with optional trip-local tags for one-off trip context.

---

## 2. Navigation Shape

Trips live under **Management**, matching `docs/07-ui-ux.md` and spec section 5.9. On Android, the Trips list is reached from the management area rather than as a new bottom-nav destination. Trip detail is a drill-through screen from:

- Trips list;
- grouped trip blocks in Dashboard / Analysis;
- movement detail when a movement has a `trip_id`;
- trip budget alerts or trip-budget surfaces once P5-6 lands.

Tags are managed from a secondary **Tags** surface reachable from Trips and from the movement form's tag picker. A trip-local tag is created from a trip context so its `trip_id` is preserved.

---

## 3. Trips List

Purpose: show exceptional events as compact, scannable objects and make the current/active trips easy to resume.

Content:

- title: Trips;
- filter chips for planned / active / finished, plus all;
- active/ongoing trips first, then planned, then finished;
- each trip row/card shows icon, name, type, status pill, date range (or ongoing), default account when set, and a small actual-spend figure when available;
- empty state with a New trip action and a short explanation.

Actions:

- New trip;
- open trip detail;
- edit trip;
- archive trip via the detail or overflow action.

Data rules:

- archived trips are hidden by default;
- status is the persisted `trips.status` value (`planned`, `active`, `finished`);
- spend figures are derived from canonical actual/flow views and never stored on the trip.

Visual identity:

- use the standard list-card row with an icon chip;
- status uses neutral pills, not decorative color;
- money figures use `MoneyText` and semantic colors.

---

## 4. Trip Detail

Purpose: answer what the trip cost, how spending evolved over trip days, and what it cost by category/tag.

Header:

- trip name;
- type/status/date range;
- default account indicator when set;
- actions: Edit, New movement, Manage tags, Budget.

KPIs:

- actual trip spend (the user's own actual expense, net of refunds);
- total account outlay/flow for trip movements;
- number of trip days;
- average actual spend per day.

Analysis blocks:

- daily chart: per-day vs cumulative toggle;
- category breakdown: total vs average/day toggle, percentage bars;
- tag breakdown: total vs average/day toggle, percentage bars;
- untagged trip expenses appear as an "untagged" bucket, visually muted;
- scoped movement list filtered to the trip.

Actions:

- New movement from trip detail preselects the trip and uses the trip's default account when present;
- opening a movement keeps the normal movement detail behavior;
- Budget opens the trip-scoped budget form once P5-6 lands.

Data rules:

- the KPIs and breakdowns read derived actual/flow data filtered by `trip_id`;
- external friend-paid splits with `trip_id` count in actual spend, matching `v_actual_expense`;
- transfers may appear in the movement list and flow/outlay views, but not in actual expense KPIs;
- refunds retain their normal actual behavior and are scoped by the refund's own `trip_id`.

---

## 5. Add/Edit Trip

Purpose: create the event container and define defaults for scoped entry.

Fields:

- name;
- type: trip / celebration / other;
- status: planned / active / finished;
- start date and optional end date;
- default account (optional);
- icon and color;
- notes.

Validation:

- hard errors only for schema-invalid data: missing name, invalid dates, end date before start date;
- default account is optional but must reference an active account when set;
- warnings are non-blocking if a finished trip has no end date or if a trip is archived while movements still reference it.

State:

- form state belongs in the ViewModel;
- save returns validation errors for schema-invalid input only.

---

## 6. Tags Management

Purpose: provide trip-only classification without polluting global categories.

Content:

- global tags first;
- trip-local tags grouped under their trip when viewing all tags, or shown directly inside trip detail;
- each tag row shows icon, name, color chip, and scope pill (global / local to trip).

Actions:

- New tag;
- edit tag;
- archive tag;
- create trip-local tag from a trip context;
- pick a tag from the movement form only after a trip is selected.

Data rules:

- one tag max per movement (`movements.tag_id`);
- a tag can be global (`tags.trip_id IS NULL`) or trip-local (`tags.trip_id = trip.id`);
- a movement may only carry a tag when it also has a trip;
- when a movement's trip changes, clear any incompatible trip-local tag unless the user picks a valid replacement.

---

## 7. Movement Integration

Movement list:

- filters gain trip and tag selectors;
- rows with a trip show a subtle trip marker;
- tag is shown as a secondary marker only when useful and never replaces category.

Movement detail:

- shows trip and tag;
- trip opens trip detail;
- tag opens tag-filtered movement list or tag management depending on context.

Add/Edit movement:

- trip picker appears beside category/account fields;
- when a trip is selected, the tag picker enables and lists global tags plus local tags for that trip;
- when no trip is selected, the tag picker is disabled and any selected tag is cleared;
- if opened from trip detail, trip is preselected and default account takes precedence over the global default account.

External split:

- the no-account split form can carry `trip_id`;
- tag is not needed for external split in P5 unless the implementation later stores a movement-backed tag equivalent; category remains required for actual analysis.

---

## 8. Trips-As-Blocks In Analysis

Purpose: keep exceptional trip spend from contaminating routine category trends.

Behavior:

- normal analysis groups trip movements into a single trip block by default;
- grouping can be toggled off, showing trip movements under their normal categories;
- drilling into a trip block opens the trip detail analysis;
- Dashboard category breakdown follows the same grouping behavior.

Data rules:

- the grouped block is an analysis presentation over `trip_id`, not a stored aggregate;
- the underlying actual/flow rows stay unchanged;
- tag breakdowns are only shown inside trip detail.

---

## 9. Trip Budgets

Scope for P5: trip budget (`budgets.scope='trip'`, `period='one_off'` unless docs are later updated).

Content:

- trip detail header may show a budget progress bar when one exists;
- trip budget form uses the existing budget component shape: limit, optional threshold, start date defaulting to trip start;
- progress compares trip budget limit against derived actual spend for that trip.

Data rules:

- budgets never block input;
- budget status uses green / amber / red semantic states;
- category-monthly budgets remain unchanged.

---

## 10. Visual Rules

Use `docs/08-design-system.md` without new tokens:

- Trips use standard list-card rows/cards, icon chips, neutral status pills, and semantic money figures.
- Tag chips use category-style muted color identity, but their color is identity only, not finance meaning.
- Trip analysis charts reuse the existing chart language; trip daily spend is neutral/category colored, not action indigo.
- Empty states stay compact and action-oriented.
- Warnings are inline banners, never blocking modals.
- Amounts use Geist Mono/tabular figures and are formatted as euros only at the UI edge.

---

## 11. String Coverage

P5-1 adds Android resource names for:

- `trip_*` for trip list, detail, form, status/type labels, KPIs, analysis modes, and validation;
- `tag_*` for tag list, form, scope labels, picker states, and validation;
- movement additions for trip and tag fields;
- analysis additions for trips-as-blocks and trip drill-through;
- budget additions for trip-scoped budgets where labels differ from category-monthly budgets.

All values are Catalan. Keep adding strings beside the slice that needs them; do not hardcode user-facing copy in Compose.

---

## 12. Acceptance Checklist

- Trips list is specified with statuses, date ranges, default account context, empty state, and New trip action.
- Trip detail is specified with KPIs, daily chart, category/tag breakdowns, scoped movement list, and scoped New movement behavior.
- Add/Edit trip covers type, status, dates, default account, icon/color, notes, and schema-invalid validation.
- Tag management is specified with global vs trip-local scopes and the one-tag-per-trip-movement rule.
- Movement integration is specified for trip and tag pickers, filters, detail display, and trip-default account precedence.
- Trips-as-blocks analysis behavior is specified as a presentation layer over derived rows, not stored aggregates.
- Trip budgets are scoped without changing category-monthly budgets.
- Android resource strings exist for the planned P5 screens.
- P5-1 remains documentation and resource preparation; no repository or production screen implementation is expected in this task.
