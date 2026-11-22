# Architecture

## Current system

Summa is an Android application backed by a local SQLite database, with a Windows desktop app on the same database contract and shared code. Android is the working product and the implementation authority.

```text
Compose UI
    ↓
ViewModel / screen state
    ↓
repository or small domain rule
    ↓
SQLDelight
    ↓
SQLite
```

The goal is traceability, not architectural layering for its own sake.

## Modules

- `android/core` — plain Kotlin/JVM: SQLDelight bindings, repositories, pure finance rules, backup validation and file replacement, and date/money formatting, with their tests. No Android or UI dependency; each app supplies the SQLite driver.
- `android/ui` — Kotlin Multiplatform (Android + desktop): the theme, shared components and ViewModels, and every Catalan string. The strings are one Android `strings.xml` (`ui/src/androidMain/res`): Android-only code reads it as `R.string`, shared code as `Res.string` from a copy the build generates with Android's escapes resolved. Shared state holds `StringResource`, never an Android resource id.
- `android/app` — the Android app: Compose UI, ViewModels, backup, notifications.
- `android/desktop` — the Windows app: Compose Desktop on `:core` and `:ui`, desktop pages over the shared ViewModels, its database under `%APPDATA%\Summa`. See `docs/future/windows.md`.

## Android boundaries

- Screens render state and send user intent; they do not run finance queries.
- ViewModels coordinate screen/flow state, validation, warnings, and repository work.
- Repositories are the database boundary and may return UI-ready/domain-ready models when that keeps the flow simple.
- Pure helpers are appropriate for deterministic rules such as recurrence, split allocation, or duplicate detection.
- `AppContainer` owns lightweight application dependencies.
- Navigation Compose owns the page back stack. Each page visit gets its own ViewModel, built next to its screen from `AppContainer`, and cross-feature context travels as route arguments. Only state that outlives a page is app-wide: the movement sheets, recurring reminders, settings/restore effects, and onboarding.
- Prefer concrete implementations. Do not add interfaces, service layers, coordinators, buses, or abstractions without a current need.
- Prefer screen/flow-local state over whole-app orchestration.
- Use IO dispatching for database work and immutable observable UI state.

## Data and finance truth

`shared/schema`, `shared/migrations`, and the genuinely canonical SQL under `shared/queries` define the database contract. Canonical finance derivations include account flow/balance/value, actual income/expense, debt, trip actuals, and goal progress.

Platform code may map these results but must not create another source of financial truth.

Procedural finance rules that need exact parity are covered by focused golden vectors under `shared/golden`.

See [data-contract.md](data-contract.md).

## Transactions and warnings

Operations whose records must stay consistent are written in one transaction—for example a movement with its split or recurring confirmation with template advancement.

Valid but risky user choices use an understandable warning and explicit continuation. Database-invalid shapes remain errors.

## Backup

Android exports/restores whole-database `.gfbackup` snapshots through the Storage Access Framework. Restore validates the candidate before replacing the live database. It first backs up the database being replaced to the same folder when it can, and accepts a snapshot from an older schema, which is migrated on the next open. The app is excluded from Android's own cloud backup, so the folder is the only copy. Automatic backup is on by default once a folder is chosen: it runs on the configured cadence, and opening the app backs up straight away when one is due. Each backup prunes the folder to the five newest plus the newest of each of up to three earlier months.

Synchronization with the desktop app is separate from backup: the two devices merge their records directly over the local network, paired once by a code; see `docs/future/sync.md`.

## Change discipline

- Prefer the smallest correct change in the existing flow.
- Remove dead or speculative seams instead of preserving them for hypothetical future work.
- Do not let a future Windows app or future sync design force current Android abstractions.
- Shared-contract changes follow [data-contract.md](data-contract.md).
- Treat the live Android database as potentially real user data.

## Future architecture

The desktop app and synchronization are intentionally outside the current architecture contract. Their current ideas live under `docs/future/` and are planning context only. They must not justify present-day infrastructure unless implementation of that future work has actually started.
