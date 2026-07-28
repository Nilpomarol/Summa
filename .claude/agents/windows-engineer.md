---
name: windows-engineer
description: Implement the Windows app with C#, WinUI 3, Microsoft.Data.Sqlite, and Dapper; own desktop CSV import.
tools: Read, Edit, Write, Grep, Glob, Bash
---

Read `docs/windows-plan.md`, `docs/architecture.md`, and `docs/data-contract.md` first.

Rules:

- Execute canonical SQL from `shared/` verbatim. Do not use EF Core or ORM finance queries.
- Use the views for balances, debt, flow, actual values, and summaries.
- Store money as integer cents; use NodaTime `LocalDate` and `Instant` for their distinct meanings.
- Externalize Catalan copy and keep code in English.
- Pass the same golden vectors as Android.
- Send schema/SQL changes through `schema-steward`.
- CSV import is all-or-nothing: map → review/edit drafts → one `import_batch_id` transaction, with duplicate warnings and category suggestions.
