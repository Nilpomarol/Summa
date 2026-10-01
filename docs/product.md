# Product

## Purpose

Summa is a private, local-first personal finance app for one person. It is euro-only, Catalan-first, and does not require a hosted account or bank connection. Android is the working product. Windows is future work.

## Current capability

First-time setup is one screen: a brief local/privacy message and a first personal account with a name and starting balance. Bank type, default-account status, and starter categories use safe defaults; account settings remain editable in Accounts. Creating the account opens Home, whose empty activity offers a new movement. Any existing account, including an archived one, bypasses onboarding on launch; an existing user with no active account can create one from Home.

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

Global movement creation offers Expense, Income, and Transfer. Refunds start from their source expense, settlements from a person's debt, and contributions/withdrawals from the shared account. These contextual operations retain their existing finance meaning. The normal movement form starts with its common fields and discloses uncommon sharing, trip/tag, recurrence, and additional metadata in related groups.

Movement history offers search and All, Expenses, Income, and Transfers as its primary filters. A single Filters sheet groups account, category, trip, and period, with common period presets and custom dates. Changes apply together; dismissing the sheet cancels them. Clearing filters preserves search. Specialist movements remain in All and search; contextual visits preserve supplied criteria and their Back path, while bottom-navigation visits reset to the global list.

Home leads with the selected account's physical balance, with net worth as secondary context. Its current-month summary shows canonical actual spending, or the monthly plan's spending against its total, what is left, and its forecast or pace, naming any exclusions. Its Pendent card lists plan partides (or the rest) that are over or heading over. It keeps five recent movements, conditional active-trip context, and existing low-balance/budget warnings. Trends, ratios, forecasts in detail, and category breakdowns remain in Analysis or Budgets.

Analysis centres on spending for a month or a year, optionally for one account. A month compares with a typical month (the average of up to three earlier months with activity); a month still running compares up to the same day and shows its pace against the typical month. A year compares with the previous year up to the same day. The page breaks spending down by category and trip (a category opens a sheet with its subcategories and last twelve months), by how it came about (recurring, variable, one-off), month by month, and shows net worth at each month end. Net worth for an earlier month is today's balance less the flow dated after it, so it counts opening balances before an account existed.

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

An expense another person paid is an ordinary expense with that person as its payer: it moves none of the owner's accounts, counts the owner's share as actual expense, and leaves the owner owing the payer that share. The owner's share is the whole amount or a split with the payer and anyone else. Changing who paid edits the same expense.

A settlement may consume all eligible debt or recurring-only debt. Consumption is chronological against debt that existed on the settlement date. Excess settlement remains directional credit for later eligible debt.

Shared-account balance, owner patrimonial value, economic split, and interpersonal debt are separate concepts. A shared-account-financed expense changes the account balance and actual expense according to the split but does not by itself create person-to-owner debt.

### Refunds

A refund belongs to an expense and reduces that expense's actual cost. It inherits the relevant classification for analysis. Over-refunds are allowed after a warning. While it has active refunds, an expense can be edited but not changed into another movement type.

### Recurring activity

Templates describe future occurrences; confirmed occurrences become real movements. Editing one confirmed movement does not silently rewrite the template. Series changes are explicit.

An occurrence is expected on its date give or take the template's margin in days: its reminder opens at the start of that window and it is overdue once the window has passed. Notifications follow the same moments, each of which Settings can turn off: ahead of the date (the chosen notice, or the start of the window if earlier), on the date, and two days after the window has passed unrecorded; a later one replaces an earlier one, and all go once the occurrence is recorded. Plan, low-balance and stalled-backup alerts can each be turned off as well. An amount is fixed or approximate; an approximate one is estimated from the average of its last three occurrences (the template's own estimate until there are any), and that estimate is what forecasts and the confirmation prefill use. An occurrence is fulfilled by confirming its reminder, or by a movement recorded by hand: a new movement of the same type and account, in the template's category when it has one, near the pending date and at a close amount is offered, ticked, as that occurrence, and a reminder can link one already recorded. Either way the template moves on to its next date. An item's page can also link several past payments at once, whatever they came to, as long as they fit its rhythm (one per expected date when all are marked); the template moves on past the dates they cover. When a fixed template's occurrence comes in at a different amount, updating the template's amount is offered, never done silently. A template is paused (resumable) or ended; only one never paid can be deleted. A template's stored split is its allocation: every occurrence — its confirmation preview, the confirmed movement, and forecasts — splits its own amount by rescaling that allocation proportionally.

### Budgets

Budgets measure canonical actual expense. They inform spending and never block a valid movement.

The monthly plan is the monthly total split into parts that always add up to it: partides (monthly category limits, each counting its category and subcategories' spending) and the rest (the total minus the partides, catching everything else). Recurring payments count in the part their category falls in; those still due that month are committed there, counting in its forecast and expected on their due dates rather than spread over the month. Partides may not overlap: a category and its parent or child cannot both have a monthly limit. The plan's inclusions are set on its total and apply to every part; trip spending is left out by default, since trips have budgets of their own. Each part shows its pace (where it should stand by today), what is left per day once what is due is set aside, and an end-of-month forecast from what is due plus recent variable spending; actual and forecast values must be labelled separately. With a total, the plan shows the month's expected saving: expected income (what has come in plus recurring income still due, or the recent monthly average when there is no recurring income) less the plan, and, when the plan leaves trips out, less what trips have spent that month and what the budgets of trips still to come or under way have left for it (by their dates); a closed month shows what came in less everything spent, trips included. An earlier month is measured against the plan it had: a change to the plan applies from the month it is made in, and months from before there was a plan take its first shape. A plan part notifies once a month when its forecast goes over what it plans, and once when it goes over. Yearly category limits sit outside the monthly plan; a trip's budget is set and followed on the trip, where a category's page also edits its own partida; a partida on the plan opens its category's page.

### Savings goals

Goals reserve existing money; they never create ledger activity. A goal either follows a dedicated account value or uses dated manual allocations. Reservations do not change account balance, actual income/expense, debt, or net worth. Completing a goal that still reserves money offers to release it, dated that day, since the money has usually been spent on what it was saved for.

### Trips and tags

A movement may belong to one trip and one compatible tag. Categories and tags remain separate dimensions. Trip budgets use trip actual expense.

## Current boundaries

- No authentication or hosted account.
- No implemented cross-device synchronization.
- No Windows application yet.
- No live bank connection.
- Investment valuations are not implemented yet; holdings, trades, units, live prices, dividends, tax lots, and multiple currencies are out of current scope.
- Future work belongs in [backlog.md](backlog.md) or `docs/future/`, not in current architecture rules.
