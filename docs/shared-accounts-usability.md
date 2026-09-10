# Shared Accounts Usability

## Status

Selected next implementation task for Android under Gate 3. This work follows the current shared-account contract and must keep physical balance, personal value, economic allocation, and debt distinct.

Gate 2 still requires its final device verification before it can formally close.

## Goal

A user should understand four things for every shared-account action:

1. which account balance changes;
2. whose money funds the action;
3. whose income or expense it represents;
4. whether the action creates debt between people.

The current data model separates most of these meanings, but several Android flows present them ambiguously or do not support the required action.

## Problems and proposed changes

| Priority | Problem | Proposed change |
|---|---|---|
| P0 | Money can enter a shared account through a contribution, but money cannot leave through an equivalent ownership-aware flow. Shared-to-personal and shared-to-shared transfers are blocked. | Add an explicit withdrawal flow and define shared-to-shared movement. Ask who receives or owns the withdrawn money and represent each balance change clearly. |
| P0 | Income recorded directly into a shared account is treated entirely as the app owner's actual income. | Add explicit income ownership: mine, another member's, or shared with a defined allocation. Deposit location must not decide economic ownership. |
| P0 | A shared-account expense says the account pays while the form still shows `Pago jo` and a participant payer selector. | When the selected account is shared, show the shared account as the funding source and use the split only to answer who consumed the expense. Hide incompatible payer controls. |
| P0 | The account activity list leads with the user's expense share, although the full amount left the account. | Make movement rows context-aware. Personal views lead with the user's economic share; account activity leads with the account delta and shows the user's share second. |
| P1 | Contribution rows do not consistently show contributor, source, destination, or direction. | Render contributions as a clear route, for example `Anna → Compte conjunt · 200 €`, and use contribution-specific labels in details. |
| P1 | Contributions cannot be edited and their form can be dismissed without the usual unsaved-change protection. | Allow correction of amount, date, contributor, source, name, and notes, or provide an explicit replace flow. Apply the standard dismissal guard. |
| P1 | A contribution does not explain its effect on personal value, ownership percentages, or debt. | Before saving, preview affected balances and state that the contribution does not change ownership percentages or settle debt. |
| P1 | The account page is primarily a ledger, while shared-account actions and key meanings are dispersed. | Make it the management centre: physical balance, owner's value and percentage, members, actions for expense/contribution/withdrawal, and activity. |
| P2 | Creating a shared account automatically enables the first person and assigns a 50/50 split. People cannot be created inline. | Require explicit member selection, offer inline person creation, then offer equal splitting as an action. |
| P2 | Account pickers show only name and colour, so shared accounts are not recognizable before selection. | Mark shared accounts in pickers and show enough secondary context where ownership changes the form. |
| P2 | Ownership and default expense percentages are labelled too tersely, and ownership edits do not preview their effect. | Add short explanations and preview the resulting personal account value before saving. |
| P2 | Recurring contributions are unavailable. | Add them after one-off money-in and money-out flows are stable. |

## Intended flows

### Create a shared account

1. Create or edit an account.
2. Enable **Compte compartit**.
3. Select members explicitly, with an inline action to create a person.
4. Set ownership percentages and default expense allocation.
5. Show separate live totals for both columns.
6. Preview the physical balance and the app owner's resulting value.
7. Save.

Suggested explanations:

- **Propietat:** quina part del saldo forma part del teu patrimoni.
- **Despeses per defecte:** com es repartiran les noves despeses; es pot canviar en cada moviment.

### Record a shared-account expense

1. Start an expense and select a shared account.
2. Show **Pagat des de: [shared account]**.
3. Pre-fill the account's default expense allocation.
4. Let the user change who consumed the expense.
5. Preview the full account debit and the app owner's economic expense.
6. Save without creating interpersonal debt.

Example:

- Account movement: **−60 €**
- Your expense: **30 €**
- Allocation: **Tu 30 € · Anna 30 €**

### Add money

Use **Aportació** when existing money is placed into a shared account.

1. Select the contributor.
2. If the app owner contributes, select a personal source account or an external source.
3. Show source and destination balance changes.
4. Explain that ownership percentages and interpersonal debt remain unchanged.
5. Save and display the complete direction in the ledger.

### Record income

Use **Ingrés** when the money is economically new income.

1. Select the destination account.
2. If it is shared, choose whose income it is: mine, another member's, or shared.
3. For shared income, define or confirm its economic allocation.
4. Preview the amount counted in the app owner's actual income.
5. Save.

### Move money out

1. Start from the shared account's **Treu diners** action or the general movement flow.
2. Select the destination.
3. Identify the receiving member or ownership meaning when required.
4. Preview both physical account changes and the app owner's value change.
5. Save with a clear withdrawal or shared-transfer representation.

### Review activity

- Personal movement lists lead with the app owner's economic amount.
- A shared account's activity leads with the physical account delta.
- Detail pages always label total amount, personal amount, funding source, allocation, and debt effect when applicable.
- Contributions and withdrawals show contributor or recipient plus source and destination.

## Decided semantics

Step 1 of the implementation order. These meanings are fixed; the contract work in step 2 implements them. Every rule below keeps physical balance, owner patrimonial value, economic allocation, and interpersonal debt separate.

### Money out of a shared account

A withdrawal is the exact mirror of a contribution: an active member takes money out of an active shared account.

- It is not income, not expense, and not a settlement. It creates no debt and does not change ownership percentages.
- The shared account loses the full amount. When the app owner withdraws, an active personal destination account may be named and receives the paired positive flow; an unnamed destination is money leaving to the outside.
- A person member withdraws to the outside only, because the app does not track that person's accounts. This mirrors the existing rule that a person's contribution cannot name a source account.
- The owner's value follows the reduced balance through the unchanged ownership percentage.

Known ceiling: ownership is a stated proportion, not a per-member capital account. A member who takes out more than their share reduces every member's value proportionally, and the correction is an explicit ownership-percentage edit. Per-member capital accounts stay out of Gate 3.

Data implication: `account_contributions` gains a direction so one table holds member money in and out; `v_account_flow` reverses both signs for an outward row, and the source-account rules become source-or-destination rules. The table keeps its name to avoid a rename across both platforms, and the contract describes it as member money entering and leaving a shared account.

### Movement between accounts

- Personal to shared and shared to personal remain a contribution or a withdrawal. The ownership meaning stays explicit rather than hidden inside a transfer.
- Shared to shared is an ordinary transfer: both physical balances change, nothing becomes income, expense, or debt, and each account's owner value follows its own ownership percentage.
- Transfers stay uncategorized and unsplit.

No schema change: only the Android rule that blocks any transfer touching a shared account narrows to the personal-versus-shared crossing.

### Income ownership

Deposit location never decides economic ownership.

- An income into a shared account may carry an allocation split with the same shape as an expense split. The app owner's line is actual income; the rest is not.
- An income without a split stays wholly the app owner's income, which keeps every existing income correct.
- Shared-account income creates no debt in either direction. The money sits in the pot and the ownership percentage governs it, exactly as for a shared-account-financed expense.
- Income into a personal account stays wholly the app owner's. Allocating it would mean holding another person's money, which is a debt question and is deferred out of Gate 3.

Contract implications: `v_actual_income` takes the app owner's split line when the income carries a split and the full amount otherwise; `v_person_balance` restricts its split-line term to expense movements so an income allocation can never become debt. No new column: the split reaches the movement through `movement_id`, and a lost split degrades to the safe default of wholly-owner income.

## Implementation order

1. Decide and document the finance semantics for withdrawals, shared-to-shared movements, and income ownership. Done, in [Decided semantics](#decided-semantics).
2. Extend the shared contract for those semantics. Done at schema 17: `account_contributions.direction`, the matching `v_account_flow` signs, `v_actual_income` reading the app owner's split line, `v_person_balance` restricted to expense movements, migration 017, golden cases for a withdrawal and an allocated income, Android bindings, and both harnesses.
3. Correct the Android expense form and account-context movement presentation. Done: on a shared account the expense form names the account it is paid from, drops the payer controls, and previews the account debit beside the owner's expense; every account ledger leads with the canonical `v_account_flow` delta and captions the owner's share beneath a shared expense.
4. Implement contribution presentation, editing/correction, and dismissal protection. Done: a contribution row reads as a route from its contributor or source account into the shared account; its detail names who contributes, the source, and the shared account; Edit opens the contribution form, which corrects amount, date, contributor, source, name and notes while keeping the shared account and direction; the form asks before discarding changes and says a contribution neither changes ownership nor settles debt. Every contribution is inward until step 5, so `direction` reaches `v_movement_summary` with the withdrawal flow.
5. Build the shared-account detail overview and contextual actions. Done at schema 18: every account page states the balance its ledger reconciles to, and a shared account's page adds the owner's value and percentage, its members, and Despesa, Aportació and Treu diners actions. A withdrawal reuses the contribution form in the outward direction, states each account's change as the amount itself, and describes the effect on ownership in words rather than recomputing the owner's value. `v_movement_summary` gains `contribution_direction`, so withdrawals read out of their shared account wherever they are listed. Transfers between two shared accounts are ordinary transfers; a transfer form crossing ownership in either direction offers the matching contribution or withdrawal.
6. Improve account creation and ownership editing. Done: making an account shared makes nobody a member; the owner switches members on, can add a person from the form, and splits evenly with the existing action. The members section explains ownership and default expenses, and states the owner's percentage as a share of the account's balance without recomputing its euro value. Account pickers mark shared accounts.
7. Complete focused tests and verify every flow on a real device.

## Acceptance criteria

- A shared account supports understandable money-in and money-out flows.
- Depositing into a shared account never silently decides whose income it is.
- Shared-account expenses have one visible funding source and no contradictory payer controls.
- Account activity reconciles to the physical balance using the primary displayed amounts.
- Personal spending and analysis continue to use the app owner's economic share.
- Contribution and withdrawal rows name their direction and participants without opening details.
- Every detail page distinguishes account movement, personal economic effect, and debt effect.
- Ownership changes preview their effect on personal value.
- Forms protect unsaved changes and preserve entered data across valid flow transitions.
- Canonical queries, Android tests, the Windows contract harness, and device verification pass.
