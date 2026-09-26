# Backlog

This is the active product backlog. Keep it short and ordered by current value, not by historical project phases.

## Now

### 1. Verify recently completed flows on a real device

- savings-goal creation/editing/allocation/archive/restore;
- shared-account expense, contribution, withdrawal, allocated income, account-ledger reconciliation, and refusal to un-share accounts with shared history;
- backup/restore around those records.

Record bugs as concrete tasks. Do not reopen completed implementation plans unless the behaviour itself is wrong.

### 2. Investment valuations

Add useful valuation semantics to the existing investment account type without turning Summa into a portfolio tracker.

First version:

- manual dated absolute valuations;
- latest valuation = current investment account value;
- deposits/withdrawals remain ledger transfers;
- derive net contributed capital separately;
- show current value, contributed capital, unrealized gain/loss, percentage return, and valuation age;
- net worth uses current value;
- market movement is never income or expense.

Out of scope: securities/holdings, units, trades, live prices, dividends, corporate actions, tax lots, and multiple currencies.

## Later

### Simplification work

Continue reducing accidental complexity where it has a concrete payoff:

- split the oversized movement feature by user flow.

Treat these as independent refactors. Preserve behaviour and real user data; do not combine all of them into one rewrite.

### Windows

A Windows app may be built after the Android product/data model is stable enough to justify it. Current notes live in `docs/future/windows.md`.

### Cross-device sync

Cross-device synchronization is not selected current work. Any future implementation should be designed when there are two real clients to synchronize rather than forcing seams into Android today. Notes live in `docs/future/sync.md`.
