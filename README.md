# Summa

Local-first personal finance software for one person, in euros, with a Catalan interface.

Android is the working application and owns the primary SQLite database. Windows currently contains the shared-SQL and golden-vector test harness; its WinUI application has not been built yet.

## Current direction

The Android UI redesign is complete. Personal Compass, the current navigation, shared components, light/dark themes, and durable interaction rules are the stable mobile baseline. Earlier redesign roadmaps, audits, and remediation trackers remain retired and must not be revived.

Development now follows the mandatory [pre-Windows plan](docs/pre-windows-plan.md): recurring settlements and explainable debt messages, savings goals, shared accounts, and investment valuations. Windows product work begins only after those four gates are implemented, migrated, tested, and documented.

Optional cloud-linked multiwriter synchronization remains a later phase after Windows core. The existing encrypted single-writer snapshot design remains the Local only mode.

## What exists

- Android app in Kotlin, Jetpack Compose, SQLDelight, and SQLite.
- Accounts, categories, movements, transfers, refunds, people, splits, debts, settlements, recurring templates, budgets, trips, tags, dashboard, analysis, notifications, and local backup/restore.
- Shared SQLite schema, migrations, canonical finance views/queries, JSON schemas, design tokens, and golden money-rule vectors.
- .NET validation harness using Microsoft.Data.Sqlite and Dapper.
- No Windows UI and no cross-device synchronization yet.

## Product behaviour

- [Budget behaviour](docs/budget.md)

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
- [Design](docs/design.md) — completed Android UI baseline and durable design rules.
- [Pre-Windows plan](docs/pre-windows-plan.md) — mandatory ordered feature gates before desktop work.
- [Windows plan](docs/windows-plan.md) — desktop implementation after the pre-Windows gates.
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
