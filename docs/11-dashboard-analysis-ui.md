# Dashboard and Analysis UI - Android P2

> Scope: P2-1. This refines the Android screens needed for Phase 2: the current-month dashboard and the main analysis surface. It does not add new product rules beyond `docs/00-Full_Spec.md`, `docs/04-data-model.md`, or `docs/08-design-system.md`.

---

## 1. Phase Scope

P2 turns the Phase 1 ledger into useful insight:

- show a current-month dashboard;
- add a broader analysis screen with time scopes, comparison, grouping, toggles, filters, and inspectable charts;
- keep all totals view-backed through shared SQL introduced in P2-2;
- keep charts focused: daily income vs. expense first, then additional widgets.

P2 must not implement people/debt analysis, recurring prompts, budgets, trips/tags management, or forecasting beyond clearly labeled placeholders if a later task needs them. Those features keep their own phases.

---

## 2. Data Semantics

Analysis has two modes that must stay explicit:

- **Actual:** what the user really spent or earned. It is based on `v_actual_expense` and `v_actual_income`. Transfers and settlements are excluded. Shared-expense handling follows the canonical views.
- **Flow:** what moved through accounts. It is based on `v_account_flow` and balance views. Transfers and settlements count because they affect account balances.

Rules:

- money remains integer cents; formatting happens only at the UI edge;
- dashboard and analysis must not recompute balances, actuals, or flow in app code;
- top-level Analysis aggregates are display-only and must not recompute balances, actuals, or flow outside the shared SQL results;
- archived/deleted rows stay excluded through canonical SQL filters;
- flow and net-worth figures are account-level and intentionally ignore the actual-only filters (category nature, one-time); those toggles apply in actual mode only. Net worth is a balance (stock), so it ignores the period entirely.

---

## 3. Navigation Shape

Once P2 dashboard implementation lands, the primary Android destinations should be:

- Dashboard;
- Movements;
- Accounts;
- Analysis;
- Categories.

The global New movement action remains available on every destination and opens the modal without changing the selected destination.

Dashboard is the default landing screen after onboarding once P2-3 is implemented. Until then, the existing Phase 1 start destination can stay unchanged.

---

## 4. Dashboard

Purpose: a dense, current-month landing screen that answers "where am I this month?" without turning into a full analysis workspace.

Content order:

1. Header:
   - title: Dashboard / Home;
   - current month label;
   - quick link to Analysis.
2. KPI strip:
   - net worth from active account balances;
   - this-month actual income;
   - this-month actual expenses;
   - net flow and savings-rate percentage.
3. Daily flow chart:
   - income vs. expense per day for the current month;
   - empty days stay visible so rhythm is readable;
   - tapping a day opens the movement list for that date once drill-down exists.
4. Accounts overview:
   - active accounts sorted by display order;
   - balance and share of net worth;
   - tap opens the account detail/flow.
5. Category breakdown:
   - current-month actual spending/income grouped by category;
   - a parent-with-children (container, spec §3.2) rolls up: it appears once with its own + all
     children's spend, and its sparkline/scatter/treemap figures roll up too; children are not
     shown as separate peer rows in the breakdown. The dashboard top-categories list uses the same
     rollup. Setting the analysis category filter to a container includes all its active children;
   - the Fixed/Variable split stays at the leaf level (`nature` is per-category);
   - trips later appear as their own block when trip grouping exists;
   - sorted by absolute amount descending;
   - uncategorized appears as "Sense categoria".
6. Latest movements:
   - recent confirmed movements;
   - tap opens movement detail.
7. Quick actions:
   - New movement;
   - Import CSV appears only on desktop later, not on Android P2.

Empty states:

- no movements: show the KPI shell, empty chart, and a New movement action;
- accounts missing should be impossible after onboarding, but still show the existing account-required copy if reached.

---

## 5. Analysis Screen

Purpose: the main workspace for comparing periods and changing the question without leaving the analysis surface.

Top controls:

- scope selector: month, year, all time, custom;
- period selector: current scope with previous/next controls where meaningful;
- comparison toggle: current period vs. previous equivalent period;
- clear filters action when any filter is active.

Primary result area:

1. Summary row:
   - actual expense;
   - actual income;
   - net actual;
   - savings rate when income is non-zero.
2. Main chart:
   - for month: daily income vs. expense;
   - for year/all time: period buckets, monthly by default.
3. Breakdown list:
   - group by category (actual mode) or account (flow mode); trip-block grouping is deferred to Phase 5 with trips;
   - percentage bar per row;
   - amount and delta vs. comparison period when enabled;
   - rows are display-only and do not open movements or trip detail.

Mode controls:

- actual vs. flow;
- totals vs. averages;
- fixed vs. variable;
- include, exclude, or only extraordinary one-time spend.

Filters:

- text search;
- type;
- account;
- category;
- nature: fixed/variable;
- amount range;
- date range for custom scope.

Widget area:

- top merchants / most frequent expenses;
- largest expenses;
- spending heatmap;
- category trends;
- net-worth over time;
- savings rate by period.

These widgets can arrive incrementally through P2-8. The Analysis screen should use the same filter/scope state for all widgets. The widgets render in actual mode and always show totals (they are not affected by the totals-vs-averages toggle, since largest expenses, merchants, and net worth are not meaningfully averaged). The daily spending heatmap and category trends are only drawn for bounded spans (roughly up to one year), so all-time stays performant.

---

## 6. Aggregate Interaction Contract

Top-level Analysis aggregate rows and chart elements are not navigation links. Tapping summary cards, chart bars/points, treemap blocks, breakdown rows, or comparison waterfall bars must keep the user on `Anàlisi`. This keeps charts available for their own gestures and avoids opening movement lists whose raw ledger rows may not faithfully add up to netted actual aggregates.

Filters, period controls, custom date controls, scope/value toggles, tabs, and inbound account/category filters remain interactive. Navigation into Analysis from Dashboard, Accounts, or Categories may still pre-apply filters because those links originate outside the Analysis page.

---

## 7. Visual Rules

Use `docs/08-design-system.md`:

- KPI hero/card styling for dashboard summary;
- daily flow chart uses green income and neutral expense bars;
- net-worth trend uses the indigo line treatment;
- category breakdown uses category colors but never relies on color alone;
- cards are individual repeated items, not nested page sections;
- dashboard is work-focused and scannable, not a marketing-style hero page.

---

## 8. String Coverage

P2-1 adds Android resource names for:

- `dashboard_*`;
- `analysis_*`.

Strings are Catalan. Keep implementation code in English and keep all visible copy in resources.

---

## 9. Acceptance Checklist

- Dashboard and Analysis responsibilities are separated.
- Actual vs. flow is explicit and view-backed.
- Dashboard stays current-month focused.
- Analysis defines scopes, comparison, grouping, toggles, filters, and display-only widgets.
- Android resource strings exist for the planned screens.
- P2-1 remains documentation and resource preparation; no SQL/query implementation is expected in this task.
