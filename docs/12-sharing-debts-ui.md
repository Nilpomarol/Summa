# Sharing, People and Debts UI - Android P3

> Scope: P3-1. This refines the Android screens needed for Phase 3: people, debt detail, split editor, shared-expense variants, and settle-up. It does not add product rules beyond `docs/00-Full_Spec.md`, `docs/04-data-model.md`, `docs/08-design-system.md`, or `docs/09-app-architecture.md`.

---

## 1. Phase Scope

P3 adds the shared-expense and interpersonal-debt surface:

- create and manage people;
- split an expense between the user and people;
- record "paid by other" expenses that still pass through the user's account;
- record external friend-paid expenses that do not create a user account movement;
- show person balances from `v_person_balance`;
- create settlements that affect account flow but not actual income/expense;
- mark shared movements in ledger lists and detail.

P3 must not implement recurring templates, refunds, budgets, trips/tags, CSV import, sync, or forecasting. External splits carry their own date and category in P3; trip attachment uses the same optional model field later when Phase 5 makes trips available in the UI.

---

## 2. Navigation Shape

When the People screen becomes usable, it lives under the Gestió hub defined in `docs/07-ui-ux.md`. The primary Android destinations remain:

- Inici;
- Moviments;
- centered New movement FAB;
- Anàlisi;
- Gestió.

Categories remains a management surface reachable from Gestió, account/movement management entry points, and category pickers. The global New movement action remains centered and opens the existing movement flow. Person-specific actions, such as Settle up or Person paid for me, start from Gestió > Persones or Person detail so the person context is preserved.

---

## 3. People List

Purpose: show who has an open balance and provide the entry point for shared expenses and settlements.

Content:

- title: People;
- summary strip with total owed to the user, total the user owes, and net debt position;
- active person rows sorted by non-zero balance first, absolute balance descending, then name;
- each row shows monogram/avatar, name, optional context from the latest related item, right-aligned amount, and direction label:
  - positive balance: "owes you";
  - negative balance: "you owe";
  - zero: "settled";
- empty state with New person action.

Actions:

- New person;
- open person detail;
- start an external friend-paid expense;
- optional filter to hide settled people once the list grows.

Data rules:

- balances are read from `v_person_balance`, never stored or recomputed as UI truth;
- archived people are hidden from the default list;
- warning totals are informational and must not block creating additional shared records.

Visual identity:

- use the design-system Person row: round monogram/avatar, direction label plus amount color, never color alone;
- positive/owed-to-user uses income/positive green; user-owes uses debt/danger red; settled uses neutral muted text.

---

## 4. Person Detail and Debt View

Purpose: explain a person's balance and expose the settlement workflow.

Content:

- person header with avatar, name, notes when present, and net balance;
- primary Settle up action when balance is non-zero;
- itemized breakdown grouped by source:
  - user-fronted shared expenses where this person owes a line;
  - friend-paid external splits where the user owes this person;
  - settlements in either direction;
- each item shows date, source name/description, category when available, signed debt effect, and source type;
- history section with the same items in reverse chronological order;
- settled state when net balance is zero.

Actions:

- Settle up;
- Person paid for me;
- Edit person;
- Delete person;
- share/export debt statement later from the same itemized data.

Delete behavior:

- the UI says Delete, but P3 uses soft-delete (`archived_at`) internally;
- if the person's derived balance is non-zero, show a dismissible warning and allow override;
- historical splits and settlements remain intact.

Data rules:

- the detail view may use a focused repository query over splits, split lines, movements, and settlements, but the displayed net balance must match `v_person_balance`;
- movement-backed rows open movement detail;
- external split rows open the split/debt item detail, not the movement list, because no movement exists.

---

## 5. Add/Edit Person

Purpose: maintain the small person record used by splits and settlements.

Fields:

- name;
- avatar/monogram;
- color from the existing category/person-safe palette;
- notes.

Validation:

- name is required;
- archived people are not offered in new split pickers unless an existing historical record already references them.

State:

- form state belongs in a ViewModel;
- save returns validation errors for schema-invalid input and warnings for non-zero archive only.

---

## 6. Split Editor

Purpose: convert a shared-expense draft into absolute owed cents that reconcile exactly.

Entry points:

- Shared toggle in Add/Edit movement for expense movements;
- Edit split from movement detail;
- Person paid for me from People or Person detail for external splits.

Common content:

- participant picker with the user plus active people;
- payer selector: user or one person;
- method segmented control: equal, exact, percentage;
- participant rows with name, amount, optional percent, and remove action;
- live reconcile footer showing balanced, remaining, or over total;
- remainder note for equal splits: leftover cents go to the payer.

Variant A - user-fronted shared expense:

- parent movement exists and provides total amount, date, category, account, and description;
- payer is the user;
- split lines must include one user line and any selected person lines;
- sum of active lines equals the movement amount;
- each person line increases that person's balance; the user line is the actual expense.

Variant B - "paid by other" through the user's account:

- parent expense movement still exists and affects account flow for the full amount;
- canonical line setup is user share = 0 and one person line = full amount;
- UI labels the movement as paid by that person;
- actual expense contribution is zero for the user.

Variant C - external friend-paid expense:

- no movement is created at purchase time;
- payer is a person;
- split owns total amount, date, description, category, and later optional trip;
- user's line is required and is what the user owes;
- it appears in People/debt and actual spending analysis, but not in the account-flow movement list.

Validation:

- hard errors only for schema-invalid data: non-positive total, missing payer, no user line where required, invalid person selection, or unreconciled totals;
- editing a split after settlements may create an over-settlement warning, but it must not hard-block saving when the user overrides;
- all intermediate UI amounts parse to integer cents before save.

---

## 7. Settle-Up

Purpose: create a settlement movement that moves real money and reduces a person's derived balance.

Entry point:

- Person detail -> Settle up.

Content:

- person;
- inferred direction:
  - person owes the user -> person-to-user inflow;
  - user owes the person -> user-to-person outflow;
- amount prefilled to the absolute outstanding balance;
- account;
- date;
- notes.

Behavior:

- amount may be edited for partial settlement;
- if amount exceeds the current outstanding balance, show an inline warning and allow override;
- saving creates a `movement` with `type='settlement'`, `person_id`, `settlement_direction`, account, amount, date, and notes;
- settlements appear in the movement list as settlement rows and are excluded from actual income/expense.

---

## 8. Movement List and Detail Integration

Movement list:

- show the shared badge using `v_movement_shared`;
- show "paid by X" for the movement-backed paid-by-other variant;
- show settlement rows with settlement color and person direction context;
- keep external friend-paid splits out of the movement list because they do not affect account flow.

Movement detail:

- for shared expenses, show split summary and Edit split action;
- for paid-by-other, show the payer label and zero user share;
- for settlements, show person, direction, account, amount, and notes;
- all delete actions remain soft-delete.

Drill-down:

- aggregate drill-downs that resolve to real movements open the movement list;
- aggregates backed only by external splits should open a debt/split detail when implemented, or remain inert until that route exists.

---

## 9. Visual Rules

Use `docs/08-design-system.md` without new tokens:

- Person rows use the existing person-row debt direction pattern.
- Split participant rows use compact list-card density: avatar chip, participant name, editable amount/percent, remove icon.
- Settle-up and split editor use mobile bottom sheets when launched from an existing screen; larger edit flows may use full-screen forms if the current Compose structure needs it.
- Warnings are inline banners, not blocking modals.
- Destructive person delete confirmation remains the compact centered destructive alert.
- Amounts use Geist Mono/tabular figures and are formatted as euros only at the UI edge.

---

## 10. String Coverage

P3-1 adds Android resource names for:

- `person_*`;
- `debt_*`;
- `split_*`;
- `settlement_*`;
- shared-expense additions under `movement_*`.

All values are Catalan. Keep adding strings beside the slice that needs them; do not hardcode user-facing copy in Compose.

---

## 11. Acceptance Checklist

- People and debt screens are separated from ledger/account screens but share the same movement detail where a real movement exists.
- Person balances are specified as `v_person_balance` backed.
- Split editor stores absolute cents and preserves the equal-split remainder rule.
- User-fronted, paid-by-other, and external friend-paid variants are distinct in UI copy and save behavior.
- Settlements are movement-backed, flow-affecting, actual-excluded, and warning-not-blocking for overpayment.
- Android resource strings exist for the planned P3 screens.
- P3-1 remains documentation and resource preparation; no repository or production screen implementation is expected in this task.
