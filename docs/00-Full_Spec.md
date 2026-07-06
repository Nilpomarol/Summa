# Personal Finance App — Full Specification

> **Status:** Working draft, pending final review. Decisions marked **[DECIDED]** are settled; no open design questions remain — items scheduled beyond v1 are listed in §8. UI is in Catalan; this document and all technical artifacts are in English.

---

## 1. Overview

### 1.1 Purpose & goals

A personal app to track personal finances: expenses, income, accounts, shared expenses, and what people owe (or are owed). It is **offline-first** and **mobile-first**: the mobile (Android) app is the primary surface and the source of truth; the desktop (Windows) app is a secondary surface offering richer analysis and heavier tools (notably bulk CSV import). All UI is in **Catalan**.

Primary goals, in priority order:

1. Effortlessly record what money is spent and earned.
2. Provide clear but deep analysis of spending and earning (monthly / yearly / all-time / custom).
3. Keep an accurate picture of where money is (account balances) and where it goes.
4. Track shared expenses and interpersonal debts without polluting personal analysis.

### 1.2 Scope

**In scope:** expense/income tracking, multiple accounts and inter-account transfers, shared expenses with flexible splits, interpersonal debt tracking and settlement, recurring movements, trips/events as grouping units, budgets, bulk CSV import (desktop), auto-categorization, backup/export, and a comprehensive analysis layer.

**Explicitly out of scope (v1):**

- Multiple currencies — euros only (§2.6).
- **Multi-user / household profiles** — **single user only.** The app is local and mobile-first; a phone has one owner, so there are no profiles, no family password, no per-user PIN, and no app lock. "The user" is unambiguously the device owner. Consequently, **People** in this model are always *external* parties (friends, family you share costs with), never co-users of the app.
- Live bank API / open-banking integration — import is via manual CSV only.
- Investment portfolio tracking (positions, market value), tax reporting, invoicing.
- Receipt/attachment storage — parked as a future enhancement (§8).
- Cloud-hosted database — the app is local-first by design.

### 1.3 Platforms & high-level architecture

- **Mobile:** native Android app. Primary surface, master database.
- **Desktop:** native Windows app. Secondary surface; same data model, adds CSV import and large-screen analysis.
- **Local-first:** each device holds a complete, fully functional local database. The app works fully offline.
- **Synchronization:** single-writer token model with versioned snapshots. Fully specified in `02-synchronization.md`; summarized in §6.
- **No backend server** is required for normal operation. Snapshot transport can ride on any file channel (e.g. a shared folder); see the sync document. Snapshots are encrypted with a user-held key before they leave a device (§7.4).

### 1.4 Glossary / terminology

| Term | Meaning |
|------|---------|
| **Movement** (*moviment*) | The base ledger record. Anything that touches an account balance. Has a *type*: `expense`, `income`, `transfer`, `settlement`, or `refund`. See §2.1 and §3.3. |
| **Refund** | A movement of type `refund`: money returned for a previous expense, linked to that expense. Adds to the account (flow) and reduces the original expense's net contribution to analysis (actual). |
| **Expense** | A movement of type `expense`. Money the user actually spent (their own consumption). |
| **Income** | A movement of type `income`. Money the user actually earned. |
| **Transfer** | A movement (or movement pair) between two of the user's own accounts. Not spending or earning. |
| **Settlement** | A movement that settles a debt with a person. Affects account flow but not analysis. |
| **Flow** | The effect on account balances. *Everything* counts toward flow. |
| **Actual** (spent/earned) | The effect on analysis. Only the user's own expenses/income count. |
| **Split** | The division of a shared expense among participants (the user + people). |
| **Debt / balance** | What a person owes the user (positive) or the user owes the person (negative). Derived, not stored as truth. |
| **Template** | The stored definition of a recurring movement, from which instances are materialized. |
| **Instance** | A concrete movement created (materialized) from a template on its due date. |
| **Trip / event** | A grouping unit for exceptional expenses tied to an event, kept as a single block in normal analysis. |

---

## 2. Core Concepts & Principles

> These cross-cutting ideas are stated once here and referenced throughout. They are the backbone of the data model.

### 2.1 Movements: the unified ledger **[DECIDED]**

Every record that touches an account balance is a **Movement**. A movement has a **type**: `expense`, `income`, `transfer`, `settlement`, or `refund`. This unified base gives one clean ledger for account flow (§2.2), while type-specific fields carry the extra information each kind needs.

**Why unified rather than separate tables:** account flow (§4.2) is defined as *"everything counts."* If expenses, income, transfers and settlements lived in four separate tables, every balance computation, every "account flow over time" chart, and every duplicate-detection pass would have to UNION four sources and keep them in sync forever. A single `movements` table with a `type` discriminator makes flow a trivial `SUM(amount) WHERE account = X`, makes the ledger view a single ordered query, and makes duplicate detection one pass. Type-specific data that doesn't fit every row (the split of a shared expense, the destination account of a transfer, the person of a settlement) hangs off the movement in satellite tables or nullable fields. This is the classic single-ledger design and it pays off everywhere downstream.

A transaction in the everyday sense (money leaving an account at a merchant) is simply a movement of type `expense`. Critically, **the amount on the movement is the full amount that left the account** — for a shared expense paid by the user, that is the entire bill, not just the user's share (§2.3, §4.2).

### 2.2 Account flow vs. actual spent/earned **[DECIDED]**

Two different questions are asked of the same data:

- **Flow** — *"How much money moved through this account?"* Driven by movements. **Everything** counts: expenses, income, transfers, settlements, the full amount of shared expenses. Flow determines account balances.
- **Actual** — *"How much did the user really spend / earn?"* Driven by analysis. **Only** the user's own expenses and income count. The user's own *share* of a shared expense counts; other people's shares do not. Transfers, settlements, and other people's shares are excluded.

This separation is the single most important rule in the app. Computation rules are in §4.2.

### 2.3 Shared expense in the ledger **[DECIDED]**

When the user pays a shared expense, it is recorded as **one movement for the full bill** that hits their account in full. The split records each participant's owed share. Analysis then **subtracts the other participants' shares** to arrive at the user's actual expense. (Chosen over the alternative of splitting the row into "user's share + receivables," which would complicate the ledger and the debt math.)

### 2.4 Splits stored as absolute amounts **[DECIDED]**

A split can be *entered* three ways: equal parts, exact amounts, or percentages. Regardless of entry method, the split is **stored as absolute amounts per participant** (integer euro cents, §2.8). The entry method is only a data-entry convenience that is immediately converted to stored cents. This keeps all debt and analysis math identical across methods, and means combining methods within one expense later is purely a UI change, not a model change.

### 2.5 "Paid by other" as a 100/0 split **[DECIDED]**

When a bill is paid entirely by someone else but passes through the user's account, it is stored as a shared movement where the other person owes 100% and the user owes 0%. In the UI it keeps its own identity ("pagat per X"), but internally it is one configuration of the general split machinery (§3.7).

### 2.6 Expense paid by another, owed back **[DECIDED]**

Distinct from 2.5: an expense the **other person paid** (it never touched the user's account) that the user must pay back. This *reduces that person's debt* — i.e. it makes the user owe them. It is modeled as a split where the payer is the person, not the user (§3.7, §4.1). No account movement is recorded for the user at purchase time (no money left their account); the user's account is only affected later, at settlement.

For **actual** spending analysis, the user's own line in this split **does count** as spending in the split's category/date, even though no account flow happened yet. Otherwise the app would understate what the user actually consumed whenever a friend paid first. The later settlement remains excluded from analysis, so there is no double-counting.

### 2.7 Transfer to a third party vs. shared expense **[DECIDED]**

Money sent to a third party that the user does **not** share with anyone (e.g. paying rent in full, gifting money) is a **normal expense** — the recipient (merchant or person) is irrelevant. Money sent that **is** shared, or that settles a debt, goes through the split or settlement machinery respectively. Rule of thumb: *Do I share this with someone or is it settling a debt? → split / settlement. Otherwise → plain expense.*

### 2.8 Single-currency assumption **[DECIDED]**

All amounts are in **euros**. No currency field, no conversion. This is a deliberate simplification for a personal app.

**Money is stored as integer euro cents** (e.g. `amount_cents INTEGER`), never as a decimal or float — this guarantees exact sums, splits, and rounding with no floating-point drift. Euros are a UI formatting concern only (cents ÷ 100, two decimals).

### 2.9 Debts are derived, not stored **[DECIDED]**

A person's balance is **computed** from shared expenses, expenses-paid-by-them, and settlements (formula in §4.1). The `balance` shown on a Person is a cached/derived value, never an independent source of truth, to prevent drift. Settlements and splits are the source records.

---

## 3. Entities

> Per-entity template: **Purpose · Fields · Relationships · Lifecycle · Business rules · Edge cases & open questions.**
>
> **Shared mixin — all entities** carry: `id` (stable unique identifier), `created_at`, `updated_at`, and `archived` (soft-delete flag, §4.7). These are not repeated below.

### 3.0 Movement (base entity)

**Purpose:** The base ledger record underlying expenses, income, transfers, and settlements. Centralizes everything that affects an account balance so that flow, the ledger view, and duplicate detection have a single source.

**Fields:**
- `type` — enum: `expense | income | transfer | settlement | refund`.
- `amount` — positive integer **euro cents** (`amount_cents`); formatted as euros in the UI only (§2.8). Always the amount that hit the account (full bill for shared expenses).
- `date` — the value date of the movement.
- `account_id` — the account affected (for transfers, the *origin*; see §3.5).
- `name` / `description` — free text.
- `payee` — optional; the merchant or counterparty (a light, optional field; remembered values can be offered as suggestions). Improves auto-categorization (§4.4) and enables merchant-level analytics, but is never required. **[v1.5]** the `payee` may be promoted to a light remembered entity (autocomplete + an optional default category); in v1 it stays a free-text field with remembered suggestions.
- `notes` — optional longer text.
- `category_id` — optional (expenses/income/refunds; null for transfers/settlements).
- `tag_id` — optional; at most one trip tag, only meaningful when `trip_id` is set (§3.13).
- `template_id` — optional; set if this movement was materialized from a recurring template (§3.10).
- `trip_id` — optional; set if this movement belongs to a trip (§3.11).
- `is_shared` — **derived** boolean; true iff a split exists for this expense (expenses only). Never an independent source of truth: it reflects the presence of a split, the way balances reflect movements (§2.9). Its physical representation (stored flag vs. computed) is a data-model concern.
- `is_one_time` — boolean (**expenses only**, default false); marks an **extraordinary / one-off** purchase (e.g. a large appliance). Orthogonal to the category's fixed/variable nature — it does not change the category; it lets analysis isolate or **exclude** extraordinary spend (§3.2, §4.8).
- `refunds_expense_id` — optional; set on a `refund`, pointing at the expense it refunds (§3.3b).
- `import_batch_id` — optional; set if created via CSV import, for traceability and dedup.

**Relationships:**
- Belongs to exactly one **Account** (origin account).
- Optionally belongs to one **Category**, one **Template**, one **Trip**, one **import batch**.
- An `expense` may have one **Split** (with many participant lines) — §3.7.
- A `transfer` has one paired **destination** reference — §3.5.
- A `settlement` references one **Person** — §3.9.
- A `refund` references one **Expense** it refunds — §3.3b.

**Lifecycle / states:** `active` → `archived`. Materialized instances additionally relate to their template's lifecycle (§3.10).

**Business rules:**
- `amount` is always positive. For `expense`/`income`/`refund`, direction is implied by `type` (expenses reduce the balance; income and refunds increase it). Two types carry extra direction information: a `transfer` references **both** an origin and a destination account, contributing −`amount` to the origin and +`amount` to the destination and appearing once in the ledger (§3.5); a `settlement` carries an explicit `direction` (person→user inflow / user→person outflow) (§3.9).
- Only `expense`, `income`, and `refund` may carry a category and participate in actual analysis.
- Only `expense` may be shared.

**Edge cases & open questions:**
- Whether `settlement` direction can be both "person pays user" and "user pays person" — resolved: yes, both directions exist (§3.9).
- See §8 for split-of-a-transfer (out of scope v1).

---

### 3.1 Accounts

**Purpose:** Models where money is stored: bank accounts, cash, savings, etc. Holds the user's balance.

**Fields:** `name`, `starting_balance`, `current_balance` (derived: `starting_balance + Σ flow`), `type` (enum: bank / cash / savings / investment / other), `icon/logo`, `color`, `is_default` (boolean), `display_order`, `low_balance_threshold` (optional; when the balance drops below it, a low-balance alert fires, §5.3).

**Relationships:**
- One account has many **Movements** (as origin).
- Referenced as destination by **transfer** movements.
- May be the default account globally; a **Trip** may set its own default account (§3.11).

**Lifecycle / states:** `active` → `archived`. Archiving is only allowed/clean when the account has no future-dated pending recurring prompts; historical movements remain (§4.7).

**Business rules:**
- `current_balance` is always derived from `starting_balance` plus the sum of flow (§4.2), never edited directly. Editing the *starting* balance is allowed and recomputes everything.
- Exactly one account may be marked global default at a time.
- **Initial balance** is modeled as the account's `starting_balance` field (not as a hidden movement row). It seeds the balance and is excluded from movement lists and from actual analysis. Keeping it a plain field rather than a special ledger row avoids a special-case entry and keeps flow computations uniform.

**Removal strategies** *(four options, aligned to the archive-first model):*
1. **Archive** (default, soft-delete) — hide the account, keep all its movements and history intact (§4.7). Reversible.
2. **Delete (empty account only)** — hard-delete an account that has no movements.
3. **Delete with all its movements** — hard-delete the account and every movement on it (guarded confirmation; destructive).
4. **Reassign then delete** — move all the account's movements to another account, then delete the now-empty account. Balances of both accounts (and any transfer-linked accounts) are recomputed afterward.

**Edge cases & open questions:**
- Negative balances are permitted (overdraft, credit-like accounts) — no constraint blocking them.
- **[DECIDED]** No special "credit card" account type with its own settling cycle. A normal account is sufficient; a credit card is just another account.

---

### 3.2 Categories

**Purpose:** Classifies expenses and income. Drives category breakdown analysis and the fixed/variable distinction.

**Fields:** `name`, `kind` (enum: expense / income / both), `nature` (enum: fixed / variable), `icon/logo`, `color`, `parent_id` (optional, for subcategories — see edge cases), `display_order`.

**Relationships:**
- One category has many **Movements**.
- Optionally a parent category (hierarchy).
- Referenced by **Budgets** (§3.14) and **Auto-categorization rules** (§3.15).

**Lifecycle / states:** `active` → `archived`. Archiving a category leaves its historical movements categorized; the category just stops appearing as a choice and is treated as absent in new analysis groupings unless explicitly included (§4.7).

**Business rules:**
- A movement's `nature` (fixed/variable) **follows its category** — there is no per-movement override (decided: simpler, one less field). Changing an expense's nature means changing (or re-assigning) its category.
- A separate per-movement **`is_one_time`** flag (§3.0) marks extraordinary/one-off **expenses**. It is **orthogonal** to fixed/variable — not a third nature value and not an override: the category still classifies fixed vs. variable, while `is_one_time` lets analysis isolate or exclude extraordinary spend (§4.8). An extraordinary purchase therefore keeps its normal category.
- Income and expense categories are distinguished by `kind` so the UI offers the right ones.

**Edge cases & open questions:**
- **[DECIDED] Subcategories.** A two-level hierarchy (e.g. *Food → Groceries / Restaurants*) is included for v1. `parent_id` is optional, so flat use still works and a subcategory inherits nothing it doesn't override.
- **"No category" virtual bucket.** Uncategorized movements are grouped under a non-deletable virtual "Sense categoria" bucket in breakdowns and analysis, so uncategorized spending is always visible rather than hidden. It is not a real category row.
- **Per-category insight view:** each category has a detail screen showing this-month total + count, this-year total + count + **share of the year**, and (for budgeted expense categories) a spend-vs-budget bar with green/amber/red status (§3.14, §4.5). Clicking a category drills into its movements.

---

### 3.3 Expenses

**Purpose:** What the user actually spent money on. A movement of type `expense`.

**Fields (in addition to §3.0):** `category_id`, `nature` (fixed/variable, defaulted from category), `is_shared`, link to **Split** if shared, `trip_id` if part of a trip.

**Relationships:**
- Is-a **Movement** (§3.0).
- Belongs to one **Account**, optionally one **Category**, one **Trip**, one **Template**.
- If shared, has one **Split** with many participant lines (§3.7).

**Lifecycle / states:** `active` → `archived`. If materialized from a template, also tracks `template_id`.

**Business rules:**
- The recorded `amount` is the full amount that left the account (§2.3).
- In **actual** analysis, a shared expense contributes only the user's own share (§4.2).
- `nature` follows the category; no per-movement override (§3.2).

**Edge cases & open questions:**
- A shared expense where the user owes 0% (§2.5) still appears in flow but contributes 0 to actual.
- **Refunds** are first-class and **linked** to the expense they refund — see §3.3b.

---

### 3.3b Refunds

**Purpose:** Money returned for a previous expense (a return, a cancelled charge, a goodwill credit). A refund is real money arriving in an account, but it is not "income earned" — it is the partial or full reversal of a specific past expense. Modeling it as its own linked movement keeps both facts true: you *did* spend, and you *did* get some back, each in its own period.

**Fields (in addition to §3.0):** `type = refund`, `refunds_expense_id` (the linked original expense), `account_id` (where the money landed), `amount`, `date`, `category_id` (defaults to the original's category), optional `actual_refund_amount` for the analysis adjustment when it differs from the cash inflow, optional `notes`/`payee`.

**Relationships:**
- Is-a **Movement** (§3.0).
- References exactly one original **Expense** via `refunds_expense_id`.
- One expense may have **many** refunds (partial refunds over time).

**Lifecycle / states:** `active` → `archived`.

**Business rules:**
- **Flow:** a refund is an **inflow** on its `date`, increasing the account balance like any money in (§4.2).
- **Actual:** a refund **never** counts as income. It nets down spending in the category it carries (inherited from the original by default), recorded **in the refund's own period** — the original expense is left untouched in its own period (past periods are never rewritten). Over any span that contains both, the original expense's net contribution is therefore `expense.amount − Σ(linked actual refund adjustments in that span)`; within a single period that excludes the refund, the expense still shows gross. For ordinary refunds, the actual adjustment equals the cash refund amount. For a shared-expense refund, the actual adjustment is only the portion attributable to the user's own share; other participants' shares are not adjusted in v1.
- A refund's amount **should not exceed** the original's remaining un-refunded amount — warn and allow override (a rare over-refund, e.g. a goodwill credit, is possible).
- A refund **inherits the original's category** by default; the user may change it.
- If the original expense is **deleted/archived**, its linked refunds are **surfaced for the user to reassign or remove**, never silently orphaned.

**Edge cases & open questions:**
- **[DECIDED] Refund of a shared expense:** the refund reduces the **user's own share** in actual analysis; refunding other participants' shares is **out of scope for v1** (it does not adjust their debt). If the cash inflow differs from the user's actual adjustment, the refund carries an explicit `actual_refund_amount`. Revisit post-v1.
- **Later-period refund:** fully supported and is a key reason for linking — the original expense stays unchanged in its month; the refund lands in its own later month as a negative adjustment to that category. Past periods are not rewritten. *Worked example:* a €100 expense in category *Electronics* dated 10 Jan, partially refunded €30 on 5 Mar. January's *Electronics* actual shows the full €100; March's shows −€30; a Jan–Mar (or all-time) view nets to €70. The €30 is never counted as income.
- **Partial refund:** supported natively via the link holding the remainder; multiple partials accumulate against the same original.

---

### 3.4 Income

**Purpose:** What the user actually earned: salary, gifts, side income, interest, etc. A movement of type `income`.

**Fields (in addition to §3.0):** `category_id`, `nature` (typically fixed for salary, variable otherwise), `trip_id` (rare but allowed). **Note:** settlements are *not* income and carry no income fields (§3.9).

**Relationships:** Is-a **Movement**. Belongs to one **Account**, optionally one **Category**, **Template**, **Trip**.

**Lifecycle / states:** `active` → `archived`; may be template-materialized (recurring salary).

**Business rules:**
- Counts fully toward actual earned and toward flow.
- Recurring income (salary) uses the same template machinery as expenses (§3.10).

**Edge cases & open questions:**
- Money received that is actually a **settlement** must be recorded as a settlement, not income, or it will double-count and distort analysis (§3.9, §4.1).

---

### 3.5 Transfers (inter-account movements)

**Purpose:** Money moved between two of the user's own accounts. Not spending or earning; affects flow on both accounts, actual on neither.

**Fields:** `origin_account_id`, `destination_account_id`, `date`, `amount`, `name/notes`.

**Relationships:**
- Touches two **Accounts** (origin and destination).
- Modeled as a movement of type `transfer`. **[DECIDED] representation:** one logical transfer with both account references (rather than two mirrored rows), so it appears once in the ledger but contributes `−amount` to origin flow and `+amount` to destination flow.

**Lifecycle / states:** `active` → `archived`.

**Business rules:**
- Never counts toward actual (analysis), always toward flow on both accounts.
- Origin and destination must differ.

**Edge cases & open questions:**
- A transfer can be recurring (e.g. monthly auto-savings) → uses templates (§3.10).
- **[DECIDED]** Transfers are **never** shared/split — a transfer is purely between two of the user's own accounts. (Splitting only applies to expenses.)

---

### 3.6 People

**Purpose:** People who participated in shared expenses or who paid things for the user. Each has a derived balance and can settle it.

**Fields:** `name`, `balance` (**derived**, §2.9), `icon/avatar`, `color`, `notes`.

**Relationships:**
- Appears in many **Splits** (as a participant) — §3.7.
- Has many **Settlements** — §3.9.
- Has many "expense-paid-by-them" records — §2.6 / §3.7.

**Lifecycle / states:** `active` → `archived`. **Business rule:** a person should not be archived while their balance ≠ 0 (warn, allow override — consistent with §4.6's never-block stance).

**Business rules:**
- `balance` is computed per §4.1, never stored as truth.
- Positive balance = the person owes the user; negative = the user owes the person.

**Edge cases & open questions:**
- **[DECIDED — v1.5] Groups of people** (e.g. "Roommates," "Trip crew") as a convenience for repeatedly splitting among the same set. Scheduled for v1.5, not v1; the split model already supports arbitrary participant sets, so groups are purely a saved shortcut with no model impact.

---

### 3.7 Shared expenses & splits

**Purpose:** Represents how a shared expense is divided. The user pays (or someone else does); each participant has an owed share. Other people's shares never count toward the user's actual expense; they create/adjust debt.

**Fields:**
- **Split** (one per shared expense): `expense_id`, `entry_method` (equal / exact / percentage — for UI recall only), `payer` (the user *or* a person — see §2.6).
- **Split line** (many per split, one per participant): `participant` (the user or a `person_id`), `owed_amount` (absolute euros, §2.4), optionally `owed_share`/`owed_percent` retained for display.

**Relationships:**
- One **Split** belongs to one **Expense** (movement).
- One **Split** has many **Split lines**, each referencing the user or a **Person**.
- Feeds **Debt calculation** (§4.1).

**Lifecycle / states:** Tied to its parent expense (active/archived). Editing the expense amount re-opens the split for rebalancing.

**Business rules:**
- Σ(`owed_amount`) across lines = expense `amount` (must reconcile exactly).
- If `payer` = user: each person's `owed_amount` increases that person's debt to the user (they owe their share). The user's own line is the only part counted in actual analysis.
- If `payer` = a person (§2.6): the user's `owed_amount` is what the user must pay back → *reduces* that person's balance (user owes them). No account movement at purchase time; only at settlement.
- "Paid by other" (§2.5) = `payer` is a person with the user's line at 0, or the user pays but owes 0 — both expressible; the canonical form is documented in the data-model phase.

**Edge cases & open questions:**
- **[DECIDED]** Methods are not combined within one expense in v1, but storage as absolute amounts means enabling combination later is UI-only.
- **[DECIDED] Rounding:** splits are computed in **integer cents**; when an equal split doesn't divide evenly, the remainder cent(s) go to the **payer**, so Σ(`owed_amount`) reconciles exactly to the expense `amount`.
- Editing a split after a partial settlement exists → must recompute debt; warn if it would make a settled amount exceed the new total.

---

### 3.8 Debts

**Purpose:** What a person owes the user (or vice versa). **Derived**, not a stored entity in its own right (§2.9) — documented here because it is a first-class concept in the UI.

**Fields (computed):** per person — `total_owed_to_user`, `total_user_owes`, `net_balance`, plus a breakdown list (which splits/settlements compose it).

**Relationships:** Aggregates **Split lines**, **expense-paid-by-them**, and **Settlements** for a given **Person**.

**Lifecycle / states:** N/A (derived). A debt is "open" while `net_balance ≠ 0` and "settled" when it returns to 0.

**Business rules:**
- See formula in §4.1.
- A person's debt detail can be **exported or shared as a statement** — the `net_balance` plus the itemized breakdown of the splits and settlements composing it (e.g. text or PDF) — for settling up (§5.5).

**Edge cases & open questions:**
- A person can simultaneously owe the user for one expense and be owed for another; the UI shows the **net** plus an itemized breakdown.

---

### 3.9 Settlements

**Purpose:** A payment that settles a debt with a person, in either direction. Affects account flow (real money moves) but **not** actual income/expense analysis. May be partial.

**Fields:** `person_id`, `date`, `amount`, `account_id` (the account that received or sent the money), `direction` (person→user / user→person), `notes`.

**Relationships:**
- Belongs to one **Person**, one **Account**.
- Is a movement of type `settlement` (so it shows in flow / the ledger).
- Feeds **Debt calculation** (§4.1).

**Lifecycle / states:** `active` → `archived`.

**Business rules:**
- **[DECIDED]** When a person pays the user, the amount **adds to the account balance (flow)** but is **excluded from income analysis**. Symmetrically, when the user pays a person, it reduces the account balance but is not an expense in analysis.
- Partial settlements are allowed; debt is reduced by the settled amount (§4.1).
- A settlement cannot exceed the outstanding debt unless explicitly overridden (warn; an overpayment flips the balance direction).
- **[DECIDED] Direction is inferred automatically** from the person's current net balance (they owe you → person→user inflow; you owe them → user→person outflow), defaulting the amount to the outstanding balance. The user can override.

**Edge cases & open questions:**
- **[DECIDED]** "Settle up" helper: given a person's net balance, pre-fill a single settlement for the full outstanding amount and the direction. The account through which the money moves is selectable.
- A settlement received in cash vs. into a bank account both work via `account_id` (cash is just an account).

---

### 3.10 Recurring movements (templates & instances)

**Purpose:** Movements that repeat on a schedule (expenses like rent/subscriptions, income like salary, transfers like auto-savings). Semi-automatic: marking a movement as recurring creates a **Template** holding all its data; on each due date an **Instance** is materialized from it, pre-filled, for the user to confirm/edit.

**Fields:**
- **Template:** all movement fields (type, amount, account, category, name, notes, split config if shared, trip if applicable) + `frequency` (e.g. monthly/weekly/fortnightly/yearly/custom interval with an explicit custom unit) + `day_of_month` (or weekday for weekly) + `next_due_date` + `amount_flexibility` (optional ± margin or "variable") + `date_flexibility` (optional ± days) + `active` flag + `lead_notification_days` (§5.3).
- **Instance:** once confirmed, a normal **Movement** with `template_id` set. A pending occurrence is a prompt/draft derived from the template, not a ledger movement.

**Relationships:**
- One **Template** has many **Instances** (movements).
- A template can be of any movement type (expense, income, transfer); shared expense templates carry a split config and **carry the previous split forward** when pre-filling each occurrence.

**Lifecycle / states:**
- **Template:** `active` ↔ `paused` → `ended`/`archived`.
- **Instance materialization:** `pending` (prompt shown, not in the ledger) → `confirmed` (movement created, possibly edited) or `skipped` (this occurrence dismissed). On confirm or skip, the template's `next_due_date` advances; if the user edits the instance and leaves the recurring flag set, the **template updates** to reflect the edits (§4.3).

**Per-item actions** *(distinct operations):*
- **Add payment** — materialize this occurrence (pre-filled, carrying over the previous split), editable before confirming.
- **Skip this month** — dismiss a single occurrence without ending the series; the series continues next period.
- **End recurring** — stop the template from a chosen period onward, leaving all past instances untouched.
- **Delete a confirmed instance** — offers a sub-choice: *keep the recurring* (the occurrence becomes due again as a prompt) or *delete the whole series*.

**Presentation:**
- **Desktop: recurring calendar** — a month grid placing each item on its day; an item shows **solid** once this period's instance exists and **greyed** while still pending; month navigation and a monthly total.
- **Mobile: recurring list** — the same items ordered by day, with a monthly total.

**Business rules:**
- Materialization presents a prompt on/near the due date (§4.3); the user can edit any field before confirming.
- `amount_flexibility` / `date_flexibility` express that real-world recurrences vary (a €40 ±€5 bill, due "around the 1st").
- Unmarking the recurring flag on an instance detaches it from the template (template untouched).
- **Month-end clamping:** a `day_of_month` greater than the days in a given month clamps to the last day (e.g. a day-31 item falls on Feb 28/29).
- **Pattern detection** scans history and proposes likely recurring items by detecting **weekly / fortnightly / monthly** cadence, which the user can then confirm as templates.
- **[DECIDED]** Pattern detection is a **user-triggered, one-shot scan** (a button on the recurring list), never automatic/background. It is scoped to weekly/fortnightly/monthly cadence only (yearly/custom patterns are not detected) and to expense/income movements only (a recurring transfer's identity also depends on its destination account, out of scope for detection). It groups movements not yet linked to a template by account + category + normalized name/payee, requires at least 3 occurrences with a consistent gap (tolerating one missed occurrence), and proposes either a **new** template or an **update** to a matching existing template of any lifecycle status. A detected pattern whose last occurrence is stale relative to its own cadence defaults to a proposed `ended` status so confirming it never creates a spurious due-prompt; a recent pattern defaults to `active`. Every proposal is reviewed and individually accepted/skipped before any template is created or updated — nothing is auto-applied.

**Edge cases & open questions:**
- **Missed/late prompts:** if the app wasn't opened across one or more due dates, then on next open **one pending occurrence per missed period** is shown (e.g. three missed monthly cycles → three pending prompts), never silently auto-added. Pending occurrences are **not part of the ledger**: they do not affect account balances, flow, or analysis until confirmed. Each can be confirmed (with edits) or skipped independently, and a **confirm-all / skip-all** convenience is offered when several are queued.
- **[DECIDED]** Overdue templates **never auto-materialize**; they only prompt (§8).
- Variable-amount recurring (e.g. utility bills): `amount` left blank/estimated in the prompt for the user to fill.

---

### 3.11 Trips / events

**Purpose:** Group exceptional expenses tied to an event (mainly trips). Because these expenses happen *only because the event happened*, the trip lets the user (a) see what the event cost in total and (b) keep those expenses as a single **block** in normal analysis so they don't contaminate ordinary month-to-month patterns.

**Fields:** `name`, `type` (enum: trip / celebration / other), `start_date`, `end_date` (optional, ongoing allowed), `icon`, `color`, `default_account_id` (optional, §5.4), `budget` (optional, §3.14), `notes`.

**Relationships:**
- One **Trip** has many **Movements** (mostly expenses, occasionally income/transfers).
- May have its own **custom tags** (§3.13) and **groupings** (§3.12).
- May define a default account and a budget.

**Lifecycle / states:** `planned` → `active` → `finished` → `archived`. (States are informational; movements can be attached regardless.)

**Business rules:**
- In normal analysis, all movements assigned to a trip are collapsed into one line/block (§3.12, §4.8) instead of being scattered across their categories.
- A trip has its own dedicated analysis view with concrete metrics: **KPIs** = total spent, number of days, average per day; a **stacked daily chart** of spending by category switchable between **per-day** and **cumulative**; a **breakdown by category and by tag** switchable between **total** and **average/day** with percentage bars (expenses without a tag fall back to their category, shown dimmed); and a **scoped movement list** where new movements default to a sensible date within the trip range and pre-attach to the trip.
- A trip carries a `type` (enum: trip / celebration / other); creating a trip of a given type may seed default tags for that type (§3.13).

**Edge cases & open questions:**
- A movement belongs to **at most one** trip.
- Shared expenses inside a trip work normally; the trip view can show "what the trip cost *me*" (actual) vs. "total trip outlay" (flow).

---

### 3.12 Trip groupings (in analysis)

**Purpose:** The mechanism by which a trip appears as a single block in normal (non-trip) analysis, giving a cleaner perspective on routine spending.

**Fields:** Not a separate stored entity — this is an **analysis behavior** driven by the `trip_id` on movements plus a user toggle ("group trips as blocks" — on by default).

**Relationships:** Reads `trip_id` from movements.

**Lifecycle / states:** N/A.

**Business rules:**
- When grouping is on (default), trip movements roll up into one "Trip: X" line in category/total views; the trip's own analysis stays available from the Events/trip surfaces rather than by tapping the top-level Analysis block (§3.11).
- When off, trip movements appear under their individual categories like any other.

**Edge cases & open questions:**
- Interaction with category breakdown: a grouped trip block is itself breakable down by category within the trip view.

---

### 3.13 Trip custom tags

**Purpose:** Like categories, but for things that mainly happen on trips, giving freedom to classify trip spending (e.g. "Activities," "Souvenirs," "Local transport") without polluting the global category list.

**Fields:** `name`, `icon`, `color`, `scope` (**[DECIDED default]** global-but-trip-oriented, with the option to mark a tag as trip-local).

**Relationships:**
- Applied to **Movements** that belong to a trip (many-to-one, or many-to-many — see edge cases).
- Optionally scoped to a specific **Trip**.

**Lifecycle / states:** `active` → `archived`.

**Business rules:**
- **[DECIDED]** Default: tags are **global and reusable across trips**, so "Activities" isn't re-created every trip, *with the option* to create a trip-local tag for one-off needs. This balances reuse against per-trip freedom.
- Trip tags are used in the trip's own analysis view, parallel to categories.

**Edge cases & open questions:**
- **[DECIDED]** A movement may carry **both** a global category *and* a trip tag. The category is always meaningful (cross-trip comparability); the tag is an optional trip-only extra. A trip movement may have just a category and no tag.
- **[DECIDED]** **One tag max per movement** — a movement carries at most one trip tag (a nullable `tag_id`, parallel to `category_id`). Chosen for simplicity over many-tags.

---

### 3.14 Budgets

**Purpose:** Spending limits on a category, a month, or an event, to give the user more control and information. Lower priority than core analysis.

**Fields:** `scope` (enum: category / overall-month / trip), `category_id` (if category-scoped), `trip_id` (if trip-scoped), `period` (e.g. monthly / one-off), `limit_amount`, `start`/`recurrence`.

**Relationships:**
- Optionally references a **Category** or a **Trip**.
- Evaluated against **Movements** (actual spend) in §4.5.

**Lifecycle / states:** `active` → `archived`.

**Business rules:**
- Evaluated as planned (limit) vs. actual (sum of relevant **actual** expenses, §4.2) for the period.
- Budgets never block anything; they inform (progress bar, over/under).

**Edge cases & open questions:**
- **[DECIDED]** v1 ships **category-monthly** budgets first; overall-month and trip budgets are fast-follows.

---

### 3.15 Auto-categorization rules

**Purpose:** Automatically assign categories to movements, especially valuable during bulk CSV import; less critical for one-by-one manual entry.

**Fields:** `name`, `priority` (for precedence), `conditions` (matchers on `name`/description text, `amount` range, `date`/day-of-month, `account`), `action` (`set_category_id`, optionally `set_trip`), `source` (user-defined / system-learned), `active`. *(No `set_nature`: nature follows the assigned category, §3.2.)*

**Relationships:**
- Targets a **Category** (and optionally other fields).
- Applied to **Movements**, primarily at import (§4.4).

**Lifecycle / states:** `active` → `paused`/`archived`.

**Business rules:**
- Rules are evaluated by **priority**; first match (or highest-priority match) wins (§4.4).
- The system may **learn** rules by observing the user's repeated manual categorizations and propose them (user confirms before a learned rule becomes active).
- Applied automatically on import; for manual single entry, used only as a non-intrusive suggestion.

**Edge cases & open questions:**
- A rule never overrides a category the user set manually on that specific movement.
- **[DECIDED]** Conflict handling when two equal-priority rules match: the **most recently created rule wins** (newer over older), with the conflict surfaced as a warning so the user can adjust priorities.

---

### 3.16 Expense paid by other (cross-reference)

This is **not a separate table** — it is the §2.6 configuration of a **Split** (§3.7) where the `payer` is a person and the user's line is what the user owes back. It is listed in the entity index for discoverability, but it is fully covered by the Split machinery and the debt formula (§4.1). Treating it as one mechanism (rather than a parallel entity) avoids duplicated debt logic.

---

## 4. Cross-Entity Rules & Workflows

### 4.1 Debt calculation **[DECIDED]**

A person's balance (positive = they owe the user; negative = the user owes them) is **derived**:

```
balance(person) =
    Σ (their owed shares of expenses the USER paid)        // they owe the user
  − Σ (the user's owed shares of expenses the PERSON paid) // user owes them (incl. "expense paid by other", §2.6)
  − Σ (settlements person → user)                          // they paid the user back
  + Σ (settlements user → person)                          // the user paid them back
```

All terms come from **Split lines** (§3.7) and **Settlements** (§3.9). Nothing about debt is stored as independent truth (§2.9); the Person's displayed `balance` is a cache of this computation.

### 4.2 Flow vs. actual computation **[DECIDED]**

- **Flow (account balances):** *everything* counts. For each account: `balance = starting_balance + Σ income + Σ refunds_in + Σ settlements_in − Σ expenses − Σ settlements_out ± transfers`. Shared expenses count at their **full** amount. This drives §3.1 balances and the account-flow analysis (§4.8).
- **Actual (analysis):** *only the user's own* expenses and income count, **net of refund adjustments**. A linked refund reduces its original expense's category contribution in the refund's own period. For a shared expense, only the user's own split line contributes, and a shared-expense refund reduces only the user's actual share in v1. A §2.6 friend-paid split also contributes the user's own line, even though it has no account flow. Transfers, settlements, and other people's shares are **excluded**. This drives all spending/earning analysis.

This is the operational form of §2.2 and the backbone of every analysis query.

### 4.3 Recurring materialization **[DECIDED]**

On (or near, per `date_flexibility`) a template's `next_due_date`, a **prompt** appears pre-filled from the template. The user may edit any field, then **confirm** (creates the instance, advances `next_due_date`) or **skip** (dismisses this occurrence, still advances). If the user edits the instance and leaves the recurring flag set, the **template is updated** to reflect those edits (so the next occurrence carries them forward). Nothing is ever auto-added without confirmation; if the app was closed across several due dates, **one pending occurrence per missed period** is shown on next open, none affecting balances until confirmed (§3.10).

### 4.4 Auto-categorization matching & precedence **[DECIDED]**

Rules (user-defined and system-learned, §3.15) are evaluated **by priority**; the highest-priority matching rule assigns the category (and optionally trip). On a tie (equal priority), the **most recently created rule wins**, with the conflict surfaced as a warning. Primary application is **CSV import**, where every imported row is run through the rules; for manual single entry, a match is offered as a **suggestion**, not auto-applied. A manually set category on a specific movement is never overridden. The system may propose new learned rules from repeated manual categorizations, pending user confirmation.

### 4.5 Budget evaluation **[DECIDED, low priority]**

For each active budget, compare its `limit_amount` against the sum of relevant **actual** expenses (§4.2) for the period/scope. Output is informational (progress, over/under); budgets never block input. Lower priority than core analysis.

### 4.6 Duplicate detection **[DECIDED]**

On CSV import **and** manual entry, the app flags likely duplicates (same/near amount, same/near date, same account, similar description). It **never blocks** — it shows a clear, dismissible warning that can be overridden. Import additionally tracks `import_batch_id` so a whole batch can be reviewed or rolled back.

### 4.6b CSV import wizard (desktop) **[DECIDED]**

A 3-step wizard:

1. **Upload** — select the bank CSV file.
2. **Map columns** — the app **auto-detects** the date / description / amount columns (recognizing multiple language header aliases), the user confirms or corrects the mapping, and picks the **destination account**.
3. **Review & edit drafts** — each row becomes an editable draft before saving. Per row: the **sign of the amount** decides income vs. expense; **category** is auto-assigned via rules (§4.4) with a history-based fallback suggestion; likely **duplicates are flagged** (§4.6). Each draft can be fully edited (category, trip, tag, splits, notes, payee), expanded, or **excluded**. A final **bulk save** commits all non-excluded drafts as one batch (`import_batch_id`).

**Settlement bridge:** because a real bank transfer that settles a debt with a person often appears in the imported CSV, a draft row can be marked as **"this settles my debt with X"** — it is then recorded as a **settlement** (§3.9) linked to that person rather than as a plain expense/income. This prevents a phantom expense and keeps the debt math correct. The same marking is available when editing any movement, not only at import.

### 4.7 Archiving rules **[DECIDED]**

Archived entities are treated **as if absent**: hidden from pickers, excluded from new analysis groupings, not offered as choices. They remain in the database so historical movements stay intact and the entity can be restored. Archiving is the universal soft-delete; hard deletion is reserved and guarded (and per the sync/security model, destructive bulk deletes are handled carefully). Archiving an entity that still has live dependencies (a person with non-zero balance, an account with pending recurring prompts) warns but does not hard-block, consistent with the never-block philosophy.

### 4.8 Analysis **[CRITICAL]**

The heart of the app. Requirements:

- **Time scopes:** monthly, yearly, all-time, and custom period.
- **Comparison:** any period vs. another (e.g. this month vs. last, this year vs. last).
- **Grouping & breakdown:** group by category (including subcategories, §3.2), by trip-as-block (§3.12), by account; full category breakdown within any scope.
- **Account flow view:** month-to-month and day-to-day balance evolution per account and overall.
- **Averages vs. totals:** on yearly/all-time scopes, the option to view **averages** (e.g. average monthly spend per category) instead of raw totals.
- **Fixed vs. variable (+ one-time):** split any view by the category's fixed/variable nature; and **isolate or exclude `is_one_time` (extraordinary) spend** — included by default, with a toggle to take extraordinary purchases out of the analysis.
- **Actual vs. flow toggle:** where meaningful, switch between "what I really spent/earned" (actual) and "what moved through my accounts" (flow) — directly exposing §2.2 to the user.
- **People/debt analysis:** outstanding balances, history with each person, who owes what.
- **Future forecasting:** projection combining (a) known recurring templates and (b) historical averages / learned patterns, to estimate upcoming months. Clearly labeled as estimate.
- **Filtering:** filter *everything* — by category, period, account, type (expense/income/transfer/settlement), nature (fixed/variable), recurring vs. one-off, person involved, trip, tag, amount range, text search.
- **Interaction:** top-level Analysis aggregates, chart elements, and breakdown rows are display/inspection surfaces and do not navigate. Explicit entry points outside the Analysis page (for example Dashboard KPIs, account/category sheets, or movement lists) may still open filtered movement or detail views.
- **Additional analysis widgets (v1):** top merchants / most-frequent expenses; largest expenses in a period; spending **heatmap** (calendar); category **trend lines** over time; **net worth** total across all accounts over time; **income-vs-expense (savings rate)** per period; **recurring-cost summary** ("you spend €X/month on subscriptions"). *(Top merchants groups by the `payee` field; richer merchant analytics improve with the v1.5 payee entity, §3.0.)*

#### 4.8b Dashboard / home screen

A monthly home screen, the primary landing surface, focused on the **current month**, with a dedicated mobile layout distinct from desktop:

- **KPI cards:** total net worth (sum of all account balances), this month's income, this month's expenses, and net flow (income − expenses) with a **savings-rate %**.
- **Daily evolution chart** — income vs. expenses per day across the current month.
- **Accounts overview** — each account as a card with balance and its share of total net worth; tapping opens that account's movement list.
- **Category breakdown** — current-month spending and income grouped by category (trips as their own group), sorted by amount.
- **Latest movements** — most recent entries, click-through to detail.
- **Quick actions** — "New movement" and "Import CSV" directly from the dashboard.
- All expense figures shown **net of others' shares** (actual, §4.2).

---

## 5. Features

### 5.1 Platform & data

Native **Android** and **Windows** apps. Local-first, fully offline-capable. Synchronization between them follows the single-writer token model in `02-synchronization.md` (summarized §6). Full CRUD on all entities. **CSV import is desktop-only** (§4.4, §4.6). Backup and export/import of all data (§5.6). **[v1.5]** A desktop-only **bulk multi-select edit** lets the user select many movements and recategorize, assign a trip/tag, or delete them in one action (cleanup and post-import).

### 5.2 Analysis

See §4.8 — the full behavioral spec lives there.

### 5.3 Categorization & recurrence

Auto-categorization (§4.4), recurring detection and materialization (§3.10, §4.3). **Notifications:** configurable to fire *N days before* a recurring movement is due (`lead_notification_days` per template, with a global default). Notifications are local (offline), since the app is local-first. Two further local alerts: a **budget alert** when a budget reaches a configurable threshold or goes over (§3.14, §4.5), and a **low-balance alert** when an account falls below its optional `low_balance_threshold` (§3.1).

### 5.4 Accounts & flow

A **global default account** for new movements, and a **per-trip default account** that takes precedence while operating within a trip (§3.1, §3.11).

### 5.5 People, debts & budgets

People management (§3.6), derived debts (§3.8, §4.1), settlements with a "settle up" helper (§3.9), and budgets (§3.14, §4.5, lower priority). Each person's debt can be **exported or shared as a statement** (net balance + itemized breakdown) for settling up (§3.8).

### 5.6 Backup, export & import

- **Backup:** full local backup of the **entire** database to a user-chosen location/file; restore from backup. The backup is complete (it includes people, trips, tags, splits, and recurring templates) and two-way (both save and restore).
- **Export:** human-readable/portable export (e.g. CSV per entity and/or a single JSON) for the user's own use and portability.
- **Import:** restore/merge from an export; bank CSV import is the separate desktop flow (§4.6b).
- Backup/export/import are straightforward given the snapshot model (the sync layer already serializes the whole DB).

### 5.7 Search

Global search and rich filtering across all movements and entities (folds into §4.8 filtering). Called out separately because at scale it becomes essential. Filters include: free-text description, type, account (plus a "paid by someone" filter), category, trip, tag, person, nature, recurring vs. one-off, amount range, and period — with a clear-all action.

### 5.8 Settings & system tools

- **Appearance:** light / dark theme toggle.
- **Backup / export / import** (§5.6).
- **Sync encryption key:** set or change the user-held key used to encrypt snapshots (§7.4); the same key must be present on each of the user's devices to read snapshots.
- **Recalculate balances:** force a full recompute of all account and person balances from source movements. Since balances are always derived (§2.9), this is an integrity/refresh tool and reassurance action rather than a correctness fix.
- **Resync recurring:** regenerate/refresh recurring templates from movements flagged recurring (maintenance tool).
- **Notification settings:** global default lead time for recurring reminders, plus toggles for budget-threshold and low-balance alerts (§5.3).
- **App version / update check.**

### 5.9 Navigation

- **Mobile:** bottom navigation; **desktop:** sidebar.
- Suggested map — *Bottom nav (mobile):* **Inici** (Dashboard) · **Moviments** (Movements) · **＋** (global New movement) · **Anàlisi** (Analysis) · **Gestió** (Management hub). The **Gestió** hub holds Accounts, Categories, People, Events (→ trip detail), Recurring, and Settings. · *Desktop:* sidebar with the same groups.
- A global **"New movement"** action available everywhere.
- **Live refresh:** a change on one screen refreshes related screens.
- **Undo last action:** after a create / edit / delete, a brief undo affordance reverts it — a safety net on top of archive-based soft-delete (§4.7).

---

## 6. Synchronization

Single-writer **control-token** model with **versioned snapshots (checkpoints)**: exactly one device may write at a time; the other is read-only; data travels as whole-DB snapshots that can be checkpointed mid-session, with the handoff of control being a checkpoint that also cedes the token. Mobile is the master. Snapshots are **encrypted with a user-held key** before leaving a device (§7.4). Full lifecycle, failure cases, and recommended policy are in **`02-synchronization.md`**.

---

## 7. Non-Functional Requirements

### 7.1 Offline behavior
The app is **fully functional offline** on each device. No network is required for any core operation. Sync happens opportunistically when devices exchange snapshots.

### 7.2 Data integrity & durability
- Account balances and debts are **always derived** from source movements (§2.9, §4.1, §4.2) — no drift.
- Each device persists work to disk continuously; a crash never loses more than work since the last checkpoint (`02-synchronization.md`).
- Splits must reconcile to the expense total (§3.7); settlements are bounded by debt unless overridden (§3.9).
- Soft-delete (archive) is the default; hard deletes are guarded.

### 7.3 Performance expectations (data scale)
- Designed for a single user's lifetime of personal data (order of thousands–tens of thousands of movements), comfortably handled by a local DB.
- Analysis queries (aggregations, breakdowns, flow over time) should feel instant at this scale; index by date, account, category, trip, person.
- CSV import should handle a full bank statement (hundreds–thousands of rows) in one pass with dedup and auto-categorization.

### 7.4 Privacy / local-first stance
- All financial data lives **on the user's devices**; there is no cloud database (§1.3).
- Snapshot transport may use a user-controlled channel (e.g. a personal shared folder); the user owns their data end to end.
- No advertising, no third-party data sharing.
- **[DECIDED] Snapshot encryption.** A snapshot is a complete copy of the database and may come to rest in a shared/cloud folder, so **every snapshot is encrypted with a user-held key before it leaves a device** (authenticated symmetric encryption; the same key is provisioned on each of the user's devices, §5.8). A device decrypts an incoming snapshot only with that key. This closes the one data exposure the sync model introduces — a readable copy of the database sitting outside the device.
- **On-device data at rest** relies on the operating system's built-in storage encryption (Android file-based encryption / Windows BitLocker), enabled by default on modern devices. The app does **not** add a separate at-rest database cipher or an app lock in v1, consistent with the single-user, no-auth stance (§1.2); both remain available as future enhancements (§8).
- This is a **personal app** for the user and a close circle, not a public or multi-tenant product. The threat model is a lost/stolen device or a snapshot resting in third-party cloud storage — not a hostile shared host.

### 7.5 Internationalization readiness
- The UI ships in **Catalan only**, but all user-facing strings are **externalized** (no hardcoded literals in the codebase) from day one, so adding another language later is a translation task rather than a refactor. No second language is planned for v1.

---

## 8. Open Questions & Future Considerations

**Resolved (now settled in the relevant sections):**

- **Credit-card account type** — *not* modeled; a credit card is a normal account (§3.1).
- **Transfer splitting** — transfers are never shared, purely between own accounts (§3.5).
- **Trip tag ↔ category coexistence** — keep both; tag is an optional trip extra, movements may have just a category (§3.13).
- **Tags per movement** — one tag max, a nullable `tag_id` (§3.13).
- **Overdue recurring** — prompt-only, never auto-add (§3.10).
- **Per-movement nature override** — not included; nature follows the category (§3.2).
- **Subcategories** — included, two-level hierarchy (§3.2).
- **Budget scope rollout** — category-monthly first (§3.14).
- **Budgets vs. categories** — budgets are a **separate entity**, not a field on the category, for more flexibility (§3.14).
- **Multi-user / profiles / PIN / app-lock** — dropped; single user, the device owner (§1.2).
- **Refunds** — first-class movements of type `refund`, **linked** to the original expense; net down the expense in actual analysis, count as inflow in flow (§3.3b).
- **Payee field** — included as an optional movement field (§3.0).
- **Encryption** — snapshots are encrypted with a user-held key in v1; on-device data at rest relies on the OS's storage encryption (§7.4). A dedicated at-rest DB cipher and an app lock are deferred (see future enhancements).
- **Auto-categorization tie-breaks** — when two equal-priority rules match, the **most recently created rule wins**, with the conflict surfaced as a warning (§3.15, §4.4).
- **Equal-split rounding** — leftover cents from an equal split are assigned to the **payer**, so the split reconciles exactly to the expense amount (§3.7).
- **Refund of a shared expense (v1)** — a refund reduces only the **user's own share** in actual analysis; refunding other participants' shares is out of scope for v1 (§3.3b).

**Scheduled but out of v1:**

1. **Person groups** ("Roommates," "Trip crew") as split shortcuts — **scheduled for v1.5**; a saved shortcut over the existing arbitrary-participant split model, with no model impact (§3.6).
2. **Refund of other participants' shares** — v1 reduces only the user's own share; adjusting others' shares is deferred post-v1 (§3.3b).
3. **Payee/merchant as a remembered entity** — promote the free-text `payee` field to a light entity (autocomplete + optional default category); **scheduled for v1.5** (§3.0).
4. **Bulk multi-select edit** (desktop) — select many movements to recategorize, retag, or delete in one action; **scheduled for v1.5** (§5.1).

No open design questions remain that block the spec, the data model, or v1 implementation.

**Future enhancements (post-v1):**

- **Savings goals / sinking funds** — *deferred to a much later date; deliberately out of v1.* Isolated enough to add anytime with zero impact on the rest of the model. Definition for when it's revisited: an **earmark over existing balance, not a container for money** — a goal has a name, target amount, optional target date, optional linked account, and a current allocated amount; progress = allocated ÷ target. It does **not** hold money, create movements, or affect flow or net worth — it annotates how much of a real account's balance is mentally reserved for a purpose.
- **Receipt / attachment storage** on movements (images), affecting storage and snapshot/blob handling.
- **Dedicated at-rest DB encryption + app lock** (PIN/biometric) beyond the OS's storage encryption (§7.4) — snapshot encryption already ships in v1; this would add device-compromise protection if wanted later.
- **Quick-add capture surfaces** (Android app shortcuts, home-screen widget, Quick Settings tile) to log a movement without opening the app — an extremely-future possibility, no model impact.
- **System-learned categorization** maturing from suggestions to confident auto-rules.
- **Multi-currency** — explicitly deferred; would touch the entire amount model.

---

*End of specification (working draft). 
