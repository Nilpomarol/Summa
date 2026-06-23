# Core Ledger UI - Android P1

> Scope: P1-1. This refines the Android screens needed for Phase 1: accounts, movement list/detail, and add/edit movement for expenses, income, and transfers. It does not add new product rules beyond `docs/00-Full_Spec.md`, `docs/04-data-model.md`, or `docs/08-design-system.md`.

---

## 1. Phase Scope

P1 builds the first usable Android ledger:

- create and manage accounts;
- create and manage categories;
- create, edit, and delete `expense`, `income`, and `transfer` movements;
- show account balances from `v_account_balance`;
- show account flow from `v_account_flow`.

The first ledger UI must not show disabled future controls for splits, people, settlements, refunds, recurring templates, budgets, trips, tags, sync, or CSV import. Those features get their own phase-specific UI pass.

---

## 2. Navigation Shape

Use the mobile shell from `docs/07-ui-ux.md` and `docs/08-design-system.md`:

- bottom navigation: Inici, Moviments, centered New movement FAB, Anàlisi, Gestió;
- Gestió hub: Comptes, Categories, Persones, Esdeveniments, Recurrents, Configuració in a 2-column × 3-row grid;
- centered primary action: New movement, always visible after onboarding;
- top app bar title changes with the selected screen;
- read-only banner can be added later by sync work, but P1 screens should reserve the pattern: edit actions can be disabled from shared app state when sync arrives.

P1 can leave Inici, Anàlisi, and later Gestió child pages as simple placeholders until their phases. Moviments, Comptes, and Categories are the active destinations for this slice. Back from Moviments or Gestió returns to Inici; Back from a Gestió child returns to the Gestió hub.

---

## 3. Accounts List

Purpose: show where money is and provide the entry point to account management.

Content:

- title: Accounts;
- total net worth, derived as the sum of active account balances from `v_account_balance`;
- active account rows/cards sorted by `display_order`, then name;
- each account row shows icon/color, name, type, optional default badge, current balance, and low-balance warning state when configured;
- empty state with a single New account action.

Actions:

- New account;
- open account detail;
- reorder accounts when P1-3 implements display order;
- delete from account detail, not from the list row, to avoid accidental destructive actions.

Visual identity:

- icon/color fields exist on accounts and categories, but Phase 1 should keep selection lightweight;
- categories use a small predefined identity set from the design tokens, not a free-form color picker;
- accounts can initially derive identity from account type and only expose a small fixed set if the UI needs it.

Data rules:

- do not store current balance in app state as truth; read it from `v_account_balance`;
- deleted accounts are hidden from the default list;
- starting balance is shown as setup data, not as a movement.

---

## 4. Account Detail

Purpose: inspect one account and its ledger flow.

Content:

- account name, type, and default badge;
- current balance from `v_account_balance`;
- starting balance from `accounts.starting_balance_cents`;
- low-balance threshold if set;
- scoped movement list for the account, read from the movement table plus `v_account_flow` where signed flow is needed;
- empty scoped state when the account has no movements.

Actions:

- New movement, prefilled with this account;
- Edit account;
- Delete account;
- back to Accounts.

Delete behavior:

- the UI says Delete, but P1 uses a soft delete (`archived_at`) internally;
- if live dependencies are found later, show a dismissible warning and allow override;
- never hard-delete from the normal P1 flow.

---

## 5. Movements List

Purpose: browse the ledger and drill into details.

Content:

- title: Movements;
- search field for concept, payee, and notes;
- compact filters for type, account, category, and period;
- grouped rows by local calendar date;
- row title from the movement concept (`movements.name`), falling back to payee/category/type label;
- subtitle with category or transfer destination, account, and optional notes snippet;
- right-aligned signed amount:
  - expense: negative;
  - income: positive;
  - transfer: neutral transfer color, not income/expense;
- empty state with New movement action.

Actions:

- New movement;
- open movement detail;
- clear filters.

Data rules:

- amounts are integer cents formatted to euros at the UI edge;
- transfer appears once in the list, even though `v_account_flow` has two flow legs;
- deleted movements are hidden by default.

---

## 6. Movement Detail

Purpose: show exactly what was saved and expose edit/delete actions.

Content:

- type, amount, date;
- origin account;
- destination account for transfers;
- category for expenses/income when set;
- concept (`movements.name`), payee, and notes when present;
- one-time badge for expenses with `is_one_time = 1`;
- audit timestamps can stay below the fold or be omitted from the first UI if they add clutter.

Actions:

- Edit;
- Delete;
- back to Movements or the source account detail.

Delete behavior:

- soft-delete only in P1;
- confirmation copy says the item is removed from active lists; it should not use archive language.

---

## 7. Add/Edit Movement

Purpose: save valid ledger rows for the Phase 1 movement types.

Form sections:

1. Type segmented control: Expense, Income, Transfer.
2. Amount: positive euro input, stored as cents.
3. Date: local calendar date.
4. Accounts:
   - expense/income: account;
   - transfer: origin account and destination account.
5. Classification:
   - expense/income: category picker with "No category" allowed;
   - transfer: no category field.
6. Details:
   - concept (`movements.name`);
   - payee;
   - notes;
   - one-time toggle for expenses only.

Primary actions:

- Save movement for create mode;
- Save changes for edit mode;
- Cancel.

Validation:

- hard errors only for schema-invalid data:
  - missing or non-positive amount;
  - missing date;
  - missing required account;
  - transfer destination missing;
  - transfer origin equals destination.
- duplicate detection is a warning, not a blocker;
- category is optional because the schema allows uncategorized movements.

State:

- form state belongs in the future `ViewModel`, not directly in composables;
- business draft uses cents and `LocalDate`;
- UI text input may temporarily hold a localized euro string until parsed.

---

## 8. Add/Edit Account

Purpose: create or update account setup data.

Fields:

- name;
- type: bank, cash, savings, investment, other;
- starting balance in cents, formatted as euros in the field;
- icon/color;
- default account toggle;
- optional low-balance threshold.

Validation:

- account name is required;
- only one active account can be default, enforced by schema and mirrored in app logic;
- starting balance can be negative;
- low-balance threshold is optional.

---

## 9. String Coverage

P1-1 adds the Android resource names that the Phase 1 screens should use. Android uses underscore resource names; docs may continue to discuss logical keys with dots.

Groups added:

- `nav_*`
- `common_*`
- `account_*`
- `category_*`
- `movement_*`

All values are Catalan. Keep adding strings beside the slice that needs them; do not hardcode user-facing copy in Compose.

---

## 10. Acceptance Checklist

- Account screens only expose account behavior in Phase 1.
- Movement form only exposes expense, income, and transfer in Phase 1.
- Derived balances and flow are described as view-backed, not app-computed.
- Strings needed by the planned screens exist in Android resources.
- P1-1 is documentation and resource preparation; no repository or production screen implementation is expected in this task.
