# Product

## Purpose

Summa is a private, offline-first personal finance app. It helps one person understand account flow, actual income and spending, shared expenses and debts, recurring activity, budgets, and trip spending without requiring a hosted account or cloud database.

The application is single-user, euro-only, and Catalan-first. Android is the primary product. Windows will become a secondary desktop surface over the same finance contract.

## Current capability

The Android application currently supports:

- accounts with opening balances, defaults, ordering, and low-balance thresholds;
- hierarchical expense and income categories;
- expenses, income, transfers, refunds, and settlements in one movement ledger;
- people, shared expenses, user/participant shares, expenses paid by somebody else, and derived debts;
- recurring templates, due-instance confirmation, skipping, and recurrence suggestions;
- central overall-month and category monthly/yearly budgets with forecasted spending, plus independent trip budgets;
- trips, trip types, scoped tags, trip budgets, and trip analysis;
- savings goals with dedicated accounts or manual reservations, progress, and target-date saving pace;
- dashboard and a compact analysis overview for month, year, or all-time periods, with previous-period context and focused account, category, nature, extraordinary-expense, and trip-grouping filters;
- a locally saved System, Light, or Dark appearance setting;
- duplicate warnings;
- local notifications;
- unencrypted whole-database backup and restore to a user-selected Android folder, with optional automatic daily, weekly, monthly, or quarterly backups that retain the five newest files.

Windows currently validates the shared schema, SQL, and money rules but has no user interface.

## Durable product rules

These rules survive visual redesigns unless the user explicitly changes the product model.

1. Money is stored as signed meaning plus positive integer euro cents. Never store finance values as floating point.
2. Movement `date` is a local calendar date (`YYYY-MM-DD`). Audit fields ending in `_at` are UTC instants.
3. The ledger stores positive `amount_cents`; movement type and related fields determine direction and meaning.
4. Account balances, account flow, actual income/expense, debts, and trip actual totals are derived from canonical SQL. They are never stored as independent truth.
5. The UI consistently calls the action **Delete**, while normal finance deletion remains recoverable soft deletion through `archived_at`; it never hard-deletes finance data. A successful deletion offers a short Undo action that reverses that complete operation, including dependency changes made by its confirmation flow.
6. Risky but valid actions warn and allow confirmation. Warnings such as duplicates, over-refunds, excess settlements, or deleting items with dependencies do not become unexplained hard blocks.
7. Android and Windows must produce the same financial result from the same database and golden vectors.
8. User-facing copy is externalized Catalan; code, identifiers, comments, and technical documentation are English.
9. The product has one app owner, is euro-only, and remains local-first. Shared accounts may model ownership by known people, but do not create app users, authentication, or profiles. Optional cloud-linked synchronization is a later deployment mode, not a requirement for local use.
10. CSV bank import belongs to Windows only.

Analysis is intentionally a single overview rather than a tabbed exploration workspace. It does not expose custom ranges or drill-down navigation. Choosing a root destination from the bottom bar or Més starts that destination from its default context; contextual links may preserve their caller-specific state and Back path.

Database constraints may reject structurally invalid records. “Warn, do not block” applies to valid but potentially surprising user decisions, not to corrupt ledger shapes.

## Finance meanings

### Account flow and actual values

Account flow answers what entered or left an account. Actual income and expense answer what economically belongs to the user. Those values can differ for transfers, shared movements, refunds, and expenses paid by another person. Screens and domain code consume the relevant canonical view rather than reconstructing either meaning.

### Shared expenses and debts

A shared expense has one payer and absolute split lines whose total reconciles with the expense. The user may be the payer, another participant may pay, or an expense may exist only as an external split when no user account was involved.

Debt is derived from active splits and settlements. It is not an editable balance. Positive and negative presentation must always name the direction so meaning is not conveyed by colour alone.

A settlement declares the debt it may consume: `all`, or `recurring` for debt raised by recurring templates only. Settlements are replayed chronologically against the debt that already existed on their date, oldest first, so the pending message names real source items and marks partially paid ones instead of collapsing older history into one carry-forward figure. Money a settlement cannot spend stays as directional credit in its own scope and is absorbed by later eligible debt, which is also how an opposite-direction settlement is represented. The derived person balance remains the authoritative total; the explanation always reconciles to it exactly.

### Refunds

A refund links to an expense and inherits its category, trip, tag, and extraordinary classification for actual spending; archiving an expense archives its active refunds. Actual expense is net of eligible refunds through the canonical view. Over-refunds are allowed after a warning because historic or imported records can legitimately need them.

### Recurring activity

Templates describe future occurrences; confirmed occurrences become real movements. Editing a linked movement does not silently rewrite the template. Ending a series, unlinking one occurrence, rescaling split amounts, or dropping participants must be explicit to the user.

Besides expenses, income, and transfers, a template can schedule a settlement with a person, direction, and consumption scope. Fixed and variable amounts both apply. A settlement template carries no category, trip, tag, or split.

### Budgets

Budgets evaluate actual expense over their active period: an optional overall monthly target, category monthly/yearly limits, and independent trip limits. Overall and category budgets can independently exclude trip spending and expenses marked extraordinary; trip budgets always evaluate their own spend. Current-month views pair actuals with an explainable forecast using the same inclusion rules. Crossing a threshold changes status and warning presentation; it does not prevent spending.

### Savings goals

A goal reserves money the user already has; it never moves it. A goal either dedicates one account to itself, so its progress is that account's value, or reserves parts of an account through dated allocations, which lets several goals share one account. Assigning money changes no balance, actual value, debt, or net worth. Each account shows what is still unallocated, and reserving more than remains is a warning the user may accept. Accounts link to their goals; affected goal details explain funding shortfalls. The linked account of an allocation goal is a default, and reservations may come from multiple accounts.

Reserving and releasing are explicit actions with positive amount entry. Pausing or completing retains reserved money and suppresses saving prompts; completing is distinct from reaching the target amount. A target is overdue on the day after its local target date. Allocation deletion offers Undo and cannot remove money already released. Dedicated accounts cannot simultaneously fund other reservations; changing funding mode requires releasing outstanding reservations first.

### Trips and tags

A movement can belong to one trip and have at most one compatible tag. Tags may be global, limited to a trip type, or local to one trip. Category and tag remain different dimensions.

## Committed pre-Windows capabilities

The following capabilities are approved but not implemented. Their detailed order and acceptance gates live in [pre-windows-plan.md](pre-windows-plan.md).

- Shared accounts that separate physical account balance, ownership, expense allocation, payer identity, and contributions.
- Investment account valuations that separate net contributions/withdrawals from market value and unrealized performance.

These capabilities must be stable in the shared contract and Android before Windows product implementation starts.

## Later cross-device direction

After Windows core exists, Summa may add an optional Cloud linked mode using a Summa server and change-based optimistic synchronization. Local SQLite remains authoritative on each device for offline use. The existing encrypted snapshot/token handoff remains the Local only mode. The two synchronization modes are explicit and are not mixed in one dataset.

## Current boundaries

- The Android database may contain real user data. Do not clear, replace, or seed it during development unless the user explicitly approves an isolated test-data flow.
- Android backup/restore is implemented; encrypted token-based Android/Windows synchronization is not.
- The Windows application, Windows CSV importer, packaging, and distribution do not exist yet.
- Category suggestions are neither a current capability nor a promise for Android; their future scope is deliberately undecided rather than mobile-only.
- No responsive-layout work is currently selected. The existing narrow-width, text-scaling, and accessibility guidance remains the baseline.
- Portfolio positions, trades, units, live quotes, dividends, corporate actions, and multi-currency investment tracking are outside the approved investment-valuation scope.
- Cloud-linked synchronization is not a pre-Windows gate.

The only active Android feature backlog is [pre-windows-plan.md](pre-windows-plan.md).
