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

The current schema version is `11`. Principal tables are:

| Area | Tables |
|---|---|
| Reference data | `accounts`, `categories`, `people`, `trips`, `tags` |
| Ledger | `movements`, `splits`, `split_lines` |
| Automation | `templates` |
| Planning/import | `budgets`, `import_batches` |
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
| `v_actual_expense` | User-owned expense net of shared portions and refunds |
| `v_actual_income` | User-owned income |
| `v_person_balance` | Derived debt direction and amount per person |
| `v_movement_shared` | Shared-movement helper data |
| `v_movement_summary` | Unified movement/external-split list and detail projection |
| `v_trip_actual_total` | Derived actual expense per trip |

Balances, debt, actual values, and flow must come from these views or shared queries built on them. Do not persist their results as truth or recalculate them ad hoc in Kotlin or C#.

## Golden procedural rules

Rules that are awkward or inappropriate to encode as SQL are implemented natively on both platforms and locked by JSON vectors:

- `split_rounding.json` — deterministic cent allocation;
- `template_split_rescale.json` — recurring split rescaling;
- `recurring_advance.json` — recurrence advancement;
- `duplicate_detection.json` — duplicate-warning candidates;
- `refund_actual.json` — expense net of refunds;
- `debt_balance.json` — person balance derivation;
- `debt_consumption.json` — chronological settlement-versus-debt consumption;
- `account_flow.json` — account flow cases.

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

Schema version `11` remains the implemented authority until each vertical slice ships. The following changes are approved targets; exact names may be refined during implementation, but their meanings and invariants are fixed by [pre-windows-plan.md](pre-windows-plan.md).

### Savings goals

- Add goals with target amount, optional target date, optional linked account, visual identity, and active/completed/paused state.
- A dedicated-account goal may derive progress from that account's canonical value.
- Goals sharing an account use dated planning allocations. Allocations reserve meaning only: they never create ledger flow, income, expense, or debt.
- Active allocations for one account must not exceed the funds available for allocation; the UI exposes any unallocated remainder.

### Shared accounts

- Add account membership/ownership data for the app owner and known people.
- Separate physical account balance from the owner's patrimonial share.
- Record whether an expense was financed by the app owner, a person, or the shared account itself; keep economic allocation in split lines.
- Add contributions for money placed into a shared account without treating them as income, expense, transfer between owned accounts, or debt settlement.
- Ownership percentages and default expense splits are explicit configuration. Do not attempt member capital-account accounting or infer exact historical ownership from deposits and consumption.

### Investment valuations

- Keep contributions and withdrawals as ledger transfers.
- Add dated absolute account valuations with source metadata; manual entry is the first version.
- Preserve separate canonical meanings for ledger cash-flow balance, current account value, net contributed capital, and unrealized gain/loss.
- Normal accounts derive current value from opening balance plus flow. Investment accounts derive current value from their latest valuation.
- Net worth uses current account value; market variation is not income or expense.
- Holdings, trades, units, live prices, dividends, corporate actions, and multiple currencies remain out of scope.

Each slice requires fresh DDL, a forward migration, canonical queries/views, Android bindings and UI, golden vectors where money behaviour changes, and passing Android plus .NET contract tests before its gate closes.
