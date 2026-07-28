# Windows Implementation Plan

## Status and boundary

Windows currently contains only the .NET shared-SQL and golden-vector validation harness. The WinUI 3 product has not started.

This plan is preserved while Android enters an open-ended redesign. Windows implementation begins when the user decides the Android product language is stable enough to port; there is no remaining Android checklist that must be completed first.

The desktop app is a secondary native surface. It uses the same finance rules and data contract, while adopting desktop-appropriate navigation, density, keyboard interaction, and large-screen layouts. CSV import remains desktop-only.

## A — Shell and shared database

- Design the desktop shell, sidebar navigation, window chrome, Catalan string usage, and large-screen layout rules.
- Scaffold the WinUI 3 app.
- Wire Microsoft.Data.Sqlite and Dapper to the existing shared schema and canonical queries.
- Implement repositories and core entity mappings without EF Core or replacement finance queries.
- Port accounts, categories, movement list/detail, and add/edit movement.
- Keep the C# golden suite and schema/query parity checks green.
- Align the shell and core screens with the shared design tokens.
- Audit canonical SQL use, MVVM simplicity, and parity with Android core behaviour.

Exit: Windows launches against the real database and performs basic ledger work.

## B — Product parity

- Port people, splits, debts, settlements, refunds, recurring activity, budgets, trips, tags, dashboard, analysis, settings, and backup-facing states.
- Add the recurring calendar: month grid, real versus pending occurrences, monthly total, and month navigation.
- Build desktop-specific analysis layouts for charts, comparisons, filters, and detail exploration.
- Add repository tests, UI smoke tests, and golden coverage.
- Apply shared tokens with desktop-appropriate component layouts.
- Audit Android/Windows behavioural parity, canonical-query use, test coverage, and implementation simplicity.

Exit: Windows can view and edit everything intentionally shared with Android.

## C — CSV import

- Design the import wizard and review surfaces.
- Accept a CSV, detect or confirm column mappings using multilingual header aliases, and select the destination account.
- Convert rows into editable drafts; derive type from sign, suggest categories, flag duplicates, and allow row exclusion.
- Support applicable split, refund, and settlement transformations explicitly.
- Commit an accepted import as one atomic batch identified by `import_batch_id`.
- Provide batch history, review, and rollback.
- Test parsing, mapping, draft edits, deduplication, categorization, atomic failure, and rollback.
- Audit the desktop-only boundary, warning UX, and importer simplicity.

Exit: Windows has full intended parity plus reliable bank CSV import.

## Non-negotiable implementation rules

- Execute the `shared/` schema and finance SQL verbatim.
- Use canonical views for balances, debt, flow, actual values, and summaries.
- Store money as integer euro cents.
- Keep calendar dates separate from UTC audit instants.
- Externalize Catalan copy and keep code in English.
- Pass the same golden vectors as Android.
- Commit CSV batches atomically.
- Do not add authentication, multi-user support, currencies, a cloud database, or EF Core.
- Implement future token-based synchronization according to [architecture.md](architecture.md), without merge logic.
