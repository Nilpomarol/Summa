# Data Contract

## Authority

`shared/` is the cross-platform finance contract:

- `shared/schema/schema.sql` — current fresh-install schema;
- `shared/migrations/` — ordered upgrades and `meta.schema_version` changes;
- `shared/queries/` — canonical views and parameterized analysis queries;
- `shared/golden/` — executable procedural money rules;
- `shared/schemas/` — JSON payload schemas;
- `shared/design/tokens/design-tokens.json` — platform-neutral visual tokens.

Android consumes the SQL through SQLDelight. Windows uses Microsoft.Data.Sqlite and Dapper. Platform code may map results but must not replace the finance derivations with its own queries.

## Schema snapshot

The current schema version is `16`. Principal tables are:

| Area | Tables |
|---|---|
| Reference data | `accounts`, `account_members`, `categories`, `people`, `trips`, `tags` |
| Ledger | `movements`, `splits`, `split_lines` |
| Automation | `templates` |
| Planning/import | `budgets`, `goals`, `goal_allocations`, `account_contributions`, `import_batches` |
| System | `meta` |

The SQL files are the field-level authority. Documentation explains the contract but does not duplicate every column.

## Ledger integrity

- `amount_cents > 0`; type determines direction.
- Expense/income account fields, transfer destination, settlement person/direction/scope, and refund source must satisfy the schema `CHECK` constraints.
- A settlement records the scope of debt it may consume (`all` or `recurring`); a template of type `settlement` carries a person, direction, and scope, and no category, trip, tag, or split.
- A transfer references different source and destination accounts.
- Split lines are absolute non-negative amounts and reconcile with their split total; zero is valid for a 100/0 share.
- A split line represents either the user or one person, never both.
- Active rows are selected with `archived_at IS NULL` where absence is intended.
- Movement dates and UTC timestamps retain separate types and meanings.

Application validation should mirror important database constraints to provide useful Catalan feedback, but the database remains the final integrity boundary.

## Canonical derived views

| View | Meaning |
|---|---|
| `v_account_flow` | Signed per-account effect of ledger activity |
| `v_account_balance` | Opening balance plus derived active flow |
| `v_account_value` | Physical balance, owner ownership percentage, and owner patrimonial value |
| `v_actual_expense` | User-owned expense net of shared portions and refunds |
| `v_actual_income` | User-owned income net of any allocation to other members |
| `v_person_balance` | Derived debt direction and amount per person |
| `v_movement_shared` | Shared-movement helper data |
| `v_movement_summary` | Unified movement/external-split list and detail projection. Carries the account's, the destination account's and the trip's own colour alongside their names; the external-split branch has no accounts, so both account colours are `NULL` there. A contribution row also carries its `contribution_direction`; every other row leaves it `NULL` |
| `v_trip_actual_total` | Derived actual expense per trip |
| `v_goal_allocation` | Signed sum of a goal's active planning allocations |
| `v_goal_progress` | Saved and remaining cents per goal, by funding mode |
| `v_account_allocation` | Account value split into allocated and unallocated |

Balances, debt, actual values, and flow must come from these views or shared queries built on them. Do not persist their results as truth or recalculate them ad hoc in Kotlin or C#.

## Savings goals

- A goal carries a positive target in cents, an optional local target date, optional visual identity, and `active` / `paused` / `completed` state.
- `funding_mode` decides where progress comes from. `dedicated_account` requires an account and follows that account's canonical value. `allocations` sums the goal's active `goal_allocations`, which is what lets several goals share one account.
- An allocation is a dated, signed, non-zero reservation against one account. Positive reserves, negative releases. Allocations are never ledger rows: they produce no movement, account flow, actual income or expense, debt, or net-worth change.
- `v_account_allocation` reports each account's balance split into `allocated_cents` and `unallocated_cents`. Only non-archived allocation-mode goals reserve value, so archiving a goal releases its reservation and restoring it takes it back. Pausing or completing a goal keeps the money set aside.
- Over-allocating is a dismissible warning, not an error: an account value can legitimately drop after the plan was made, and `unallocated_cents` may go negative. Releasing more than a goal holds is refused, because negative progress is meaningless.
- An account dedicated to a goal does not also host allocations. Repository mutations validate exclusivity transactionally, including restore and funding-mode changes.
- `goal_account_allocations.sql` is the canonical reservation total per goal/account. Each account total must remain nonnegative through create, edit, delete, and restore; invalid mutations roll back. Changing funding mode requires zero outstanding reservations.
- Monthly pace counts calendar months inclusively, rounding cents up. A target date before today has zero funding periods and is overdue unless already reached; a target today still has one period.

## Shared accounts

- A personal account is wholly owner-controlled. A shared account has one app-owner member and one or more active existing people, with ownership and default expense percentages stored as integer basis points. Each percentage set totals 10,000. SQLite validates the complete set when an account becomes shared; member changes are staged while it is personal and then validated atomically.
- `v_account_balance` remains the physical balance. `v_account_value` rounds the owner percentage of that balance to the nearest cent, symmetrically for negative values; net worth and dedicated-goal value consume this patrimonial amount.
- Split lines describe economic consumption. `movements.expense_funding` independently records whether the app owner or the shared account financed an expense. A shared-account-financed expense changes the full physical balance and the user's split changes actual expense, but it creates no person-to-owner debt. Such an expense names the split it is consumed through in `movements.shared_split_id`, written before the split itself inside the same transaction on a deferred foreign key, and it keeps that identifier for life.
- `account_contributions` records an active member moving money into or out of an active shared account; `direction` says which way. `source_account_id` names the app owner's own account on the other side, the source of an inward row and the destination of an outward one, and creates the paired opposite flow; a person's row names no account in either direction, because the contract does not track their accounts. Neither direction is actual income, actual expense, a settlement, or debt, and neither changes ownership percentages: ownership stays a stated proportion rather than a per-member capital account, so a member taking out more than their share moves every member's value proportionally until the percentages are edited.
- An income into a shared account may carry an allocation split of the same shape as an expense split. `v_actual_income` counts the app owner's line alone; an income without a split is wholly the app owner's. The money sits in the pot and the ownership percentage governs it, so a shared-account income creates no debt in either direction. Debt therefore reads split lines from expense movements only, and the writing repository refuses an allocation on a personal account's income.
- Ordinary transfers remain between accounts of the same ownership: personal to personal, or shared to shared, where each account's owner value follows its own percentage. Crossing between personal and shared ownership uses a contribution or a withdrawal, so the ownership meaning stays explicit; the contract does not infer member capital balances or historical ownership from these flows.
- Ownership is one-way once used: an account that financed a shared expense or carries member money in or out stays shared, archived rows included, because both keep naming a shared account and can be restored. Membership staging makes every account update pass transiently through `personal`, so this rule is enforced by the writing repository against the caller’s intended ownership rather than by a trigger on the transition.

## Golden procedural rules

Rules that are awkward or inappropriate to encode as SQL are implemented natively on both platforms and locked by JSON vectors:

- `split_rounding.json` — deterministic cent allocation;
- `template_split_rescale.json` — recurring split rescaling;
- `recurring_advance.json` — recurrence advancement;
- `duplicate_detection.json` — duplicate-warning candidates;
- `refund_actual.json` — expense net of refunds;
- `debt_balance.json` — person balance derivation;
- `debt_consumption.json` — chronological settlement-versus-debt consumption;
- `account_flow.json` — account flow cases;
- `goal_progress.json` — savings-goal progress and required monthly pace.

Change a golden vector first when intentionally changing one of these rules. Then update both implementations. Never weaken an expected result merely to make a test pass.

## Atomic change protocol

A schema or shared finance-rule change is one change set:

1. Update the relevant golden vector first when behaviour changes.
2. Update `schema.sql` when the fresh schema changes.
3. Add a migration and bump `meta.schema_version`; never rewrite an already-shipped migration.
4. Update every affected canonical view/query, including copies embedded in migrations.
5. Regenerate or update Android SQLDelight bindings/migration sources.
6. Update Android and Windows mappings or procedural implementations.
7. Update this document only when the durable contract changes.
8. Run both platform test harnesses.

## Verification

From `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

From the repository root:

```powershell
dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj
```

Any red golden test blocks delivery of a money-rule or shared-contract change.


## Approved pre-Windows contract changes

Schema version `18` is the implemented authority. Savings goals and shared accounts have shipped and are described above with the rest of the contract. The remaining change is an approved target whose meanings and invariants are fixed by [pre-windows-plan.md](pre-windows-plan.md).

### Investment valuations

- Keep contributions and withdrawals as ledger transfers.
- Add dated absolute account valuations with source metadata; manual entry is the first version.
- Preserve separate canonical meanings for ledger cash-flow balance, current account value, net contributed capital, and unrealized gain/loss.
- Normal accounts derive current value from opening balance plus flow. Investment accounts derive current value from their latest valuation.
- Net worth uses current account value; market variation is not income or expense.
- Holdings, trades, units, live prices, dividends, corporate actions, and multiple currencies remain out of scope.

Each slice requires fresh DDL, a forward migration, canonical queries/views, Android bindings and UI, golden vectors where money behaviour changes, and passing Android plus .NET contract tests before its gate closes.
