# Architecture

## Topology

Gestor Finances uses two native applications around one SQLite contract:

```text
Android (current writer) ─┐
                          ├─ shared schema, SQL, golden rules, tokens
Windows (future surface) ─┘
```

There is no server database. Each platform opens a local SQLite file. Android is currently the only product surface and therefore the only writer in practice.

## Shared logic strategy

The repository shares behaviour in two forms:

1. Schema and set-based finance logic are single-sourced as SQL under `shared/` and consumed verbatim by both platforms.
2. Procedural rules are implemented in Kotlin and C#, with equivalence enforced by the same `shared/golden/*.json` vectors.

This avoids a cross-platform runtime while still preventing Android/Windows finance drift.

## Android

Stack: Kotlin, Jetpack Compose, SQLDelight, SQLite, coroutines, and `StateFlow`.

The working dependency direction is:

```text
Compose screen → ViewModel/state → domain rule or repository → SQLDelight → SQLite
```

- Screens render state and send user intent; they do not perform finance queries.
- ViewModels coordinate forms, warnings, navigation-facing state, and repository work.
- Repositories are the database boundary and map SQLDelight rows into domain/UI-ready models.
- Pure domain rules cover recurrence, split allocation, duplicate detection, and categorization.
- `AppContainer` owns lightweight application dependencies. Avoid speculative service layers or interfaces without a real second implementation.
- Long-running database work uses the IO dispatcher; UI state is exposed as immutable flows/state.
- User-visible strings live in Android resources and are Catalan.

The build prepares the shared schema and analysis SQL for SQLDelight. A shared query must not gain an Android-only semantic fork.

## Windows

The current `windows/` project is a .NET test harness for the shared contract. The future app uses C#, WinUI 3, Microsoft.Data.Sqlite, Dapper, and simple MVVM.

Windows will execute the shared schema and canonical SQL directly. EF Core and finance logic expressed through ORM queries are out of scope. Desktop-only CSV import belongs in the Windows application and commits each accepted batch atomically.

See [windows-plan.md](windows-plan.md) for the preserved implementation sequence.

## Transactions and warnings

Operations that must succeed together use one database transaction: movement plus split, recurring confirmation plus template advancement, quick-template creation plus movement save, and CSV batch import.

Repositories return failures; ViewModels translate them into externalized user feedback. Valid but risky actions use a dismissible warning and explicit continuation. Invalid database shapes remain errors.

## Backup today

Android can export and restore an unencrypted `.gfbackup` SQLite snapshot through the Storage Access Framework.

- Export increments `meta.snapshot_version` and creates a consistent single-file image with `VACUUM INTO` when supported.
- The Android compatibility fallback checkpoints WAL, closes the database, copies the stable main file, and recreates the app container.
- Restore validates SQLite integrity, required objects, and metadata before replacing the database.
- Older or same-version manual backups warn but may be restored deliberately.
- An optional Android WorkManager job requests one daily automatic export to the selected folder. It only uses the live `VACUUM INTO` path; if that safe path is unavailable, it retries later rather than closing the active database for the compatibility fallback.
- This format is not the future encrypted sync format.

## Future sync contract

Android/Windows synchronization is manual handoff, not merging:

- Exactly one device holds the control token and may write; the other is strictly read-only.
- The writer sends monotonically versioned, consistent SQLite snapshots. A receiver rejects a sync snapshot whose version is not newer.
- Applying a snapshot is atomic: decrypt to a temporary file, validate, fsync, then rename over the live database.
- Snapshots use a distinct `.gfsnap` format encrypted with AES-256-GCM. The user passphrase is processed with PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt, 256-bit key); each file uses a unique 12-byte nonce and authenticates its header as AAD.
- The exchange location contains a token marker naming the writer, session, operation, and referenced snapshot.
- Desktop can emit checkpoints while keeping control. Returning control emits a final checkpoint and makes desktop read-only.
- A lost desktop session is never reclaimed automatically. Mobile stays read-only until the user explicitly discards/reclaims the session; later files from a discarded session are never applied silently.
- Read-only state must be prominent and editing affordances visibly disabled.

The current Android `DeviceAccessState` is only a shell seam and always reports writer. Real handoff is not implemented.

## Dependency and change discipline

- Prefer existing platform/library capabilities over new dependencies.
- Build vertical slices and change the deepest correct layer when a UI discovery exposes a logic problem.
- Shared-contract changes follow [data-contract.md](data-contract.md) atomically.
- Treat the Android database as potentially real user data: do not clear or seed it casually.
