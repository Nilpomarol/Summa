# Data Contract

## Authority

The SQL files are the field-level authority:

- `shared/schema/schema.sql` — fresh-install schema;
- `shared/migrations/` — ordered forward migrations;
- `shared/queries/` — canonical finance views/queries;
- `shared/golden/` — focused procedural finance cases.

The current schema version is `21`.

Android consumes the contract through SQLDelight. The .NET project is currently a validation harness for the same contract.

## Core integrity

- Money is integer euro cents; ledger amounts are positive and type/related fields determine meaning.
- Movement `date` is a local calendar date; `*_at` fields are UTC instants.
- Transfers use different source/destination accounts.
- Split lines are absolute non-negative amounts and reconcile with their split total. Every split belongs to a movement.
- An expense has one movement identity whoever paid it. `payer_person_id` is NULL when the owner paid from `account_id`. An expense another person paid names that person, has no account, never recurs, and carries a split whose owner line is what the owner owes the payer. Who paid is read only from `payer_person_id`, never from a split's shape: an owner line of 0 can also mean the owner paid entirely for someone else.
- Normal absence uses `archived_at IS NULL`; finance data is normally soft-deleted.
- Database constraints are the final structural-integrity boundary; app validation should provide clearer Catalan feedback before they are hit.

## Canonical finance truth

These meanings are derived and must not be persisted or independently recomputed in app code:

- account flow and physical balance;
- account value / owner patrimonial share;
- actual income and expense;
- person debt/balance;
- trip actual expense;
- savings-goal progress and account allocation.

`v_movement_summary` is a read projection for the current ledger UI, not a reason to force every future platform or presentation concern into one universal model.

## Shared accounts

- `v_account_balance` is the physical account balance.
- `v_account_value` is the app owner's patrimonial share.
- Expense/income splits describe economic allocation independently from account ownership.
- Shared-account contributions/withdrawals move member money into/out of the account without becoming income, expense, settlement, or debt.
- Crossing between personal and shared ownership uses those explicit contribution/withdrawal semantics; same-ownership account movement may remain a transfer.
- Shared-account ownership percentages are stated proportions, not inferred per-member capital accounts.

## Savings goals

- Goals reserve existing money and do not create ledger flow.
- `dedicated_account` progress follows that account's canonical value.
- `allocations` progress comes from dated planning allocations.
- Over-allocation is a warning; impossible negative reservation states remain invalid.

## Golden rules

Keep golden vectors only for rules where exact behaviour matters across implementations, such as:

- split rounding;
- recurring advancement/rescaling;
- duplicate detection;
- refund actuals;
- debt balance/consumption;
- account-flow edge cases;
- goal progress.

When intentionally changing one of these rules, update the focused expected case together with the implementation. Do not weaken tests merely to make code pass.

## Changing the contract

For a genuine schema/canonical-finance change:

1. Update `schema.sql` for fresh installs.
2. Add a forward migration and bump `meta.schema_version`; do not rewrite shipped migrations.
3. Update only the affected canonical SQL and focused golden cases.
4. Update Android bindings. The Android build generates its SQLDelight inputs from `shared/`: migrations and non-view queries (exposed under their camelCased file name) are discovered automatically; a new `v_*.sql` view must be added to the dependency-ordered `sharedViewFiles` list in `android/app/build.gradle.kts`.
5. Update mappings/tests in affected implementations or validation harnesses.
6. Update this document only if a durable meaning changed.

Do not turn ordinary Android UI/repository work into a shared-contract change.

## Verification

Android, when affected:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

.NET shared-contract harness, when the shared contract is affected:

```powershell
dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj
```

## Planned investment semantics

Investment valuation is future product work. When implemented, market value must stay separate from ledger cash flow and actual income/expense. Details belong in [backlog.md](backlog.md), not in the current schema contract until the feature is implemented.
