# Product

## Purpose

Summa is a private, local-first personal finance app for one person. It is euro-only, Catalan-first, and does not require a hosted account or bank connection. Android is the working product. Windows is future work.

## Current capability

Android currently supports:

- personal and shared accounts;
- expenses, income, transfers, refunds, settlements, and shared-account contributions/withdrawals;
- categories, trips, tags, search, and filters;
- people, expense splits, shared income allocation, and derived debts;
- recurring templates and due-instance confirmation;
- monthly/yearly budgets, trip budgets, and current-month forecasts;
- savings goals using a dedicated account or manual allocations;
- dashboard and period analysis;
- local notifications, themes, backup, and restore.

The Windows project is only a shared-contract validation harness; there is no Windows UI.

## Durable product rules

1. Money is stored as positive integer euro cents. Type and related fields carry direction and meaning.
2. Movement `date` is a local `YYYY-MM-DD`; `*_at` fields are UTC instants.
3. Account flow, balances, actual income/expense, debt, trip actuals, and goal progress are derived; screens must not invent competing totals.
4. Normal finance deletion is recoverable soft deletion through `archived_at`.
5. Risky but valid actions warn and allow confirmation. Structurally invalid data remains an error.
6. The product has one app owner. Shared accounts reference known people but do not create app users, profiles, or authentication.
7. User-facing copy is resource-backed Catalan. Code and technical documentation are English.
8. The Android database may contain real data and must never be cleared or replaced casually.

## Finance meanings

### Account flow vs actual values

**Account flow** answers what physically entered or left an account. **Actual income/expense** answers what economically belongs to the app owner. Transfers, shared movements, refunds, and externally-paid expenses can make these values differ.

### Shared expenses and debt

Expense splits describe economic consumption. Funding/payer information describes where the money came from. Debt is derived from active expense splits and settlements; it is not an editable balance.

An expense another person paid is an ordinary expense with that person as its payer: it moves none of the owner's accounts, counts the owner's share as actual expense, and leaves the owner owing the payer that share. Changing who paid edits the same expense.

A settlement may consume all eligible debt or recurring-only debt. Consumption is chronological against debt that existed on the settlement date. Excess settlement remains directional credit for later eligible debt.

Shared-account balance, owner patrimonial value, economic split, and interpersonal debt are separate concepts. A shared-account-financed expense changes the account balance and actual expense according to the split but does not by itself create person-to-owner debt.

### Refunds

A refund belongs to an expense and reduces that expense's actual cost. It inherits the relevant classification for analysis. Over-refunds are allowed after a warning.

### Recurring activity

Templates describe future occurrences; confirmed occurrences become real movements. Editing one confirmed movement does not silently rewrite the template. Series changes are explicit.

### Budgets

Budgets measure canonical actual expense. They inform spending and never block a valid movement. The current-month forecast combines recorded expense, known remaining fixed recurring expense, and a recent variable-spending estimate when enough history exists. Actual and forecast values must be labelled separately.

### Savings goals

Goals reserve existing money; they never create ledger activity. A goal either follows a dedicated account value or uses dated manual allocations. Reservations do not change account balance, actual income/expense, debt, or net worth.

### Trips and tags

A movement may belong to one trip and one compatible tag. Categories and tags remain separate dimensions. Trip budgets use trip actual expense.

## Current boundaries

- No authentication or hosted account.
- No implemented cross-device synchronization.
- No Windows application yet.
- No live bank connection.
- Investment valuations are not implemented yet; holdings, trades, units, live prices, dividends, tax lots, and multiple currencies are out of current scope.
- Future work belongs in [backlog.md](backlog.md) or `docs/future/`, not in current architecture rules.
