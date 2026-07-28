# Gestor Finances

Local-first personal finance software for one person, in euros, with a Catalan interface.

Android is the working application and owns the primary SQLite database. Windows currently contains the shared-SQL and golden-vector test harness; its WinUI application has not been built yet.

## Current direction

The project has entered a fresh Android redesign phase. It intentionally has no predefined backlog, target aesthetic, or completion checklist. Work is chosen one page, feature, interaction, or supporting code area at a time, and may expand into related side work when useful.

Earlier Android roadmaps and remediation trackers have been retired. Their unfinished items are not obligations. The implemented code, the shared data contract, and the current concise documentation are the starting point.

The future Windows implementation remains planned and unchanged in intent. It begins when the Android product is considered stable enough to port.

## What exists

- Android app in Kotlin, Jetpack Compose, SQLDelight, and SQLite.
- Accounts, categories, movements, transfers, refunds, people, splits, debts, settlements, recurring templates, budgets, trips, tags, dashboard, analysis, notifications, and local backup/restore.
- Shared SQLite schema, migrations, canonical finance views/queries, JSON schemas, design tokens, and golden money-rule vectors.
- .NET validation harness using Microsoft.Data.Sqlite and Dapper.
- No Windows UI and no cross-device synchronization yet.

## Repository

```text
android/   Android application and tests
windows/   .NET shared-contract test harness; future WinUI app
shared/    Cross-platform schema, SQL, golden vectors, JSON schemas, and tokens
docs/      Small set of current project contracts
```

## Active documentation

- [Product](docs/product.md) — current scope, capabilities, and durable behaviour.
- [Data contract](docs/data-contract.md) — schema, canonical SQL, migrations, and golden rules.
- [Architecture](docs/architecture.md) — app boundaries, shared wiring, backup, and future sync.
- [Design](docs/design.md) — current UI baseline and the open-ended redesign working model.
- [Windows plan](docs/windows-plan.md) — the preserved desktop implementation plan.
- [AGENTS.md](AGENTS.md) — concise operating rules for coding agents.

## Build and test

Android, from `android/`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Windows/shared harness, from the repository root:

```powershell
dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj
```
