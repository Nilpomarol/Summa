# Redesign Plan

Status: agreed direction for the Android redesign (September 2026). The visual system is a proposal to verify on device, not a fixed spec. It becomes durable only as each part ships: move what ships into `design.md`/`product.md` and delete it here. Per-screen observations live in [redesign-notes.md](redesign-notes.md).

Visual references (mood only, not content, IA, or layout): [style board](../images/redesign/style-board.webp), [logo](../images/redesign/logo.webp).

## Information architecture

### Page model (app-wide rule)

- Anything you can look at is a route: Account, Person, Trip, Category, Tag, Goal, plus the hub pages.
- Creating or editing opens a bottom sheet above the current page. Confirmations are dialogs.
- Back always pops. No in-page state swaps with their own `BackHandler`, and contextual flows do not pop the stack (`openSection`/`openManagement` behaviour goes).

### Root navigation

`Inici` · `Moviments` · (+) · `Anàlisi` · `Més`. Unchanged skeleton.

### Home: current context

Home shows the state of what is used daily, not a global overview.

- Header: the logo tile, the app name, and today's date (no owner name is stored).
- Hero: the chosen account's balance (account picker top right), total net worth as a secondary line, and this month's money in and out of the account. The choice persists across app starts; the default account is the initial one.
- This month: one compact block, not a card: spent against the overall budget, a bar with the forecast as a paler run, one caption line.
- Pendent: the one card on the page. Due recurring items, low balance, category budget warnings, and open debts. The cold-start due-reminders sheet stays; this is the persistent entry point.
- Active trip, when there is one, as a plain row.
- Latest movements from all accounts; "Veure tots" opens Moviments.
- Net worth also heads the Comptes page.

### Entity detail template

One layout for Account, Person, Category, Trip, Tag, and Goal: header with the key figure, primary actions, then the shared movement list with that entity's filter applied. The account variant leads rows with the account's signed physical delta so the list reconciles with the balance.

### Més hub

```text
Diners         Comptes · Objectius
Planificació   Pressupostos · Recurrents
Organització   Categories · Viatges · Etiquetes
Persones
Configuració
```

Contextual shortcuts stay as secondary entries.

### Movements

Day grouping (no day totals: income/expense totals must come from canonical SQL, and the list filters in app code), tags as a filter, and Recurring as a labelled entry. Quick type filters and the filter button always share one non-scrolling, non-wrapping row; the button sits at the right and, while filters are active, widens into a filled pill with the count and an × that clears them.

### Analysis

Spending leads: the hero compares with a typical month (or last year), then the month's pace, where the money went (top rows, the rest folded), how it came about, twelve months of bars, and net worth. Only the account filter remains; a category opens a detail sheet, a trip its page, a bar its month.

## Visual system

Direction: editorial, organic, restrained. A carefully kept personal finance notebook, not luxury banking or a trading app. No gradients, glass, heavy shadows, or many-colour charts. Rule of thumb: 80% neutrals, 15% green, 5% functional colour.

### Light palette

| Token | Hex | Use |
|---|---|---|
| Primary (Forest) | `#063E29` | Brand, primary buttons, hero surface, selected nav |
| Primary pressed | `#043320` | |
| Forest soft | `#3F6550` | Secondary icons, tertiary text buttons, progress fill |
| Sage | `#90A988` | Charts, progress, decoration only (2.3:1, never text) |
| Sage light | `#B7CEAE` | Chart line on Forest, chips |
| Sage surface | `#E0EBDD` | Primary soft: selected chips, icon backgrounds |
| Ivory | `#F9F2E7` | Text on Forest |
| App background | `#F8F4EC` | |
| Card | `#FFFCF6` | |
| Stone | `#F3EBDC` | Secondary cards, fields |
| Sand | `#E6DCC9` | Progress tracks, neutral components |
| Border | `#DED8CC` | Borders and dividers |
| Text | `#18201C` | |
| Text secondary | `#657069` | Metadata, labels (4.7:1) |
| Text muted | `#8C948F` | Disabled and placeholder only (2.8:1) |
| Income | `#40785B` | |
| Expense | `#96524A` | Darkened from `#A85F55` to pass 4.5:1 on Stone |
| Warning | `#8A6428` | Darkened from `#B58643` (3.0:1) for text; the lighter value is fine for icons/fills |
| Error | `#A74E4A` | |

### Dark palette (first pass, tune on device)

| Token | Hex |
|---|---|
| App background | `#0F1813` |
| Card | `#17221B` |
| Primary (on dark) | `#B7CEAE` |
| Text | `#EDE7DA` |
| Text secondary | `#A7B0A8` |
| Text muted | `#7D867F` |
| Income | `#8FC4A0` |
| Expense | `#E0A095` |
| Warning | `#D9B26E` |

The hero surface stays Forest `#063E29` in both themes.

### Money and colour

- Meaning never depends on colour alone: amounts keep their sign and labels.
- Expenses render in primary ink with a minus sign; only income, refunds, and warnings use functional colour. Cards stay neutral.
- Entity colours (accounts, categories, people, trips, tags, goals) move from the free HSV picker to a curated muted palette of 24 swatches (a 3 × 8 grid), so charts stay calm and no odd colours appear.

### Typography

- Newsreader (serif) for screen titles, section headings, and hero amounts.
- Inter (sans) for all interface text and for list/table amounts with tabular figures (`tnum`).
- Keep the current downloadable Google Fonts mechanism and its fallback.

| Use | Size | Weight |
|---|---|---|
| Hero amount | 32–36 sp | Medium serif |
| Screen title | 28 sp | Medium serif |
| Section heading | 20 sp | Medium serif |
| Card title | 16 sp | Medium |
| Body | 14–15 sp | Regular |
| Secondary | 13 sp | Regular |
| Label | 12 sp | Medium |
| Metadata | 11–12 sp | Regular |

### Shape, spacing, elevation

- Radius: main card 20, small card 16, input 14, button 16, icon background 12, chips pill, bottom sheet 28 (top).
- Spacing on a 4 dp scale (4, 8, 12, 16, 20, 24, 32, 40); 20 dp page gutter.
- Elevation 0–1 dp. Separation comes from space, surface change, and borders.
- Buttons: primary 52 dp Forest with Ivory text; secondary transparent with border `#CFC8BC` and Forest text; tertiary text in Forest soft.
- Icons: outline, monochrome, Forest or Text secondary.
- Movement lists: flat rows on the page background with subtle dividers, not a card per row.

## Order of work

1. Page model and navigation: routes instead of in-page swaps, the Més hub, and Back behaviour. Done for the hub, person and account pages; forms become sheets per screen in step 3.
2. Tokens, typography, and core components: movement row, entity detail template, sheet, hub list, buttons, chips. Done (palette, type, components, 24-swatch identity palette).
3. Screens: Home → Movements and movement detail → movement form → entity details (Account, Person, Category, Trip, Tag, Goal) → Analysis → remaining hub pages. Home, Movements (with movement detail), the movement form, every entity page, and the list pages done. Entity create/edit forms are sheets on one shared layout. Pressupostos (the monthly plan with its budget sheets), Recurrents (list, item pages, form sheet), Analysis and Configuració done.
4. Launcher icon and in-app logo. Done (the S mark on forest; the header tile on Home).
