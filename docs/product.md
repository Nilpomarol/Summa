# Product

## Purpose

Gestor Finances is a private, offline-first personal finance app. It helps one person understand account flow, actual income and spending, shared expenses and debts, recurring activity, budgets, and trip spending without requiring a hosted account or cloud database.

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
- dashboard and multi-period analysis using canonical derived data;
- duplicate warnings and category suggestions;
- local notifications;
- unencrypted whole-database backup and restore to a user-selected Android folder.

Windows currently validates the shared schema, SQL, and money rules but has no user interface.

## Durable product rules

These rules survive visual redesigns unless the user explicitly changes the product model.

1. Money is stored as signed meaning plus positive integer euro cents. Never store finance values as floating point.
2. Movement `date` is a local calendar date (`YYYY-MM-DD`). Audit fields ending in `_at` are UTC instants.
3. The ledger stores positive `amount_cents`; movement type and related fields determine direction and meaning.
4. Account balances, account flow, actual income/expense, debts, and trip actual totals are derived from canonical SQL. They are never stored as independent truth.
5. Archived records use `archived_at`. Normal product flows do not hard-delete finance data.
6. Risky but valid actions warn and allow confirmation. Warnings such as duplicates, over-refunds, excess settlements, or archiving with dependencies do not become unexplained hard blocks.
7. Android and Windows must produce the same financial result from the same database and golden vectors.
8. User-facing copy is externalized Catalan; code, identifiers, comments, and technical documentation are English.
9. The product remains single-user, euro-only, and local-first. No authentication, profiles, currency field, or hosted cloud database is in scope.
10. CSV bank import belongs to Windows only.

Database constraints may reject structurally invalid records. “Warn, do not block” applies to valid but potentially surprising user decisions, not to corrupt ledger shapes.

## Finance meanings

### Account flow and actual values

Account flow answers what entered or left an account. Actual income and expense answer what economically belongs to the user. Those values can differ for transfers, shared movements, refunds, and expenses paid by another person. Screens and domain code consume the relevant canonical view rather than reconstructing either meaning.

### Shared expenses and debts

A shared expense has one payer and absolute split lines whose total reconciles with the expense. The user may be the payer, another participant may pay, or an expense may exist only as an external split when no user account was involved.

Debt is derived from active splits and settlements. It is not an editable balance. Positive and negative presentation must always name the direction so meaning is not conveyed by colour alone.

### Refunds

A refund links to an expense. Actual expense is net of eligible refunds through the canonical view. Over-refunds are allowed after a warning because historic or imported records can legitimately need them.

### Recurring activity

Templates describe future occurrences; confirmed occurrences become real movements. Editing a linked movement does not silently rewrite the template. Ending a series, unlinking one occurrence, rescaling split amounts, or dropping participants must be explicit to the user.

### Budgets

Budgets evaluate actual expense over their active period: an optional overall monthly target, category monthly/yearly limits, and independent trip limits. Current-month views pair actuals with an explainable forecast. Crossing a threshold changes status and warning presentation; it does not prevent spending.

### Trips and tags

A movement can belong to one trip and have at most one compatible tag. Tags may be global, limited to a trip type, or local to one trip. Category and tag remain different dimensions.

## Current boundaries

- The Android database may contain real user data. Do not clear, replace, or seed it during development unless the user explicitly approves an isolated test-data flow.
- Android backup/restore is implemented; encrypted token-based Android/Windows synchronization is not.
- Auto-categorization suggestions exist, but a complete rules-management interface does not.
- The Windows application, Windows CSV importer, packaging, and distribution do not exist yet.

These are boundaries, not an active backlog. Work is selected explicitly during the redesign or Windows phases.
