# Personal Finance App — UI/UX Scaffold

> The **structural** UI foundation: information architecture, navigation, the screen inventory with each screen's content blocks, the core flows, and the string-catalog structure. It turns the behavior in `docs/00-Full_Spec.md` into a concrete set of screens.
>
> **Out of scope (separate design pass):** visual style & aesthetics — colour, typography, spacing, theming, iconography, motion. This doc describes *what is on each page and how you move between pages*, not how it looks. Per-screen layout detail is refined just before building each phase.
>
> Phase-specific refinements: `docs/10-core-ledger-ui.md` defines the Android Phase 1 ledger screens; `docs/11-dashboard-analysis-ui.md` defines the Android Phase 2 dashboard and analysis screens; `docs/12-sharing-debts-ui.md` defines the Android Phase 3 people, split, debt, and settlement screens; `docs/13-recurring-refunds-budgets-ui.md` defines the Android Phase 4 recurring, refunds, budgets, and notification screens; `docs/14-trips-tags-ui.md` defines the Android Phase 5 trips, tags, and trip-analysis screens.

---

## 1. Information architecture & navigation

Same screens on both platforms; different shells.

- **Mobile (Android, primary):** bottom navigation with 4–5 destinations; a global **"＋ New movement"** primary action reachable everywhere; secondary destinations reached from within sections.
- **Desktop (Windows, secondary):** left **sidebar** with the same groups; wider content area, frequently **two-pane** (list + detail).

**Navigation groups** (from spec §5.9):

- **Principal:** Dashboard · Analysis
- **Finances:** Movements · Accounts · Recurring
- **Management:** Categories · People · Trips
- **Footer/system:** Settings (theme toggle, backup, sync)

**Global behaviors:** "New movement" available everywhere; **live refresh** (a change on one screen updates related screens); a **read-only banner** when this device doesn't hold the sync token (spec §6, `docs/02`).

---

## 2. Layout conventions (structure only)

- **Mobile:** top app bar (title + contextual actions) · single scrollable column of sections/cards · bottom nav · FAB for "New movement". Editors and pickers are full-screen or bottom sheets.
- **Desktop:** sidebar · content area; lists and their detail shown **side-by-side** (master/detail) where it helps; analysis uses the extra width (multi-column, larger charts); editors are dialogs/panes.
- **Lists** everywhere: support filter + search, are **drill-through** to detail, and show derived figures (balances, "actual") read from the canonical views.
- **Never-block:** warnings are inline, dismissible banners — never modal hard-stops (spec §4.6, §4.7).

---

## 3. Screen inventory & scaffolds

Each entry: **purpose · content blocks · primary actions · platform note.**

### Principal

**Dashboard / Home** (monthly) — spec §4.8b
- Purpose: current-month landing surface.
- Blocks: KPI cards (net worth, month income, month expenses, net flow + savings %); daily income-vs-expense chart; accounts overview (card per account + share of net worth); category breakdown (trips as their own group); latest movements; quick actions.
- Actions: New movement; Import CSV (desktop); tap any block to drill in.
- Platform: mobile single column; desktop multi-column grid.

**Analysis** — spec §4.8
- Purpose: deep spending/earning analysis.
- Blocks: scope selector (month/year/all-time/custom) + period comparison; grouping (category / trip-as-block / account); breakdown list + chart; toggles (actual↔flow, totals↔averages, fixed↔variable, include/exclude one-time); widget area (top merchants, largest expenses, heatmap, category trends, net-worth-over-time, savings rate); filter bar; forecast section (labeled estimate).
- Actions: change scope/grouping/filters; drill any aggregate → movement list.
- Platform: mobile stacked with a filter sheet; desktop filters in a side rail, charts wide.

### Finances

**Movements list**
- Blocks: filter/search bar (type, account, category, trip, tag, person, nature, recurring, amount range, period, "paid by someone"); grouped/sorted list; each row shows concept, date, account, signed amount, shared/"paid by X" marker.
- Actions: New movement; open detail; bulk-select (desktop, v1.5).
- Platform: desktop master/detail (list + detail pane).

**Movement detail**
- Blocks: header (type, amount, date, account); category/payee/notes/trip/tag; type-specific (transfer dest; settlement person+direction; refund→linked expense; split summary if shared); refund list if applicable; audit (created/updated).
- Actions: Edit; Delete; add Refund (expenses); "settles a debt with X" bridge; open split editor.

**Add / Edit movement**
- Blocks: type selector (expense / income / transfer / settlement / refund); amount; date; account (+ dest for transfer); category (expense/income/refund); payee; notes; trip + tag; **Shared** toggle → opens Split editor; **One-time** toggle (mark an extraordinary purchase; expenses only); recurring toggle → template fields.
- Actions: Save (with duplicate warning if flagged); Cancel.
- Platform: mobile full-screen; desktop dialog.

**Accounts list**
- Blocks: account cards (name, type, balance, share of net worth); total net worth.
- Actions: New account; open account; reorder; delete.

**Account detail**
- Blocks: balance + starting balance; scoped movement list; low-balance threshold indicator.
- Actions: Edit account; New movement (prefilled account); removal strategies (delete-soft / delete-empty / delete-with-movements / reassign-then-delete).

**Add / Edit account**
- Blocks: name; starting balance; type; icon; colour; set-as-default; low-balance threshold.

**Recurring**
- Blocks: **mobile** — list ordered by day + monthly total; **desktop** — month calendar grid (solid = instance exists, greyed = pending) + monthly total + month nav. **Due prompts** queue (one card per due occurrence).
- Actions per item: Add payment (confirm, editable) · Skip this period · End recurring · Edit template. Per prompt: Confirm / Skip (with confirm-all / skip-all when several queued).

**Add / Edit template (recurring)**
- Blocks: the movement pre-fill fields; schedule (frequency, anchor day/weekday, custom interval+unit); amount (fixed or variable) + flexibility (± amount, ± days); split config (if shared); notification lead days; active/paused.

### Management

**Categories list**
- Blocks: two-level hierarchy; per-category insight (this-month total + count, this-year total + share, budget bar green/amber/red if budgeted); "Sense categoria" bucket shown.
- Actions: New category; open category; delete; reorder.

**Category detail**
- Blocks: this-month + this-year totals, share of year; budget status; movement list (drill).

**Add / Edit category**
- Blocks: name; kind (expense/income/both); nature (fixed/variable); parent (optional); icon; colour.

**People list**
- Blocks: person rows with net balance (owes you / you owe); totals.
- Actions: New person; open person.

**Person detail**
- Blocks: net balance; itemized breakdown (which splits / expenses-paid-by-them / settlements compose it); history.
- Actions: **Settle up** (helper) · Export/share debt statement · Edit · Delete (warn if balance ≠ 0).

**Add / Edit person**
- Blocks: name; avatar; colour; notes.

**Trips list**
- Blocks: trip cards (name, type, dates/ongoing, status).
- Actions: New trip; open trip.

**Trip detail** — spec §3.11
- Blocks: KPIs (total spent, days, avg/day); stacked daily chart (per-day ↔ cumulative); breakdown by category & by tag (total ↔ avg/day); scoped movement list (new movements default within trip dates + pre-attach).
- Actions: Edit trip; New movement (scoped); set default account; trip budget.

**Add / Edit trip**
- Blocks: name; type (trip/celebration/other); start/end (end optional); default account; budget; notes; status.

**Tags management**
- Blocks: tag list (global vs trip-local marker).
- Actions: New tag; edit; delete; scope (global / trip-local).

**Budgets**
- Blocks: budget list with progress (limit vs actual, over/under); scope (category-monthly first).
- Actions: New budget; edit; set alert threshold.

### Editors / flows (modal or sub-screens)

**Split editor**
- Blocks: participants (user + people picker); method tabs (equal / exact / percentage) → resolves to absolute cents (live reconcile to total); payer selector (user, or person for §2.6); per-participant amount rows; remainder indicator.
- Variants: normal; §2.5 "paid by other" (user share 0, label "pagat per X"); §2.6 external (no account movement, carries own date/category/trip).

**Settle-up**
- Blocks: person; direction (inferred, editable); amount (prefilled = outstanding); account; notes; over-settlement warning.

**Refund**
- Blocks: linked expense; amount (cash); actual-refund amount (shared-expense case); category (inherited); date; over-refund warning.

**CSV import wizard** (desktop only) — spec §4.6b
- Step 1 Upload: choose file.
- Step 2 Map: auto-detected date/description/amount columns (confirm/correct) + destination account.
- Step 3 Review: per-row editable drafts (sign→type, auto-category, duplicate flags, edit/exclude, split/refund/settlement bridge) → **bulk save** as one batch.

### System

**Settings** — spec §5.8
- Blocks: appearance (light/dark) · backup / export / import · sync encryption key · recalculate balances · resync recurring · notification settings (lead time, budget & low-balance alerts) · app version / update.

**Onboarding / first run**
- Blocks: create first account + starting balance; seed default Catalan categories; (optional) set default account.

**Sync / device state** — spec §6, `docs/02`
- Blocks: token state (this device writer / read-only); **prominent read-only banner** with staleness note; actions to hand over / take control; reclaim-or-discard a lost session.

**Search**
- Global search + the same filter set as Movements/Analysis; clear-all. (Folds into the Movements/Analysis filtering surfaces.)

---

## 4. Core flows (step sequences)

- **Record an expense:** New movement → type=expense → amount/account/category → (optional Shared → Split editor) → Save (→ duplicate warning if flagged).
- **Shared expense:** in Add movement, toggle Shared → Split editor (pick people, method, reconcile) → Save → debts update (derived).
- **Friend paid (§2.6):** New shared expense → payer = person, no account movement → records "you owe X" + counts your share as actual.
- **Settle a debt:** Person detail → Settle up → confirm direction/amount/account → Save → balance returns toward 0.
- **Confirm a recurring item:** open Recurring → due prompt → edit if needed → Confirm (creates movement) or Skip.
- **Refund:** Movement detail (expense) → Add refund → amount (+ actual-refund if shared) → Save → nets down actual in its period; account inflow.
- **Import CSV (desktop):** Import → upload → map columns → review/edit drafts → bulk save.
- **Hand off to desktop / take back:** sync state screen → hand over token (mobile→desktop) or reclaim/return.

---

## 5. String catalog (structure)

- All user-facing copy is **Catalan**, stored as an externalized key→value catalog (no hardcoded literals), so the apps stay i18n-ready (spec §7.5).
- **Keys** are namespaced by screen/feature, e.g. `movement.add.title`, `movement.field.amount`, `account.removal.reassign`, `sync.banner.readOnly`, `common.save`, `common.cancel`.
- Each app maps the same keys to its native resource format (Android string resources; WinUI `.resw`). The **key set is shared**; values are authored once in Catalan.
- Build the catalog incrementally per phase, but keep the namespacing convention from the start.

---

## 6. Deferred to the style pass (separate)

Colour palette & theming (light/dark), typography scale, spacing/grid, component visual specs, iconography, illustration, motion/transitions, empty-state art. This scaffold fixes structure and content; the style pass dresses it.
