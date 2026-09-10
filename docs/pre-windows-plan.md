# Pre-Windows Plan

## Status and authority

This is the only active product backlog before Windows. The Android UI redesign is complete, and retired redesign plans, audits, remediation trackers, and unchecked phase tasks are not requirements.

Complete the gates in order because later work depends on earlier financial semantics. A gate closes only when its shared contract, migration, Android flow, tests, and concise durable documentation are complete. Keep the Windows shared-contract harness green throughout; do not start WinUI product work before Gate 4 closes.

## Gate 1 — Recurring settlements and explainable debt messages

- Allow recurring templates to create settlements with person, direction, amount, cadence, next date, and scope.
- Support scope `all` and `recurring`; recurring scope may consume only debt items created from recurring templates.
- Replace the last-settlement cutoff and generic carry-forward message with chronological FIFO debt consumption.
- Apply each settlement only to eligible debt already existing at the settlement date, oldest first.
- Preserve over-settlement as directional credit so later debt can consume it.
- Show residual source lines in the generated Catalan message, including partially paid items.
- Keep `v_person_balance` authoritative for the total and require the explanatory residuals to reconcile exactly.
- Do not persist settlement-to-expense allocations in this version.

Status: closed. Shared contract, Android implementation, both test harnesses, and device verification are accepted.

Exit: fixed and variable recurring settlements materialize correctly; normal and recurring-scoped settlements produce explainable residual messages independent of whether exceptional expenses occurred before or after the settlement; golden cases cover partial payment, future debt, opposite direction, and excess credit.

## Gate 2 — Savings goals

- Add active, paused, and completed goals with name, target cents, optional target date, optional linked account, and visual identity.
- Support a dedicated-account goal whose progress follows that account's canonical value.
- Support multiple goals within one account through dated planning allocations.
- Keep allocations outside the ledger: assigning money changes neither account balance, actual income/expense, debt, nor net worth.
- Show progress, remaining amount, unallocated account value, and the required monthly pace when a target date exists.
- Prevent or explicitly resolve over-allocation against the linked account's available value.
- Do not add automatic allocation rules in the first version.

Status: implemented in the shared contract (schema v12, migration 012, `v_goal_allocation`, `v_goal_progress`, `v_account_allocation`, the `goal_progress` golden vector), Android, and both test harnesses; manual verification on a device is the remaining step before this gate closes.

Exit: goals remain mathematically consistent through allocation edits, account-value changes, completion/pausing, archive/restore, and backup/restore.

## Gate 3 — Shared accounts

- Model account members using the app owner and existing people; this does not create multiple app users.
- Store explicit ownership percentages and default expense splits.
- Separate physical account balance, app-owner patrimonial value, economic expense split, and payer/financing source.
- Allow the shared account itself to finance an expense without pretending that the app owner or one person paid it.
- Add contributions for money placed into a shared account without classifying them as income, expense, or settlement.
- Keep transfers between owner-controlled accounts as transfers; define explicit flows when money crosses between personal and shared ownership.
- Update account value, net worth, debt, message, and analysis queries so the meanings remain labelled and reconcilable.
- Do not build per-member capital accounts or retroactively infer exact ownership from historical consumption.

Status: in progress. The current shared contract is complete at schema 17, but the Android usability review found incomplete or ambiguous flows around withdrawals, income ownership, shared-account expense funding, account-context amounts, and contribution presentation. The selected next implementation task is [Shared Accounts Usability](shared-accounts-usability.md). Refusing to un-share an account that carries shared history is enforced and covered by tests but still requires device verification.

Exit: a shared account can represent its bank balance and the owner's patrimonial share while splits still answer who economically consumed an expense and debt still answers who owes whom.

## Gate 4 — Investment valuations

- Give the existing investment account type real valuation semantics.
- Add manual dated absolute valuation snapshots with source and audit timestamps.
- Keep deposits and withdrawals as transfers and derive net contributed capital separately.
- Expose current market value, net contributed capital, unrealized gain/loss, percentage return, and valuation age.
- Use latest valuation for investment account value and opening balance plus flow for normal account value.
- Include current investment value in net worth without recording market variation as income or expense.
- Handle no-valuation and stale-valuation states explicitly.
- Do not add securities, holdings, trades, units, live prices, dividends, corporate actions, tax lots, or multiple currencies.

Exit: investment value can change without a movement, contributions do not alter actual income/expense, and net worth plus performance reconcile across valuation history.

## After the gates

Begin [Windows implementation](windows-plan.md) using the stabilized schema and product meanings.

Optional Cloud linked multiwriter synchronization follows Windows core. It keeps local SQLite on each client, synchronizes atomic changes through a Summa server, uses optimistic concurrency, and coexists as an alternative to the Local only encrypted snapshot/token mode. It is not a pre-Windows gate.
