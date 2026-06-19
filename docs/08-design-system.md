# Design System — Finance App

> **Direction A · refined** · v0.1
> A precise, calm interface where **color carries meaning, never decoration**. Neutral grays and ink structure everything; each hue has one fixed job — type, category, status, direction. Built for mobile (Android) and desktop (Windows), in light and dark mode.

This document is the source of truth for tokens, typography, spacing, and the component library. The living, visual reference lives in `Sistema de disseny.dc.html`.

---

## 1. Principles

| Principle | What it means |
|---|---|
| **Neutral does the work** | Cool grays and ink structure the entire interface. Density is compact; reading stays calm. |
| **Color communicates** | Each hue has a fixed meaning — green income, red debt, amber alert. Never color for its own sake. |
| **Never blocks** | Warnings are inline, dismissible banners. Indigo is reserved for action. Only destructive confirmations interrupt. |

---

## 2. Color

### 2.1 Neutrals — light mode

The structural backbone. Cool-gray ramp from white to near-black ink.

| Token | Hex | Typical use |
|---|---|---|
| `N0`   | `#FFFFFF` | Surfaces, cards |
| `N50`  | `#F7F8FA` | App background, table headers |
| `N100` | `#F0F1F4` | Track / fill backgrounds |
| `N150` | `#EAECEF` | Hairline dividers |
| `N200` | `#E2E5EA` | Default borders |
| `N300` | `#CDD2DA` | Strong borders |
| `N400` | `#A4ABB7` | Disabled text |
| `N500` | `#8A92A0` | Secondary / muted text |
| `N700` | `#4A5160` | Body labels |
| `N900` | `#0B0D12` | Primary ink |

### 2.2 Neutrals — dark mode

Cool, almost-black surfaces (never pure black). Elevation comes from lighter surfaces and borders rather than shadow.

| Token | Hex | Typical use |
|---|---|---|
| `D0`   | `#0E1014` | App background |
| `D50`  | `#16181E` | Cards |
| `D100` | `#1E2128` | Track / control backgrounds |
| `D150` | `#23262E` | Dividers / borders |
| `D200` | `#2C303A` | Strong borders / active segment |
| `D300` | `#3A3F4B` | — |
| `D400` | `#565E6C` | — |
| `D500` | `#7C8494` | Secondary text |
| `D700` | `#AEB6C4` | Body labels |
| `D900` | `#F2F4F8` | Primary text |

### 2.3 Brand / interactive — Indigo

Reserved exclusively for **action and interactive state**. Never used to decorate.

| Token | Hex | Use |
|---|---|---|
| Default | `#3344E0` | Primary buttons, links, active nav |
| Hover   | `#2A3BCB` | Hover state |
| Pressed | `#2230AE` | Pressed state, active nav text |
| Tint    | `#ECEEFD` | Light fills, active backgrounds |
| Dark (mode) | `#6E7BFF` | Indigo on dark surfaces |

### 2.4 Functional colors — fixed meaning

Each role maps to one hue, with a lighter variant for dark mode. **Expense is the default — plain ink, no hue.**

| Role | Use | Light | Dark |
|---|---|---|---|
| Income / Positive | Income, gains, owed to you | `#1F8F5F` | `#43C28A` |
| Expense | Default — ink, no hue | `#20242E` | `#F2F4F8` |
| Transfer | Movements between your own accounts | `#5B6B86` | `#8A99B5` |
| Settlement | Debt payments | `#B9772A` | `#D9A152` |
| Refund | Linked returns | `#128A93` | `#3FB6BE` |
| Debt / Danger | You owe, over budget, destructive | `#CC4B4B` | `#E8736F` |
| Alert | Near the limit, warnings | `#C98A14` | `#E0A93C` |

### 2.5 Category identity — muted palette

Categories get a muted color + soft tint background for their icon chip. Distinct from functional color.

| Category | Color | Tint bg | Icon |
|---|---|---|---|
| Housing | `#C77D4A` | `#F4E7DA` | `home` |
| Groceries | `#3F9E72` | `#E0F0E8` | `shopping_cart` |
| Restaurants | `#C9554E` | `#F6E2DF` | `restaurant` |
| Transport | `#4B7DC4` | `#E1EAF6` | `directions_car` |
| Leisure | `#8A6FD1` | `#EBE4F7` | `sports_esports` |
| Subscriptions | `#2C9AA6` | `#DCEFF1` | `credit_card` |
| Uncategorized | `#9097A3` | `#F0F1F4` | `more_horiz` |

---

## 3. Typography

**Geist** for the entire interface. **Geist Mono** for all ledger figures, with **tabular numerals always on** (`font-feature-settings: 'tnum'`) so amount columns align. Locale formatting: **decimal comma, thousands dot** (e.g. `18.420,15 €`).

```
font-family: Geist, sans-serif;          /* interface */
font-family: 'Geist Mono', monospace;    /* figures — tnum on */
```

### Type scale

| Role | Size / weight | Example |
|---|---|---|
| Display | 28 / 600 | Net worth |
| Title | 21 / 600 | June 2025 |
| Heading | 16 / 600 | Expenses by category |
| Body | 14 / 500 | Dinner Els Pescadors |
| Body small | 13 / 500 | Restaurants · paid by you |
| Label | 12 / 500 | Checking account |
| Caption | 11 / 500 | vs May · −8,2% |

Headings and body text use `0em` tracking.

---

## 4. Spacing, radius & elevation

### Spacing — base 4

`2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 32` (px). Compact density; default gaps are 8–14px.

### Radius

| Token | Value | Use |
|---|---|---|
| `r1` | `7px` | Small chips, inputs-in-rows |
| `r2` | `10px` | Buttons, fields, small cards |
| `r3` | `14px` | Cards, panels |
| `r4` | `18px` | Sheets, large surfaces |
| `pill` | `999px` | Toggles, pill badges |

### Elevation

| Token | Shadow | Use |
|---|---|---|
| `e0 · flat` | `none` (1px border) | Default — bordered surfaces |
| `e1` | `0 1px 2px rgba(11,13,18,.06)` | Separators |
| `e2` | `0 2px 8px rgba(11,13,18,.06)` | Cards |
| `e3` | `0 8px 18px -6px rgba(51,68,224,.45)` | Button / FAB (indigo glow) |
| `e4` | `0 24px 50px -12px rgba(15,18,30,.28)` | Sheets / dialogs |

> In **dark mode**, elevation is expressed through lighter surfaces and borders rather than shadow.

---

## 5. Iconography

**Material Symbols Outlined**, medium weight (`wght 400`). Outlined by default; **filled (`FILL 1`)** for the active navigation item only.

```
font-variation-settings: 'FILL' 0;   /* default */
font-variation-settings: 'FILL' 1;   /* active nav */
```

Core set: `space_dashboard` · `receipt_long` · `monitoring` · `account_balance_wallet` · `groups` · `add` · `search` · `tune` · `sort` · `home` · `shopping_cart` · `restaurant` · `directions_car` · `sports_esports` · `credit_card` · `savings` · `payments` · `swap_horiz` · `handshake` · `assignment_return` · `autorenew` · `trending_up` · `calendar_month` · `notifications` · `settings` · `warning` · `error` · `info` · `check_circle` · `north_east` · `south_west` · `edit` · `archive` · `bolt` (one-time) · `lock` (read-only).

---

## 6. Components

### Buttons

| Variant | Spec |
|---|---|
| Primary | Indigo `#3344E0` fill, white text, `r2` (11px), height 40, `e3` glow shadow. Optional leading icon. |
| Secondary | White surface, `N200` border, ink text. |
| Ghost | No fill/border, indigo text. |
| Destructive | White surface, soft red border `#E7C4C4`, red text `#CC4B4B`. |
| Disabled | `N100` fill, `N400` text. |
| Icon button | 40×40, `N200` border. |
| Small | Height 32, `r` 9px. |

### Filters & segments

- **Filter chips:** active = ink `#0B0D12` fill + white text; inactive = `N150`-bordered, `N700` text; dropdown chips append `expand_more`.
- **Segmented control:** `N100`/`F4F5F7` track, selected segment = white fill + `e1` shadow.
- **Toggle:** 38×22 pill — on = indigo, off = `#D7DBE1`; 18px white knob.
- **Checkbox:** 18px, `r` 5px, indigo fill + white `check` when selected.

### Fields

- Default: height 44, `N200` border, `r2` (11px), 13px text.
- **Focused / amount:** 1.5px indigo border + `0 0 0 3px rgba(51,68,224,.1)` focus ring. Amount fields show a muted `€` prefix and large Geist Mono figure.
- Select: trailing `expand_more` in `N` muted.

### Cards

- **KPI hero:** dark ink `#0B0D12` surface, white text, `r3`; trailing % delta badge in green tint. Net-worth figure in Geist Mono.
- **Account / list card:** white, `N150` border, `r3` (13px), icon chip + title + sub + right-aligned mono amount.

### Transaction row

Icon chip colored by **category** (tint bg + category color), title (optional inline badges: `autorenew` recurring · `group` shared · `bolt` one-time/extraordinary — a **neutral** pill, since one-time is orthogonal to type and category), category subtitle, right-aligned mono amount colored by **type** (functional color; expense = ink).

### Person row (debt direction)

Round colored monogram avatar, name + context sub, right-aligned amount **and direction label** colored by direction — `owes you` = green, `you owe` = red, `settled` = neutral `#9097A3`.

### Bars

- **Category bar:** 5px track `N100`, fill in category color.
- **Budget bar:** 6px track; fill + status label colored by state — within = green, near limit (≈94%) = amber, over = red. Always shows `spent / limit`.

### Inline banners — never block

Full-width, `r` 11px, soft tint bg + matching border, leading status icon, dismissible `close`. Four kinds:

| Kind | Icon | Light bg / border / text |
|---|---|---|
| Info | `info` | `#ECEEFD` / `#D5D9FA` / `#28308C` |
| Success | `check_circle` | `#E6F4EC` / `#C6E6D4` / `#136443` |
| Alert | `warning` | `#FBF1DD` / `#F1E2BE` / `#7A5A12` |
| Error | `error` | `#FBEAEA` / `#F0CDCD` / `#8E2F2F` |

### Read-only / sync state

A **persistent, non-dismissible** top banner (distinct from the never-block banners above) when this device is read-only — it does not hold the sync token (spec §6): alert tint, leading `lock` icon, names which device currently holds control, with a single inline action (hand over / reclaim). While read-only, editing affordances — the FAB / "New", edit, delete, save — render in the **disabled** style (`N100` fill, `N400` text) and are inert.

### Navigation

- **Mobile:** bottom nav (5 groups) with a centered indigo **FAB** raised −22px; active item = indigo + filled icon.
- **Desktop:** same groups become a **248px sidebar**, grouped with section labels; active = light-indigo (`#EEF0FE`) fill + filled icon; pending counts as indigo pills.

### Charts

- **Daily flow:** dual bar (income up, green `#1F8F5F`; expense down, gray `#6E7891`) split on a center axis.
- **Net worth:** indigo line + 8% fill area sparkline.

---

## 7. Desktop (Windows)

The same system in a window.

- **Window chrome:** draggable title bar in warm `#F3F1EC` (`N50`-warm) with min/max/close; white top bar below.
- **Top bar:** screen title + **command search (`Ctrl K`)** + notifications.
- **Sidebar:** 248px, grouped; primary "New transaction" action at top (`Ctrl N`); active = light indigo + filled icon; pending counter pill.
- **Master / detail:** master list (~540px full-screen) with the selected row highlighted (very light indigo `#F4F5FE` + `#DADFFB` border) and a detail panel filling the rest.
- **Data table:** `N50` header, thin row separators, amounts in a fixed right-aligned tabular-numeral column.
- **Calendar cell:** current day = light indigo fill + indigo number; colored dots = recurring series by category.

Full screens: `Sistema - Escriptori.dc.html`.

---

## 8. Modals

Same content, two forms — actions always anchored at the bottom.

- **Scrim:** 55% ink — `rgba(11,13,18,.55)`.
- **Desktop · centered dialog:** white surface, `r4` (16px), `e4` shadow, width **344–480px** by density. Header (title + close) → scrollable body → footer (secondary left, primary right).
- **Mobile · bottom sheet:** full-width, anchored bottom, `r4` top corners, 34×4 handle on top. Primary action anchored; no secondary button (dismiss via handle or ✕).
- **Destructive confirmation — identical on both devices:** compact centered alert (~312px), **never a sheet**. Red circular icon, description of what's lost, destructive button in red (right on desktop / top when stacked on mobile).

All non-destructive modals can be dismissed by tapping the scrim. Full screens: `Modals.dc.html`.

---

## 9. Dark mode

Cool, almost-black surfaces (not pure black). Functional color **lightens slightly** to hold contrast (see §2.4 Dark column). Shadows give way to lighter surfaces and borders. Indigo shifts to `#6E7BFF`; on indigo buttons the label becomes near-black `#0E1014`.

---

## 10. Accessibility

- Body and label text meets **WCAG AA** contrast on its surface; muted `N400`/`N500` is for non-essential text only.
- Touch targets are **≥ 44px** on mobile (rows, buttons, toggles, nav items sized accordingly).
- **Never rely on colour alone** — direction and status always pair colour with an icon or label (e.g. the person-row "owes you"/"you owe" text; expense = ink default; the one-time `bolt` badge).

## 11. Deferred (extend per phase)

Specced when their phase arrives, to avoid over-designing now: the **split-line row** (Phase 3), **empty states** across screens, the analysis widgets beyond the two charts — **heatmap** and **category trend lines** (Phases 2 / 5) — and the **CSV-import wizard stepper** (Phase 6C).

## File map

| File | Contents |
|---|---|
| `Sistema de disseny.dc.html` | This system, fully visualized (the living reference) |
| `Sistema - Escriptori.dc.html` | Full desktop validation screens |
| `Modals.dc.html` | Full modal screens |
| `DesktopNav.dc.html` | Desktop navigation |
| `Estils - Direcció A.dc.html` | Style direction A explorations |
| `Estils - Mostres.dc.html` | Style samples |
